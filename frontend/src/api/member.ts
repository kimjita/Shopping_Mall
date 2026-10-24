import type { Me } from './types'
import { apiFetch } from './client'

export const fetchMe = () => apiFetch<Me>('/api/members/me', { auth: 'required' })

export const signup = (body: { email: string; password: string; name: string; phone: string }) =>
  apiFetch<{ memberId: number }>('/api/auth/signup', {
    method: 'POST',
    auth: 'none',
    json: body,
  })
