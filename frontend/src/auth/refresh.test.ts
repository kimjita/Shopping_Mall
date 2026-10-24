import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '../test/server'
import { refreshAccessToken } from './refresh'
import { tokenStore } from './tokenStore'

afterEach(() => vi.unstubAllGlobals())

describe('refreshAccessToken', () => {
  it('navigator.locks가 있으면 dewy-refresh 잠금 안에서 보낸다', async () => {
    const request = vi.fn((_name: string, fn: () => unknown) => fn())
    vi.stubGlobal('navigator', { ...navigator, locks: { request } })
    server.use(
      http.post('*/api/auth/refresh', () =>
        HttpResponse.json({ accessToken: 'new', tokenType: 'Bearer', expiresIn: 900 }),
      ),
    )

    await expect(refreshAccessToken()).resolves.toBe('new')

    expect(request).toHaveBeenCalledTimes(1)
    expect(request.mock.calls[0][0]).toBe('dewy-refresh')
    expect(tokenStore.getToken()).toBe('new')
  })

  it('refresh 5xx는 토큰을 지우지 않고 던진다', async () => {
    tokenStore.setToken('old')
    server.use(http.post('*/api/auth/refresh', () => new HttpResponse('down', { status: 503 })))

    await expect(refreshAccessToken()).rejects.toMatchObject({ status: 503 })

    expect(tokenStore.getToken()).toBe('old')
  })
})
