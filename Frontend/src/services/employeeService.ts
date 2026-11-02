// src/services/employeeService.ts
import { apiClient } from './apiClient'
import type { Employee, EmployeeSalaryHistory } from '../types'

export interface EmployeeCreatePayload {
  epfNo: string
  nameEnglish: string
  nameSinhala: string
  designationEnglish: string
  designationSinhala: string
  incrementDate: string
  currentStep: number
  permanentDate?: string | null
  salaryTypeCode: string
  unit?: string | null
  phoneNumber?: string | null
  isActive?: boolean
}

export interface EmployeePatchPayload {
  nameEnglish?: string
  nameSinhala?: string
  designationEnglish?: string
  designationSinhala?: string
  incrementDate?: string
  permanentDate?: string | null
  unit?: string | null
  phoneNumber?: string | null
  isActive?: boolean
}

export const employeeService = {
  getById: async (id: number): Promise<Employee> => {
    const { data } = await apiClient.get(`/employees/${id}`)
    return data
  },

  searchByEpf: async (epfNo: string): Promise<Employee> => {
    const { data } = await apiClient.get('/employees/search', { params: { epfNo } })
    return data
  },

  searchByName: async (name: string): Promise<Employee[]> => {
    const { data } = await apiClient.get('/employees/search-by-name', { params: { name } })
    return data
  },

  create: async (payload: EmployeeCreatePayload): Promise<Employee> => {
    const { data } = await apiClient.post('/employees', payload)
    return data
  },

  patch: async (id: number, payload: EmployeePatchPayload): Promise<Employee> => {
    const { data } = await apiClient.patch(`/employees/${id}`, payload)
    return data
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/employees/${id}`)
  },

  update: async (id: number, payload: EmployeePatchPayload): Promise<Employee> => {
    const { data } = await apiClient.put(`/employees/${id}`, payload)
    return data
  },

  changeSalaryType: async (id: number, payload: {
    newSalaryTypeCode: string
    newStep: number
    effectiveFrom: string
    remark: string
    changedBy: string
  }): Promise<EmployeeSalaryHistory> => {
    const { data } = await apiClient.post(`/employees/${id}/salary-type-change`, payload)
    return data
  },

  getSalaryHistory: async (id: number): Promise<EmployeeSalaryHistory[]> => {
    const { data } = await apiClient.get(`/employees/${id}/salary-history`)
    return data
  },
}
