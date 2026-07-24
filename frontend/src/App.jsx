import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, RequireAuth, useAuth } from './auth/AuthContext'
import { StompProvider, useStomp } from './ws/StompContext'
import ErrorToast from './components/ErrorToast'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'

function Home() {
  const { user, logout } = useAuth()
  const { connected } = useStomp()
  return (
    <div className="flex gap-2 p-4">
      <span>Logged in as {user.username}</span>
      <span className="text-gray-500">{connected ? '● connected' : '○ connecting…'}</span>
      <button className="underline" onClick={logout}>Log out</button>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <StompProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/" element={<RequireAuth><Home /></RequireAuth>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          <ErrorToast />
        </StompProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
