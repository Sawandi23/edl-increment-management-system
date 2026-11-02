// src/types/index.ts
// Mirrors the backend DTOs exactly (lk.ceb.increment.dto.response.*)

export type IncrementStatus =
  | 'ELIGIBLE' | 'AP_DRAFT' | 'AP_SENT'
  | 'AP_STOPPED' | 'AP_PAUSED'
  | 'CERTIFICATE_READY' | 'CERTIFICATE_ISSUED'
  | 'PAID' | 'REJECTED'

export type IncrementType = 'NORMAL' | 'EFFICIENCY_BAR' | 'SURPLUS' | 'MANUAL'

// Matches SalaryTypeResponse.java
export interface SalaryType {
  id: number
  code: string
  description: string
  startSalary: number
  band1Increment: number
  band1Years: number
  band2Increment: number
  band2Years: number
  maxSalary: number
  scaleString: string
}

export interface SalaryScaleVersion {
  id: number
  salaryTypeCode: string
  scaleString: string
  effectiveFrom: string
  effectiveTo: string | null
  createdAt: string
  createdBy: string
}

// Matches EmployeeResponse.java
export interface Employee {
  id: number
  epfNo: string
  nameEnglish: string
  nameSinhala: string
  designationEnglish: string
  designationSinhala: string
  incrementDate: string
  permanentDate: string | null
  unit: string
  phoneNumber: string
  isActive: boolean
  currentSalaryTypeCode?: string
  currentSalaryScale?: string | null
  currentStep?: number
  currentSalary?: number
  hasPromotionFlag?: boolean
  totalServiceYears?: number
}

// Matches SalaryHistoryResponse.java
export interface EmployeeSalaryHistory {
  id: number
  salaryTypeCode: string
  salaryTypeScaleString: string
  currentStep: number
  currentSalary: number
  effectiveFrom: string
  effectiveTo: string | null
  hasPromotion: boolean
  promotionRemark: string | null
  changedBy: string | null
  changedAt: string | null
}

// Matches IncrementCycleResponse.java — flattened, no nested employee object
export interface IncrementCycle {
  id: number
  employeeId: number
  epfNo: string
  employeeNameEnglish: string
  employeeNameSinhala: string
  designationEnglish: string
  unit: string

  salaryTypeCode: string
  salaryScale?: string | null
  cycleYear: number
  incrementDate: string
  periodFrom?: string | null
  periodTo?: string | null

  previousSalary: number
  previousStep: number
  incrementAmount: number
  newSalary: number
  newStep: number

  isSurplus: boolean
  surplusLabel: string | null
  isCertificateYear: boolean
  incrementType: IncrementType

  evaluationScore: string | null
  suspended: boolean
  hasPraise: boolean
  hasPunishment: boolean

  serviceTotal?: string | null
  servicePost?: string | null
  serviceGrade?: string | null

  status: IncrementStatus
  referenceNumber: string | null

  apFormGeneratedAt: string | null
  apFormSentAt: string | null
  certificateGeneratedAt: string | null
  paidAt: string | null

  isManualOverride: boolean
  overrideNotes: string | null
  clerkName?: string | null
  hrOfficerName?: string | null
  dgmName?: string | null
}

// Matches IncrementStatusLogResponse.java
export interface IncrementStatusLogEntry {
  id: number
  oldStatus: IncrementStatus | null
  newStatus: IncrementStatus
  changedBy: string
  changedAt: string
  notes: string | null
}

// Matches DashboardStatsResponse.java
export interface DashboardStats {
  dueThisPeriod: number
  apFormsPending: number
  sentToUnit: number
  paidThisYear: number
}

// ── Display helpers ──────────────────────────────────────────────────────────

export const STATUS_LABELS: Record<IncrementStatus, string> = {
  ELIGIBLE:           'Eligible',
  AP_DRAFT:           'AP Draft',
  AP_SENT:            'Sent to unit',
  AP_STOPPED:         'Stopped',
  AP_PAUSED:          'Paused',
  CERTIFICATE_READY:  'Certificate ready',
  CERTIFICATE_ISSUED: 'Certificate issued',
  PAID:               'Paid',
  REJECTED:           'Rejected',
}

export const STATUS_CLASS: Record<IncrementStatus, string> = {
  ELIGIBLE:           'badge-eligible',
  AP_DRAFT:           'badge-ap-draft',
  AP_SENT:            'badge-ap-sent',
  AP_STOPPED:         'badge-stopped',
  AP_PAUSED:          'badge-paused',
  CERTIFICATE_READY:  'badge-cert-ready',
  CERTIFICATE_ISSUED: 'badge-cert-issued',
  PAID:               'badge-paid',
  REJECTED:           'badge-rejected',
}
