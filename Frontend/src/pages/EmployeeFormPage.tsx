import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { employeeService, type EmployeeCreatePayload, type EmployeePatchPayload } from '../services/employeeService'
import { salaryTypeService } from '../services/salaryTypeService'
import type { Employee, SalaryType } from '../types'
import { Spinner, ErrorMsg, SuccessMsg, SectionHeader } from '../components/ui'
import { ArrowLeft, Save, PlusCircle, UserRound, Building2, Phone, CalendarDays, BadgeInfo, Hash, Trash2 } from 'lucide-react'

type EmployeeFormState = {
  epfNo: string
  nameEnglish: string
  nameSinhala: string
  designationEnglish: string
  designationSinhala: string
  incrementDate: string
  permanentDate: string
  salaryTypeCode: string
  currentStep: string
  unit: string
  phoneNumber: string
  isActive: boolean
}

const UNIT_OPTIONS = [
  'HRO (CC)',
  'Acc (Rev)',
  'Acc (Exp)',
  'CE (Col. South)',
  'CE (Col. West)',
  'CE (Col. North)',
  'CE (Col. East)',
  'CE ( GI & SCS )',
  'CE ( P & D)',
  'CE (Maintenance)',
  'CE ( Operations)',
  'ITO (CC)',
  'Director (CC)',
]

const EMPTY_FORM: EmployeeFormState = {
  epfNo: '',
  nameEnglish: '',
  nameSinhala: '',
  designationEnglish: '',
  designationSinhala: '',
  incrementDate: '',
  permanentDate: '',
  salaryTypeCode: '',
  currentStep: '',
  unit: '',
  phoneNumber: '',
  isActive: true,
}

const EPF_PATTERN = /^[0-9]{1,20}$/
const PHONE_PATTERN = /^(?:[0-9]{9}|[0-9]{10})$/

