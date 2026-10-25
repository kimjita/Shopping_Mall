import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthProvider'
import { isOrderNumber } from '../lib/orderName'
import { apiFetch } from './client'
import type { Order } from './types'

export const useOrder = (orderNumber: string) => {
  const { status } = useAuth()
  return useQuery({
    queryKey: ['order', orderNumber],
    queryFn: () => apiFetch<Order>(`/api/members/me/orders/${orderNumber}`, { auth: 'required' }),
    enabled: status === 'member' && isOrderNumber(orderNumber),
  })
}

// 성공한 뮤테이션의 응답으로 상세 캐시를 바꾸고 목록은 다시 받는다.
function useOrderMutation<V>(request: (v: V) => Promise<Order>, alsoCart = false) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (order) => {
      queryClient.setQueryData(['order', order.orderNumber], order)
      return refresh(queryClient, alsoCart)
    },
  })
}

const refresh = (queryClient: QueryClient, alsoCart: boolean) =>
  Promise.all([
    queryClient.invalidateQueries({ queryKey: ['orders'] }),
    alsoCart ? queryClient.invalidateQueries({ queryKey: ['cart'] }) : undefined,
  ])

const post = (path: string, json?: unknown) => apiFetch<Order>(path, { method: 'POST', auth: 'required', json })

export const useCreateOrder = () =>
  useOrderMutation((v: { cartItemIds: number[]; memberCouponId: number | null; points: number; addressId: number }) =>
    post('/api/members/me/orders', v),
  )

export const useConfirmPayment = () =>
  useOrderMutation(
    ({ orderNumber, ...json }: { orderNumber: string; paymentKey: string; amount: number }) =>
      post(`/api/members/me/orders/${orderNumber}/payment`, json),
    true,
  )

export const useCancelOrder = () =>
  useOrderMutation((orderNumber: string) => post(`/api/members/me/orders/${orderNumber}/cancel`))

export const useConfirmOrder = () =>
  useOrderMutation((orderNumber: string) => post(`/api/members/me/orders/${orderNumber}/confirm`))
