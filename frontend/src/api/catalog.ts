import { useQuery } from '@tanstack/react-query'
import { apiFetch } from './client'
import type { Brand, Category } from './types'

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
