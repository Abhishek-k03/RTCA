import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import { useStomp, useTopic } from '../ws/StompContext'
import { usePresence } from '../ws/usePresence'
import { conversationTitle } from '../lib'
import FormError from './FormError'
import GroupInfoPanel from './GroupInfoPanel'
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

export default function ChatWindow({ conversationId: id, onRead, onChanged, onDeleted }) {
  const { user } = useAuth()
  const { connected, publish, pushError } = useStomp()
  const [conv, setConv] = useState(null)
  const [messages, setMessages] = useState([])
  const [cursor, setCursor] = useState(null)
  const [hasMore, setHasMore] = useState(false)
  const [typing, setTyping] = useState({})
  const [showInfo, setShowInfo] = useState(false)
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
    if (m.pending) return 'sending…'
    if (!others.length) return ''
    if (others.every((p) => (p.lastReadMessageId ?? 0) >= m.id)) return '✓✓ read'
    if (others.every((p) => Math.max(p.lastDeliveredMessageId ?? 0, p.lastReadMessageId ?? 0) >= m.id)) return '✓✓'
    return '✓'
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

  const loadOlder = () =>
    api.messages(id, { before: cursor }).then((p) => {
      setMessages((l) => [...[...p.items].reverse(), ...l])
      setCursor(p.nextCursor)
      setHasMore(p.hasMore)
    }).catch(setError)

  const deleteChat = () =>
    api.deleteConversation(id).then(() => onDeleted(id)).catch((err) => pushError(err.message))

  const changed = (updated) => {
    setConv(updated)
    onChanged(updated)
  }

  if (error && !conv) return <div className="p-4"><FormError error={error} /></div>
  if (!conv) return <p className="p-4">Loading…</p>

  return (
    <div className="flex min-h-0 flex-1">
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-2 border-b p-2">
          <span className="font-bold">{conversationTitle(conv, user.id)}</span>
          {conv.type === 'DIRECT'
            ? <PresenceDot status={presence[others[0]?.userId]} />
            : <span className="text-sm text-gray-500">{conv.participants.length} members</span>}
          <button className="ml-auto border px-2" onClick={() => setShowInfo(!showInfo)}>
            {showInfo ? 'Hide info' : 'Info'}
          </button>
        </header>
        <MessageList messages={messages} meId={user.id} status={receipt}
          onRetry={(m) => sendRest(m.clientMessageId, m.content)}
          hasMore={hasMore} onLoadOlder={loadOlder} />
        {removed ? (
          <div className="flex items-center gap-2 border-t p-2">
            <span>You are no longer a member of this group.</span>
            <button className="ml-auto border px-2" onClick={deleteChat}>Delete chat</button>
          </div>
        ) : (
          <>
            <TypingIndicator names={Object.values(typing)} />
            <MessageInput onSend={send} onTyping={(t) => publish(`/app/conversations.${id}.typing`, { typing: t })} />
          </>
        )}
      </div>
      {showInfo && (
        <GroupInfoPanel conversation={conv} meId={user.id} presence={presence}
          onChanged={changed} />
      )}
    </div>
  )
}
