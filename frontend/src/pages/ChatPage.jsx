import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import { useStomp, useTopic } from '../ws/StompContext'
import { applyProfile, dateHeading, displayName } from '../lib'
import { useRecentSearches } from '../recentSearches'
import { notify } from '../notifications'
import ChatWindow from '../components/ChatWindow'
import Sidebar from '../components/Sidebar'

const BASE_TITLE = document.title

function notification(c, m) {
  const sender = c.participants.find((p) => p.userId === m.senderId)
  const name = sender ? displayName(sender) : m.senderUsername
  const body = m.type === 'IMAGE' ? (m.content ? `Photo · ${m.content}` : 'Photo') : m.content
  return { title: c.type === 'GROUP' ? `${name} · ${c.name}` : name, body, tag: c.id }
}

function EmptyState() {
  const { weekday, date } = dateHeading(new Date().toISOString())
  return (
    <div className="flex flex-1 flex-col justify-between p-10 md:p-16">
      <div className="text-right">
        <p className="eyebrow">{weekday}</p>
        <p className="eyebrow mt-1 text-ink-3">{date}</p>
      </div>
      <div className="max-w-md">
        <p className="font-serif text-5xl leading-[1.05] text-ink">Nothing open.</p>
        <p className="mt-4 text-sm leading-relaxed text-ink-2">
          Choose a correspondence from the index, or search for someone to write to.
        </p>
      </div>
    </div>
  )
}

