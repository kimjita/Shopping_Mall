import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { CursorPage, MyReview, WritableReview } from './types'

const page = <T>(path: string) => ({
  queryFn: ({ pageParam }: { pageParam: string }) =>
    apiFetch<CursorPage<T>>(`${path}?size=20${pageParam ? `&cursor=${encodeURIComponent(pageParam)}` : ''}`, {
      auth: 'required',
    }),
  initialPageParam: '',
  getNextPageParam: (last: CursorPage<T>) => last.nextCursor ?? undefined,
})

export const useWritableReviews = () =>
  useInfiniteQuery({
    queryKey: ['writableReviews'],
    ...page<WritableReview>('/api/members/me/reviews/writable'),
  })

export const useMyReviews = () =>
  useInfiniteQuery({
    queryKey: ['myReviews'],
    ...page<MyReview>('/api/members/me/reviews'),
  })

// 내 리뷰·작성 가능 목록·상품 리뷰 목록·상품 평점이 모두 바뀐다.
function useInvalidateReviews() {
  const queryClient = useQueryClient()
  return (productId: number) =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['myReviews'] }),
      queryClient.invalidateQueries({ queryKey: ['writableReviews'] }),
      queryClient.invalidateQueries({ queryKey: ['reviews', productId] }),
      queryClient.invalidateQueries({ queryKey: ['product', productId] }),
    ])
}

export function useWriteReview() {
  const invalidate = useInvalidateReviews()
  return useMutation({
    mutationFn: ({
      body,
    }: {
      productId: number
      body: {
        orderItemId: number
        rating: number
        content: string
        imageUrls: string[]
      }
    }) =>
      apiFetch<MyReview>('/api/members/me/reviews', {
        method: 'POST',
        auth: 'required',
        json: body,
      }),
    onSuccess: (_res, { productId }) => invalidate(productId),
  })
}

export function useEditReview() {
  const invalidate = useInvalidateReviews()
  return useMutation({
    mutationFn: ({
      reviewId,
      body,
    }: {
      reviewId: number
      productId: number
      body: { rating: number; content: string; imageUrls: string[] }
    }) =>
      apiFetch<MyReview>(`/api/members/me/reviews/${reviewId}`, {
        method: 'PUT',
        auth: 'required',
        json: body,
      }),
    onSuccess: (_res, { productId }) => invalidate(productId),
  })
}

export function useDeleteReview() {
  const invalidate = useInvalidateReviews()
  return useMutation({
    mutationFn: ({ reviewId }: { reviewId: number; productId: number }) =>
      apiFetch<void>(`/api/members/me/reviews/${reviewId}`, {
        method: 'DELETE',
        auth: 'required',
      }),
    onSuccess: (_res, { productId }) => invalidate(productId),
  })
}
