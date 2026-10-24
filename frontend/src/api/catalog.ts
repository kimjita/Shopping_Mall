import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import { toSearchParams, type ListParams } from '../lib/listParams'
import type { Brand, Category, CursorPage, ProductSummary } from './types'

const TEN_MINUTES = 10 * 60 * 1000

export const useCategories = (enabled = true) =>
  useQuery({
    queryKey: ['categories'],
    queryFn: () => apiFetch<Category[]>('/api/categories', { auth: 'none' }),
    staleTime: TEN_MINUTES,
    enabled,
  })

export const useBrands = () =>
  useQuery({
    queryKey: ['brands'],
    queryFn: () => apiFetch<Brand[]>('/api/brands', { auth: 'none' }),
    staleTime: TEN_MINUTES,
  })

export const useProductSearch = (p: ListParams) => {
  const base = toSearchParams(p)
  return useInfiniteQuery({
    queryKey: ['products', base.toString()],
    queryFn: ({ pageParam }) => {
      // 요청은 URL이 아니라 검증을 거친 조건으로만 만든다.
      const sp = new URLSearchParams(base)
      sp.set('size', '20')
      if (pageParam) sp.set('cursor', pageParam)
      return apiFetch<CursorPage<ProductSummary>>(`/api/products?${sp}`)
    },
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
}
