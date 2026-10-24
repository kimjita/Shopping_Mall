import { useEffect, useRef } from 'react'
import { useAuth } from '../auth/AuthProvider'
import { guestRecentViews } from '../lib/guestRecentViews'
import { apiFetch } from './client'

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
