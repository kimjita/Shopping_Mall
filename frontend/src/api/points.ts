import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthProvider'
import { apiFetch } from './client'
import type { CursorPage, PointHistoryItem } from './types'

export const usePointBalance = () => {
  const { status } = useAuth()
  return useQuery({
    queryKey: ['points'],
    queryFn: () =>
      apiFetch<{ balance: number }>('/api/members/me/points', {
        auth: 'required',
      }),
    enabled: status === 'member',
  })
}

export const usePointHistory = () =>
  useInfiniteQuery({
    queryKey: ['pointHistory'],
    queryFn: ({ pageParam }) =>
      apiFetch<CursorPage<PointHistoryItem>>(
        `/api/members/me/points/history?size=20${pageParam ? `&cursor=${encodeURIComponent(pageParam)}` : ''}`,
        { auth: 'required' },
      ),
    initialPageParam: '',
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  })
