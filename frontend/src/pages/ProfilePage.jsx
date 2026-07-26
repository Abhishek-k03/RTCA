import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import FormError from '../components/FormError'
import { formatTime } from '../lib'

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

  return (
    <div className="mx-auto mt-8 flex max-w-sm flex-col gap-2 p-4">
      <Link to="/" className="underline">← Back to chats</Link>
      <h1 className="text-xl">Profile</h1>
      <p>Username: {user.username}</p>
      <p>Email: {user.email}</p>
      <p>Role: {user.role}</p>
      <p>Joined: {formatTime(user.createdAt)}</p>
      <form onSubmit={submit} className="flex flex-col gap-2 border p-2">
        <label>Display name</label>
        <input className="border p-1" value={name} onChange={(e) => setName(e.target.value)} />
        <FormError error={error} field="displayName" />
        <FormError error={error} />
        <button className="border p-1">Save</button>
        {saved && <p className="text-sm">Saved</p>}
      </form>
    </div>
  )
}
