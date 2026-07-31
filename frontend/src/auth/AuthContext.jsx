import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { Navigate } from 'react-router'
import { getToken, setOnUnauthorized, setToken } from '../api/client'
import { api } from '../api/endpoints'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(getToken())
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(!!getToken())

  const logout = useCallback(() => {
    setToken(null)
    setTokenState(null)
    setUser(null)
  }, [])

  useEffect(() => setOnUnauthorized(logout), [logout])

  useEffect(() => {
    if (!token || user) return
    api.me().then(setUser).catch(logout).finally(() => setLoading(false))
  }, [token, user, logout])

  const login = async (login, password) => {
    const res = await api.login(login, password)
    setToken(res.accessToken)
    setUser(res.user)
    setTokenState(res.accessToken)
  }

  return (
    <AuthContext.Provider value={{ token, user, setUser, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)

export function RequireAuth({ children }) {
  const { token, user, loading } = useAuth()
  if (!token) return <Navigate to="/login" replace />
  if (loading || !user) return <p className="eyebrow grid h-full place-items-center">Opening correspondence…</p>
  return children
}

export function RequireAdmin({ children }) {
  const { user } = useAuth()
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return children
}
