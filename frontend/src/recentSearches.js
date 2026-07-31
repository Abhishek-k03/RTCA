import { useCallback, useState } from 'react'

const MAX = 10

const read = (key) => {
  try {
    return JSON.parse(localStorage.getItem(key)) || []
  } catch {
    return []
  }
}

const write = (key, items) => {
  try {
    localStorage.setItem(key, JSON.stringify(items))
  } catch {
    // storage full or blocked, recents are just a convenience
  }
}

const same = (a, b) => a.type === b.type && a.id === b.id

// entries: { type: 'chat', id, title } or { type: 'user', id, username, displayName }
export function useRecentSearches(userId) {
  const key = `recentSearches:${userId}`
  const [items, setItems] = useState(() => read(key))

  const update = useCallback((fn) => {
    setItems((current) => {
      const next = fn(current)
      write(key, next)
      return next
    })
  }, [key])

  const add = useCallback((entry) => update((l) => [entry, ...l.filter((x) => !same(x, entry))].slice(0, MAX)), [update])
  const remove = useCallback((entry) => update((l) => l.filter((x) => !same(x, entry))), [update])
  const clear = useCallback(() => update(() => []), [update])

  return { items, add, remove, clear }
}
