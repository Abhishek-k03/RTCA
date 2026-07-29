import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ArrowRight } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { api } from '../api/endpoints'
import AuthLayout from '../components/AuthLayout'
import FormError from '../components/FormError'

const fields = [
  ['username', 'Username', 'text', 'username'],
  ['email', 'Email', 'email', 'email'],
  ['password', 'Password', 'password', 'new-password'],
  ['displayName', 'Display name (optional)', 'text', 'name'],
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
    <AuthLayout eyebrow="New account" title="Begin a correspondence"
      footer={<>Already have an account? <Link to="/login" className="text-ink underline decoration-hairline underline-offset-4 hover:decoration-accent">Sign in</Link></>}>
      <form onSubmit={submit} className="flex flex-col gap-6">
        {fields.map(([name, label, type, auto], i) => (
          <label key={name} className="flex flex-col">
            <span className="eyebrow">{label}</span>
            <input className="field" type={type} autoComplete={auto} autoFocus={i === 0} value={form[name]}
              onChange={(e) => setForm({ ...form, [name]: e.target.value })} />
            <FormError error={error} field={name} />
          </label>
        ))}
        <FormError error={error} />
        <button className="btn-primary group mt-2 self-start" disabled={busy}>
          Create account <ArrowRight size={16} className="transition-transform duration-150 group-hover:translate-x-0.5" />
        </button>
      </form>
    </AuthLayout>
  )
}
