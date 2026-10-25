import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthProvider'
import { apiFetch } from './client'
import type { CheckoutPreview } from './types'

export const useCheckoutPreview = (ids: number[], couponId: number | null, points: number) => {
  const { status } = useAuth()
  return useQuery({
    queryKey: ['checkoutPreview', ids.join(','), couponId, points],
    queryFn: () =>
      apiFetch<CheckoutPreview>('/api/members/me/checkout/preview', {
        method: 'POST',
        auth: 'required',
        json: { cartItemIds: ids, memberCouponId: couponId, points },
      }),
    enabled: status === 'member',
    placeholderData: keepPreviousData,
  })
}
