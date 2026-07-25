import { Link } from 'react-router'
import { conversationTitle, formatTime } from '../lib'

export default function ConversationList({ conversations, activeId, meId }) {
  if (!conversations.length) return <p className="p-2 text-gray-500">No conversations yet</p>
  return (
    <ul className="flex flex-col">
      {conversations.map((c) => (
        <li key={c.id} className={`border-b ${c.id === activeId ? 'font-bold' : ''}`}>
          <Link to={`/c/${c.id}`} className="flex flex-col p-2">
            <span className="flex justify-between gap-2">
              <span>{c.type === 'GROUP' ? '# ' : ''}{conversationTitle(c, meId)}{c.removedAt && ' (removed)'}</span>
              {c.unreadCount > 0 && <span className="border px-1 text-sm">{c.unreadCount}</span>}
            </span>
            <span className="text-xs text-gray-500">{formatTime(c.lastMessageAt || c.createdAt)}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
