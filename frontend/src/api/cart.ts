import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthProvider'
import { apiFetch } from './client'
import type { CartItem } from './types'

export const useCart = () => {
  const { status } = useAuth()
  return useQuery({
    queryKey: ['cart'],
    queryFn: () => apiFetch<{ items: CartItem[] }>('/api/members/me/cart', { auth: 'required' }),
    enabled: status === 'member',
  })
}
