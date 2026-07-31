import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, PanelRight } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import { useStomp, useTopic } from '../ws/StompContext'
import { usePresence } from '../ws/usePresence'
import { conversationTitle, displayName, indexLabel } from '../lib'
import ConfirmButton from './ConfirmButton'
import ContextPanel from './ContextPanel'
import MessageInput from './MessageInput'
import MessageList from './MessageList'
import PresenceDot from './PresenceDot'
import TypingIndicator from './TypingIndicator'

const TYPING_TIMEOUT_MS = 4000

// match by server id, or by clientMessageId for our own optimistic messages
function upsert(list, m) {
  const i = list.findIndex((x) =>
    (m.id != null && x.id === m.id) ||
    (m.clientMessageId && x.clientMessageId === m.clientMessageId && x.senderId === m.senderId))
  const next = i === -1 ? [...list, m] : list.map((x, j) => (j === i ? { ...x, ...m } : x))
  return next.sort((a, b) => (a.id ?? Number.MAX_SAFE_INTEGER) - (b.id ?? Number.MAX_SAFE_INTEGER))
}

const advance = (conv, userId, field, messageId) => conv && {
  ...conv,
  participants: conv.participants.map((p) =>
    p.userId === userId ? { ...p, [field]: Math.max(p[field] ?? 0, messageId) } : p),
}

const markDeleted = (list, messageId) =>
  list.map((m) => (m.id === messageId ? { ...m, deleted: true, content: null } : m))

