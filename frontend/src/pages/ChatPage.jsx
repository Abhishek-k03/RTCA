import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import { useStomp, useTopic } from '../ws/StompContext'
import { displayName } from '../lib'
import ChatWindow from '../components/ChatWindow'
import ConversationList from '../components/ConversationList'
import NewGroupDialog from '../components/NewGroupDialog'
import SearchPanel from '../components/SearchPanel'
import { useRecentSearches } from '../recentSearches'

export default function ChatPage() {
  const { user, logout } = useAuth()
  const { connected, subscribe, publish, pushError } = useStomp()
  const { id } = useParams()
  const activeId = id ? Number(id) : null
  const navigate = useNavigate()
  const [conversations, setConversations] = useState([])
  const [showNewGroup, setShowNewGroup] = useState(false)
  const [searching, setSearching] = useState(false)
  const [query, setQuery] = useState('')
  const recents = useRecentSearches(user.id)

  // new chats arrive as ADDED events; focus refetch is just a safety net
  const load = useCallback(() => {
    api.conversations().then((p) => setConversations(p.content)).catch(() => {})
  }, [])

  useEffect(() => {
    load()
    window.addEventListener('focus', load)
    return () => window.removeEventListener('focus', load)
  }, [load])

  // messages that arrived before we were subscribed (offline, or in a chat we were
  // just added to) only get a delivered receipt here
  const syncDelivered = useCallback(() => {
    api.conversations().then((p) => {
      setConversations(p.content)
      p.content.filter((c) => c.unreadCount > 0 && !c.removedAt).forEach((c) =>
        api.messages(c.id, { limit: 1 }).then(({ items }) => {
          if (items[0]) publish(`/app/conversations.${c.id}.delivered`, { messageId: items[0].id })
        }).catch(() => {}))
    }).catch(() => {})
  }, [publish])

  useEffect(() => {
    if (connected) syncDelivered()
  }, [connected, syncDelivered])

  const onEvent = useRef(null)
  useEffect(() => {
    onEvent.current = (cid, { type, payload: m }) => {
      if (type !== 'MESSAGE') return
      const mine = m.senderId === user.id
      if (!mine) publish(`/app/conversations.${cid}.delivered`, { messageId: m.id })
      setConversations((list) => {
        const c = list.find((x) => x.id === cid)
        if (!c) return list
        const bump = !mine && cid !== activeId
        const updated = { ...c, lastMessageAt: m.createdAt, unreadCount: (c.unreadCount ?? 0) + (bump ? 1 : 0) }
        return [updated, ...list.filter((x) => x.id !== cid)]
      })
    }
  })

  // ADDED: new or restored membership. REMOVED: chat turns read-only, and subscribing would be rejected
  useTopic('/user/queue/events', ({ type, payload }) => {
    if (type === 'ADDED') syncDelivered()
    if (type !== 'REMOVED') return
    setConversations((list) => list.map((c) =>
      (c.id === payload.conversationId ? { ...c, removedAt: payload.removedAt, lastMessageAt: payload.removedAt } : c)))
  })

  // sorted so reordering the list doesn't resubscribe
  const idsKey = conversations.filter((c) => !c.removedAt).map((c) => c.id).sort((a, b) => a - b).join(',')
  useEffect(() => {
    if (!idsKey) return
    const unsubs = idsKey.split(',').map(Number).map((cid) =>
      subscribe(`/topic/conversations.${cid}`, (e) => onEvent.current(cid, e)))
    return () => unsubs.forEach((u) => u())
  }, [idsKey, subscribe])

  const onRead = useCallback((cid) => {
    setConversations((list) => list.map((c) => (c.id === cid ? { ...c, unreadCount: 0 } : c)))
  }, [])

  const onChanged = useCallback((updated) => {
    setConversations((list) => list.map((c) => (c.id === updated.id ? { ...updated, unreadCount: c.unreadCount } : c)))
  }, [])

  const onGroupCreated = (c) => {
    setConversations((list) => (list.some((x) => x.id === c.id) ? list : [c, ...list]))
    setShowNewGroup(false)
    navigate(`/c/${c.id}`)
  }

  const closeSearch = () => {
    setSearching(false)
    setQuery('')
  }

  const openChat = (entry) => {
    recents.add(entry)
    closeSearch()
    navigate(`/c/${entry.id}`)
  }

  // a new direct chat stays out of the list until the first message is sent
  const openUser = async (entry) => {
    recents.add({ type: 'user', id: entry.id, username: entry.username, displayName: entry.displayName })
    closeSearch()
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
    <div className="flex h-screen flex-col">
      <header className="flex items-center gap-3 border-b p-2">
        <span className="font-bold">RTCA</span>
        <span className="text-sm text-gray-500">{connected ? '● connected' : '○ connecting…'}</span>
        <span className="ml-auto">{displayName(user)}</span>
        <Link to="/profile" className="underline">Profile</Link>
        {user.role === 'ADMIN' && <Link to="/admin" className="underline">Admin</Link>}
        <button className="underline" onClick={logout}>Log out</button>
      </header>

      <div className="flex min-h-0 flex-1">
        <aside className="flex w-72 flex-col border-r">
          <div className="flex gap-1 border-b p-2">
            <input className="min-w-0 flex-1 border p-1" placeholder="Search" value={query}
              onFocus={() => setSearching(true)}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Escape') closeSearch()
              }} />
            {searching
              ? <button className="px-2" onClick={closeSearch}>Cancel</button>
              : <button className="border px-2" onClick={() => setShowNewGroup(!showNewGroup)}>New group</button>}
          </div>
          {showNewGroup && !searching && (
            <NewGroupDialog onCreated={onGroupCreated} onClose={() => setShowNewGroup(false)} />
          )}
          <div className="flex-1 overflow-y-auto">
            {searching
              ? <SearchPanel query={query} conversations={conversations} meId={user.id} recents={recents}
                  onOpenChat={openChat} onOpenUser={openUser} />
              : <ConversationList conversations={conversations} activeId={activeId} meId={user.id} />}
          </div>
        </aside>

        <main className="flex min-w-0 flex-1 flex-col">
          {activeId
            ? <ChatWindow key={activeId} conversationId={activeId} onRead={onRead} onChanged={onChanged} onDeleted={onDeleted} />
            : <p className="p-4 text-gray-500">Select a chat, or search for someone to message</p>}
        </main>
      </div>
    </div>
  )
}