export default function ChatPage() {
  const { user, setUser } = useAuth()
  const { connected, subscribe, publish, pushError } = useStomp()
  const { id } = useParams()
  const activeId = id ?? null
  const navigate = useNavigate()
  const [conversations, setConversations] = useState([])
  const recents = useRecentSearches(user.id)
  const activeRef = useRef(activeId)
  const conversationsRef = useRef(conversations)
  useEffect(() => {
    activeRef.current = activeId
    conversationsRef.current = conversations
  })

  // unread count in the tab title
  const unread = conversations.reduce((n, c) => n + (c.removedAt ? 0 : c.unreadCount || 0), 0)
  useEffect(() => {
    document.title = unread ? `(${unread}) ${BASE_TITLE}` : BASE_TITLE
  }, [unread])
  useEffect(() => () => { document.title = BASE_TITLE }, [])

  // the open chat is being read, so a refetch racing its read receipt must not bring the badge back
  const applyList = useCallback((list) => {
    setConversations(list.map((c) => (c.id === activeRef.current ? { ...c, unreadCount: 0 } : c)))
  }, [])

  // new chats arrive as ADDED events; focus refetch is just a safety net
  const load = useCallback(() => {
    api.conversations().then((p) => applyList(p.content)).catch(() => {})
  }, [applyList])

  useEffect(() => {
    load()
    window.addEventListener('focus', load)
    return () => window.removeEventListener('focus', load)
  }, [load])

  // messages that arrived before we were subscribed (offline, or in a chat we were
  // just added to) only get a delivered receipt here
  const syncDelivered = useCallback(() => {
    api.conversations().then((p) => {
      applyList(p.content)
      p.content.filter((c) => c.unreadCount > 0 && !c.removedAt).forEach((c) =>
        api.messages(c.id, { limit: 1 }).then(({ items }) => {
          if (items[0]) publish(`/app/conversations.${c.id}.delivered`, { messageId: items[0].id })
        }).catch(() => {}))
    }).catch(() => {})
  }, [publish, applyList])

  useEffect(() => {
    if (connected) syncDelivered()
  }, [connected, syncDelivered])

  const updateConversation = (cid, fn) =>
    setConversations((list) => list.map((c) => (c.id === cid ? fn(c) : c)))

  const onEvent = useRef(null)
  useEffect(() => {
    onEvent.current = (cid, { type, payload: m }) => {
      if (type === 'EDITED') {
        updateConversation(cid, (c) => (c.lastMessage?.id === m.id
          ? { ...c, lastMessage: { ...c.lastMessage, content: m.content } } : c))
        return
      }
      if (type === 'GROUP_UPDATED') {
        updateConversation(cid, (c) => ({ ...c, name: m.name, avatarUrl: m.avatarUrl }))
        return
      }
      if (type === 'DELETED') {
        updateConversation(cid, (c) => (c.lastMessage?.id === m.messageId
          ? { ...c, lastMessage: { ...c.lastMessage, content: null, deleted: true } } : c))
        return
      }
      if (type !== 'MESSAGE') return
      const mine = m.senderId === user.id
      if (!mine) publish(`/app/conversations.${cid}.delivered`, { messageId: m.id })
      const known = conversationsRef.current.find((x) => x.id === cid)
      if (!mine && known) notify({ ...notification(known, m), onClick: () => navigate(`/c/${cid}`) })
      setConversations((list) => {
        const c = list.find((x) => x.id === cid)
        if (!c) return list
        const sender = c.participants.find((p) => p.userId === m.senderId)
        // the open chat counts too while the tab is hidden, it's marked read on return
        const bump = !mine && (cid !== activeId || document.visibilityState !== 'visible')
        const updated = {
          ...c,
          lastMessageAt: m.createdAt,
          lastMessage: {
            id: m.id, senderId: m.senderId, senderName: sender ? displayName(sender) : m.senderUsername,
            content: m.content, type: m.type, deleted: false, createdAt: m.createdAt,
          },
          unreadCount: (c.unreadCount ?? 0) + (bump ? 1 : 0),
        }
        return [updated, ...list.filter((x) => x.id !== cid)]
      })
    }
  })

  // ADDED: new or restored membership. REMOVED: chat turns read-only, and subscribing would be rejected
  useTopic('/user/queue/events', ({ type, payload }) => {
    if (type === 'ADDED') syncDelivered()
    if (type === 'PROFILE') {
      setConversations((list) => list.map((c) => ({ ...c, participants: applyProfile(c.participants, payload) })))
      // our own change, made in another tab
      if (payload.id === user.id) {
        setUser((u) => ({ ...u, displayName: payload.displayName, bio: payload.bio, avatarUrl: payload.avatarUrl }))
      }
    }
    if (type !== 'REMOVED') return
    updateConversation(payload.conversationId, (c) => ({ ...c, removedAt: payload.removedAt, lastMessageAt: payload.removedAt }))
  })

  // sorted so reordering the list doesn't resubscribe
  const idsKey = conversations.filter((c) => !c.removedAt).map((c) => c.id).sort().join(',')
  useEffect(() => {
    if (!idsKey) return
    const unsubs = idsKey.split(',').map((cid) =>
      subscribe(`/topic/conversations.${cid}`, (e) => onEvent.current(cid, e)))
    return () => unsubs.forEach((u) => u())
  }, [idsKey, subscribe])

  const onRead = useCallback((cid) => {
    setConversations((list) => list.map((c) => (c.id === cid ? { ...c, unreadCount: 0 } : c)))
  }, [])

  const onChanged = useCallback((updated) => {
    // single-conversation responses carry no preview, keep the one we have
    setConversations((list) => list.map((c) => (c.id === updated.id ? { ...c, ...updated, unreadCount: c.unreadCount } : c)))
  }, [])

  const onGroupCreated = (c) => {
    setConversations((list) => (list.some((x) => x.id === c.id) ? list : [c, ...list]))
    navigate(`/c/${c.id}`)
  }

  const openChat = (entry) => {
    recents.add(entry)
    navigate(`/c/${entry.id}`)
  }

  // a new direct chat stays out of the list until the first message is sent
  const openUser = async (entry) => {
    recents.add({ type: 'user', id: entry.id, username: entry.username, displayName: entry.displayName, avatarUrl: entry.avatarUrl })
    try {
      const c = await api.createDirect(entry.id)
      navigate(`/c/${c.id}`)
    } catch (err) {
      pushError(err.message)
    }
  }

  const onDeleted = (cid) => {
    setConversations((list) => list.filter((c) => c.id !== cid))
    navigate('/')
  }

  return (
    <div className="flex h-full">
      <Sidebar
        className={`hairline w-full md:w-[340px] md:shrink-0 md:border-r ${activeId ? 'hidden md:flex' : 'flex'}`}
        conversations={conversations} activeId={activeId} recents={recents}
        onOpenChat={openChat} onOpenUser={openUser} onGroupCreated={onGroupCreated} />

      <main className={`min-w-0 flex-1 flex-col bg-surface ${activeId ? 'flex' : 'hidden md:flex'}`}>
        {activeId
          ? <ChatWindow key={activeId} conversationId={activeId} onRead={onRead} onChanged={onChanged}
              onDeleted={onDeleted} onRefresh={load} />
          : <EmptyState />}
      </main>
    </div>
  )
}
