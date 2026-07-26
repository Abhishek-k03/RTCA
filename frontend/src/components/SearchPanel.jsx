import { useEffect, useState } from 'react'
import { api } from '../api/endpoints'
import { conversationTitle, displayName } from '../lib'

function matchesChat(c, q, meId) {
  if (conversationTitle(c, meId).toLowerCase().includes(q)) return true
  if (c.type !== 'DIRECT') return false
  const other = c.participants.find((p) => p.userId !== meId)
  return !!other?.username.toLowerCase().includes(q)
}

export default function SearchPanel({ query, conversations, meId, recents, onOpenChat, onOpenUser }) {
  const q = query.trim().toLowerCase()
  const [people, setPeople] = useState([])

  useEffect(() => {
    if (!q) return
    const t = setTimeout(() => {
      api.searchUsers(q).then((page) => setPeople(page.content)).catch(() => setPeople([]))
    }, 300)
    return () => clearTimeout(t)
  }, [q])

  if (!q) {
    return (
      <div className="flex flex-col">
        <div className="flex items-center justify-between p-2 text-sm text-gray-500">
          <span>Recent</span>
          {recents.items.length > 0 && <button className="underline" onClick={recents.clear}>Clear all</button>}
        </div>
        {!recents.items.length && <p className="p-2 text-sm text-gray-500">No recent searches</p>}
        <ul>
          {recents.items.map((r) => (
            <li key={`${r.type}:${r.id}`} className="flex items-center border-b">
              <button className="flex-1 p-2 text-left"
                onClick={() => (r.type === 'chat' ? onOpenChat(r) : onOpenUser(r))}>
                {r.type === 'chat' ? r.title : <>{displayName(r)} <span className="text-gray-500">@{r.username}</span></>}
              </button>
              <button className="px-2" title="Remove" onClick={() => recents.remove(r)}>×</button>
            </li>
          ))}
        </ul>
      </div>
    )
  }

  const chats = conversations.filter((c) => matchesChat(c, q, meId))
  // people you already have a direct chat with show up under Chats
  const directWith = new Set(chats.filter((c) => c.type === 'DIRECT')
    .map((c) => c.participants.find((p) => p.userId !== meId)?.userId))
  const others = people.filter((u) => u.id !== meId && !directWith.has(u.id))

  return (
    <div className="flex flex-col">
      <p className="p-2 text-sm text-gray-500">Chats</p>
      {!chats.length && <p className="px-2 text-sm text-gray-500">No matching chats</p>}
      <ul>
        {chats.map((c) => (
          <li key={c.id} className="border-b">
            <button className="w-full p-2 text-left"
              onClick={() => onOpenChat({ type: 'chat', id: c.id, title: conversationTitle(c, meId) })}>
              {c.type === 'GROUP' ? '# ' : ''}{conversationTitle(c, meId)}
            </button>
          </li>
        ))}
      </ul>

      <p className="p-2 text-sm text-gray-500">People</p>
      {!others.length && <p className="px-2 text-sm text-gray-500">No matching people</p>}
      <ul>
        {others.map((u) => (
          <li key={u.id} className="border-b">
            <button className="w-full p-2 text-left" onClick={() => onOpenUser({ type: 'user', ...u })}>
              {displayName(u)} <span className="text-gray-500">@{u.username}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
