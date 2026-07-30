import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ArrowUp, Pencil, X } from 'lucide-react'

// backend drops typing=true repeats within 2s, so refresh just after that
const TYPING_REFRESH_MS = 2100
const TYPING_STOP_MS = 3000
const MAX_HEIGHT = 200

export default function MessageInput({ onSend, onTyping, editing, onSubmitEdit, onCancelEdit }) {
  const [text, setText] = useState('')
  const area = useRef(null)
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

  // entering edit mode loads the original text
  const [editingShown, setEditingShown] = useState(editing)
  if (editing !== editingShown) {
    setEditingShown(editing)
    if (editing) setText(editing.content)
  }

  useEffect(() => {
    if (editing) area.current?.focus()
  }, [editing])

  useLayoutEffect(() => {
    const el = area.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`
  }, [text])

  const change = (value) => {
    setText(value)
    if (editing) return
    const now = Date.now()
    if (now - lastSent.current > TYPING_REFRESH_MS) {
      onTypingRef.current(true)
      lastSent.current = now
    }
    clearTimeout(stopTimer.current)
    stopTimer.current = setTimeout(stopTyping, TYPING_STOP_MS)
  }

  const cancelEdit = () => {
    setText('')
    onCancelEdit()
  }

  const submit = (e) => {
    e?.preventDefault()
    const content = text.trim()
    if (!content) return
    if (editing) {
      if (content !== editing.content) onSubmitEdit(editing, content)
      else onCancelEdit()
    } else {
      onSend(content)
      stopTyping()
    }
    setText('')
  }

  return (
    <div className="px-4 pb-4 md:px-12 md:pb-8">
      <form onSubmit={submit}
        className="hairline mx-auto max-w-3xl rounded-[10px] border bg-elevated transition-colors focus-within:border-ink-3">
        {editing && (
          <div className="hairline flex items-center gap-3 border-b px-4 py-2 text-[13px]">
            <Pencil size={13} className="text-accent" />
            <span className="eyebrow text-accent">Editing</span>
            <span className="min-w-0 flex-1 truncate text-ink-3">{editing.content}</span>
            <button type="button" className="text-ink-3 hover:text-ink" onClick={cancelEdit} title="Cancel edit"><X size={14} /></button>
          </div>
        )}
        <div className="flex items-end gap-3 px-4 py-3">
          <textarea ref={area} rows={1} maxLength={4000} value={text}
            className="max-h-[200px] min-h-[28px] flex-1 resize-none bg-transparent py-1 text-[15px] leading-relaxed text-ink outline-none placeholder:text-ink-3"
            placeholder={editing ? 'Edit your message…' : 'Write something…'}
            onChange={(e) => change(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) submit(e)
              if (e.key === 'Escape' && editing) cancelEdit()
            }} />
          <button className="group inline-flex size-9 shrink-0 items-center justify-center rounded-[8px] bg-ink text-canvas transition duration-150 hover:bg-accent disabled:bg-hairline disabled:text-ink-3"
            disabled={!text.trim()} title={editing ? 'Save' : 'Send'}>
            <ArrowUp size={17} className="transition-transform duration-150 group-hover:-translate-y-0.5 group-disabled:translate-y-0" />
          </button>
        </div>
      </form>
      <p className="meta mx-auto mt-2 hidden max-w-3xl pl-1 md:block">enter to send · shift + enter for a new line</p>
    </div>
  )
}
