import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import FormError from '../components/FormError'

const fields = [
  ['username', 'Username', 'text'],
  ['email', 'Email', 'email'],
  ['password', 'Password (8+ chars)', 'password'],
  ['displayName', 'Display name (optional)', 'text'],
]

export default function RegisterPage() {
  const { token, login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', email: '', password: '', displayName: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  if (token) return <Navigate to="/" replace />

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.register({ ...form, displayName: form.displayName || null })
      await login(form.username, form.password)
      navigate('/')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="mx-auto mt-16 flex max-w-sm flex-col gap-2 border p-4">
      <h1 className="text-xl">Register</h1>
      {fields.map(([name, label, type]) => (
        <div key={name} className="flex flex-col">
          <input className="border p-1" type={type} placeholder={label} value={form[name]}
            onChange={(e) => setForm({ ...form, [name]: e.target.value })} />
          <FormError error={error} field={name} />
        </div>
      ))}
      <FormError error={error} />
      <button className="border p-1" disabled={busy}>Register</button>
      <Link to="/login" className="underline">Already have an account?</Link>
    </form>
  )
}
