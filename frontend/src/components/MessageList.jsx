import { useEffect, useRef, useState } from 'react'
import { Check, CheckCheck, Copy, Pencil, RotateCcw, Trash2 } from 'lucide-react'
import { dateHeading, formatClock, sameDay, withinWindow } from '../lib'

const GROUP_GAP_MS = 5 * 60 * 1000

// consecutive messages from one sender, same day, close together
function groupMessages(messages) {
  const groups = []
  for (const m of messages) {
    const last = groups.at(-1)
    const prev = last?.items.at(-1)
    const joins = prev && m.type !== 'SYSTEM' && prev.type !== 'SYSTEM'
      && prev.senderId === m.senderId && sameDay(prev.createdAt, m.createdAt)
      && new Date(m.createdAt) - new Date(prev.createdAt) < GROUP_GAP_MS
    if (joins) last.items.push(m)
    else groups.push({ senderId: m.senderId, items: [m] })
  }
  return groups
}

function DateSeparator({ iso }) {
  const { weekday, date } = dateHeading(iso)
  return (
    <div className="my-12 flex items-center gap-6 first:mt-2">
      <span className="hairline h-px flex-1 border-t" />
      <div className="pr-6 text-right md:pr-16">
        <p className="eyebrow">{weekday}</p>
        <p className="eyebrow mt-0.5 text-ink-3">{date}</p>
      </div>
    </div>
  )
}

function Receipt({ status }) {
  if (status === 'sending') return <span className="meta">sending</span>
  if (status === 'read') return <CheckCheck size={13} className="text-accent" aria-label="read" />
  if (status === 'delivered') return <CheckCheck size={13} className="text-ink-3" aria-label="delivered" />
  if (status === 'sent') return <Check size={13} className="text-ink-3" aria-label="sent" />
  return null
}

