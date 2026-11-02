// src/pages/SalaryScales.tsx
import { useEffect, useMemo, useState, Fragment } from 'react'
import { salaryTypeService } from '../services/salaryTypeService'
import { salaryScaleVersionService } from '../services/salaryScaleVersionService'
import type { SalaryScaleVersion, SalaryType } from '../types'
import { Spinner, ErrorMsg } from '../components/ui'
import { ChevronDown, ChevronUp, Plus, TrendingUp } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'

type DraftState = {
  scaleString: string
  effectiveFrom: string
}

export default function SalaryScales() {
  const [types, setTypes] = useState<SalaryType[]>([])
  const [versions, setVersions] = useState<SalaryScaleVersion[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [drafts, setDrafts] = useState<Record<string, DraftState>>({})
  const [savingCode, setSavingCode] = useState<string | null>(null)
  // Per-row save error, so a failed submit stays visible right next to the
  // form that caused it instead of only flashing a banner at the top of the
  // page (which is easy to miss once you're scrolled into an expanded row).
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({})
  const { user } = useAuth()

  useEffect(() => {
    Promise.all([
      salaryTypeService.getAll(),
      salaryScaleVersionService.getAll(),
    ])
      .then(([salaryTypes, salaryVersions]) => {
        setTypes(salaryTypes)
        setVersions(salaryVersions)
      })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const versionsByCode = useMemo(() => {
    return versions.reduce<Record<string, SalaryScaleVersion[]>>((acc, version) => {
      if (!acc[version.salaryTypeCode]) {
        acc[version.salaryTypeCode] = []
      }
      acc[version.salaryTypeCode].push(version)
      return acc
    }, {})
  }, [versions])

  const currentUser = user?.username ?? 'system'

  function openType(code: string, seedScale: string) {
    setExpanded(prev => prev === code ? null : code)
    setDrafts(prev => prev[code] ? prev : {
      ...prev,
      [code]: {
        scaleString: seedScale,
        effectiveFrom: '',
      },
    })
  }

  function updateDraft(code: string, patch: Partial<DraftState>) {
    setDrafts(prev => ({
      ...prev,
      [code]: {
        ...prev[code],
        ...patch,
      },
    }))
  }

  async function submitVersion(code: string) {
    const draft = drafts[code]
    if (!draft?.scaleString?.trim() || !draft?.effectiveFrom) {
      setRowErrors(prev => ({ ...prev, [code]: 'Scale string and effective-from date are required.' }))
      return
    }

    try {
      setSavingCode(code)
      setRowErrors(prev => ({ ...prev, [code]: '' }))
      await salaryScaleVersionService.create({
        salaryTypeCode: code,
        scaleString: draft.scaleString.trim(),
        effectiveFrom: draft.effectiveFrom,
        createdBy: currentUser,
      })
      // Only re-fetch versions on success — the expanded row, drafts for
      // OTHER codes, and the rest of the page stay exactly as they were.
      const refreshed = await salaryScaleVersionService.getAll()
      setVersions(refreshed)
      setDrafts(prev => ({
        ...prev,
        [code]: {
          scaleString: draft.scaleString.trim(),
          effectiveFrom: '',
        },
      }))
    } catch (e: any) {
      // Stay expanded, keep whatever the user typed, just show the error
      // right next to the form that failed.
      setRowErrors(prev => ({ ...prev, [code]: e?.response?.data?.error ?? e.message }))
    } finally {
      setSavingCode(null)
    }
  }

  if (loading) {
    return <Spinner label="Loading salary scales…" />
  }

  return (
    <div>
      {error && <ErrorMsg message={error} />}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 20 }}>
        <SummaryCard label="Total salary types" value={types.length} />
        <SummaryCard label="Version records" value={versions.length} />
        <SummaryCard
          label="Current effective scales"
          value={Object.values(versionsByCode).filter(list => list.some(v => v.effectiveTo === null)).length}
        />
      </div>

      <div className="card" style={{ padding: 0 }}>
        <div style={{ padding: '16px 22px', borderBottom: '1px solid #F1F5F9' }}>
          <div style={{ fontSize: 13.5, fontWeight: 700, color: '#374151' }}>Salary scales and effective-dated versions</div>
          <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 3 }}>Expand a code to review its current version, history, and add a new version</div>
        </div>
        <table className="data-table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Description</th>
              <th>Start salary</th>
              <th>Band 1 increment</th>
              <th>Band 2 increment</th>
              <th>Max salary</th>
              <th>Scale string</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {types.map(t => (
              <Fragment key={t.code}>
                <tr onClick={() => openType(t.code, t.scaleString)} style={{ cursor: 'pointer' }}>
                  <td>
                    <span style={{ fontFamily: 'monospace', fontWeight: 800, fontSize: 13, padding: '3px 9px', borderRadius: 6, background: '#EEF3FB', color: '#1E3A6E' }}>
                      {t.code}
                    </span>
                  </td>
                  <td style={{ color: '#374151', fontWeight: 500 }}>{t.description}</td>
                  <td style={{ fontWeight: 600 }}>Rs. {t.startSalary.toLocaleString()}</td>
                  <td style={{ color: '#166534', fontWeight: 600 }}>+{t.band1Increment.toLocaleString()} <span style={{ color: '#94A3B8', fontWeight: 400, fontSize: 11 }}>× {t.band1Years} yrs</span></td>
                  <td style={{ color: '#166534', fontWeight: 600 }}>+{t.band2Increment.toLocaleString()} <span style={{ color: '#94A3B8', fontWeight: 400, fontSize: 11 }}>× {t.band2Years} yrs</span></td>
                  <td style={{ fontWeight: 700, color: '#0F172A' }}>Rs. {t.maxSalary.toLocaleString()}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: 11.5, color: '#64748B' }}>{t.scaleString}</td>
                  <td>
                    {expanded === t.code
                      ? <ChevronUp size={15} color="#94A3B8" />
                      : <ChevronDown size={15} color="#94A3B8" />}
                  </td>
                </tr>
                {expanded === t.code && (
                  <tr>
                    <td colSpan={8} style={{ padding: 0, background: '#F8FAFD', borderBottom: '1.5px solid #E8ECF2' }}>
                      <div style={{ padding: '16px 24px', display: 'grid', gap: 18 }}>
                        <div>
                          <div style={{ fontSize: 12, fontWeight: 700, color: '#64748B', marginBottom: 12, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                            Step progression
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(8, 1fr)', gap: 8 }}>
                            {buildSteps(t).map(s => (
                              <div key={s.step} style={{
                                background: s.step === 0 ? '#EEF3FB' : '#fff',
                                border: `1px solid ${s.step === 0 ? '#B8D0F0' : '#E2E8F0'}`,
                                borderRadius: 8, padding: '10px 12px', textAlign: 'center',
                              }}>
                                <div style={{ fontSize: 10.5, fontWeight: 700, color: '#94A3B8', marginBottom: 4, textTransform: 'uppercase' }}>
                                  {s.step === 0 ? 'Start' : `Step ${s.step}`}
                                </div>
                                <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>
                                  {s.salary.toLocaleString()}
                                </div>
                                {s.step > 0 && (
                                  <div style={{ fontSize: 10.5, color: '#166534', marginTop: 2 }}>
                                    +{s.step <= t.band1Years ? t.band1Increment : t.band2Increment}
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>

                        <div>
                          <div style={{ fontSize: 12, fontWeight: 700, color: '#64748B', marginBottom: 12, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                            Effective version
                          </div>
                          <div style={{ display: 'grid', gap: 10 }}>
                            {currentVersionFor(versionsByCode, t.code) ? (
                              <VersionCard
                                label="Current"
                                version={currentVersionFor(versionsByCode, t.code)!}
                              />
                            ) : (
                              <div className="card" style={{ margin: 0, background: '#fffaf1', borderColor: '#f5d89f' }}>
                                No version has been created yet for this code.
                              </div>
                            )}

                            <div style={{ marginTop: 6 }}>
                              <div style={{ fontSize: 12, fontWeight: 700, color: '#64748B', marginBottom: 10, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                                History
                              </div>
                              <div style={{ display: 'grid', gap: 8 }}>
                                {(versionsByCode[t.code] || []).slice(1).map(version => (
                                  <VersionCard key={version.id} label="Past" version={version} compact />
                                ))}
                                {(versionsByCode[t.code] || []).length <= 1 && (
                                  <div style={{ fontSize: 12.5, color: '#94A3B8' }}>No historical versions yet.</div>
                                )}
                              </div>
                            </div>

                            <div style={{ marginTop: 6, padding: 16, background: '#fff', border: '1px solid #E8ECF2', borderRadius: 12 }}>
                              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 12 }}>
                                <div>
                                  <div style={{ fontSize: 12, fontWeight: 800, color: '#0F172A' }}>Add new version</div>
                                  <div style={{ fontSize: 11.5, color: '#94A3B8', marginTop: 2 }}>Add only. Existing versions are never edited.</div>
                                </div>
                                <Plus size={16} color="#64748B" />
                              </div>

                              {rowErrors[t.code] && (
                                <div style={{
                                  marginBottom: 10, padding: '8px 12px', borderRadius: 8,
                                  background: '#FFF5F5', border: '1px solid #FCA5A5',
                                  fontSize: 12, color: '#991B1B',
                                }}>
                                  {rowErrors[t.code]}
                                </div>
                              )}

                              <div style={{ display: 'grid', gridTemplateColumns: '1fr 180px auto', gap: 10, alignItems: 'end' }}>
                                <Field
                                  label="Scale string"
                                  value={drafts[t.code]?.scaleString ?? t.scaleString}
                                  onChange={value => updateDraft(t.code, { scaleString: value })}
                                  placeholder="140,490-5x2,500-10x2,650-179,240/-(ELspl)"
                                />
                                <Field
                                  label="Effective from"
                                  type="date"
                                  value={drafts[t.code]?.effectiveFrom ?? ''}
                                  onChange={value => updateDraft(t.code, { effectiveFrom: value })}
                                />
                                <button
                                  className="btn btn-primary"
                                  onClick={() => submitVersion(t.code)}
                                  disabled={savingCode === t.code}
                                  style={{ height: 40, minWidth: 130 }}
                                >
                                  {savingCode === t.code ? 'Saving…' : 'Add version'}
                                </button>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function SummaryCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="card">
      <div style={{ fontSize: 11.5, fontWeight: 600, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 6 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 800, color: '#0F172A' }}>{value}</div>
    </div>
  )
}

function VersionCard({ version, label, compact = false }: { version: SalaryScaleVersion; label: string; compact?: boolean }) {
  return (
    <div className="card" style={{ margin: 0, background: compact ? '#fff' : '#F8FAFD', borderColor: '#E8ECF2' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'start' }}>
        <div>
          <div style={{ fontSize: 11, fontWeight: 800, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: 4 }}>{label}</div>
          <div style={{ fontFamily: 'monospace', fontSize: compact ? 11.5 : 12.5, color: '#0F172A', lineHeight: 1.5 }}>
            {version.scaleString}
          </div>
        </div>
        <div style={{ textAlign: 'right', fontSize: 11.5, color: '#64748B' }}>
          <div style={{ fontWeight: 700, color: '#0F172A' }}>{version.effectiveFrom}</div>
          <div>{version.effectiveTo ? `until ${version.effectiveTo}` : 'current'}</div>
        </div>
      </div>
    </div>
  )
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
}: {
  label: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  type?: string
}) {
  return (
    <label style={{ display: 'grid', gap: 6 }}>
      <span style={{ fontSize: 11.5, fontWeight: 700, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.4px' }}>{label}</span>
      <input
        type={type}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        style={{
          height: 40,
          borderRadius: 10,
          border: '1px solid #D7E0EA',
          padding: '0 12px',
          fontSize: 13,
          outline: 'none',
          background: '#fff',
        }}
      />
    </label>
  )
}

function currentVersionFor(versionsByCode: Record<string, SalaryScaleVersion[]>, code: string) {
  return (versionsByCode[code] || []).find(version => version.effectiveTo === null) || (versionsByCode[code] || [])[0]
}

function buildSteps(type: SalaryType) {
  const steps: { step: number; salary: number }[] = []
  let salary = type.startSalary
  const total = type.band1Years + type.band2Years
  for (let step = 0; step <= total; step++) {
    steps.push({ step, salary })
    if (step < total) salary += step < type.band1Years ? type.band1Increment : type.band2Increment
  }
  return steps
}