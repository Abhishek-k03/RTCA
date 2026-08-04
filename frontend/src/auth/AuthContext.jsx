import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { Navigate } from 'react-router'
import { refreshSession, setOnUnauthorized, setToken } from '../api/client'
import { api } from '../api/endpoints'
import { clearFileCache } from '../api/files'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  // local only, for when the server already ended the session
  const clear = useCallback(() => {
    setToken(null)
    setUser(null)
    clearFileCache()
  }, [])

  const logout = useCallback(() => {
    api.logout().catch(() => {})
    clear()
  }, [clear])

  useEffect(() => setOnUnauthorized(clear), [clear])

  // the refresh cookie brings the session back after a reload
  useEffect(() => {
    refreshSession()
      .then((data) => data && setUser(data.user))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const login = async (login, password) => {
    const res = await api.login(login, password)
    setToken(res.accessToken)
    setUser(res.user)
  }

  return (
    <AuthContext.Provider value={{ user, setUser, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)

export function RequireAuth({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <p className="eyebrow grid h-full place-items-center">Opening correspondence…</p>
  if (!user) return <Navigate to="/login" replace />
  return children
}

export function RequireAdmin({ children }) {
  const { user } = useAuth()
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return children
}
