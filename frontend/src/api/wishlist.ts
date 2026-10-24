import { useInfiniteQuery, useMutation, useQueries, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthProvider'
import { apiFetch } from './client'
import type { CursorPage, WishItem } from './types'

const CHUNK = 100 // 서버가 한 번에 받는 productIds 상한이다.

// 비회원·로딩이거나 ids가 비어 있으면 요청 없이 빈 Set을 돌려준다.
export function useWishIds(productIds: number[]): Set<number> {
  const { status } = useAuth()
  const chunks: number[][] = []
  for (let i = 0; i < productIds.length; i += CHUNK) chunks.push(productIds.slice(i, i + CHUNK))
  const results = useQueries({
    queries: chunks.map((ids) => ({
      queryKey: ['wishIds', ...ids],
      queryFn: () =>
        apiFetch<{ productIds: number[] }>(`/api/members/me/wishlist/ids?productIds=${ids.join(',')}`, {
          auth: 'required',
        }),
      enabled: status === 'member',
    })),
  })
  return new Set(results.flatMap((r) => r.data?.productIds ?? []))
}

export function useToggleWish() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ productId, wished }: { productId: number; wished: boolean }) =>
      apiFetch<void>(`/api/members/me/wishlist/${productId}`, {
        method: wished ? 'PUT' : 'DELETE',
        auth: 'required',
      }),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['wishIds'] }),
        queryClient.invalidateQueries({ queryKey: ['wishlist'] }),
      ]),
  })
}

export const useWishlist = () =>
  useInfiniteQuery({
    queryKey: ['wishlist'],
    queryFn: ({ pageParam }) =>
      apiFetch<CursorPage<WishItem>>(`/api/members/me/wishlist?size=20${pageParam ? `&cursor=${encodeURIComponent(pageParam)}` : ''}`, {
        auth: 'required',
      }),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
