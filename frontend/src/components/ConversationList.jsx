import { Link } from 'react-router'
import { conversationTitle, indexLabel, shortWhen } from '../lib'

function preview(c, meId) {
  if (c.removedAt) return <span className="italic">No longer a member</span>
  const m = c.lastMessage
  if (!m) return <span className="text-ink-3">No messages yet</span>
  if (m.deleted) return <span className="italic">Message deleted</span>
  const who = m.senderId === meId ? 'You: ' : c.type === 'GROUP' ? `${m.senderName}: ` : ''
  return <>{who}{m.content}</>
}

function ConversationRow({ c, meId, active }) {
  const unread = c.unreadCount > 0
  return (
    <li>
      <Link to={`/c/${c.id}`}
        className="group relative flex gap-4 px-6 py-3.5 transition-colors duration-150 hover:bg-outgoing/60">
        <span className={`absolute top-3 bottom-3 left-0 w-[2px] origin-center bg-accent transition-all duration-200 ${active ? 'scale-y-100 opacity-100' : 'scale-y-50 opacity-0'}`} />
        <span className={`meta w-6 shrink-0 pt-[3px] ${active ? 'text-accent' : ''}`}>{indexLabel(c.id)}</span>
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline justify-between gap-3">
            <span className={`truncate text-[14.5px] text-ink ${unread ? 'font-semibold' : 'font-medium'}`}>
              {conversationTitle(c, meId)}
            </span>
            <span className="meta shrink-0">{shortWhen(c.lastMessage?.createdAt || c.lastMessageAt || c.createdAt)}</span>
          </span>
          <span className="mt-0.5 flex items-center gap-3">
            <span className={`truncate text-[13px] ${unread ? 'text-ink' : 'text-ink-2'}`}>{preview(c, meId)}</span>
            {unread && (
              <span className="ml-auto shrink-0 font-mono text-[10.5px] text-accent" title={`${c.unreadCount} unread`}>
                {c.unreadCount > 99 ? '99+' : c.unreadCount}
              </span>
            )}
          </span>
        </span>
      </Link>
    </li>
  )
}

export default function ConversationList({ conversations, activeId, meId }) {
  if (!conversations.length) {
    return (
      <div className="px-6 py-10">
        <p className="font-serif text-xl text-ink">An empty index.</p>
        <p className="mt-2 text-[13px] leading-relaxed text-ink-2">Search for someone above to begin, or start a group.</p>
      </div>
    )
  }
  return (
    <ul className="flex flex-col pb-4">
      {conversations.map((c) => <ConversationRow key={c.id} c={c} meId={meId} active={c.id === activeId} />)}
    </ul>
  )
}
