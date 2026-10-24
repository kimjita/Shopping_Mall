import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { Banner, CursorPage, ProductSummary, RankingItem, Recommendations, SkinType } from './types'

export const useBanners = () => useQuery({ queryKey: ['banners'], queryFn: () => apiFetch<Banner[]>('/api/banners') })

export const useTodaySale = () =>
  useQuery({
    queryKey: ['home', 'sale'],
    queryFn: () => apiFetch<CursorPage<ProductSummary>>('/api/products?onSale=true&size=8'),
  })

export const useNewArrivals = () =>
  useQuery({
    queryKey: ['home', 'newest'],
    queryFn: () => apiFetch<CursorPage<ProductSummary>>('/api/products?sort=NEWEST&size=8'),
  })

export const useRankings = () =>
  useQuery({ queryKey: ['rankings'], queryFn: () => apiFetch<{ items: RankingItem[] }>('/api/rankings') })

export const useRecommendations = (skinType: SkinType | null) =>
  useQuery({
    queryKey: ['recommendations', skinType],
    queryFn: () => apiFetch<Recommendations>(`/api/recommendations?skinType=${skinType}`),
    enabled: skinType !== null,
  })
