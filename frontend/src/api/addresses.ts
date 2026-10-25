import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthProvider'
import { apiFetch } from './client'
import type { Address, AddressInput } from './types'

export const useAddresses = () => {
  const { status } = useAuth()
  return useQuery({
    queryKey: ['addresses'],
    queryFn: () => apiFetch<Address[]>('/api/members/me/addresses', { auth: 'required' }),
    enabled: status === 'member',
  })
}

export function useSaveAddress() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: AddressInput }) =>
      apiFetch<Address>(id === undefined ? '/api/members/me/addresses' : `/api/members/me/addresses/${id}`, {
        method: id === undefined ? 'POST' : 'PUT',
        auth: 'required',
        json: input,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['addresses'] }),
  })
}

export function useDeleteAddress() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiFetch<void>(`/api/members/me/addresses/${id}`, { method: 'DELETE', auth: 'required' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['addresses'] }),
  })
}
