// src/components/layout/Topbar.tsx
import { Bell, Search, HelpCircle } from 'lucide-react'
import type { AuthUser } from '../../hooks/useAuth'

interface Props {
  title: string
  subtitle?: string
  user: AuthUser
  actions?: React.ReactNode
}

export default function Topbar({ title, subtitle, user, actions }: Props) {
  return (
    <div className="topbar">
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', lineHeight: 1.2 }}>{title}</div>
        {subtitle && <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 1 }}>{subtitle}</div>}
      </div>

      {actions && <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>{actions}</div>}

      <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginLeft: 12 }}>
        <button className="btn btn-ghost btn-icon" title="Help">
          <HelpCircle size={17} strokeWidth={1.8} />
        </button>
        <button className="btn btn-ghost btn-icon" title="Notifications">
          <Bell size={17} strokeWidth={1.8} />
        </button>
        <div style={{ width: 1, height: 20, background: '#E8ECF2', margin: '0 6px' }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
          <div className="avatar" style={{ width: 32, height: 32, fontSize: 11 }}>
            {user.displayName.split(' ').slice(0, 2).map(w => w[0]).join('')}
          </div>
          <div>
            <div style={{ fontSize: 12.5, fontWeight: 600, color: '#0F172A', lineHeight: 1.2 }}>{user.displayName}</div>
            <div style={{ fontSize: 11, color: '#94A3B8' }}>{user.role.replace('_', ' ')}</div>
          </div>
        </div>
      </div>
    </div>
  )
}
