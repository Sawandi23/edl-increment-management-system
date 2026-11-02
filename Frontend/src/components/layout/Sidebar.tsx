// src/components/layout/Sidebar.tsx
import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard, Users, TrendingUp,
  LogOut, ChevronRight, Settings,
} from 'lucide-react'
import type { AuthUser } from '../../hooks/useAuth'

const MAIN_NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/employees', label: 'Employee Search', icon: Users },
]
const REF_NAV = [
  { to: '/salary-scales', label: 'Salary Scales', icon: TrendingUp },
]

interface Props {
  user: AuthUser
  onLogout: () => void
}

export default function Sidebar({ user, onLogout }: Props) {
  return (
    <aside className="sidebar">
      {/* Logo */}
      <div className="sidebar-logo">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <img
            src="/edl-logo.png"
            alt="EDL"
            style={{ width: 70, height: 70, borderRadius: 10, flexShrink: 0, objectFit: 'contain', background: '#fff', padding: 4 }}
          />
          <div>
            <div style={{ fontSize: 30, fontWeight: 700, color: '#fff', lineHeight: 1.2 }}> SIMS</div>
            <div style={{ fontSize: 15, color: 'rgba(255,255,255,0.55)', marginTop: 2 }}>HR - Colombo City</div>
          </div>
        </div>
      </div>

      {/* Nav */}
      <div style={{ flex: 1, overflowY: 'auto', paddingBottom: 16 }}>
        <NavSection title="Main" items={MAIN_NAV} />
        <NavSection title="Reference" items={REF_NAV} />
      </div>

      {/* Bottom user card */}
      <div style={{
        padding: '12px 10px',
        borderTop: '1px solid rgba(255,255,255,0.07)',
        marginTop: 'auto',
      }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 10,
          padding: '10px 12px', borderRadius: 9,
          background: 'rgba(255,255,255,0.05)',
        }}>
          <div className="avatar" style={{ width: 34, height: 34, fontSize: 12, flexShrink: 0 }}>
            {user.displayName.split(' ').slice(0, 2).map(w => w[0]).join('')}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 12.5, fontWeight: 600, color: '#fff', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {user.displayName}
            </div>
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.4)', marginTop: 1 }}>
              {user.role.replace('_', ' ')}
            </div>
          </div>
          <button
            onClick={onLogout}
            title="Sign out"
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'rgba(255,255,255,0.35)', padding: 4, borderRadius: 6, transition: 'color 0.15s' }}
            onMouseEnter={e => (e.currentTarget.style.color = 'rgba(255,255,255,0.8)')}
            onMouseLeave={e => (e.currentTarget.style.color = 'rgba(255,255,255,0.35)')}
          >
            <LogOut size={15} />
          </button>
        </div>
      </div>
    </aside>
  )
}

function NavSection({ title, items }: { title: string; items: { to: string; label: string; icon: any; end?: boolean }[] }) {
  return (
    <div>
      <div className="sidebar-section-title">{title}</div>
      {items.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) => `sidebar-item${isActive ? ' active' : ''}`}
        >
          {({ isActive }) => (
            <>
              <Icon size={16} strokeWidth={2} className="sidebar-icon" style={isActive ? { color: 'var(--accent)' } : {}} />
              <span style={{ flex: 1 }}>{label}</span>
              {isActive && <ChevronRight size={13} strokeWidth={2.5} style={{ color: 'rgba(255,255,255,0.3)' }} />}
            </>
          )}
        </NavLink>
      ))}
    </div>
  )
}
