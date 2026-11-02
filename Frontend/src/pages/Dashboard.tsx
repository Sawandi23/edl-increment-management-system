// src/pages/Dashboard.tsx
import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { incrementCycleService } from '../services/incrementCycleService'
import { salaryTypeService } from '../services/salaryTypeService'
import type { IncrementCycle, SalaryType, DashboardStats } from '../types'
import { StatusBadge, Spinner, EmptyState, ErrorMsg, SkeletonRows, SectionHeader } from '../components/ui'
import {
  CalendarDays, FileClock, Send, CheckCircle2,
  Download, SlidersHorizontal, ChevronRight, RefreshCw,
  Inbox, ArrowUpRight, Clock,
} from 'lucide-react'

const STATUS_OPTIONS = [
  '', 'ELIGIBLE', 'AP_DRAFT', 'AP_SENT', 'AP_STOPPED',
  'AP_PAUSED', 'CERTIFICATE_READY', 'CERTIFICATE_ISSUED', 'PAID', 'REJECTED',
]
const STATUS_LABEL: Record<string, string> = {
  '': 'All statuses', ELIGIBLE: 'Eligible', AP_DRAFT: 'AP Draft',
  AP_SENT: 'Sent to unit', AP_STOPPED: 'Stopped', AP_PAUSED: 'Paused',
  CERTIFICATE_READY: 'Certificate ready', CERTIFICATE_ISSUED: 'Certificate issued',
  PAID: 'Paid', REJECTED: 'Rejected',
}

function isoDate(d: Date) { return d.toISOString().slice(0, 10) }
const today = new Date()

