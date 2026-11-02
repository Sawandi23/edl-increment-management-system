import { apiClient } from './apiClient'

export interface SalaryScaleVersion {
  id: number
  salaryTypeCode: string
  scaleString: string
  effectiveFrom: string
  effectiveTo: string | null
  createdAt: string
  createdBy: string
}

export interface CreateSalaryScaleVersionRequest {
  salaryTypeCode: string
  scaleString: string
  effectiveFrom: string
  createdBy: string
}

export const salaryScaleVersionService = {
  getAll: async (): Promise<SalaryScaleVersion[]> => {
    const { data } = await apiClient.get('/salary-scale-versions')
    return data
  },

  getByCode: async (salaryTypeCode: string): Promise<SalaryScaleVersion[]> => {
    const { data } = await apiClient.get(`/salary-scale-versions/${salaryTypeCode}`)
    return data
  },

  create: async (request: CreateSalaryScaleVersionRequest): Promise<SalaryScaleVersion> => {
    const { data } = await apiClient.post('/salary-scale-versions', request)
    return data
  },
}