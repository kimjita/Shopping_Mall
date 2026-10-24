import { useInfiniteQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { CursorPage, Review, ReviewSort, SkinType } from './types'

export const useProductReviews = (productId: number, sort: ReviewSort, skinType: SkinType | null) =>
  useInfiniteQuery({
    queryKey: ['reviews', productId, sort, skinType],
    queryFn: ({ pageParam }) => {
      const sp = new URLSearchParams({ sort, size: '10' })
      if (skinType) sp.set('skinType', skinType)
      if (pageParam) sp.set('cursor', pageParam)
      return apiFetch<CursorPage<Review>>(`/api/products/${productId}/reviews?${sp}`)
    },
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
