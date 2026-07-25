import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, RequireAuth } from './auth/AuthContext'
import { StompProvider } from './ws/StompContext'
import ErrorToast from './components/ErrorToast'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ChatPage from './pages/ChatPage'

const authed = (el) => <RequireAuth>{el}</RequireAuth>

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <StompProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/" element={authed(<ChatPage />)} />
            <Route path="/c/:id" element={authed(<ChatPage />)} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          <ErrorToast />
        </StompProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
