// src/hooks/useAuth.ts
import { useState, useEffect } from 'react'
import { apiClient } from '../services/apiClient'

export interface AuthUser {
  username: string
  displayName: string
  role: 'ADMIN'
}

const STORAGE_KEY = 'ceb_auth_user'

export function useAuth() {
  const [user, setUser] = useState<AuthUser | null>(() => {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY)
      return stored ? JSON.parse(stored) : null
    } catch { return null }
  })

  useEffect(() => {
    if (!user) return
    apiClient.get<AuthUser>('/auth/me').catch(() => {
      sessionStorage.removeItem(STORAGE_KEY)
      setUser(null)
    })
  }, [])

  const login = async (username: string, password: string): Promise<void> => {
    const response = await apiClient.post<AuthUser>('/auth/login', { username, password })
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(response.data))
    setUser(response.data)
  }

  const signup = async (username: string, displayName: string, password: string): Promise<void> => {
    await apiClient.post('/auth/signup', { username, displayName, password })
    await login(username, password)
  }

  const logout = async () => {
    try {
      await apiClient.post('/auth/logout')
    } finally {
      sessionStorage.removeItem(STORAGE_KEY)
      setUser(null)
    }
  }

  return { user, login, signup, logout, isAuthenticated: user !== null }
}
