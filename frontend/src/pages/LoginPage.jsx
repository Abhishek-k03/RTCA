import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import FormError from '../components/FormError'

export default function LoginPage() {
  const { token, login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ login: '', password: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  if (token) return <Navigate to="/" replace />

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(form.login, form.password)
      navigate('/')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="mx-auto mt-16 flex max-w-sm flex-col gap-2 border p-4">
      <h1 className="text-xl">Log in</h1>
      <input className="border p-1" placeholder="Username or email" value={form.login}
        onChange={(e) => setForm({ ...form, login: e.target.value })} />
      <FormError error={error} field="login" />
      <input className="border p-1" type="password" placeholder="Password" value={form.password}
        onChange={(e) => setForm({ ...form, password: e.target.value })} />
      <FormError error={error} field="password" />
      <FormError error={error} />
      <button className="border p-1" disabled={busy}>Log in</button>
      <Link to="/register" className="underline">Create an account</Link>
    </form>
  )
}
