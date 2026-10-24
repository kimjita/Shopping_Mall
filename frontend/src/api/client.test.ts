import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { tokenStore } from '../auth/tokenStore'
import { server } from '../test/server'
import { ApiError, apiFetch } from './client'

function refreshHandler(token: string | null) {
  const state = { calls: 0 }
  server.use(
    http.post('*/api/auth/refresh', () => {
      state.calls++
      return token
        ? HttpResponse.json({ accessToken: token, tokenType: 'Bearer', expiresIn: 900 })
        : HttpResponse.json({ code: 'INVALID_REFRESH_TOKEN', message: '다시 로그인해 주세요.', errors: [] }, { status: 401 })
    }),
  )
  return state
}

describe('apiFetch', () => {
  it('401이 동시에 세 번 나도 refresh는 한 번만 보낸다', async () => {
    tokenStore.setToken('old')
    const refresh = refreshHandler('new')
    server.use(
      http.get('*/api/members/me', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer new'
          ? HttpResponse.json({ id: 1 })
          : HttpResponse.json({ code: 'UNAUTHORIZED', message: '로그인이 필요합니다.', errors: [] }, { status: 401 }),
      ),
    )

    const results = await Promise.all(
      [1, 2, 3].map(() => apiFetch('/api/members/me', { auth: 'required' })),
    )

    expect(results).toHaveLength(3)
    expect(refresh.calls).toBe(1)
    expect(tokenStore.getToken()).toBe('new')
  })

  it('refresh가 401이면 optional 요청은 토큰 없이 다시 보낸다', async () => {
    tokenStore.setToken('old')
    refreshHandler(null)
    const seen: (string | null)[] = []
    server.use(
      http.get('*/api/banners', ({ request }) => {
        const header = request.headers.get('Authorization')
        seen.push(header)
        return header
          ? HttpResponse.json({ code: 'UNAUTHORIZED', message: '만료되었습니다.', errors: [] }, { status: 401 })
          : HttpResponse.json([])
      }),
    )

    await expect(apiFetch('/api/banners')).resolves.toEqual([])

    expect(seen.at(-1)).toBeNull()
    expect(tokenStore.getToken()).toBeNull()
  })

  it('refresh가 401이면 required 요청은 401 ApiError', async () => {
    tokenStore.setToken('old')
    refreshHandler(null)
    server.use(
      http.get('*/api/members/me', () =>
        HttpResponse.json({ code: 'UNAUTHORIZED', message: '로그인이 필요합니다.', errors: [] }, { status: 401 }),
      ),
    )

    await expect(apiFetch('/api/members/me', { auth: 'required' })).rejects.toMatchObject({ status: 401 })
  })

  it('재시도도 401이면 다시 refresh하지 않는다', async () => {
    tokenStore.setToken('old')
    const refresh = refreshHandler('new')
    server.use(
      http.get('*/api/members/me', () =>
        HttpResponse.json({ code: 'UNAUTHORIZED', message: '로그인이 필요합니다.', errors: [] }, { status: 401 }),
      ),
    )

    await expect(apiFetch('/api/members/me', { auth: 'required' })).rejects.toMatchObject({ status: 401 })

    expect(refresh.calls).toBe(1)
  })

  it('auth none은 401에 refresh하지 않는다', async () => {
    const refresh = refreshHandler('new')
    server.use(
      http.post('*/api/auth/login', () =>
        HttpResponse.json({ code: 'INVALID_CREDENTIALS', message: '이메일 또는 비밀번호가 올바르지 않습니다.', errors: [] }, { status: 401 }),
      ),
    )

    const error = await apiFetch('/api/auth/login', { method: 'POST', json: {}, auth: 'none' }).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'INVALID_CREDENTIALS' })
    expect(refresh.calls).toBe(0)
  })

  it('오류 본문의 errors를 fieldErrors로 바꾼다', async () => {
    server.use(
      http.post('*/api/members', () =>
        HttpResponse.json(
          { code: 'INVALID_INPUT', message: '입력값이 올바르지 않습니다.', errors: [{ field: 'email', reason: '이메일 형식이 아닙니다.' }] },
          { status: 400 },
        ),
      ),
    )

    const error = (await apiFetch('/api/members', { method: 'POST', json: {}, auth: 'none' }).catch((e) => e)) as ApiError

    expect(error.fieldErrors.email).toBe('이메일 형식이 아닙니다.')
  })

  it('JSON이 아닌 오류 본문은 UNKNOWN', async () => {
    server.use(http.get('*/api/banners', () => new HttpResponse('Bad Gateway', { status: 502 })))

    const error = (await apiFetch('/api/banners', { auth: 'none' }).catch((e) => e)) as ApiError

    expect(error).toMatchObject({ status: 502, code: 'UNKNOWN', message: '잠시 후 다시 시도해 주세요.' })
  })
})
