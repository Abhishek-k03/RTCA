import { useState } from 'react'
import { Check } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import Avatar from '../components/Avatar'
import FormError from '../components/FormError'
import PageShell from '../components/PageShell'
import { dateHeading, displayName } from '../lib'

const Row = ({ label, children }) => (
  <div className="hairline grid grid-cols-[120px_1fr] items-baseline gap-6 border-t py-4">
    <span className="eyebrow">{label}</span>
    <span className="text-sm text-ink">{children}</span>
  </div>
)

export default function ProfilePage() {
  const { user, setUser } = useAuth()
  const [name, setName] = useState(user.displayName || '')
  const [error, setError] = useState(null)
  const [saved, setSaved] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    setSaved(false)
    try {
      setUser(await api.updateMe(name))
      setSaved(true)
    } catch (err) {
      setError(err)
    }
  }

  const joined = dateHeading(user.createdAt).date

  return (
    <PageShell eyebrow="Profile" title={displayName(user)}>
      <div className="flex items-center gap-5">
        <Avatar name={displayName(user)} size="lg" />
        <p className="text-sm text-ink-2">@{user.username}</p>
      </div>

      <div className="mt-12">
        <Row label="Email">{user.email}</Row>
        <Row label="Role"><span className="font-mono text-[12px]">{user.role.toLowerCase()}</span></Row>
        <Row label="Since">{joined}</Row>
      </div>

      <form onSubmit={submit} className="hairline mt-12 border-t pt-10">
        <label className="flex flex-col">
          <span className="eyebrow">Display name</span>
          <input className="field font-serif text-2xl" value={name} onChange={(e) => {
            setName(e.target.value)
            setSaved(false)
          }} />
          <FormError error={error} field="displayName" />
        </label>
        <FormError error={error} />
        <div className="mt-6 flex items-center gap-4">
          <button className="btn-primary" disabled={!name.trim() || name === user.displayName}>Save</button>
          {saved && <span className="animate-rise inline-flex items-center gap-1.5 text-sm text-sage"><Check size={15} /> Saved</span>}
        </div>
      </form>
    </PageShell>
  )
}
