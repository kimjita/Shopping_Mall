import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiFetch } from '../api/client'
import { fetchMe } from '../api/member'
import type { Me, TokenResponse } from '../api/types'
import { authChannel } from './authChannel'
import { refreshAccessToken } from './refresh'
import { tokenStore } from './tokenStore'

interface AuthValue {
  status: 'loading' | 'guest' | 'member'
  me: Me | undefined
  login(email: string, password: string): Promise<void>
  logout(): Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

const MEMBER_QUERY_KEYS = [
  'me',
  'cart',
  'wishIds',
  'wishlist',
  'recentViews',
  'addresses',
  'myCoupons',
  'downloadableCoupons',
  'orders',
  'order',
  'checkoutPreview',
]

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider 안에서만 useAuth를 쓸 수 있습니다.')
  return value
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState(tokenStore.getToken())
  const [booted, setBooted] = useState(false)

  useEffect(() => {
    let prev = tokenStore.getToken()
    const unsubscribe = tokenStore.subscribe((t) => {
      setToken(t)
      // 요청 중 refresh가 401이면 토큰만 사라지므로 이전 회원의 캐시를 여기서 버린다. 비회원 부팅의 null은 건드리지 않는다.
      if (prev !== null && t === null) MEMBER_QUERY_KEYS.forEach((key) => queryClient.removeQueries({ queryKey: [key] }))
      prev = t
    })
    // StrictMode의 두 번째 실행은 refresh.ts의 inFlight가 같은 요청으로 묶는다.
    refreshAccessToken()
      .catch(() => {}) // 네트워크·5xx면 토큰 없이 비회원으로 둔다.
      .finally(() => setBooted(true))
    const offMessage = authChannel.onMessage((msg) => {
      if (msg.type === 'logout') {
        tokenStore.setToken(null)
        queryClient.clear()
      } else {
        queryClient.clear() // 이 탭이 들고 있던 이전 계정의 캐시를 버린다.
        refreshAccessToken().catch(() => {})
      }
    })
    return () => {
      unsubscribe()
      offMessage()
    }
  }, [queryClient])

  const meQuery = useQuery({
    queryKey: ['me'],
    queryFn: fetchMe,
    enabled: token !== null,
  })

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await apiFetch<TokenResponse>('/api/auth/login', {
        method: 'POST',
        auth: 'none',
        json: { email, password },
      })
      tokenStore.setToken(res.accessToken)
      queryClient.clear()
      authChannel.broadcast({ type: 'login' })
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    await apiFetch<void>('/api/auth/logout', {
      method: 'POST',
      auth: 'none',
    }).catch(() => {}) // 서버 실패와 무관하게 이 기기에서는 로그아웃한다.
    tokenStore.setToken(null)
    queryClient.clear()
    authChannel.broadcast({ type: 'logout' })
  }, [queryClient])

  const status: AuthValue['status'] = !booted
    ? 'loading'
    : token === null
      ? 'guest'
      : meQuery.data
        ? 'member'
        : meQuery.isError
          ? 'guest'
          : 'loading'

  const me = status === 'member' ? meQuery.data : undefined
  const value = useMemo(() => ({ status, me, login, logout }), [status, me, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
