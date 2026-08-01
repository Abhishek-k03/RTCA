import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { api } from '../api/endpoints'
import { conversationTitle, displayName } from '../lib'
import Avatar from './Avatar'

function matchesChat(c, q, meId) {
  if (conversationTitle(c, meId).toLowerCase().includes(q)) return true
  if (c.type !== 'DIRECT') return false
  const other = c.participants.find((p) => p.userId !== meId)
  return !!other?.username.toLowerCase().includes(q)
}

const Section = ({ label, children }) => (
  <section className="pt-4">
    <p className="eyebrow px-6 pb-2">{label}</p>
    {children}
  </section>
)

const Empty = ({ children }) => <p className="px-6 py-1 text-[13px] text-ink-3">{children}</p>

const rowClass = 'flex w-full items-center gap-4 px-6 py-2.5 text-left transition-colors duration-150 hover:bg-outgoing/60'

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
      <Section label={
        <span className="flex items-center justify-between">
          Recent
          {recents.items.length > 0 && (
            <button className="tracking-normal normal-case text-ink-3 hover:text-ink" onClick={recents.clear}>Clear all</button>
          )}
        </span>
      }>
        {!recents.items.length && <Empty>Nothing searched yet.</Empty>}
        <ul>
          {recents.items.map((r) => (
            <li key={`${r.type}:${r.id}`} className="group flex items-center pr-4 transition-colors duration-150 hover:bg-outgoing/60">
              <button className="flex min-w-0 flex-1 items-center gap-4 py-2.5 pl-6 text-left"
                onClick={() => (r.type === 'chat' ? onOpenChat(r) : onOpenUser(r))}>
                <Avatar name={r.type === 'chat' ? r.title : displayName(r)} size="sm" />
                <span className="truncate text-sm text-ink">{r.type === 'chat' ? r.title : displayName(r)}</span>
              </button>
              <button className="icon-btn opacity-0 group-hover:opacity-100 focus:opacity-100" title="Remove"
                onClick={() => recents.remove(r)}>
                <X size={14} />
              </button>
            </li>
          ))}
        </ul>
      </Section>
    )
  }

  const chats = conversations.filter((c) => matchesChat(c, q, meId))
  // people you already have a direct chat with show up under Correspondence
  const directWith = new Set(chats.filter((c) => c.type === 'DIRECT')
    .map((c) => c.participants.find((p) => p.userId !== meId)?.userId))
  const others = people.filter((u) => u.id !== meId && !directWith.has(u.id))

  return (
    <>
      <Section label="Correspondence">
        {!chats.length && <Empty>No matching chats.</Empty>}
        <ul>
          {chats.map((c) => (
            <li key={c.id}>
              <button className={rowClass}
                onClick={() => onOpenChat({ type: 'chat', id: c.id, title: conversationTitle(c, meId) })}>
                <Avatar name={conversationTitle(c, meId)} size="sm" />
                <span className="truncate text-sm text-ink">{conversationTitle(c, meId)}</span>
              </button>
            </li>
          ))}
        </ul>
      </Section>
      <Section label="People">
        {!others.length && <Empty>No one else by that name.</Empty>}
        <ul>
          {others.map((u) => (
            <li key={u.id}>
              <button className={rowClass} onClick={() => onOpenUser({ type: 'user', ...u })}>
                <Avatar name={displayName(u)} size="sm" />
                <span className="truncate text-sm text-ink">
                  {displayName(u)} <span className="text-ink-3">@{u.username}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </Section>
    </>
  )
}