function Toolbar({ m, mine, readOnly, onEdit, onDelete, align }) {
  const [menu, setMenu] = useState(false)
  const canChange = mine && !readOnly && !m.deleted && !m.pending && withinWindow(m.createdAt)

  useEffect(() => {
    if (!menu) return
    const close = () => setMenu(false)
    window.addEventListener('click', close)
    return () => window.removeEventListener('click', close)
  }, [menu])

  if (m.pending || m.deleted) return null
  const btn = 'inline-flex size-7 items-center justify-center rounded-[6px] text-ink-2 transition-colors hover:bg-outgoing hover:text-ink'

  return (
    <div className={`absolute -top-6 z-10 flex translate-y-0.5 items-center gap-0.5 rounded-[8px] border border-hairline bg-elevated p-0.5 opacity-0 shadow-[0_1px_2px_rgba(28,27,25,0.05)] transition duration-150 group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100 ${menu ? 'translate-y-0 opacity-100' : ''} ${align}`}>
      <button className={btn} title="Copy" onClick={() => navigator.clipboard?.writeText(m.content)}><Copy size={14} /></button>
      {canChange && <button className={btn} title="Edit" onClick={() => onEdit(m)}><Pencil size={14} /></button>}
      <div className="relative">
        <button className={btn} title="Delete" onClick={(e) => {
          e.stopPropagation()
          setMenu(!menu)
        }}><Trash2 size={14} /></button>
        {menu && (
          <div className={`absolute top-8 flex w-44 flex-col rounded-[8px] border border-hairline bg-elevated py-1 text-sm shadow-[0_2px_8px_rgba(28,27,25,0.06)] ${mine ? 'right-0' : 'left-0'}`}>
            <button className="px-3 py-1.5 text-left text-ink hover:bg-outgoing" onClick={() => onDelete(m, 'me')}>Delete for me</button>
            {canChange && (
              <button className="px-3 py-1.5 text-left text-danger hover:bg-outgoing" onClick={() => onDelete(m, 'everyone')}>
                Delete for everyone
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function Body({ m, mine }) {
  if (m.deleted) {
    return <p className="text-[14px] text-ink-3 italic">{mine ? 'You deleted this message.' : 'This message was deleted.'}</p>
  }
  return (
    <p className="text-[15px] leading-relaxed break-words whitespace-pre-wrap text-ink">
      {m.content}
      {m.editedAt && <span className="ml-2 align-baseline text-[11px] text-ink-3">edited</span>}
    </p>
  )
}

function Group({ group, meId, showName, readOnly, nameOf, status, onRetry, onEdit, onDelete }) {
  const mine = group.senderId === meId
  const last = group.items.at(-1)

  if (last.type === 'SYSTEM') {
    return <p className="my-6 text-center text-[13px] text-ink-2 italic">{last.content}</p>
  }

  return (
    <div className={`flex flex-col ${mine ? 'items-end' : 'items-start'}`}>
      {!mine && showName && <p className="mb-2 text-[13px] font-medium text-ink-2">{nameOf(group.senderId, last)}</p>}
      <div className={`flex w-full flex-col gap-1.5 ${mine ? 'items-end' : 'items-start'}`}>
        {group.items.map((m) => (
          <div key={m.id ?? m.clientMessageId} tabIndex={0}
            className={`group animate-rise relative max-w-[82%] outline-none md:max-w-[70%] ${mine
              ? `rounded-[10px] px-4 py-2.5 ${m.deleted ? 'border border-dashed border-hairline' : 'bg-outgoing'}`
              : 'py-0.5'} ${m.pending ? 'opacity-70' : ''}`}>
            <Toolbar m={m} mine={mine} readOnly={readOnly} onEdit={onEdit} onDelete={onDelete} align={mine ? 'right-2' : 'left-0'} />
            <Body m={m} mine={mine} />
            {m.pending && (
              <button className="meta mt-1 inline-flex items-center gap-1 hover:text-accent" onClick={() => onRetry(m)}>
                <RotateCcw size={11} /> retry
              </button>
            )}
          </div>
        ))}
      </div>
      <p className={`mt-2 flex items-center gap-1.5 ${mine ? 'pr-1' : ''}`}>
        <span className="meta">{formatClock(last.createdAt)}</span>
        {mine && <Receipt status={status(last)} />}
      </p>
    </div>
  )
}

export default function MessageList({ messages, meId, isGroup, readOnly, nameOf, status, onRetry, onEdit, onDelete, hasMore, onLoadOlder }) {
  const ref = useRef(null)
  const last = messages.at(-1)
  const lastKey = last ? last.id ?? last.clientMessageId : null

  // only jump to bottom when the newest message changes, not when older pages load
  useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight
  }, [lastKey])

  const groups = groupMessages(messages)

  return (
    <div ref={ref} className="min-h-0 flex-1 overflow-y-auto">
      <div className="mx-auto flex max-w-3xl flex-col gap-7 px-6 pt-6 pb-10 md:px-12">
        {hasMore && (
          <button className="eyebrow self-center py-2 transition-colors hover:text-ink" onClick={onLoadOlder}>
            Earlier correspondence
          </button>
        )}
        {!messages.length && (
          <div className="py-24 text-center">
            <p className="font-serif text-3xl text-ink">A blank page.</p>
            <p className="mt-2 text-sm text-ink-2">Write the first line below.</p>
          </div>
        )}
        {groups.map((g, i) => {
          const first = g.items[0]
          const prevLast = groups[i - 1]?.items.at(-1)
          const newDay = !prevLast || !sameDay(prevLast.createdAt, first.createdAt)
          return (
            <div key={first.id ?? first.clientMessageId} className="flex flex-col">
              {newDay && <DateSeparator iso={first.createdAt} />}
              <Group group={g} meId={meId} showName={isGroup} readOnly={readOnly} nameOf={nameOf} status={status}
                onRetry={onRetry} onEdit={onEdit} onDelete={onDelete} />
            </div>
          )
        })}
      </div>
    </div>
  )
}
