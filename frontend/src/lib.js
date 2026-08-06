export const displayName = (u) => u?.displayName || u?.username || `user ${u?.userId ?? u?.id}`

export function conversationTitle(c, meId) {
  if (c.type === 'GROUP') return c.name
  const other = c.participants.find((p) => p.userId !== meId)
  return other ? displayName(other) : 'Just you'
}

// direct chats show the other person's photo, groups have none
export function conversationAvatar(c, meId) {
  if (c.type === 'GROUP') return null
  return c.participants.find((p) => p.userId !== meId)?.avatarUrl ?? null
}

export function initials(name = '') {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return '·'
  const letters = parts.length > 1 ? parts[0][0] + parts.at(-1)[0] : parts[0].slice(0, 2)
  return letters.toUpperCase()
}

export const formatTime = (iso) => (iso ? new Date(iso).toLocaleString() : '')

export const formatClock = (iso) =>
  iso ? new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : ''

const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()

export const sameDay = (a, b) => startOfDay(new Date(a)) === startOfDay(new Date(b))

// "SATURDAY" / "26 SEPTEMBER 2026"
export function dateHeading(iso) {
  const d = new Date(iso)
  return {
    weekday: d.toLocaleDateString('en-GB', { weekday: 'long' }),
    date: d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }),
  }
}

// sidebar: time today, weekday this week, otherwise a short date
export function shortWhen(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const days = (startOfDay(new Date()) - startOfDay(d)) / 86400000
  if (days === 0) return formatClock(iso)
  if (days < 7) return d.toLocaleDateString('en-GB', { weekday: 'short' })
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}

export function lastSeen(iso) {
  if (!iso) return 'away'
  const d = new Date(iso)
  return sameDay(d, new Date()) ? `last seen ${formatClock(iso)}` : `last seen ${shortWhen(iso)}`
}

// same set the server accepts
export const REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🙏']

// one line for a quoted message or a reply banner
export function previewText(m) {
  if (m.deleted) return 'Deleted message'
  if (m.type === 'IMAGE') return m.content ? `Photo · ${m.content}` : 'Photo'
  return m.content
}

export const EDIT_WINDOW_MS = 15 * 60 * 1000

export const withinWindow = (iso) => Date.now() - new Date(iso).getTime() < EDIT_WINDOW_MS
