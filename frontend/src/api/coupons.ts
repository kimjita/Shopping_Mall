import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthProvider'
import { apiFetch } from './client'
import type { DownloadableCoupon, MyCoupon } from './types'

export const useDownloadableCoupons = () => {
  const { status } = useAuth()
  return useQuery({
    queryKey: ['downloadableCoupons'],
    queryFn: () => apiFetch<DownloadableCoupon[]>('/api/members/me/coupons/downloadable', { auth: 'required' }),
    enabled: status === 'member',
  })
}

export const useMyCoupons = () => {
  const { status } = useAuth()
  return useQuery({
    queryKey: ['myCoupons'],
    queryFn: () => apiFetch<MyCoupon[]>('/api/members/me/coupons', { auth: 'required' }),
    enabled: status === 'member',
  })
}

export function useDownloadCoupon() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (couponId: number) =>
      apiFetch<MyCoupon>(`/api/members/me/coupons/${couponId}/download`, { method: 'POST', auth: 'required' }),
    // 실패(소진·중복 등)해도 서버 목록이 바뀌었을 수 있어 settle 시점에 다시 받는다.
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['downloadableCoupons'] }),
        queryClient.invalidateQueries({ queryKey: ['myCoupons'] }),
      ]),
  })
}
