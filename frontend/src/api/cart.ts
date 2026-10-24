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

export function useChangeQuantity() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, quantity }: { id: number; quantity: number }) =>
      apiFetch<CartItem>(`/api/members/me/cart/items/${id}`, { method: 'PATCH', auth: 'required', json: { quantity } }),
    onSuccess: (item) =>
      queryClient.setQueryData<{ items: CartItem[] }>(['cart'], (old) =>
        old && { items: old.items.map((i) => (i.id === item.id ? item : i)) },
      ),
  })
}

export function useRemoveCartItem() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiFetch<void>(`/api/members/me/cart/items/${id}`, { method: 'DELETE', auth: 'required' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cart'] }),
  })
}