export default function EmployeeFormPage() {
  const { employeeId } = useParams<{ employeeId?: string }>()
  const navigate = useNavigate()
  const isEdit = Boolean(employeeId)

  const [employee, setEmployee] = useState<Employee | null>(null)
  const [salaryTypes, setSalaryTypes] = useState<SalaryType[]>([])
  const [form, setForm] = useState<EmployeeFormState>(EMPTY_FORM)
  const [loading, setLoading] = useState(isEdit)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let active = true

    const load = async () => {
      try {
        if (isEdit && employeeId) {
          const employeeRecord = await employeeService.getById(Number(employeeId))
          if (!active) return
          setEmployee(employeeRecord)
          setForm(fromEmployee(employeeRecord))
        } else {
          const types = await salaryTypeService.getAll()
          if (!active) return
          setSalaryTypes(types)
        }
      } catch (e: any) {
        if (!active) return
        setError(e.message)
      } finally {
        if (active) setLoading(false)
      }
    }

    load()

    return () => { active = false }
  }, [employeeId, isEdit])

  const salaryTypeOptions = useMemo(
    () => salaryTypes.map(type => ({ value: type.code, label: type.code })),
    [salaryTypes],
  )

  const updateField = (key: keyof EmployeeFormState, value: string | boolean) => {
    setForm(prev => ({ ...prev, [key]: value }))
  }

  const validate = () => {
    const nextErrors: Record<string, string> = {}

    if (!isEdit && !EPF_PATTERN.test(form.epfNo.trim())) {
      nextErrors.epfNo = 'EPF number must contain digits only'
    }
    if (!form.nameEnglish.trim()) nextErrors.nameEnglish = 'English name is required'
    if (!form.nameSinhala.trim()) nextErrors.nameSinhala = 'Sinhala name is required'
    if (!form.designationEnglish.trim()) nextErrors.designationEnglish = 'English designation is required'
    if (!form.designationSinhala.trim()) nextErrors.designationSinhala = 'Sinhala designation is required'
    if (!form.incrementDate) nextErrors.incrementDate = 'Increment date is required'
    if (!form.unit.trim()) nextErrors.unit = 'Unit is required'
    if (!isEdit && !form.salaryTypeCode.trim()) nextErrors.salaryTypeCode = 'Salary type is required'
    if (!isEdit && !form.currentStep.trim()) nextErrors.currentStep = 'Current salary step is required'
    if (form.phoneNumber && !PHONE_PATTERN.test(form.phoneNumber.trim())) {
      nextErrors.phoneNumber = 'Phone number must be 9 or 10 digits'
    }

    setFieldErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  const save = async () => {
    if (!validate()) return
    setSaving(true)
    setError(null)
    setSuccess(null)

    try {
      if (isEdit && employeeId) {
        const payload: EmployeePatchPayload = {
          nameEnglish: form.nameEnglish.trim(),
          nameSinhala: form.nameSinhala.trim(),
          designationEnglish: form.designationEnglish.trim(),
          designationSinhala: form.designationSinhala.trim(),
          incrementDate: form.incrementDate,
          permanentDate: form.permanentDate || null,
          unit: form.unit.trim() || null,
          phoneNumber: form.phoneNumber.trim() || null,
          isActive: form.isActive,
        }
        const saved = await employeeService.patch(Number(employeeId), payload)
        setSuccess('Employee updated successfully.')
        navigate(`/employees?epf=${saved.epfNo}`)
      } else {
        const payload: EmployeeCreatePayload = {
          epfNo: form.epfNo.trim(),
          nameEnglish: form.nameEnglish.trim(),
          nameSinhala: form.nameSinhala.trim(),
          designationEnglish: form.designationEnglish.trim(),
          designationSinhala: form.designationSinhala.trim(),
          incrementDate: form.incrementDate,
          currentStep: Number(form.currentStep),
          permanentDate: form.permanentDate || null,
          salaryTypeCode: form.salaryTypeCode.trim(),
          unit: form.unit.trim() || null,
          phoneNumber: form.phoneNumber.trim() || null,
          isActive: form.isActive,
        }
        await employeeService.create(payload)
        setSuccess('Employee created successfully.')
        const incrementDate = new Date(form.incrementDate)
        const from = `${incrementDate.getFullYear()}-01-01`
        const to = `${incrementDate.getFullYear()}-12-31`
        navigate(`/?from=${from}&to=${to}`)
      }
    } catch (e: any) {
      setError(e.response?.data?.message || e.message)
    } finally {
      setSaving(false)
    }
  }

  const deleteEmployee = async () => {
    if (!isEdit || !employeeId) return
    const confirmed = window.confirm('Delete this employee? This will remove the employee and their related records.')
    if (!confirmed) return

    setDeleting(true)
    setError(null)
    try {
      await employeeService.delete(Number(employeeId))
      navigate('/employees')
    } catch (e: any) {
      setError(e.response?.data?.message || e.message)
    } finally {
      setDeleting(false)
    }
  }

  if (loading) return <Spinner label={isEdit ? 'Loading employee…' : 'Loading employee form…'} />
  if (error && !employee) return <ErrorMsg message={error} />

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto' }}>
      <button className="btn btn-ghost btn-sm" onClick={() => navigate(-1)} style={{ gap: 6, marginBottom: 18 }}>
        <ArrowLeft size={15} /> Back
      </button>

      {error && <ErrorMsg message={error} />}
      {success && <SuccessMsg message={success} />}

      <div style={{ display: 'grid', gridTemplateColumns: '1.15fr 0.85fr', gap: 16, alignItems: 'start' }}>
        <div className="card">
          <SectionHeader
            title={isEdit ? 'Edit employee' : 'Add employee'}
            action={<span style={{ fontSize: 12, color: '#94A3B8' }}>{isEdit ? 'PATCH /api/employees/{id}' : 'POST /api/employees'}</span>}
          />

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 14 }}>
            {!isEdit && <TextField icon={BadgeInfo} label="EPF number" value={form.epfNo} onChange={value => updateField('epfNo', value)} error={fieldErrors.epfNo} placeholder="e.g. 26856" />}
            <TextField icon={UserRound} label="Name (English)" value={form.nameEnglish} onChange={value => updateField('nameEnglish', value)} error={fieldErrors.nameEnglish} />
            <TextField icon={UserRound} label="Name (Sinhala)" value={form.nameSinhala} onChange={value => updateField('nameSinhala', value)} error={fieldErrors.nameSinhala} />
            <TextField icon={UserRound} label="Designation (English)" value={form.designationEnglish} onChange={value => updateField('designationEnglish', value)} error={fieldErrors.designationEnglish} />
            <TextField icon={UserRound} label="Designation (Sinhala)" value={form.designationSinhala} onChange={value => updateField('designationSinhala', value)} error={fieldErrors.designationSinhala} />
            <TextField icon={CalendarDays} label="Increment date" type="date" value={form.incrementDate} onChange={value => updateField('incrementDate', value)} error={fieldErrors.incrementDate} />
            <TextField icon={CalendarDays} label="Permanent date" type="date" value={form.permanentDate} onChange={value => updateField('permanentDate', value)} />
            <SelectField icon={Building2} label="Unit" value={form.unit} onChange={value => updateField('unit', value)} options={UNIT_OPTIONS} error={fieldErrors.unit} />
            <TextField icon={Phone} label="Phone number" value={form.phoneNumber} onChange={value => updateField('phoneNumber', value)} error={fieldErrors.phoneNumber} placeholder="9 or 10 digits" />

            {!isEdit && (
              <>
                <SelectField
                  icon={Hash}
                  label="Salary type code"
                  value={form.salaryTypeCode}
                  onChange={value => updateField('salaryTypeCode', value)}
                  options={salaryTypeOptions}
                  error={fieldErrors.salaryTypeCode}
                />
                <TextField
                  icon={Hash}
                  label="Current salary step"
                  type="number"
                  value={form.currentStep}
                  onChange={value => updateField('currentStep', value)}
                  error={fieldErrors.currentStep}
                  placeholder="0"
                />
              </>
            )}

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, gridColumn: '1 / -1', paddingTop: 4 }}>
              <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#475569', fontWeight: 600 }}>
                <input type="checkbox" checked={form.isActive} onChange={e => updateField('isActive', e.target.checked)} />
                Active employee
              </label>
            </div>

            {isEdit && employee && (
              <div style={{ gridColumn: '1 / -1', marginTop: 4, padding: 14, borderRadius: 10, border: '1px solid #E8ECF2', background: '#F8FAFD' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                  <div>
                    <div style={{ fontSize: 12, color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.4px' }}>Read-only identity</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', marginTop: 4 }}>EPF {employee.epfNo}</div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                    <span style={{ fontSize: 12, color: '#64748B' }}>Current salary type</span>
                    <span style={{ fontSize: 13, fontWeight: 700, color: '#1E3A6E' }}>{employee.currentSalaryTypeCode || '—'}</span>
                    <span style={{ fontSize: 12, color: '#64748B' }}>Current salary scale</span>
                    <span style={{ fontSize: 13, fontWeight: 700, color: '#1E3A6E' }}>{employee.currentSalaryScale || '—'}</span>
                  </div>
                </div>
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap', marginTop: 18 }}>
            {isEdit && (
              <button className="btn btn-danger" onClick={deleteEmployee} disabled={deleting} style={{ gap: 8 }}>
                <Trash2 size={15} strokeWidth={2.2} />
                {deleting ? 'Deleting…' : 'Delete employee'}
              </button>
            )}
            <button className="btn btn-primary" onClick={save} disabled={saving} style={{ gap: 8, minWidth: 160, marginLeft: 'auto' }}>
              {isEdit ? <Save size={15} strokeWidth={2.2} /> : <PlusCircle size={15} strokeWidth={2.2} />}
              {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create employee'}
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div className="card">
            <SectionHeader title={isEdit ? 'Edit guidance' : 'Create guidance'} />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 13, color: '#475569', lineHeight: 1.7 }}>
              <div style={{ padding: '10px 12px', borderRadius: 9, background: '#F8FAFD', border: '1px solid #E8ECF2' }}>
                EPF numbers must be unique and digit-only.
              </div>
              <div style={{ padding: '10px 12px', borderRadius: 9, background: '#F8FAFD', border: '1px solid #E8ECF2' }}>
                Increment date drives service-year and increment calculations.
              </div>
              {!isEdit && (
                <div style={{ padding: '10px 12px', borderRadius: 9, background: '#F8FAFD', border: '1px solid #E8ECF2' }}>
                  Pick a salary type code such as SN1, SN2, or EL1. The selected code and current step seed the opening salary history and the initial increment cycle.
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function fromEmployee(employee: Employee): EmployeeFormState {
  return {
    epfNo: employee.epfNo,
    nameEnglish: employee.nameEnglish,
    nameSinhala: employee.nameSinhala,
    designationEnglish: employee.designationEnglish,
    designationSinhala: employee.designationSinhala,
    incrementDate: employee.incrementDate,
    permanentDate: employee.permanentDate || '',
    salaryTypeCode: employee.currentSalaryTypeCode || '',
    currentStep: employee.currentStep != null ? String(employee.currentStep) : '',
    unit: employee.unit || '',
    phoneNumber: employee.phoneNumber || '',
    isActive: employee.isActive,
  }
}

function TextField({
  icon: Icon,
  label,
  value,
  onChange,
  error,
  type = 'text',
  placeholder,
}: {
  icon: any
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  type?: string
  placeholder?: string
}) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 12, fontWeight: 700, color: '#475569' }}>{label}</span>
      <div className="input-wrap">
        <Icon size={14} className="input-icon-l" />
        <input
          className="input has-icon-l"
          type={type}
          value={value}
          placeholder={placeholder}
          onChange={e => onChange(e.target.value)}
        />
      </div>
      {error && <span style={{ fontSize: 11.5, color: '#B91C1C' }}>{error}</span>}
    </label>
  )
}

function SelectField({
  icon: Icon,
  label,
  value,
  onChange,
  options,
  error,
}: {
  icon: any
  label: string
  value: string
  onChange: (value: string) => void
  options: string[] | { value: string; label: string }[]
  error?: string
}) {
  const normalizedOptions = typeof options[0] === 'string'
    ? (options as string[]).map(value => ({ value, label: value }))
    : (options as { value: string; label: string }[])

  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 12, fontWeight: 700, color: '#475569' }}>{label}</span>
      <div className="input-wrap">
        <Icon size={14} className="input-icon-l" />
        <select className="input has-icon-l select" value={value} onChange={e => onChange(e.target.value)}>
          <option value="">Select {label.toLowerCase()}</option>
          {normalizedOptions.map(option => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
      </div>
      {error && <span style={{ fontSize: 11.5, color: '#B91C1C' }}>{error}</span>}
    </label>
  )
}