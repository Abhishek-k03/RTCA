import { useRef, useState } from 'react'
import { Camera } from 'lucide-react'
import { IMAGE_TYPES, squareAvatar } from '../image'
import Avatar from './Avatar'
import FormError from './FormError'

// round photo with change/remove, for profiles and groups. onUpload gets the cropped square
export default function PhotoPicker({ name, src, onUpload, onRemove, children }) {
  const input = useRef(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const run = async (fn) => {
    setError(null)
    setBusy(true)
    try {
      await fn()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const pick = (file) => file && run(async () => onUpload(await squareAvatar(file)))
  const choose = () => input.current.click()

  return (
    <div className="flex items-center gap-6">
      <button type="button" className="group relative shrink-0 rounded-full disabled:opacity-60" title="Change photo"
        disabled={busy} onClick={choose}>
        <Avatar name={name} src={src} size="xl" />
        <span className="absolute inset-0 grid place-items-center rounded-full bg-ink/45 text-canvas opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-visible:opacity-100">
          <Camera size={20} />
        </span>
      </button>
      <div className="min-w-0">
        {children}
        <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1">
          <button type="button" className="btn-quiet" disabled={busy} onClick={choose}>
            {busy ? 'Saving…' : src ? 'Change photo' : 'Add photo'}
          </button>
          {src && (
            <button type="button" className="btn-quiet hover:text-danger" disabled={busy} onClick={() => run(onRemove)}>Remove</button>
          )}
        </div>
        <FormError error={error} />
      </div>
      <input ref={input} type="file" accept={IMAGE_TYPES.join(',')} hidden onChange={(e) => {
        pick(e.target.files[0])
        e.target.value = ''
      }} />
    </div>
  )
}
