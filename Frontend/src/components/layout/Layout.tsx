// src/components/layout/Layout.tsx
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import type { AuthUser } from '../../hooks/useAuth'

const PAGE_TITLES: Record<string, { title: string; subtitle: string }> = {
  '/': { title: 'Increment Dashboard', subtitle: 'Filter and manage employee salary increment cycles' },
  '/employees': { title: 'Employee Search', subtitle: 'Search by EPF number to view and manage employee records' },
  '/employees/new': { title: 'Add Employee', subtitle: 'Create a new employee record and seed salary history' },
  '/salary-scales': { title: 'Salary Scale Versions', subtitle: 'Review effective-dated salary scales and add new versions without mutating history' },
  '/forms/ap': { title: 'AP Form Generator', subtitle: 'Generate and download AP forms in Sinhala' },
  '/forms/certificate': { title: 'Increment Certificates', subtitle: 'Generate and download increment certificates in English' },
}

interface Props {
  user: AuthUser
  onLogout: () => void
}

function resolvePage(pathname: string) {
  if (pathname === '/employees/new') {
    return PAGE_TITLES['/employees/new']
  }
  if (pathname.startsWith('/employees/') && pathname.endsWith('/edit')) {
    return { title: 'Edit Employee', subtitle: 'Update editable employee details' }
  }
  if (pathname.startsWith('/forms/ap/')) {
    return PAGE_TITLES['/forms/ap']
  }
  if (pathname.startsWith('/forms/certificate/')) {
    return PAGE_TITLES['/forms/certificate']
  }
  return PAGE_TITLES[pathname] || { title: 'EDL Salary Increment Management System', subtitle: '' }
}

export default function Layout({ user, onLogout }: Props) {
  const location = useLocation()
  const page = resolvePage(location.pathname)

  return (
    <>
      <Sidebar user={user} onLogout={onLogout} />
      <div className="page-wrapper">
        <Topbar title={page.title} subtitle={page.subtitle} user={user} />
        <main className="main-content animate-in">
          <Outlet />
        </main>
      </div>
    </>
  )
}
