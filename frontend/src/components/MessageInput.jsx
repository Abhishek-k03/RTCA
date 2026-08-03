import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ArrowUp, ImagePlus, Pencil, X } from 'lucide-react'
import { IMAGE_TYPES } from '../image'

// backend drops typing=true repeats within 2s, so refresh just after that
const TYPING_REFRESH_MS = 2100
const TYPING_STOP_MS = 3000
const MAX_HEIGHT = 200

const firstImage = (files) => [...(files || [])].find((f) => f.type.startsWith('image/'))

export default function MessageInput({ onSend, onSendImage, onTyping, editing, onSubmitEdit, onCancelEdit }) {
  const [text, setText] = useState('')
  const [image, setImage] = useState(null)
  const [dragging, setDragging] = useState(false)
  const area = useRef(null)
  const fileInput = useRef(null)
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
    if (editing) {
      setText(editing.content)
      setImage(null)
    }
  }

  useEffect(() => {
    if (editing) area.current?.focus()
  }, [editing])

  // the preview url lives as long as the picked image
  useEffect(() => {
    if (!image) return
    return () => URL.revokeObjectURL(image.preview)
  }, [image])

  useLayoutEffect(() => {
    const el = area.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`
  }, [text])

  const pickImage = (file) => {
    if (!file || editing) return
    setImage({ file, preview: URL.createObjectURL(file) })
    area.current?.focus()
  }

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
    if (image) {
      onSendImage(image.file, content)
      setImage(null)
      stopTyping()
    } else if (!content) {
      return
    } else if (editing) {
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
        onDragOver={(e) => {
          if (editing || !e.dataTransfer.types.includes('Files')) return
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={(e) => !e.currentTarget.contains(e.relatedTarget) && setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          pickImage(firstImage(e.dataTransfer.files))
        }}
        className={`hairline mx-auto max-w-3xl rounded-[10px] border bg-elevated transition-colors focus-within:border-ink-3 ${dragging ? 'border-accent! border-dashed' : ''}`}>
        {editing && (
          <div className="hairline flex items-center gap-3 border-b px-4 py-2 text-[13px]">
            <Pencil size={13} className="text-accent" />
            <span className="eyebrow text-accent">Editing</span>
            <span className="min-w-0 flex-1 truncate text-ink-3">{editing.content}</span>
            <button type="button" className="text-ink-3 hover:text-ink" onClick={cancelEdit} title="Cancel edit"><X size={14} /></button>
          </div>
        )}
        {image && (
          <div className="hairline animate-rise flex items-center gap-3 border-b px-4 py-2.5">
            <img src={image.preview} alt="" className="size-12 rounded-[6px] object-cover" />
            <span className="min-w-0 flex-1">
              <span className="eyebrow block text-accent">Photo</span>
              <span className="block truncate text-[13px] text-ink-3">{image.file.name || 'Pasted image'}</span>
            </span>
            <button type="button" className="text-ink-3 hover:text-ink" onClick={() => setImage(null)} title="Remove photo"><X size={14} /></button>
          </div>
        )}
        <div className="flex items-end gap-2 px-3 py-3 md:gap-3 md:px-4">
          <button type="button" className="icon-btn shrink-0 disabled:opacity-40 disabled:hover:bg-transparent" title="Send a photo"
            disabled={!!editing} onClick={() => fileInput.current.click()}>
            <ImagePlus size={17} />
          </button>
          <input ref={fileInput} type="file" accept={IMAGE_TYPES.join(',')} hidden onChange={(e) => {
            pickImage(e.target.files[0])
            e.target.value = ''
          }} />
          <textarea ref={area} rows={1} maxLength={4000} value={text}
            className="max-h-[200px] min-h-[28px] flex-1 resize-none bg-transparent py-1 text-[15px] leading-relaxed text-ink outline-none placeholder:text-ink-3"
            placeholder={editing ? 'Edit your message…' : image ? 'Add a caption…' : 'Write something…'}
            onChange={(e) => change(e.target.value)}
            onPaste={(e) => {
              const file = firstImage(e.clipboardData.files)
              if (file && !editing) {
                e.preventDefault()
                pickImage(file)
              }
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) submit(e)
              if (e.key === 'Escape' && editing) cancelEdit()
              if (e.key === 'Escape' && image) setImage(null)
            }} />
          <button className="group inline-flex size-9 shrink-0 items-center justify-center rounded-[8px] bg-ink text-canvas transition duration-150 hover:bg-accent disabled:bg-hairline disabled:text-ink-3"
            disabled={!text.trim() && !image} title={editing ? 'Save' : 'Send'}>
            <ArrowUp size={17} className="transition-transform duration-150 group-hover:-translate-y-0.5 group-disabled:translate-y-0" />
          </button>
        </div>
      </form>
      <p className="meta mx-auto mt-2 hidden max-w-3xl pl-1 md:block">enter to send · shift + enter for a new line · paste or drop a photo</p>
    </div>
  )
}
