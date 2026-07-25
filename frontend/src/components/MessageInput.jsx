import { useEffect, useRef, useState } from 'react'

// backend drops typing=true repeats within 2s, so refresh just after that
const TYPING_REFRESH_MS = 2100
const TYPING_STOP_MS = 3000

export default function MessageInput({ onSend, onTyping }) {
  const [text, setText] = useState('')
  const lastSent = useRef(0)
  const stopTimer = useRef(null)
  const onTypingRef = useRef(onTyping)
  useEffect(() => { onTypingRef.current = onTyping })

  const stopTyping = () => {
    clearTimeout(stopTimer.current)
    if (lastSent.current) {
      onTypingRef.current(false)
      lastSent.current = 0
    }
  }

  useEffect(() => stopTyping, [])

  const change = (value) => {
    setText(value)
    const now = Date.now()
    if (now - lastSent.current > TYPING_REFRESH_MS) {
      onTypingRef.current(true)
      lastSent.current = now
    }
    clearTimeout(stopTimer.current)
    stopTimer.current = setTimeout(stopTyping, TYPING_STOP_MS)
  }

  const submit = (e) => {
    e?.preventDefault()
    const content = text.trim()
    if (!content) return
    onSend(content)
    setText('')
    stopTyping()
  }

  return (
    <form onSubmit={submit} className="flex gap-2 border-t p-2">
      <textarea className="flex-1 border p-1" rows={2} maxLength={4000} value={text}
        placeholder="Type a message (Enter to send, Shift+Enter for newline)"
        onChange={(e) => change(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey) submit(e)
        }} />
      <button className="border px-3">Send</button>
    </form>
  )
}
