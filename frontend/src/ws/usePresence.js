import { useEffect, useState } from 'react'
import { api } from '../api/endpoints'
import { useStomp } from './StompContext'

export function usePresence(userIds) {
  const { subscribe } = useStomp()
  const [statuses, setStatuses] = useState({})
  const key = [...new Set(userIds)].sort().join(',')

  useEffect(() => {
    if (!key) return
    let active = true
    const ids = key.split(',')
    api.presence(ids)
      .then((list) => {
        if (active) setStatuses((s) => ({ ...s, ...Object.fromEntries(list.map((x) => [x.userId, x])) }))
      })
      .catch(() => {})
    const unsubs = ids.map((id) =>
      subscribe(`/topic/presence.${id}`, (e) => {
        if (e.type === 'PRESENCE') setStatuses((s) => ({ ...s, [e.payload.userId]: e.payload }))
      }),
    )
    return () => {
      active = false
      unsubs.forEach((u) => u())
    }
  }, [key, subscribe])

  return statuses
}
