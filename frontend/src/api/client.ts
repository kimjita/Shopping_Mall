import { refreshAccessToken } from '../auth/refresh'
import { tokenStore } from '../auth/tokenStore'
import type { ErrorBody } from './types'

const UNKNOWN_MESSAGE = '잠시 후 다시 시도해 주세요.'

export class ApiError extends Error {
  status: number
  code: string
  fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

async function toApiError(res: Response): Promise<ApiError> {
  const body = (await res.json().catch(() => null)) as Partial<ErrorBody> | null
  if (typeof body?.code !== 'string' || typeof body.message !== 'string') {
    return new ApiError(res.status, 'UNKNOWN', UNKNOWN_MESSAGE)
  }
  const fieldErrors = Object.fromEntries((body.errors ?? []).map((e) => [e.field, e.reason]))
  return new ApiError(res.status, body.code, body.message, fieldErrors)
}

type ApiInit = RequestInit & { auth?: 'none' | 'optional' | 'required'; json?: unknown }

export async function apiFetch<T>(path: string, init: ApiInit = {}): Promise<T> {
  const { auth = 'optional', json, ...rest } = init
  const url = new URL(path, window.location.origin)

  const send = (token: string | null) => {
    const headers = new Headers(rest.headers)
    if (json !== undefined) headers.set('Content-Type', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    return fetch(url, {
      ...rest,
      headers,
      body: json !== undefined ? JSON.stringify(json) : rest.body,
      credentials: 'same-origin',
    })
  }

  const sent = auth === 'none' ? null : tokenStore.getToken()
  let res = await send(sent)

  if (res.status === 401 && auth !== 'none') {
    // 다른 요청이 이미 토큰을 갈아 두었으면 다시 갱신하지 않고 그 토큰으로 재시도한다.
    const current = tokenStore.getToken()
    const fresh = current && current !== sent ? current : await refreshAccessToken()
    if (fresh) res = await send(fresh)
    else if (auth === 'optional') res = await send(null)
  }

  if (!res.ok) throw await toApiError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
