import { useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { api } from '../api/endpoints'
import { displayName } from '../lib'
import Avatar from './Avatar'

export default function UserSearch({ onPick, excludeIds = [], placeholder = 'Search people…' }) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState([])

  useEffect(() => {
    if (!q.trim()) return
    const t = setTimeout(() => {
      api.searchUsers(q.trim()).then((page) => setResults(page.content)).catch(() => setResults([]))
    }, 300)
    return () => clearTimeout(t)
  }, [q])

  const shown = q.trim() ? results.filter((u) => !excludeIds.includes(u.id)) : []

  return (
    <div className="flex flex-col gap-1">
      <input className="field" placeholder={placeholder} value={q} onChange={(e) => setQ(e.target.value)} />
      <ul className="max-h-56 overflow-y-auto">
        {shown.map((u) => (
          <li key={u.id}>
            <button className="group flex w-full items-center gap-3 py-2 text-left" onClick={() => onPick(u)}>
              <Avatar name={displayName(u)} size="sm" />
              <span className="min-w-0 flex-1 truncate text-sm text-ink">
                {displayName(u)} <span className="text-ink-3">@{u.username}</span>
              </span>
              <Plus size={16} className="text-ink-3 transition-colors group-hover:text-accent" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
