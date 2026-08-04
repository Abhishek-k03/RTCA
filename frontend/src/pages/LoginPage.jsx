import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ArrowRight } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import AuthLayout from '../components/AuthLayout'
import FormError from '../components/FormError'

export default function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ login: '', password: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  if (loading) return null
  if (user) return <Navigate to="/" replace />

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
    <AuthLayout eyebrow="Sign in" title="Welcome back"
      footer={<>New here? <Link to="/register" className="text-ink underline decoration-hairline underline-offset-4 hover:decoration-accent">Create an account</Link></>}>
      <form onSubmit={submit} className="flex flex-col gap-6">
        <label className="flex flex-col">
          <span className="eyebrow">Username or email</span>
          <input className="field" autoComplete="username" autoFocus value={form.login}
            onChange={(e) => setForm({ ...form, login: e.target.value })} />
          <FormError error={error} field="login" />
        </label>
        <label className="flex flex-col">
          <span className="eyebrow">Password</span>
          <input className="field" type="password" autoComplete="current-password" value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
          <FormError error={error} field="password" />
        </label>
        <FormError error={error} />
        <button className="btn-primary group mt-2 self-start" disabled={busy}>
          Continue <ArrowRight size={16} className="transition-transform duration-150 group-hover:translate-x-0.5" />
        </button>
      </form>
    </AuthLayout>
  )
}
