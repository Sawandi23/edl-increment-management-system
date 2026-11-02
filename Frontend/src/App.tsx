// src/App.tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import Login from './pages/Login'
import Layout from './components/layout/Layout'
import Dashboard from './pages/Dashboard'
import EmployeeProfile from './pages/EmployeeProfile'
import EmployeeFormPage from './pages/EmployeeFormPage'
import SalaryScales from './pages/SalaryScales'
import APFormPage from './pages/APFormPage'

export default function App() {
  const { user, isAuthenticated, login, signup, logout } = useAuth()

  if (!isAuthenticated) {
    return <Login onLogin={login} onSignup={signup} />
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout user={user!} onLogout={logout} />}>
          <Route index element={<Dashboard />} />
          <Route path="employees/new" element={<EmployeeFormPage />} />
          <Route path="employees/:employeeId/edit" element={<EmployeeFormPage />} />
          <Route path="employees" element={<EmployeeProfile />} />
          <Route path="salary-scales" element={<SalaryScales />} />
          <Route path="forms/ap/:cycleId" element={<APFormPage />} />
          
          <Route path="forms/certificate" element={<div style={{ padding: 24 }}><ComingSoon title="Increment Certificates" /></div>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

function ComingSoon({ title }: { title: string }) {
  return (
    <div style={{ background: '#fff', border: '1px solid #E8ECF2', borderRadius: 12, padding: '60px 40px', textAlign: 'center' }}>
      <div style={{ width: 56, height: 56, borderRadius: 14, background: '#EEF3FB', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px', fontSize: 24 }}>📋</div>
      <div style={{ fontSize: 18, fontWeight: 700, color: '#0F172A', marginBottom: 8 }}>{title}</div>
      <div style={{ fontSize: 13.5, color: '#94A3B8', maxWidth: 340, margin: '0 auto' }}>
        This module is being built. The AP form generation and PDF download functionality will be available in the next phase.
      </div>
    </div>
  )
}
