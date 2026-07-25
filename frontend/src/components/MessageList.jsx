import { useEffect, useRef } from 'react'
import { formatTime } from '../lib'

export default function MessageList({ messages, meId, status, onRetry, hasMore, onLoadOlder }) {
  const ref = useRef(null)
  const last = messages.at(-1)
  const lastKey = last ? last.id ?? last.clientMessageId : null

  // only jump to bottom when the newest message changes, not when older pages load
  useEffect(() => {
    if (ref.current) ref.current.scrollTop = ref.current.scrollHeight
  }, [lastKey])

  return (
    <div ref={ref} className="flex flex-1 flex-col gap-1 overflow-y-auto p-2">
      {hasMore && <button className="self-center border px-2" onClick={onLoadOlder}>Load older</button>}
      {messages.map((m) => {
        const key = m.id ?? m.clientMessageId
        if (m.type === 'SYSTEM') {
          return <p key={key} className="self-center text-sm italic text-gray-500">{m.content}</p>
        }
        const mine = m.senderId === meId
        return (
          <div key={key} className={`flex max-w-[70%] flex-col border p-1 ${mine ? 'self-end' : 'self-start'}`}>
            <span className="text-xs text-gray-500">{mine ? 'You' : m.senderUsername}</span>
            <span className="whitespace-pre-wrap break-words">{m.content}</span>
            <span className="text-xs text-gray-500">
              {formatTime(m.createdAt)} {mine && status(m)}
              {m.pending && <button className="ml-1 underline" onClick={() => onRetry(m)}>retry</button>}
            </span>
          </div>
        )
      })}
    </div>
  )
}
