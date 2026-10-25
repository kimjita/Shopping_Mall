import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { testMe } from '../test/render'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { LoginPage } from './LoginPage'

function renderLogin(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cart" element={<p>cart page</p>} />
      <Route path="/" element={<p>home page</p>} />
    </Routes>,
    { route },
  )
}

async function submit() {
  await userEvent.type(await screen.findByLabelText('이메일'), 'user@example.com')
  await userEvent.type(screen.getByLabelText('비밀번호'), 'password1')
  await userEvent.click(screen.getByRole('button', { name: '로그인' }))
}

function allowLogin() {
  server.use(
    http.post('*/api/auth/login', () =>
      HttpResponse.json({
        accessToken: 'tok',
        tokenType: 'Bearer',
        expiresIn: 1800,
      }),
    ),
    http.get('*/api/members/me', () => HttpResponse.json(testMe)),
  )
}

describe('LoginPage', () => {
  it('로그인 성공 시 redirect 경로로 간다', async () => {
    allowLogin()
    renderLogin('/login?redirect=%2Fcart')
    await submit()
    expect(await screen.findByText('cart page')).toBeInTheDocument()
  })

  it('redirect가 외부 주소면 홈으로 간다', async () => {
    allowLogin()
    renderLogin('/login?redirect=%2F%2Fevil.com')
    await submit()
    expect(await screen.findByText('home page')).toBeInTheDocument()
  })

  it('401이면 서버 메시지를 보여 준다', async () => {
    server.use(
      http.post('*/api/auth/login', () =>
        HttpResponse.json(
          {
            code: 'INVALID_CREDENTIALS',
            message: '이메일 또는 비밀번호가 올바르지 않습니다.',
            errors: [],
          },
          { status: 401 },
        ),
      ),
    )
    renderLogin('/login')
    await submit()
    expect(await screen.findByRole('alert')).toHaveTextContent('이메일 또는 비밀번호가 올바르지 않습니다.')
  })

  it('state의 notice를 보여 준다', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/login" element={<LoginPage />} />
      </Routes>,
      { route: { pathname: '/login', state: { notice: '탈퇴가 완료되었습니다.' } } },
    )
    expect(await screen.findByRole('status')).toHaveTextContent('탈퇴가 완료되었습니다.')
  })
})
