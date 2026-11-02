// src/pages/APFormPage.tsx
import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { incrementCycleService } from '../services/incrementCycleService'
import { formService } from '../services/formService'
import type { IncrementCycle } from '../types'
import { Spinner, ErrorMsg, SuccessMsg, SectionHeader } from '../components/ui'
import {
  Download, Send, Pause, Ban, ChevronLeft,
  FileText, AlertTriangle, CheckCircle2, Clock,
  Pencil, Check, X,
} from 'lucide-react'

export default function APFormPage() {
  const { cycleId } = useParams<{ cycleId: string }>()
  const navigate = useNavigate()

  const [cycle, setCycle] = useState<IncrementCycle | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [generating, setGenerating] = useState(false)
  const [nextCycleLoading, setNextCycleLoading] = useState(false)

  const [overrides, setOverrides] = useState<Record<string, string>>({})
  const [editingField, setEditingField] = useState<string | null>(null)
  const [statusLoading, setStatusLoading] = useState(false)
  const [stopNotes, setStopNotes] = useState('')
  const [showStopDialog, setShowStopDialog] = useState(false)

  useEffect(() => {
    if (!cycleId) return
    incrementCycleService.getById(Number(cycleId))
      .then(c => { setCycle(c); setLoading(false) })
      .catch(e => { setError(e.message); setLoading(false) })
  }, [cycleId])

  const downloadApForm = async () => {
    if (!cycleId || !cycle) return
    setGenerating(true)
    setError(null)
    try {
      const blob = await formService.generateApFormDocx(Number(cycleId), overrides)
      formService.downloadBlob(blob, `AP_Form_${cycle.epfNo}_${cycle.cycleYear}.docx`)
      const updated = await incrementCycleService.getById(Number(cycleId))
      setCycle(updated)
      setSuccess('AP form downloaded successfully.')
    } catch (e: any) {
      setError(e.response?.data?.error || 'Document generation failed. Make sure templates are in place.')
    } finally {
      setGenerating(false)
    }
  }

  const downloadCertificate = async () => {
    if (!cycleId || !cycle) return
    setGenerating(true)
    setError(null)
    try {
      const blob = await formService.generateCertificateDocx(Number(cycleId))
      formService.downloadBlob(blob, `Certificate_${cycle.epfNo}_${cycle.cycleYear}.docx`)
      const updated = await incrementCycleService.getById(Number(cycleId))
      setCycle(updated)
      setSuccess('Certificate downloaded successfully.')
    } catch (e: any) {
      setError(e.response?.data?.error || 'Certificate generation failed.')
    } finally {
      setGenerating(false)
    }
  }

  const generateNextCycle = async () => {
    if (!cycleId) return
    setNextCycleLoading(true)
    setError(null)
    try {
      const newCycle = await incrementCycleService.nextCycle(Number(cycleId))
      setSuccess(`Next cycle (${newCycle.cycleYear}) created.`)
      navigate(`/forms/ap/${newCycle.id}`)
    } catch (e: any) {
      if (e.response?.status === 409) {
        setError(e.response?.data?.error || 'Could not generate next cycle — it may already exist.')
      } else {
        setError('Failed to generate next cycle.')
      }
    } finally {
      setNextCycleLoading(false)
    }
  }

  const changeStatus = async (newStatus: string, notes?: string) => {
    if (!cycleId) return
    setStatusLoading(true)
    try {
      const updated = await incrementCycleService.changeStatus(Number(cycleId), newStatus, 'hr.officer', notes)
      setCycle(updated)
      setSuccess(`Status updated to: ${newStatus.replace(/_/g, ' ')}`)
      setShowStopDialog(false)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setStatusLoading(false)
    }
  }

  const setOverride = (key: string, value: string) => {
    setOverrides(prev => ({ ...prev, [key]: value }))
  }

  if (loading) return <Spinner label="Loading increment cycle…" />
  if (!cycle) return <ErrorMsg message={error || 'Cycle not found'} />

  const emp = {
    name: cycle.employeeNameSinhala || cycle.employeeNameEnglish,
    epf: cycle.epfNo,
    desig: cycle.designationEnglish,
  }

  const scaleStr = cycle.salaryScale || `[Loaded from salary type ${cycle.salaryTypeCode}]`
  const currSalaryStr = `Rs. ${cycle.previousSalary?.toLocaleString()}/-  (Step ${cycle.previousStep})`
  const newSalaryStr = cycle.isSurplus && cycle.surplusLabel
    ? `Rs. ${cycle.newSalary?.toLocaleString()}/- (${cycle.surplusLabel})`
    : `Rs. ${cycle.newSalary?.toLocaleString()}/- (Step ${cycle.newStep})`

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <button className="btn btn-ghost btn-sm" style={{ marginBottom: 18, gap: 6 }} onClick={() => navigate(-1)}>
        <ChevronLeft size={15} /> Back to employee
      </button>

      {error && <ErrorMsg message={error} />}
      {success && <SuccessMsg message={success} />}

      <div className="card" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{ width: 44, height: 44, borderRadius: 11, background: '#EEF3FB', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <FileText size={20} strokeWidth={1.8} color="#1E3A6E" />
            </div>
            <div>
              <div style={{ fontSize: 16, fontWeight: 700 }}>AP Form — {cycle.cycleYear}</div>
              <div style={{ fontSize: 12.5, color: '#64748B', marginTop: 2 }}>
                EPF {cycle.epfNo} &nbsp;·&nbsp; {cycle.employeeNameEnglish} &nbsp;·&nbsp; {cycle.salaryTypeCode}
              </div>
            </div>
          </div>
        </div>

        <div style={{ marginTop: 18 }}>
          <WorkflowBar status={cycle.status} />
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 300px', gap: 16, alignItems: 'start' }}>
        <div className="card">
          <SectionHeader
            title="Form fields — auto-filled from employee record"
            action={<span style={{ fontSize: 11.5, color: '#94A3B8' }}>Click any field to edit before downloading</span>}
          />

          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <FormFieldSection title='"අ" කොටස — Employee details' />
            <EditableField label="Reference no." fieldKey="REF_NO" value={cycle.referenceNumber || ''} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="Period from" fieldKey="PERIOD_FROM" value={cycle.periodFrom || ''} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="Period to" fieldKey="PERIOD_TO" value={cycle.periodTo || ''} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="Employee name" fieldKey="EMP_NAME" value={emp.name} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} sinhala />
            <EditableField label="EPF No." fieldKey="EPF_NO" value={emp.epf} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="Designation" fieldKey="DESIGNATION" value={cycle.designationEnglish} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="Salary scale" fieldKey="SALARY_SCALE" value={scaleStr} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="Current salary" fieldKey="CURR_SALARY" value={currSalaryStr} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="New salary" fieldKey="NEXT_SALARY" value={newSalaryStr} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />

            <FormFieldSection title='"ආ" කොටස — Service details' />
            <EditableField label="Total service" fieldKey="SERVICE_TOTAL" value="" overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} placeholder="e.g. අවු 25" />
            <EditableField label="Post service" fieldKey="SERVICE_POST" value="" overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} placeholder="e.g. අවු 10" />
            <EditableField label="Grade service" fieldKey="SERVICE_GRADE" value="" overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} placeholder="e.g. අවු 5" />

            <FormFieldSection title='"ඇ" කොටස — Appraisal' />
            <EditableField label="Suspended" fieldKey="SUSPENDED" value={cycle.suspended ? 'ඇත' : 'නැත'} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="Praise" fieldKey="PRAISE" value={cycle.hasPraise ? 'ඇත' : 'නැත'} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="Evaluation score" fieldKey="PREV_SCORE" value={cycle.evaluationScore || ''} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} placeholder="e.g. 96.29%" />

            <FormFieldSection title="Officers" />
            <EditableField label="Clerk name" fieldKey="CLERK_NAME" value={cycle.clerkName || ''} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="HR officer" fieldKey="HR_OFFICER" value={cycle.hrOfficerName || ''} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
            <EditableField label="DGM / Head" fieldKey="OPS_HEAD" value={cycle.dgmName || ''} overrides={overrides} onSave={setOverride} editing={editingField} onEdit={setEditingField} />
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div className="card">
            <SectionHeader title="Generate form" />
            <button
              className="btn btn-primary"
              style={{ width: '100%', gap: 8, padding: '11px 16px', fontSize: 14, fontWeight: 600, marginBottom: 10 }}
              onClick={downloadApForm}
              disabled={generating}
            >
              {generating ? <><Spinner label="" /> Generating…</> : <><Download size={16} strokeWidth={2} /> Generate Appraisal Report</>}
            </button>
            <div style={{ fontSize: 11.5, color: '#94A3B8', lineHeight: 1.6 }}>
              Downloads the filled AP form as a Word document, ready to print and send.
            </div>
          </div>

          <div className="card">
            <SectionHeader title="Workflow actions" />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {(cycle.status === 'AP_DRAFT' || cycle.status === 'ELIGIBLE') && (
                <button className="btn btn-accent" style={{ width: '100%', gap: 7 }} onClick={() => changeStatus('AP_SENT', 'Sent to unit')} disabled={statusLoading}>
                  <Send size={14} strokeWidth={2.2} />
                  Send to unit + SMS
                </button>
              )}

              {(cycle.status === 'AP_PAUSED' || cycle.status === 'AP_STOPPED') && (
                <button className="btn btn-primary" style={{ width: '100%', gap: 7 }} onClick={() => changeStatus('AP_SENT', 'Resumed')} disabled={statusLoading}>
                  <CheckCircle2 size={14} strokeWidth={2.2} />
                  Resume — resend to unit
                </button>
              )}

              {cycle.status === 'AP_SENT' && (
                <button className="btn" style={{ width: '100%', gap: 7 }} onClick={() => changeStatus('AP_PAUSED', 'Paused pending review')} disabled={statusLoading}>
                  <Pause size={14} strokeWidth={2.2} />
                  Mark as paused
                </button>
              )}

              {(cycle.status === 'AP_SENT' || cycle.status === 'AP_PAUSED') && (
                <>
                  <button className="btn btn-danger" style={{ width: '100%', gap: 7 }} onClick={() => setShowStopDialog(true)}>
                    <Ban size={14} strokeWidth={2.2} />
                    Mark as stopped
                  </button>
                  {showStopDialog && (
                    <div style={{ background: '#FFF5F5', border: '1px solid #FCA5A5', borderRadius: 9, padding: 14 }}>
                      <div style={{ fontSize: 12.5, fontWeight: 600, marginBottom: 8, color: '#991B1B' }}>
                        Reason for stopping (required)
                      </div>
                      <textarea
                        className="input"
                        rows={3}
                        style={{ resize: 'vertical', fontSize: 12.5 }}
                        placeholder="Enter reason…"
                        value={stopNotes}
                        onChange={e => setStopNotes(e.target.value)}
                      />
                      <div style={{ display: 'flex', gap: 7, marginTop: 9 }}>
                        <button className="btn btn-danger btn-sm" style={{ flex: 1 }} onClick={() => changeStatus('AP_STOPPED', stopNotes)} disabled={!stopNotes.trim() || statusLoading}>
                          Confirm stop
                        </button>
                        <button className="btn btn-ghost btn-sm" onClick={() => setShowStopDialog(false)}>
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}

              {cycle.status === 'AP_SENT' && (
                <button className="btn" style={{ width: '100%', gap: 7, borderColor: '#86EFAC', color: '#166534', background: '#F0FDF4' }} onClick={() => changeStatus('CERTIFICATE_READY', 'Unit approved')} disabled={statusLoading}>
                  <CheckCircle2 size={14} strokeWidth={2.2} />
                  Unit approved — issue certificate
                </button>
              )}
            </div>
          </div>

          {cycle.status === 'CERTIFICATE_READY' && (
            <div className="card" style={{ borderColor: '#86EFAC' }}>
              <SectionHeader title="Increment certificate" />
              <button className="btn" style={{ width: '100%', gap: 8, borderColor: '#86EFAC', color: '#166534', background: '#F0FDF4' }} onClick={downloadCertificate} disabled={generating}>
                {generating ? <><Spinner label="" /> Generating…</> : <><Download size={16} strokeWidth={2} /> Download Certificate (Word)</>}
              </button>
            </div>
          )}

          {cycle.status === 'PAID' && (
            <div className="card" style={{ borderColor: '#93C5FD' }}>
              <SectionHeader title="Next year's cycle" />
              <button className="btn btn-primary" style={{ width: '100%', gap: 8 }} onClick={generateNextCycle} disabled={nextCycleLoading}>
                {nextCycleLoading ? <><Spinner label="" /> Generating…</> : <><CheckCircle2 size={16} strokeWidth={2} /> Generate Next Cycle ({cycle.cycleYear + 1})</>}
              </button>
              <div style={{ fontSize: 11.5, color: '#94A3B8', lineHeight: 1.6, marginTop: 8 }}>
                Creates {cycle.cycleYear + 1}'s increment cycle for this employee, ready for next year's AP form.
              </div>
            </div>
          )}

          {cycle.isSurplus && (
            <div style={{ background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 10, padding: '12px 14px' }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                <AlertTriangle size={15} strokeWidth={2} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
                <div>
                  <div style={{ fontSize: 12.5, fontWeight: 600, color: '#92400E', marginBottom: 3 }}>Surplus stage</div>
                  <div style={{ fontSize: 12, color: '#B45309', lineHeight: 1.5 }}>
                    This employee is past step 15. AP form is issued but salary changes only when the certificate is printed.
                    {cycle.surplusLabel && <><br />Label: <b>{cycle.surplusLabel}</b></>}
                  </div>
                </div>
              </div>
            </div>
          )}

          {cycle.apFormGeneratedAt && (
            <div style={{ background: '#F8FAFD', border: '1px solid #E8ECF2', borderRadius: 10, padding: '12px 14px' }}>
              <div style={{ fontSize: 11.5, fontWeight: 600, color: '#64748B', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.4px' }}>Activity</div>
              {cycle.apFormGeneratedAt && <TimeRow icon={Clock} label="Form generated" value={cycle.apFormGeneratedAt} />}
              {cycle.apFormSentAt && <TimeRow icon={Send} label="Sent to unit" value={cycle.apFormSentAt} />}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

function WorkflowBar({ status }: { status: string }) {
  const steps = ['ELIGIBLE', 'AP_DRAFT', 'AP_SENT', 'CERTIFICATE_READY', 'CERTIFICATE_ISSUED', 'PAID']
  const statusOrder: Record<string, number> = {
    ELIGIBLE: 0, AP_DRAFT: 1, AP_SENT: 2, AP_PAUSED: 2, AP_STOPPED: 2,
    CERTIFICATE_READY: 3, CERTIFICATE_ISSUED: 4, PAID: 5, REJECTED: 5,
  }
  const labels: Record<string, string> = {
    ELIGIBLE: 'Eligible', AP_DRAFT: 'AP ready', AP_SENT: 'Sent to unit',
    CERTIFICATE_READY: 'Cert. ready', CERTIFICATE_ISSUED: 'Cert. issued', PAID: 'Paid',
  }
  const current = statusOrder[status] ?? 0

  return (
    <div className="workflow">
      {steps.map((s, i) => (
        <div key={s} className={`wf-step ${i < current ? 'done' : i === current ? 'active' : ''}`}>
          {i < current && <Check size={11} strokeWidth={3} style={{ marginRight: 4, display: 'inline' }} />}
          {labels[s]}
        </div>
      ))}
    </div>
  )
}

function FormFieldSection({ title }: { title: string }) {
  return (
    <div style={{ padding: '10px 0 4px', fontSize: 12, fontWeight: 700, color: '#1E3A6E', borderBottom: '1.5px solid #EEF3FB', marginTop: 6 }}>
      {title}
    </div>
  )
}

function EditableField({ label, fieldKey, value, overrides, onSave, editing, onEdit, sinhala, placeholder }: {
  label: string; fieldKey: string; value: string
  overrides: Record<string, string>; onSave: (k: string, v: string) => void
  editing: string | null; onEdit: (k: string | null) => void
  sinhala?: boolean; placeholder?: string
}) {
  const displayValue = overrides[fieldKey] !== undefined ? overrides[fieldKey] : value
  const isEditing    = editing === fieldKey
  const isOverridden = overrides[fieldKey] !== undefined

  return (
    <div style={{ display: 'flex', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid #F8FAFC', gap: 12 }}>
      <div style={{ width: 130, fontSize: 12, color: '#94A3B8', flexShrink: 0 }}>{label}</div>

      {isEditing ? (
        <div style={{ flex: 1, display: 'flex', gap: 6 }}>
          <input
            autoFocus
            className={`input input-sm ${sinhala ? 'font-sinhala' : ''}`}
            defaultValue={displayValue}
            placeholder={placeholder}
            onKeyDown={e => {
              if (e.key === 'Enter') { onSave(fieldKey, (e.target as HTMLInputElement).value); onEdit(null) }
              if (e.key === 'Escape') onEdit(null)
            }}
            onBlur={e => { onSave(fieldKey, e.target.value); onEdit(null) }}
          />
          <button className="btn btn-ghost btn-xs" onClick={() => onEdit(null)}><X size={12} /></button>
        </div>
      ) : (
        <>
          <div style={{ flex: 1, fontSize: 13, fontWeight: 500, color: isOverridden ? '#1E3A6E' : '#374151', fontFamily: sinhala ? 'Noto Sans Sinhala, sans-serif' : 'inherit' }}>
            {displayValue || <span style={{ color: '#CBD5E1', fontStyle: 'italic' }}>{placeholder || 'Not set'}</span>}
            {isOverridden && <span style={{ marginLeft: 8, fontSize: 10.5, color: '#7C3AED', fontWeight: 600 }}>edited</span>}
          </div>
          <button className="btn btn-ghost btn-xs" onClick={() => onEdit(fieldKey)} style={{ opacity: 0.4, transition: 'opacity 0.15s' }}
                  onMouseEnter={e => (e.currentTarget.style.opacity = '1')}
                  onMouseLeave={e => (e.currentTarget.style.opacity = '0.4')}>
            <Pencil size={12} />
          </button>
        </>
      )}
    </div>
  )
}

function TimeRow({ icon: Icon, label, value }: { icon: any; label: string; value: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, fontSize: 12 }}>
      <Icon size={12} color="#94A3B8" />
      <span style={{ color: '#64748B' }}>{label}</span>
      <span style={{ color: '#94A3B8', marginLeft: 'auto' }}>{new Date(value).toLocaleDateString()}</span>
    </div>
  )
}