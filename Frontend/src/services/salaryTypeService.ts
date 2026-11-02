// src/services/salaryTypeService.ts
import { apiClient } from './apiClient'
import type { SalaryType } from '../types'

export const salaryTypeService = {
  getAll: async (): Promise<SalaryType[]> => {
    const { data } = await apiClient.get('/salary-types')
    return data
  },

  getByCode: async (code: string): Promise<SalaryType> => {
    const { data } = await apiClient.get(`/salary-types/${code}`)
    return data
  },
}
