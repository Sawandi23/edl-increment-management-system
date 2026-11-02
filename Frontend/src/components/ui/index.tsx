// src/components/ui/index.tsx
import { Loader2, AlertCircle, CheckCircle2, Inbox } from 'lucide-react'
import type { IncrementStatus } from '../../types'
import { STATUS_LABELS } from '../../types'

// ── Status badge ──────────────────────────────────────────────────────────────
const STATUS_CLASS: Record<IncrementStatus, string> = {
  ELIGIBLE:           'b-eligible',
  AP_DRAFT:           'b-ap-draft',
  AP_SENT:            'b-ap-sent',
  AP_STOPPED:         'b-stopped',
  AP_PAUSED:          'b-paused',
  CERTIFICATE_READY:  'b-cert-ready',
  CERTIFICATE_ISSUED: 'b-cert-issued',
  PAID:               'b-paid',
  REJECTED:           'b-rejected',
}

export function StatusBadge({ status }: { status: IncrementStatus }) {
  return <span className={`badge ${STATUS_CLASS[status]}`}>{STATUS_LABELS[status]}</span>
}

// ── Loading ───────────────────────────────────────────────────────────────────
export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, padding: '48px 0', color: '#94A3B8', fontSize: 13.5 }}>
      <Loader2 size={17} style={{ animation: 'spin 0.8s linear infinite', color: '#3563A8' }} />
      {label}
    </div>
  )
}

// ── Empty state ───────────────────────────────────────────────────────────────
export function EmptyState({ icon: Icon = Inbox, title, subtitle }: { icon?: any; title: string; subtitle?: string }) {
  return (
    <div style={{ textAlign: 'center', padding: '56px 0' }}>
      <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 14px' }}>
        <Icon size={22} strokeWidth={1.7} color="#94A3B8" />
      </div>
      <div style={{ fontSize: 14, fontWeight: 600, color: '#475569', marginBottom: 5 }}>{title}</div>
      {subtitle && <div style={{ fontSize: 13, color: '#94A3B8' }}>{subtitle}</div>}
    </div>
  )
}

// ── Error ─────────────────────────────────────────────────────────────────────
export function ErrorMsg({ message }: { message: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, background: 'var(--danger-bg)', color: 'var(--danger)', border: '1px solid #FCA5A5', borderRadius: 9, padding: '11px 15px', fontSize: 13.5, fontWeight: 500, marginBottom: 14 }}>
      <AlertCircle size={16} strokeWidth={2.2} style={{ flexShrink: 0 }} />
      {message}
    </div>
  )
}

// ── Success toast ─────────────────────────────────────────────────────────────
export function SuccessMsg({ message }: { message: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, background: 'var(--success-bg)', color: 'var(--success)', border: '1px solid #86EFAC', borderRadius: 9, padding: '11px 15px', fontSize: 13.5, fontWeight: 500, marginBottom: 14 }}>
      <CheckCircle2 size={16} strokeWidth={2.2} style={{ flexShrink: 0 }} />
      {message}
    </div>
  )
}

// ── Skeleton rows ─────────────────────────────────────────────────────────────
export function SkeletonRows({ cols = 7, rows = 6 }: { cols?: number; rows?: number }) {
  return (
    <>
      {Array.from({ length: rows }).map((_, i) => (
        <tr key={i}>
          {Array.from({ length: cols }).map((_, j) => (
            <td key={j} style={{ padding: '12px 14px' }}>
              <div className="skeleton" style={{ height: 14, width: j === 0 ? 60 : j === 1 ? 160 : 80 }} />
            </td>
          ))}
        </tr>
      ))}
    </>
  )
}

// ── Section header ────────────────────────────────────────────────────────────
export function SectionHeader({ title, action }: { title: string; action?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, paddingBottom: 12, borderBottom: '1px solid #F1F5F9' }}>
      <div style={{ fontSize: 13.5, fontWeight: 700, color: '#374151' }}>{title}</div>
      {action}
    </div>
  )
}
