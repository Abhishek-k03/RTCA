export const displayName = (u) => u?.displayName || u?.username || `user ${u?.userId ?? u?.id}`

export function conversationTitle(c, meId) {
  if (c.type === 'GROUP') return c.name
  const other = c.participants.find((p) => p.userId !== meId)
  return other ? displayName(other) : 'Just you'
}

export const formatTime = (iso) => (iso ? new Date(iso).toLocaleString() : '')