export default function ChatWindow({ conversationId: id, onRead, onChanged, onDeleted, onRefresh }) {
  const { user } = useAuth()
  const { connected, publish, pushError } = useStomp()
  const [conv, setConv] = useState(null)
  const [messages, setMessages] = useState([])
  const [cursor, setCursor] = useState(null)
  const [hasMore, setHasMore] = useState(false)
  const [typing, setTyping] = useState({})
  const [showInfo, setShowInfo] = useState(false)
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState(null)
  const typingTimers = useRef({})
  const readUpTo = useRef(0)
  const messagesRef = useRef(messages)
  useEffect(() => { messagesRef.current = messages })

  useEffect(() => {
    api.conversation(id).then(setConv).catch(setError)
    api.messages(id).then((p) => {
      setMessages([...p.items].reverse())
      setCursor(p.nextCursor)
      setHasMore(p.hasMore)
    }).catch(setError)
  }, [id])

  useEffect(() => () => Object.values(typingTimers.current).forEach(clearTimeout), [])

  // fetch anything newer than what we have (after a reconnect or being re-added)
  const catchUp = useCallback(() => {
    const lastId = messagesRef.current.findLast((m) => m.id != null)?.id
    if (!lastId) return
    api.messages(id, { after: lastId, limit: 100 })
      .then((p) => setMessages((l) => p.items.reduce((acc, m) => upsert(acc, { ...m, pending: false }), l)))
      .catch(() => {})
  }, [id])

  useEffect(() => {
    if (connected) catchUp()
  }, [connected, catchUp])

  const setUserTyping = (userId, name, on) => {
    clearTimeout(typingTimers.current[userId])
    setTyping((t) => {
      const next = { ...t }
      if (on) next[userId] = name
      else delete next[userId]
      return next
    })
    if (on) typingTimers.current[userId] = setTimeout(() => setUserTyping(userId, name, false), TYPING_TIMEOUT_MS)
  }

  const removed = !!conv?.removedAt

  // removed members get no live events, the server would reject the subscribe
  useTopic(conv && !removed ? `/topic/conversations.${id}` : null, ({ type, payload: p }) => {
    if (type === 'MESSAGE') {
      setMessages((l) => upsert(l, { ...p, pending: false }))
      if (p.senderId !== user.id) setUserTyping(p.senderId, null, false)
    } else if (type === 'EDITED') {
      setMessages((l) => (l.some((m) => m.id === p.id) ? upsert(l, p) : l))
    } else if (type === 'DELETED') {
      setMessages((l) => markDeleted(l, p.messageId))
      setEditing((e) => (e?.id === p.messageId ? null : e))
    } else if (type === 'TYPING' && p.userId !== user.id) {
      setUserTyping(p.userId, p.username, p.typing)
    } else if (type === 'DELIVERED') {
      setConv((c) => advance(c, p.userId, 'lastDeliveredMessageId', p.messageId))
    } else if (type === 'READ') {
      setConv((c) => advance(c, p.userId, 'lastReadMessageId', p.messageId))
    }
  })

  useTopic('/user/queue/events', ({ type, payload: p }) => {
    if (type === 'REMOVED' && p.conversationId === id) {
      setConv((c) => c && { ...c, removedAt: p.removedAt })
      setTyping({})
      setEditing(null)
      return
    }
    if (type === 'ADDED' && p.conversationId === id) {
      api.conversation(id).then((c) => {
        setConv(c)
        onChanged(c)
      }).catch(() => {})
      catchUp()
      return
    }
    if (type !== 'ACK') return
    setMessages((l) => l.some((m) => m.clientMessageId === p.clientMessageId && m.senderId === user.id)
      ? upsert(l, { clientMessageId: p.clientMessageId, senderId: user.id, id: p.messageId, pending: false })
      : l)
  })

  // mark the newest message from others as read while the tab is visible.
  // removed members aren't in the participant list, so track what we sent locally too
  const myLastRead = conv?.participants.find((p) => p.userId === user.id)?.lastReadMessageId ?? 0
  useEffect(() => {
    const lastOther = messages.findLast((m) => m.id != null && m.senderId !== user.id)
    if (!conv || !lastOther || Math.max(myLastRead, readUpTo.current) >= lastOther.id) return
    const mark = () => {
      if (document.visibilityState !== 'visible' || readUpTo.current >= lastOther.id) return
      readUpTo.current = lastOther.id
      if (!publish(`/app/conversations.${id}.read`, { messageId: lastOther.id })) {
        api.markRead(id, lastOther.id).catch(() => {})
      }
      setConv((c) => advance(c, user.id, 'lastReadMessageId', lastOther.id))
      onRead(id)
    }
    mark()
    document.addEventListener('visibilitychange', mark)
    return () => document.removeEventListener('visibilitychange', mark)
  }, [messages, myLastRead, conv, id, user.id, publish, onRead])

  const others = conv ? conv.participants.filter((p) => p.userId !== user.id) : []
  const presence = usePresence(others.map((p) => p.userId))

  const receipt = (m) => {
    if (m.pending) return 'sending'
    if (!others.length || m.deleted) return null
    if (others.every((p) => (p.lastReadMessageId ?? 0) >= m.id)) return 'read'
    if (others.every((p) => Math.max(p.lastDeliveredMessageId ?? 0, p.lastReadMessageId ?? 0) >= m.id)) return 'delivered'
    return 'sent'
  }

  const nameOf = (userId, m) => {
    const p = conv?.participants.find((x) => x.userId === userId)
    return p ? displayName(p) : m.senderUsername
  }

  const sendRest = (clientMessageId, content) =>
    api.sendMessage(id, clientMessageId, content)
      .then((m) => setMessages((l) => upsert(l, { ...m, pending: false })))
      .catch((err) => pushError(err.message))

  const send = (content) => {
    const clientMessageId = crypto.randomUUID()
    setMessages((l) => [...l, {
      clientMessageId, content, senderId: user.id, senderUsername: user.username,
      type: 'TEXT', createdAt: new Date().toISOString(), pending: true,
    }])
    if (!publish(`/app/conversations.${id}.send`, { clientMessageId, content })) sendRest(clientMessageId, content)
  }

  const saveEdit = (m, content) => {
    setEditing(null)
    api.editMessage(id, m.id, content)
      .then((updated) => setMessages((l) => upsert(l, updated)))
      .catch((err) => pushError(err.message))
  }

  const deleteMessage = (m, scope) => {
    if (editing?.id === m.id) setEditing(null)
    api.deleteMessage(id, m.id, scope).then(() => {
      setMessages((l) => (scope === 'me' ? l.filter((x) => x.id !== m.id) : markDeleted(l, m.id)))
      if (scope === 'me') onRefresh()
    }).catch((err) => pushError(err.message))
  }

  const loadOlder = () =>
    api.messages(id, { before: cursor }).then((p) => {
      setMessages((l) => [...[...p.items].reverse(), ...l])
      setCursor(p.nextCursor)
      setHasMore(p.hasMore)
    }).catch(setError)

  const clearChat = () =>
    api.clearConversation(id).then(() => {
      setMessages([])
      setHasMore(false)
      setEditing(null)
      onRefresh()
    }).catch((err) => pushError(err.message))

  const deleteChat = () =>
    api.deleteConversation(id).then(() => onDeleted(id)).catch((err) => pushError(err.message))

  const changed = (updated) => {
    setConv(updated)
    onChanged(updated)
  }

  if (error && !conv) {
    return (
      <div className="flex flex-1 flex-col justify-center p-10 md:p-16">
        <p className="meta">Correspondence / {indexLabel(id)}</p>
        <p className="mt-4 font-serif text-4xl text-ink">Not available.</p>
        <p className="mt-2 text-sm text-ink-2">{error.message}</p>
        <Link to="/" className="btn-quiet mt-8"><ArrowLeft size={15} /> Back to the index</Link>
      </div>
    )
  }
  if (!conv) return <p className="eyebrow grid flex-1 place-items-center">Opening…</p>

  const isGroup = conv.type === 'GROUP'

  return (
    <div className="flex min-h-0 flex-1">
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-start gap-4 px-4 pt-5 pb-3 md:px-12 md:pt-8">
          <Link to="/" className="icon-btn md:hidden" title="Back"><ArrowLeft size={18} /></Link>
          <div className="ml-auto flex min-w-0 flex-col items-end text-right">
            <p className="meta">Correspondence / {indexLabel(conv.id)}</p>
            <h1 className="mt-1.5 max-w-full truncate text-[13px] font-medium tracking-[0.16em] text-ink uppercase">
              {conversationTitle(conv, user.id)}
            </h1>
            <div className="mt-1">
              {isGroup
                ? <span className="text-xs text-ink-2">{conv.participants.length} members{removed && ' · read only'}</span>
                : <PresenceDot status={presence[others[0]?.userId]} pulse />}
            </div>
          </div>
          <button className={`icon-btn ${showInfo ? 'bg-outgoing text-ink' : ''}`} title="Details" onClick={() => setShowInfo(!showInfo)}>
            <PanelRight size={17} />
          </button>
        </header>

        <MessageList messages={messages} meId={user.id} isGroup={isGroup} readOnly={removed} nameOf={nameOf} status={receipt}
          onRetry={(m) => sendRest(m.clientMessageId, m.content)}
          onEdit={setEditing} onDelete={deleteMessage}
          hasMore={hasMore} onLoadOlder={loadOlder} />

        {removed ? (
          <div className="px-4 pb-6 md:px-12 md:pb-8">
            <div className="hairline mx-auto flex max-w-3xl flex-wrap items-center gap-x-6 gap-y-2 border-t pt-5">
              <p className="flex-1 text-sm text-ink-2">
                <span className="font-serif text-lg text-ink italic">Read only.</span> You are no longer a member of this group.
              </p>
              <ConfirmButton danger onConfirm={deleteChat} confirmText="Click again to delete">Delete conversation</ConfirmButton>
            </div>
          </div>
        ) : (
          <>
            <TypingIndicator names={Object.values(typing)} />
            <MessageInput onSend={send} editing={editing} onSubmitEdit={saveEdit} onCancelEdit={() => setEditing(null)}
              onTyping={(t) => publish(`/app/conversations.${id}.typing`, { typing: t })} />
          </>
        )}
      </div>

      {showInfo && (
        <ContextPanel conversation={conv} meId={user.id} presence={presence} onChanged={changed}
          onClose={() => setShowInfo(false)} onClear={clearChat} onDeleteChat={deleteChat} />
      )}
    </div>
  )
}
