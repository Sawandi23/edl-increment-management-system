// src/pages/EmployeeProfile.tsx
import { useState, useEffect } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { employeeService } from '../services/employeeService'
import { incrementCycleService } from '../services/incrementCycleService'
import type { Employee, EmployeeSalaryHistory, IncrementCycle } from '../types'
import { StatusBadge, Spinner, EmptyState, ErrorMsg, SectionHeader } from '../components/ui'
import {
  Search, FileText, Pencil, AlertTriangle, History,
  Calendar, Briefcase, Phone, Hash, Layers, Banknote, Clock3,
} from 'lucide-react'

export default function EmployeeProfile() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const initialEpf = searchParams.get('epf') || ''

  const [epfInput, setEpfInput] = useState(initialEpf)
  const [emp, setEmp] = useState<Employee | null>(null)
  const [history, setHistory] = useState<EmployeeSalaryHistory[]>([])
  const [cycles, setCycles] = useState<IncrementCycle[]>([])
  const [loading, setLoading] = useState(false)
  const [openingAp, setOpeningAp] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = async (epf: string) => {
    if (!epf.trim()) return
    setLoading(true)
    setError(null)
    setEmp(null)
    try {
      const e = await employeeService.searchByEpf(epf.trim())
      setEmp(e)
      setSearchParams({ epf: epf.trim() })
      const [h, c] = await Promise.all([
        employeeService.getSalaryHistory(e.id),
        incrementCycleService.getByEmployee(e.id),
      ])
      setHistory(h)
      setCycles(c)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { if (initialEpf) load(initialEpf) }, []) // eslint-disable-line

  const hasPromotion = history.some(h => h.hasPromotion)

  const openApForm = async () => {
    if (!emp) return
    setOpeningAp(true)
    setError(null)
    try {
      const cycle = cycles[0] ?? await incrementCycleService.createInitialCycle(emp.id)
      if (cycles.length === 0) {
        setCycles([cycle])
      }
      navigate(`/forms/ap/${cycle.id}`)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setOpeningAp(false)
    }
  }

  return (
    <div>
      <div style={{ background: '#fff', border: '1px solid #E8ECF2', borderRadius: 12, padding: '20px 22px', marginBottom: 20 }}>
        <div style={{ fontSize: 15, fontWeight: 600, color: '#374151', marginBottom: 12 }}>Search employee by EPF number</div>
        <div style={{ display: 'flex', gap: 10, maxWidth: 720, flexWrap: 'wrap', alignItems: 'center' }}>
          <div className="input-wrap" style={{ flex: 1 }}>
            <Search size={15} className="input-icon-l" />
            <input
              className="input has-icon-l"
              placeholder="e.g. 26856"
              value={epfInput}
              onChange={e => setEpfInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && load(epfInput)}
            />
          </div>
          <button className="btn btn-primary" onClick={() => load(epfInput)} style={{ paddingLeft: 20, paddingRight: 20 }}>
            Load profile
          </button>
          <button className="btn btn-primary" onClick={() => navigate('/employees/new')} style={{ paddingLeft: 20, paddingRight: 20 }}>
            Add employee
          </button>
        </div>
      </div>

      {loading && <Spinner label="Loading employee record…" />}
      {error && <ErrorMsg message={error} />}

      {!loading && !error && !emp && (
        <div style={{ background: '#fff', border: '1px solid #E8ECF2', borderRadius: 12, padding: 24 }}>
          <EmptyState icon={Search} title="No employee loaded" subtitle="Enter an EPF number above and press Load profile." />
        </div>
      )}

      {emp && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.6fr', gap: 18, alignItems: 'start' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className="card">
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', paddingBottom: 18, borderBottom: '1px solid #F1F5F9', marginBottom: 18 }}>
                <div className="avatar" style={{ width: 64, height: 64, fontSize: 20, marginBottom: 14 }}>
                  {initials(emp.nameEnglish)}
                </div>
                <div style={{ fontSize: 17, fontWeight: 800, color: '#0F172A', marginBottom: 4 }}>{emp.nameEnglish}</div>
                <div style={{ fontSize: 12.5, color: '#64748B', marginBottom: 12 }}>{emp.designationEnglish}</div>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 11.5, fontWeight: 700, padding: '4px 10px', borderRadius: 99, background: '#EEF3FB', color: '#1E3A6E', fontFamily: 'monospace' }}>
                    EPF {emp.epfNo}
                  </span>
                  <span className={`badge ${emp.isActive ? 'b-eligible' : 'b-stopped'}`}>
                    {emp.isActive ? 'Active' : 'Inactive'}
                  </span>
                </div>
              </div>

              {hasPromotion && (
                <div style={{ display: 'flex', gap: 10, padding: '10px 14px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 9, marginBottom: 16, fontSize: 12.5, color: '#92400E', fontWeight: 500 }}>
                  <AlertTriangle size={15} strokeWidth={2.2} style={{ flexShrink: 0, marginTop: 1 }} />
                  Mid-career salary type change on record
                </div>
              )}

              <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                <InfoRow icon={Hash} label="EPF No." value={emp.epfNo} />
                <InfoRow icon={Calendar} label="Increment date" value={emp.incrementDate} />
                <InfoRow icon={Calendar} label="Permanent date" value={emp.permanentDate || '—'} />
                <InfoRow icon={Clock3} label="Total service" value={emp.totalServiceYears != null ? `${emp.totalServiceYears} years` : '—'} />
                <InfoRow icon={Phone} label="Phone" value={emp.phoneNumber || '—'} />
                <InfoRow icon={Briefcase} label="Unit" value={emp.unit || '—'} />
              </div>
            </div>

            <div className="card">
              <SectionHeader title="Current salary" />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                <InfoRow icon={Layers} label="Salary type" value={emp.currentSalaryTypeCode || '—'} />
                <InfoRow icon={Layers} label="Salary scale" value={emp.currentSalaryScale || '—'} />
                <InfoRow icon={Hash} label="Step" value={emp.currentStep != null ? String(emp.currentStep) : '—'} />
                <InfoRow icon={Banknote} label="Salary" value={emp.currentSalary != null ? `Rs. ${emp.currentSalary.toLocaleString()}/-` : '—'} />
              </div>
            </div>

            <div className="card" style={{ display: 'flex', gap: 9, flexWrap: 'wrap' }}>
              <button className="btn btn-primary btn-sm" style={{ gap: 6 }} onClick={openApForm} disabled={openingAp}>
                <FileText size={20} strokeWidth={2.2} /> {openingAp ? 'Opening…' : 'Generate Appraisal form'}
              </button>
              <button className="btn btn-sm" style={{ gap: 6 }} onClick={() => emp && navigate(`/employees/${emp.id}/edit`)}>
                <Pencil size={20} strokeWidth={2.2} /> Edit details
              </button>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className="card">
              <SectionHeader
                title="Increment history"
                action={<span style={{ fontSize: 15, color: '#94A3B8' }}>{cycles.length} record{cycles.length !== 1 ? 's' : ''}</span>}
              />
              {cycles.length === 0 ? (
                <EmptyState icon={History} title="No increment cycles yet" subtitle="Cycles will appear here once the employee data is imported." />
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Year</th>
                      <th>Period</th>
                      <th>Step</th>
                      <th>Previous salary</th>
                      <th>New salary</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {cycles.map(c => (
                      <tr key={c.id}>
                        <td><span style={{ fontWeight: 700, color: '#1E3A6E' }}>{c.cycleYear}</span></td>
                        <td style={{ fontSize: 12.5, color: '#64748B' }}>{c.periodFrom || c.incrementDate} → {c.periodTo || '—'}</td>
                        <td style={{ textAlign: 'center' }}>
                          <span style={{ fontWeight: 600 }}>{c.previousStep}</span>
                          <span style={{ color: '#CBD5E1', margin: '0 5px' }}>→</span>
                          <span style={{ fontWeight: 600 }}>{c.newStep}</span>
                          {c.isSurplus && <span style={{ marginLeft: 6, fontSize: 10.5, color: '#7C3AED', fontWeight: 600 }}>{c.surplusLabel}</span>}
                        </td>
                        <td style={{ color: '#64748B' }}>Rs. {c.previousSalary.toLocaleString()}</td>
                        <td style={{ fontWeight: 600, color: '#0F172A' }}>Rs. {c.newSalary.toLocaleString()}</td>
                        <td><StatusBadge status={c.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {history.length > 0 && (
              <div className="card">
                <SectionHeader title="Salary type history" />
                {history.map(h => (
                  <div key={h.id} style={{ display: 'flex', alignItems: 'flex-start', gap: 14, padding: '12px 0', borderBottom: '1px solid #F1F5F9' }}>
                    <div style={{ width: 10, height: 10, borderRadius: '50%', background: h.hasPromotion ? 'var(--accent)' : '#CBD5E1', marginTop: 4, flexShrink: 0 }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 13, color: '#1E3A6E', background: '#EEF3FB', padding: '2px 7px', borderRadius: 5 }}>{h.salaryTypeCode}</span>
                        {h.hasPromotion && <span style={{ fontSize: 11, fontWeight: 600, color: '#92400E', background: '#FEF3C7', padding: '2px 7px', borderRadius: 5 }}>Promotion</span>}
                      </div>
                      <div style={{ fontSize: 12, color: '#64748B', marginTop: 4 }}>
                        {h.effectiveFrom} → {h.effectiveTo || 'Present'} &nbsp;·&nbsp; Step {h.currentStep} &nbsp;·&nbsp; Rs. {h.currentSalary.toLocaleString()}
                      </div>
                      {h.promotionRemark && (
                        <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 3, fontStyle: 'italic' }}>&quot;{h.promotionRemark}&quot;</div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function InfoRow({ icon: Icon, label, value }: { icon: any; label: string; value: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', padding: '9px 0', borderBottom: '1px solid #F8FAFC' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: 130, flexShrink: 0 }}>
        <Icon size={13} strokeWidth={2} color="#94A3B8" />
        <span style={{ fontSize: 12, color: '#94A3B8', fontWeight: 500 }}>{label}</span>
      </div>
      <span style={{ fontSize: 13, fontWeight: 600, color: '#374151' }}>{value}</span>
    </div>
  )
}

function initials(name: string) {
  return name.split(' ').filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase()
}
