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
    const unsubscribe = tokenStore.subscribe(setToken)
    // StrictMode의 두 번째 실행은 refresh.ts의 inFlight가 같은 요청으로 묶는다.
    refreshAccessToken()
      .catch(() => {}) // 네트워크·5xx면 토큰 없이 비회원으로 둔다.
      .finally(() => setBooted(true))
    const offMessage = authChannel.onMessage((msg) => {
      if (msg.type === 'logout') {
        tokenStore.setToken(null)
        queryClient.clear()
      } else {
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

  const value = useMemo(() => ({ status, me: meQuery.data, login, logout }), [status, meQuery.data, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
