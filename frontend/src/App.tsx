import { Navigate, Route, Routes } from 'react-router-dom'

import { AppShell } from './components/AppShell'
import { AuthCallbackPage } from './auth/AuthCallbackPage'
import { LoginPage } from './auth/LoginPage'
import { useAuth } from './auth/AuthContext'
import { DashboardPage } from './pages/DashboardPage'
import { OperationsPage } from './pages/OperationsPage'
import { ReservationsPage } from './pages/ReservationsPage'
import { SessionsPage } from './pages/SessionsPage'

export function App() {
  const { enabled, loading, user, error } = useAuth()

  if (window.location.pathname === '/auth/callback') {
    return <Routes><Route path="auth/callback" element={<AuthCallbackPage />} /></Routes>
  }
  if (loading) {
    return <main className="auth-page"><p>Restoring operator session…</p></main>
  }
  if ((enabled && !user) || (!enabled && error)) {
    return <LoginPage />
  }

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<DashboardPage />} />
        <Route path="operations" element={<OperationsPage />} />
        <Route path="reservations" element={<ReservationsPage />} />
        <Route path="sessions" element={<SessionsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
