import { useState } from 'react'
import { Check } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import FormError from '../components/FormError'
import PageShell from '../components/PageShell'
import PhotoPicker from '../components/PhotoPicker'
import { dateHeading, displayName } from '../lib'

const BIO_MAX = 200

const Row = ({ label, children }) => (
  <div className="hairline grid grid-cols-[120px_1fr] items-baseline gap-6 border-t py-4">
    <span className="eyebrow">{label}</span>
    <span className="text-sm text-ink">{children}</span>
  </div>
)

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
      <PhotoPicker name={displayName(user)} src={user.avatarUrl}
        onUpload={async (blob) => setUser(await api.uploadAvatar(blob))}
        onRemove={async () => setUser(await api.removeAvatar())}>
        <p className="text-sm text-ink-2">@{user.username}</p>
      </PhotoPicker>

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
