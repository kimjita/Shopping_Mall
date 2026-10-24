import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders, testMe } from '../test/render'
import { server } from '../test/server'
import { SignupPage } from './SignupPage'

function renderSignup() {
  return renderWithProviders(
    <Routes>
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/" element={<p>home page</p>} />
    </Routes>,
    { route: '/signup' },
  )
}

async function fill(password = 'password1') {
  await userEvent.type(await screen.findByLabelText('이메일'), 'new@example.com')
  await userEvent.type(screen.getByLabelText('비밀번호'), password)
  await userEvent.type(screen.getByLabelText('이름'), '홍길동')
  await userEvent.type(screen.getByLabelText('휴대폰 번호'), '010-1234-5678')
  await userEvent.click(screen.getByRole('button', { name: '가입하기' }))
}

describe('SignupPage', () => {
  it('가입 성공 시 자동 로그인 후 홈으로 간다', async () => {
    const signupBody = vi.fn()
    server.use(
      http.post('*/api/auth/signup', async ({ request }) => {
        signupBody(await request.json())
        return HttpResponse.json({ memberId: 1 }, { status: 201 })
      }),
      http.post('*/api/auth/login', () =>
        HttpResponse.json({
          accessToken: 'tok',
          tokenType: 'Bearer',
          expiresIn: 1800,
        }),
      ),
      http.get('*/api/members/me', () => HttpResponse.json(testMe)),
    )
    renderSignup()
    await fill()
    expect(await screen.findByText('home page')).toBeInTheDocument()
    expect(signupBody).toHaveBeenCalledWith({
      email: 'new@example.com',
      password: 'password1',
      name: '홍길동',
      phone: '010-1234-5678',
    })
  })

  it('서버 필드 오류를 입력 아래에 보여 준다', async () => {
    server.use(
      http.post('*/api/auth/signup', () =>
        HttpResponse.json(
          {
            code: 'DUPLICATE_EMAIL',
            message: '이미 가입된 이메일입니다.',
            errors: [],
          },
          { status: 409 },
        ),
      ),
    )
    renderSignup()
    await fill()
    expect(await screen.findByText('이미 가입된 이메일입니다.')).toBeInTheDocument()
    expect(screen.getByLabelText('이메일')).toHaveAccessibleDescription('이미 가입된 이메일입니다.')

    server.use(
      http.post('*/api/auth/signup', () =>
        HttpResponse.json(
          {
            code: 'INVALID_INPUT',
            message: '입력값이 올바르지 않습니다.',
            errors: [{ field: 'phone', reason: '휴대폰 번호 형식이 아닙니다.' }],
          },
          { status: 400 },
        ),
      ),
    )
    await userEvent.click(screen.getByRole('button', { name: '가입하기' }))
    expect(await screen.findByText('휴대폰 번호 형식이 아닙니다.')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('입력값이 올바르지 않습니다.')
    expect(screen.getByLabelText('휴대폰 번호')).toHaveAccessibleDescription('휴대폰 번호 형식이 아닙니다.')
  })

  it('비밀번호 규칙을 먼저 검사한다', async () => {
    const called = vi.fn()
    server.use(
      http.post('*/api/auth/signup', () => {
        called()
        return HttpResponse.json({ memberId: 1 }, { status: 201 })
      }),
    )
    renderSignup()
    await fill('abcdefgh')
    expect(
      await screen.findByText('비밀번호는 영문과 숫자를 각각 1자 이상 포함한 8~64자여야 합니다.'),
    ).toBeInTheDocument()
    expect(called).not.toHaveBeenCalled()
  })

  it('비밀번호는 다듬지 않고 그대로 검사한다', async () => {
    const called = vi.fn()
    server.use(
      http.post('*/api/auth/signup', () => {
        called()
        return HttpResponse.json({ memberId: 1 }, { status: 201 })
      }),
    )
    renderSignup()
    await fill('password1 ')
    expect(
      await screen.findByText('비밀번호는 영문과 숫자를 각각 1자 이상 포함한 8~64자여야 합니다.'),
    ).toBeInTheDocument()
    expect(called).not.toHaveBeenCalled()
  })
})
