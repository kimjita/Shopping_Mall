import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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

export function useAddToCart() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { skuId: number; quantity: number }) =>
      apiFetch<CartItem>('/api/members/me/cart/items', { method: 'POST', auth: 'required', json: body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cart'] }),
  })
}
