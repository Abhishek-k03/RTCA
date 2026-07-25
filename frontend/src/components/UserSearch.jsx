import { useEffect, useState } from 'react'
import { api } from '../api/endpoints'
import { displayName } from '../lib'

export default function UserSearch({ onPick, excludeIds = [] }) {
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
      <input className="border p-1" placeholder="Search users…" value={q} onChange={(e) => setQ(e.target.value)} />
      <ul className="max-h-48 overflow-y-auto">
        {shown.map((u) => (
          <li key={u.id}>
            <button className="w-full p-1 text-left" onClick={() => onPick(u)}>
              {displayName(u)} <span className="text-gray-500">@{u.username}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
