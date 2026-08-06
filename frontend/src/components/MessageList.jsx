import { useEffect, useRef, useState } from 'react'
import { Check, CheckCheck, Copy, CornerUpLeft, Image as ImageIcon, Pencil, RotateCcw, SmilePlus, Trash2 } from 'lucide-react'
import { useFileUrl } from '../api/files'
import { REACTIONS, dateHeading, formatClock, previewText, sameDay, withinWindow } from '../lib'
import ImageViewer from './ImageViewer'

const GROUP_GAP_MS = 5 * 60 * 1000
const IMAGE_MAX_W = 360
const IMAGE_MAX_H = 420

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

function Toolbar({ m, mine, readOnly, onEdit, onDelete, onReply, onReact, align }) {
  // null, 'react' or 'delete'
  const [menu, setMenu] = useState(null)
  const canChange = mine && !readOnly && !m.deleted && !m.pending && withinWindow(m.createdAt)
  const canEdit = canChange && m.type !== 'IMAGE'

  useEffect(() => {
    if (!menu) return
    const close = () => setMenu(null)
    window.addEventListener('click', close)
    return () => window.removeEventListener('click', close)
  }, [menu])

  if (m.pending || m.deleted) return null
  const btn = 'inline-flex size-7 items-center justify-center rounded-[6px] text-ink-2 transition-colors hover:bg-outgoing hover:text-ink'
  const open = (name) => (e) => {
    e.stopPropagation()
    setMenu(menu === name ? null : name)
  }
  const popover = `absolute top-8 border border-hairline bg-elevated shadow-[0_2px_8px_rgba(28,27,25,0.06)] ${mine ? 'right-0' : 'left-0'}`

  return (
    <div className={`absolute -top-6 z-10 flex translate-y-0.5 items-center gap-0.5 rounded-[8px] border border-hairline bg-elevated p-0.5 opacity-0 shadow-[0_1px_2px_rgba(28,27,25,0.05)] transition duration-150 group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100 ${menu ? 'translate-y-0 opacity-100' : ''} ${align}`}>
      {!readOnly && <button className={btn} title="Reply" onClick={() => onReply(m)}><CornerUpLeft size={14} /></button>}
      {!readOnly && (
        <div className="relative">
          <button className={btn} title="React" onClick={open('react')}><SmilePlus size={14} /></button>
          {menu === 'react' && (
            <div className={`${popover} flex gap-0.5 rounded-full p-1`}>
              {REACTIONS.map((emoji) => (
                <button key={emoji} title={`React ${emoji}`} onClick={() => onReact(m, emoji)}
                  className="inline-flex size-8 items-center justify-center rounded-full text-[17px] transition-transform duration-150 hover:scale-125 hover:bg-outgoing">
                  {emoji}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
      {m.content && <button className={btn} title="Copy" onClick={() => navigator.clipboard?.writeText(m.content)}><Copy size={14} /></button>}
      {canEdit && <button className={btn} title="Edit" onClick={() => onEdit(m)}><Pencil size={14} /></button>}
      <div className="relative">
        <button className={btn} title="Delete" onClick={open('delete')}><Trash2 size={14} /></button>
        {menu === 'delete' && (
          <div className={`${popover} flex w-44 flex-col rounded-[8px] py-1 text-sm`}>
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

// space is reserved from the known size, so loading doesn't shift the list
function Photo({ image, onOpen }) {
  const url = useFileUrl(image.url)
  const ratio = image.width && image.height ? image.width / image.height : 4 / 3
  const width = Math.min(IMAGE_MAX_W, image.width || IMAGE_MAX_W, IMAGE_MAX_H * ratio)
  return (
    <button type="button" className="grid max-w-full place-items-center overflow-hidden rounded-[7px] bg-ink/5"
      style={{ width, aspectRatio: ratio }} title="View photo" disabled={!url} onClick={() => onOpen(url)}>
      {url
        ? <img src={url} alt="" className="size-full object-cover" />
        : <ImageIcon size={20} className="animate-pulse text-ink-3" />}
    </button>
  )
}

// the message this one replies to. clicking it jumps there if it's loaded
function Quote({ reply, nameOf, onJump, className = '' }) {
  return (
    <button type="button" onClick={() => onJump(reply.id)}
      className={`mb-1.5 block max-w-full border-l-2 border-accent/70 pl-2.5 text-left ${className}`}>
      <span className="block truncate text-[12px] font-medium text-accent">
        {nameOf(reply.senderId, { senderUsername: reply.senderName })}
      </span>
      <span className={`block truncate text-[13px] ${reply.deleted ? 'text-ink-3 italic' : 'text-ink-2'}`}>{previewText(reply)}</span>
    </button>
  )
}

function Reactions({ m, meId, readOnly, nameOf, onReact, className = '' }) {
  if (!m.reactions?.length || m.deleted) return null
  return (
    <div className={`mt-2 flex flex-wrap gap-1 ${className}`}>
      {m.reactions.map((r) => {
        const picked = r.userIds.includes(meId)
        const who = r.userIds.map((u) => (u === meId ? 'You' : nameOf(u, {}) ?? 'Someone')).join(', ')
        return (
          <button key={r.emoji} type="button" title={who} disabled={readOnly} onClick={() => onReact(m, r.emoji)}
            className={`inline-flex items-center gap-1 rounded-full border px-1.5 text-[13px] leading-6 transition-colors ${picked
              ? 'border-accent/50 bg-accent/10 text-ink'
              : 'border-hairline bg-elevated text-ink-2 enabled:hover:border-ink-3'}`}>
            <span>{r.emoji}</span>
            <span className="font-mono text-[11px]">{r.userIds.length}</span>
          </button>
        )
      })}
    </div>
  )
}

function Body({ m, mine, nameOf, onOpenImage, onJump }) {
  if (m.deleted) {
    return <p className="text-[14px] text-ink-3 italic">{mine ? 'You deleted this message.' : 'This message was deleted.'}</p>
  }
  const quote = m.replyTo && (
    <Quote reply={m.replyTo} nameOf={nameOf} onJump={onJump} className={m.image && mine ? 'mx-1 mt-1' : ''} />
  )
  if (m.type === 'IMAGE' && m.image) {
    return (
      <>
        {quote}
        <Photo image={m.image} onOpen={onOpenImage} />
        {m.content && (
          <p className={`max-w-[360px] text-[15px] leading-relaxed break-words whitespace-pre-wrap text-ink ${mine ? 'px-2.5 pt-2 pb-1' : 'pt-2'}`}>
            {m.content}
          </p>
        )}
      </>
    )
  }
  return (
    <>
      {quote}
      <p className="text-[15px] leading-relaxed break-words whitespace-pre-wrap text-ink">
        {m.content}
        {m.editedAt && <span className="ml-2 align-baseline text-[11px] text-ink-3">edited</span>}
      </p>
    </>
  )
}

function Group({ group, meId, showName, readOnly, nameOf, status, flash, onRetry, onEdit, onDelete, onReply, onReact, onOpenImage, onJump }) {
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
          <div key={m.id ?? m.clientMessageId} tabIndex={0} data-mid={m.id}
            className={`group animate-rise relative max-w-[82%] rounded-[10px] outline-none transition-shadow duration-500 md:max-w-[70%] ${mine
              ? `${m.image && !m.deleted ? 'p-1.5' : 'px-4 py-2.5'} ${m.deleted ? 'border border-dashed border-hairline' : 'bg-outgoing'}`
              : 'py-0.5'} ${m.pending ? 'opacity-70' : ''} ${flash === m.id ? 'ring-2 ring-accent/40 ring-offset-4 ring-offset-surface' : ''}`}>
            <Toolbar m={m} mine={mine} readOnly={readOnly} onEdit={onEdit} onDelete={onDelete} onReply={onReply} onReact={onReact}
              align={mine ? 'right-2' : 'left-0'} />
            <Body m={m} mine={mine} nameOf={nameOf} onOpenImage={onOpenImage} onJump={onJump} />
            <Reactions m={m} meId={meId} readOnly={readOnly} nameOf={nameOf} onReact={onReact}
              className={m.image && mine ? 'px-1 pb-0.5' : ''} />
            {m.pending && (
              <button className={`meta mt-1 inline-flex items-center gap-1 hover:text-accent ${m.image ? 'px-2.5 pb-1' : ''}`} onClick={() => onRetry(m)}>
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

export default function MessageList({ messages, meId, isGroup, readOnly, nameOf, status, onRetry, onEdit, onDelete, onReply, onReact, hasMore, onLoadOlder }) {
  const ref = useRef(null)
  const [viewing, setViewing] = useState(null)
  const [flash, setFlash] = useState(null)
  const flashTimer = useRef(null)
  useEffect(() => () => clearTimeout(flashTimer.current), [])

  const jumpTo = (messageId) => {
    const el = ref.current?.querySelector(`[data-mid="${messageId}"]`)
    if (!el) return
    el.scrollIntoView({ block: 'center', behavior: 'smooth' })
    setFlash(messageId)
    clearTimeout(flashTimer.current)
    flashTimer.current = setTimeout(() => setFlash(null), 1600)
  }
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
              <Group group={g} meId={meId} showName={isGroup} readOnly={readOnly} nameOf={nameOf} status={status} flash={flash}
                onRetry={onRetry} onEdit={onEdit} onDelete={onDelete} onReply={onReply} onReact={onReact}
                onOpenImage={setViewing} onJump={jumpTo} />
            </div>
          )
        })}
      </div>
      {viewing && <ImageViewer url={viewing} onClose={() => setViewing(null)} />}
    </div>
  )
}