export default function Dashboard() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [fromDate, setFromDate] = useState(searchParams.get('from') || isoDate(new Date(today.getFullYear(), 0, 1)))
  const [toDate, setToDate]     = useState(searchParams.get('to') || isoDate(new Date(today.getFullYear(), 11, 31)))
  const [status, setStatus]     = useState(searchParams.get('status') || '')
  const [typeCode, setTypeCode] = useState(searchParams.get('type') || '')

  const [salaryTypes, setSalaryTypes] = useState<SalaryType[]>([])
  const [cycles, setCycles]     = useState<IncrementCycle[]>([])
  const [stats, setStats]       = useState<DashboardStats | null>(null)
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState<string | null>(null)

  useEffect(() => { salaryTypeService.getAll().then(setSalaryTypes).catch(() => {}) }, [])
  useEffect(() => { search() }, []) // eslint-disable-line

  const search = async () => {
    setLoading(true); setError(null)
    try {
      setSearchParams(
        {
          from: fromDate,
          to: toDate,
          ...(status ? { status } : {}),
          ...(typeCode ? { type: typeCode } : {}),
        },
        { replace: true },
      )
      const [c, s] = await Promise.all([
        incrementCycleService.filterDashboard({ fromDate, toDate, status: status || undefined, salaryTypeCode: typeCode || undefined }),
        incrementCycleService.getStats(fromDate, toDate),
      ])
      setCycles(c); setStats(s)
    } catch (e: any) { setError(e.message) }
    finally { setLoading(false) }
  }

  const exportCsv = () => {
    const headers = ['EPF No', 'Name', 'Increment Date', 'Salary Type', 'Step', 'New Salary (Rs)', 'Unit', 'Status']
    const rows = cycles.map(c => [c.epfNo, c.employeeNameEnglish, c.incrementDate, c.salaryTypeCode, c.newStep, c.newSalary, c.unit, c.status])
    const csv = [headers, ...rows].map(r => r.join(',')).join('\n')
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
    a.download = `increment_list_${fromDate}_${toDate}.csv`
    a.click()
  }

  return (
    <div>
      {/* ── Stat cards ── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 24 }}>
        {stats ? <>
          <StatCard icon={CalendarDays} label="Due this period" value={stats.dueThisPeriod} accent="#1E3A6E" trend="+0 from last month" />
          <StatCard icon={FileClock}    label="AP forms pending" value={stats.apFormsPending} accent="#92400E" trend="Needs action" />
          <StatCard icon={Send}         label="Sent to unit"    value={stats.sentToUnit}    accent="#0369A1" trend="Awaiting response" />
          <StatCard icon={CheckCircle2} label="Paid this year"  value={stats.paidThisYear}  accent="#166534" trend="Completed" />
        </> : <>
          {[0,1,2,3].map(i => <div key={i} className="stat-card"><div className="skeleton" style={{ height: 80 }} /></div>)}
        </>}
      </div>

      {/* ── Filter + table ── */}
      <div className="card">
        <SectionHeader
          title="Increment cycles"
          action={
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-sm" onClick={search} style={{ gap: 6 }}>
                <RefreshCw size={17} strokeWidth={2.2} /> Refresh
              </button>
              <button className="btn btn-sm" onClick={exportCsv} disabled={cycles.length === 0} style={{ gap: 6 }}>
                <Download size={13} strokeWidth={2.2} /> Export CSV
              </button>
            </div>
          }
        />

        {/* Filters */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'flex-end', marginBottom: 20, padding: '16px 18px', background: '#F8FAFD', borderRadius: 10, border: '1px solid #EEF1F8' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginRight: 4 }}>
            <SlidersHorizontal size={14} strokeWidth={2.2} color="#64748B" />
            <span style={{ fontSize: 12.5, fontWeight: 600, color: '#11075a' }}>Filters</span>
          </div>
          <FilterGroup label="From">
            <input type="date" className="input input-sm" style={{ width: 145 }} value={fromDate} onChange={e => setFromDate(e.target.value)} />
          </FilterGroup>
          <FilterGroup label="To">
            <input type="date" className="input input-sm" style={{ width: 145 }} value={toDate} onChange={e => setToDate(e.target.value)} />
          </FilterGroup>
          <FilterGroup label="Status">
            <select className="input input-sm select" style={{ width: 160 }} value={status} onChange={e => setStatus(e.target.value)}>
              {STATUS_OPTIONS.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
            </select>
          </FilterGroup>
          <FilterGroup label="Salary type">
            <select className="input input-sm select" style={{ width: 130 }} value={typeCode} onChange={e => setTypeCode(e.target.value)}>
              <option value="">All types</option>
              {salaryTypes.map(t => <option key={t.code} value={t.code}>{t.code}</option>)}
            </select>
          </FilterGroup>
          <button className="btn btn-primary btn-sm" onClick={search} style={{ marginLeft: 'auto', paddingLeft: 18, paddingRight: 18 }}>
            Search
          </button>
        </div>

        {error && <ErrorMsg message={error} />}

        {/* Table */}
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>EPF No.</th>
                <th>Employee name</th>
                <th>Increment date</th>
                <th>Salary type</th>
                <th style={{ textAlign: 'center' }}>Step</th>
                <th>New salary</th>
                <th>Unit</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <SkeletonRows cols={9} rows={7} />
              ) : cycles.length === 0 ? (
                <tr>
                  <td colSpan={9}>
                    <EmptyState icon={Inbox} title="No increment cycles found" subtitle="Adjust the date range or clear filters to see results." />
                  </td>
                </tr>
              ) : cycles.map(c => (
                <tr key={c.id} onClick={() => navigate(`/employees?epf=${c.epfNo}`)}>
                  <td>
                    <span style={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 13, color: '#1E3A6E' }}>{c.epfNo}</span>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: '#0F172A' }}>{c.employeeNameEnglish}</div>
                    <div style={{ fontSize: 12, color: '#4675b7', marginTop: 1 }}>{c.designationEnglish}</div>
                  </td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#475569', fontSize: 13 }}>
                      <Clock size={12} color="#94A3B8" />
                      {c.incrementDate}
                    </div>
                  </td>
                  <td>
                    <span style={{ fontFamily: 'monospace', fontSize: 12, fontWeight: 700, padding: '3px 8px', borderRadius: 6, background: '#EEF3FB', color: '#1E3A6E' }}>
                      {c.salaryTypeCode}
                    </span>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <span style={{ fontWeight: 600, color: '#374151' }}>{c.newStep}</span>
                  </td>
                  <td>
                    <span style={{ fontWeight: 700, color: '#0F172A' }}>Rs. {c.newSalary.toLocaleString()}/-</span>
                  </td>
                  <td style={{ color: '#64748B', fontSize: 12.5 }}>{c.unit}</td>
                  <td><StatusBadge status={c.status} /></td>
                  <td>
                    <ChevronRight size={15} color="#CBD5E1" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {cycles.length > 0 && (
          <div style={{ padding: '12px 4px 0', borderTop: '1px solid #F1F5F9', marginTop: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 12.5, color: '#94A3B8' }}>Showing {cycles.length} record{cycles.length !== 1 ? 's' : ''}</span>
          </div>
        )}
      </div>
    </div>
  )
}

function StatCard({ icon: Icon, label, value, accent, trend }: { icon: any; label: string; value: number; accent: string; trend: string }) {
  return (
    <div className="stat-card">
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 16 }}>
        <div style={{ width: 40, height: 40, borderRadius: 10, background: `${accent}14`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Icon size={18} strokeWidth={2} color={accent} />
        </div>
        <ArrowUpRight size={14} color="#CBD5E1" />
      </div>
      <div style={{ fontSize: 30, fontWeight: 800, color: '#0F172A', lineHeight: 1, marginBottom: 6, letterSpacing: '-1px' }}>{value}</div>
      <div style={{ fontSize: 12.5, fontWeight: 600, color: '#374151', marginBottom: 3 }}>{label}</div>
      <div style={{ fontSize: 11.5, color: '#94A3B8' }}>{trend}</div>
      <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 3, background: accent, opacity: 0.6, borderRadius: '0 0 12px 12px' }} />
    </div>
  )
}

function FilterGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      <span style={{ fontSize: '11px', fontWeight: 600, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.5px' }}>{label}</span>
      {children}
    </div>
  )
}
