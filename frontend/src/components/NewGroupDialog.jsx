import { useState } from 'react'
import { X } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import { displayName } from '../lib'
import FormError from './FormError'
import UserSearch from './UserSearch'

export default function NewGroupDialog({ onCreated, onClose }) {
  const { user } = useAuth()
  const [name, setName] = useState('')
  const [members, setMembers] = useState([])
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const create = async () => {
    setError(null)
    setBusy(true)
    try {
      onCreated(await api.createGroup(name.trim(), members.map((m) => m.id)))
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="animate-rise flex flex-col gap-5 px-6 pt-2 pb-6">
      <div className="flex items-center justify-between">
        <p className="eyebrow">New circle</p>
        <button className="icon-btn -mr-2" onClick={onClose} title="Close"><X size={16} /></button>
      </div>

      <label className="flex flex-col">
        <span className="eyebrow text-[9.5px]">Name</span>
        <input className="field font-serif text-xl" autoFocus placeholder="Untitled group" value={name}
          onChange={(e) => setName(e.target.value)} />
        <FormError error={error} field="name" />
      </label>

      {members.length > 0 && (
        <ul className="flex flex-wrap gap-x-3 gap-y-1.5">
          {members.map((m) => (
            <li key={m.id} className="inline-flex items-center gap-1 text-[13px] text-ink">
              {displayName(m)}
              <button className="text-ink-3 hover:text-accent" title="Remove"
                onClick={() => setMembers(members.filter((x) => x.id !== m.id))}>
                <X size={12} />
              </button>
            </li>
          ))}
        </ul>
      )}

      <UserSearch placeholder="Add people…" onPick={(u) => setMembers((m) => [...m, u])}
        excludeIds={[user.id, ...members.map((m) => m.id)]} />

      <FormError error={error} />
      <button className="btn-primary self-start" disabled={busy || !name.trim() || !members.length} onClick={create}>
        Create group
      </button>
    </div>
  )
}
