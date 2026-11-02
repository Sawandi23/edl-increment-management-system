// src/services/incrementCycleService.ts
import { apiClient } from './apiClient'
import type { IncrementCycle, DashboardStats, IncrementStatusLogEntry } from '../types'

export interface DashboardFilter {
  fromDate: string
  toDate: string
  status?: string
  salaryTypeCode?: string
}

export const incrementCycleService = {
  filterDashboard: async (filter: DashboardFilter): Promise<IncrementCycle[]> => {
    const { data } = await apiClient.post('/increment-cycles/filter', filter)
    return data
  },

  getStats: async (from: string, to: string): Promise<DashboardStats> => {
    const { data } = await apiClient.get('/increment-cycles/stats', { params: { from, to } })
    return data
  },

  getByEmployee: async (employeeId: number): Promise<IncrementCycle[]> => {
    const { data } = await apiClient.get(`/increment-cycles/employee/${employeeId}`)
    return data
  },

  getById: async (id: number): Promise<IncrementCycle> => {
    const { data } = await apiClient.get(`/increment-cycles/${id}`)
    return data
  },

  createInitialCycle: async (employeeId: number): Promise<IncrementCycle> => {
    const { data } = await apiClient.post(`/increment-cycles/employee/${employeeId}/create-initial`)
    return data
  },

  nextCycle: async (cycleId: number): Promise<IncrementCycle> => {
    const { data } = await apiClient.post(`/increment-cycles/${cycleId}/next-cycle`)
    return data
  },

  getStatusHistory: async (id: number): Promise<IncrementStatusLogEntry[]> => {
    const { data } = await apiClient.get(`/increment-cycles/${id}/status-history`)
    return data
  },

  changeStatus: async (id: number, newStatus: string, changedBy: string, notes?: string): Promise<IncrementCycle> => {
    const { data } = await apiClient.patch(`/increment-cycles/${id}/status`, { newStatus, changedBy, notes })
    return data
  },

  manualOverride: async (id: number, payload: {
    newSalary: number
    newStep: number
    notes: string
    changedBy: string
  }): Promise<IncrementCycle> => {
    const { data } = await apiClient.patch(`/increment-cycles/${id}/override`, payload)
    return data
  },
}
