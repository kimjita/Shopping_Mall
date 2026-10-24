import { ApiError, apiFetch } from '../api/client'
import type { TokenResponse } from '../api/types'
import { tokenStore } from './tokenStore'

let inFlight: Promise<string | null> | null = null

// 다른 탭과 같은 refresh 쿠키를 동시에 보내면 서버가 재사용으로 보고 패밀리를 폐기하므로 탭 사이에서도 줄을 세운다.
const withCrossTabLock = <T>(fn: () => Promise<T>): Promise<T> =>
  navigator.locks ? navigator.locks.request('dewy-refresh', fn) : fn()

async function doRefresh(): Promise<string | null> {
  try {
    const { accessToken } = await apiFetch<TokenResponse>('/api/auth/refresh', { method: 'POST', auth: 'none' })
    tokenStore.setToken(accessToken)
    return accessToken
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      tokenStore.setToken(null)
      return null
    }
    throw e // 네트워크·5xx는 로그아웃시키지 않는다.
  }
}

export function refreshAccessToken(): Promise<string | null> {
  // 같은 탭의 동시 호출(StrictMode 포함)은 진행 중인 Promise 하나를 같이 기다린다.
  inFlight ??= withCrossTabLock(doRefresh).finally(() => {
    inFlight = null
  })
  return inFlight
}

export function resetRefreshForTest(): void {
  inFlight = null
}
