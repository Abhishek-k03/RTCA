import { useState } from 'react'
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

  const create = async () => {
    setError(null)
    try {
      onCreated(await api.createGroup(name.trim(), members.map((m) => m.id)))
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="flex flex-col gap-2 border-b p-2">
      <div className="flex">
        <span className="font-bold">New group</span>
        <button className="ml-auto" onClick={onClose}>✕</button>
      </div>
      <input className="border p-1" placeholder="Group name" value={name} onChange={(e) => setName(e.target.value)} />
      <FormError error={error} field="name" />
      <ul className="flex flex-wrap gap-1">
        {members.map((m) => (
          <li key={m.id} className="border px-1">
            {displayName(m)}{' '}
            <button onClick={() => setMembers(members.filter((x) => x.id !== m.id))}>×</button>
          </li>
        ))}
      </ul>
      <UserSearch onPick={(u) => setMembers((m) => [...m, u])} excludeIds={[user.id, ...members.map((m) => m.id)]} />
      <button className="border p-1" disabled={!name.trim() || !members.length} onClick={create}>
        Create group
      </button>
      <FormError error={error} />
    </div>
  )
}
