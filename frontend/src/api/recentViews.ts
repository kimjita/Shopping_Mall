import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useAuth } from '../auth/AuthProvider'
import { guestRecentViews } from '../lib/guestRecentViews'
import { productQuery } from './catalog'
import { ApiError, apiFetch } from './client'
import type { ProductDetail, ProductSummary, RecentView } from './types'

// 상세가 로드된 뒤 상품 번호마다 한 번만 기록한다. StrictMode 재실행과 재조회에도 중복되지 않는다.
export function useRecordRecentView(productId: number, loaded: boolean) {
  const { status } = useAuth()
  const done = useRef<number | null>(null)
  useEffect(() => {
    if (!loaded || status === 'loading' || done.current === productId) return
    done.current = productId
    if (status === 'member') {
      apiFetch<void>('/api/members/me/recent-views', { method: 'POST', auth: 'required', json: { productId } }).catch(() => {})
    } else {
      guestRecentViews.record(productId)
    }
  }, [loaded, status, productId])
}

export const useRecentViews = () =>
  useQuery({
    queryKey: ['recentViews'],
    queryFn: () => apiFetch<RecentView[]>('/api/members/me/recent-views', { auth: 'required' }),
  })

function useRecentDelete<V>(path: (v: V) => string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (v: V) => apiFetch<void>(path(v), { method: 'DELETE', auth: 'required' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['recentViews'] }),
  })
}

export const useRemoveRecentView = () => useRecentDelete<number>((id) => `/api/members/me/recent-views/${id}`)
export const useClearRecentViews = () => useRecentDelete<void>(() => '/api/members/me/recent-views')

export function toSummaryFromDetail(d: ProductDetail): ProductSummary {
  return {
    id: d.id,
    name: d.name,
    brandName: d.brand.name,
    thumbnailUrl: d.imageUrls[0] ?? null,
    price: Math.min(...d.skus.map((s) => s.price)),
    salePrice: Math.min(...d.skus.map((s) => s.salePrice)),
    saleRate: d.saleRate,
    soldOut: d.soldOut,
    ratingAvg: d.ratingAvg,
    reviewCount: d.reviewCount,
  }
}

// 비회원 목록: 상세를 읽어 카드로 바꾼다. 404는 저장소에서 지우고, 다른 오류는 그 카드만 뺀다.
export function useGuestRecentProducts(ids: number[]) {
  const results = useQueries({ queries: ids.map(productQuery) })
  const gone = ids.filter((_, i) => results[i].error instanceof ApiError && results[i].error.status === 404)
  const goneKey = gone.join(',')
  useEffect(() => {
    if (goneKey) goneKey.split(',').forEach((id) => guestRecentViews.remove(Number(id)))
  }, [goneKey])
  return {
    isPending: results.some((r) => r.isPending),
    products: results.flatMap((r) => (r.data ? [toSummaryFromDetail(r.data)] : [])),
  }
}
