// src/services/formService.ts
import { apiClient } from './apiClient'

export const formService = {
  generateApFormDocx: async (cycleId: number, overrides: Record<string, string> = {}): Promise<Blob> => {
    const response = await apiClient.post(
      `/forms/ap/${cycleId}/docx`,
      overrides,
      { responseType: 'blob' }
    )
    return response.data
  },

  generateApForm: async (cycleId: number): Promise<Blob> => {
    const response = await apiClient.post(`/forms/ap/${cycleId}`, {}, { responseType: 'blob' })
    return response.data
  },

  /**
   * Generate increment certificate PDF.
   */
  generateCertificateDocx: async (cycleId: number): Promise<Blob> => {
    const response = await apiClient.post(`/forms/certificate/${cycleId}/docx`, {}, { responseType: 'blob' })
    return response.data
  },

  generateCertificate: async (cycleId: number): Promise<Blob> => {
    const response = await apiClient.post(`/forms/certificate/${cycleId}`, {}, { responseType: 'blob' })
    return response.data
  },

  /**
   * Trigger PDF download in browser.
   */
  downloadBlob: (blob: Blob, filename: string) => {
    const url = URL.createObjectURL(blob)
    const a   = document.createElement('a')
    a.href    = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  },
}
