import { useRef, useState } from 'react'
import { Camera, Check } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import { IMAGE_TYPES, squareAvatar } from '../image'
import Avatar from '../components/Avatar'
import FormError from '../components/FormError'
import PageShell from '../components/PageShell'
import { dateHeading, displayName } from '../lib'

const BIO_MAX = 200

const Row = ({ label, children }) => (
  <div className="hairline grid grid-cols-[120px_1fr] items-baseline gap-6 border-t py-4">
    <span className="eyebrow">{label}</span>
    <span className="text-sm text-ink">{children}</span>
  </div>
)

function PhotoPicker({ user, setUser }) {
  const input = useRef(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const run = async (fn) => {
    setError(null)
    setBusy(true)
    try {
      setUser(await fn())
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const pick = (file) => file && run(async () => api.uploadAvatar(await squareAvatar(file)))

  return (
    <div className="flex items-center gap-6">
      <button type="button" className="group relative rounded-full disabled:opacity-60" title="Change photo"
        disabled={busy} onClick={() => input.current.click()}>
        <Avatar name={displayName(user)} src={user.avatarUrl} size="xl" />
        <span className="absolute inset-0 grid place-items-center rounded-full bg-ink/45 text-canvas opacity-0 transition-opacity duration-150 group-hover:opacity-100 group-focus-visible:opacity-100">
          <Camera size={20} />
        </span>
      </button>
      <div>
        <p className="text-sm text-ink-2">@{user.username}</p>
        <div className="mt-2 flex gap-5">
          <button type="button" className="btn-quiet" disabled={busy} onClick={() => input.current.click()}>
            {busy ? 'Saving…' : user.avatarUrl ? 'Change photo' : 'Add photo'}
          </button>
          {user.avatarUrl && (
            <button type="button" className="btn-quiet hover:text-danger" disabled={busy}
              onClick={() => run(api.removeAvatar)}>Remove</button>
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

export default function ProfilePage() {
  const { user, setUser } = useAuth()
  const [name, setName] = useState(user.displayName || '')
  const [bio, setBio] = useState(user.bio || '')
  const [error, setError] = useState(null)
  const [saved, setSaved] = useState(false)

  const dirty = name !== (user.displayName || '') || bio !== (user.bio || '')

  const edit = (setter) => (e) => {
    setter(e.target.value)
    setSaved(false)
  }

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    setSaved(false)
    try {
      const updated = await api.updateMe(name, bio)
      setUser(updated)
      setBio(updated.bio || '')
      setSaved(true)
    } catch (err) {
      setError(err)
    }
  }

  const joined = dateHeading(user.createdAt).date

  return (
    <PageShell eyebrow="Profile" title={displayName(user)}>
      <PhotoPicker user={user} setUser={setUser} />

      <div className="mt-12">
        <Row label="Email">{user.email}</Row>
        <Row label="Role"><span className="font-mono text-[12px]">{user.role.toLowerCase()}</span></Row>
        <Row label="Since">{joined}</Row>
      </div>

      <form onSubmit={submit} className="hairline mt-12 flex flex-col gap-8 border-t pt-10">
        <label className="flex flex-col">
          <span className="eyebrow">Display name</span>
          <input className="field font-serif text-2xl" value={name} onChange={edit(setName)} />
          <FormError error={error} field="displayName" />
        </label>
        <label className="flex flex-col">
          <span className="flex items-baseline justify-between">
            <span className="eyebrow">Bio</span>
            <span className={`meta ${bio.length > BIO_MAX - 20 ? 'text-accent' : ''}`}>{bio.length} / {BIO_MAX}</span>
          </span>
          <textarea className="field min-h-[84px] resize-none leading-relaxed" rows={3} maxLength={BIO_MAX}
            placeholder="A line or two about you" value={bio} onChange={edit(setBio)} />
          <FormError error={error} field="bio" />
        </label>
        <FormError error={error} />
        <div className="flex items-center gap-4">
          <button className="btn-primary" disabled={!name.trim() || !dirty}>Save</button>
          {saved && <span className="animate-rise inline-flex items-center gap-1.5 text-sm text-sage"><Check size={15} /> Saved</span>}
        </div>
      </form>
    </PageShell>
  )
}
