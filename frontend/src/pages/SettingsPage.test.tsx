import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route, Routes, useLocation } from 'react-router'
import { expect, it, vi } from 'vitest'
import { useAuth } from '../auth/AuthProvider'
import { testMe, renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { SettingsPage } from './SettingsPage'

function LoginStub() {
  const { status } = useAuth()
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  return (
    <p>
      login page {status} {notice}
    </p>
  )
}

function setup(member = {}) {
  const calls: { method: string; url: string; body?: unknown }[] = []
  const record = async (method: string, request: Request) => {
    const text = await request.text()
    calls.push({ method, url: new URL(request.url).pathname, body: text ? JSON.parse(text) : undefined })
  }
  server.use(
    http.patch('*/api/members/me', async ({ request }) => {
      await record('PATCH', request.clone())
      return HttpResponse.json({ ...testMe, ...((await request.json()) as object) })
    }),
    http.put('*/api/members/me/skin-profile', async ({ request }) => {
      await record('PUT', request.clone())
      return HttpResponse.json({ ...testMe, ...((await request.json()) as object) })
    }),
    http.post('*/api/auth/logout', async ({ request }) => {
      await record('POST', request)
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderWithProviders(
    <Routes>
      <Route path="/mypage/settings" element={<SettingsPage />} />
      <Route path="/login" element={<LoginStub />} />
    </Routes>,
    { route: '/mypage/settings', member },
  )
  return calls
}

const section = (name: string) => screen.getByRole('region', { name })
const error = (code: string, message: string, status: number) =>
  HttpResponse.json({ code, message, errors: [] }, { status })

it('프로필을 저장하면 PATCH하고 안내한다', async () => {
  const calls = setup()
  const profile = within(await screen.findByRole('region', { name: '프로필' }))
  const name = profile.getByLabelText('이름')
  expect(name).toHaveValue('홍길동')
  await userEvent.clear(name)
  await userEvent.type(name, '김하늘')
  await userEvent.clear(profile.getByLabelText('휴대폰 번호'))
  await userEvent.type(profile.getByLabelText('휴대폰 번호'), '010-9999-8888')
  await userEvent.click(profile.getByRole('button', { name: '저장' }))
  expect(await profile.findByRole('status')).toHaveTextContent('프로필을 저장했습니다.')
  expect(calls.find((c) => c.method === 'PATCH')?.body).toEqual({ name: '김하늘', phone: '010-9999-8888' })
  await userEvent.type(name, '!')
  expect(profile.queryByRole('status')).toBeNull()
})

it('프로필 서버 필드 오류는 입력 아래에 보인다', async () => {
  setup()
  server.use(
    http.patch('*/api/members/me', () =>
      HttpResponse.json(
        { code: 'INVALID_INPUT', message: '입력값이 올바르지 않습니다.', errors: [{ field: 'name', reason: '이름은 1~30자여야 합니다.' }] },
        { status: 400 },
      ),
    ),
  )
  const profile = within(await screen.findByRole('region', { name: '프로필' }))
  await userEvent.click(profile.getByRole('button', { name: '저장' }))
  expect(await profile.findByText('이름은 1~30자여야 합니다.')).toBeInTheDocument()
})

it('피부 프로필은 me 값으로 미리 채운다', async () => {
  setup({ skinType: 'OILY', skinTone: 'WARM', skinConcerns: ['PORE'] })
  const skin = within(await screen.findByRole('region', { name: '피부 프로필' }))
  expect(skin.getByRole('radio', { name: '지성' })).toBeChecked()
  expect(skin.getByRole('radio', { name: '웜톤' })).toBeChecked()
  expect(skin.getByRole('checkbox', { name: '모공' })).toBeChecked()
  expect(skin.getByRole('checkbox', { name: '주름' })).not.toBeChecked()
  expect(skin.getAllByRole('checkbox')).toHaveLength(13)
})

it('피부 프로필은 선택한 값으로 PUT한다', async () => {
  const calls = setup()
  const skin = within(await screen.findByRole('region', { name: '피부 프로필' }))
  expect(skin.getByRole('radiogroup', { name: '피부 타입' })).toBeInTheDocument()
  await userEvent.click(skin.getByRole('radio', { name: '건성' }))
  await userEvent.click(skin.getByRole('checkbox', { name: '모공' }))
  await userEvent.click(skin.getByRole('checkbox', { name: '주름' }))
  await userEvent.click(skin.getByRole('button', { name: '저장' }))
  expect(await skin.findByRole('status')).toHaveTextContent('피부 프로필을 저장했습니다.')
  expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
    skinType: 'DRY',
    skinTone: null,
    skinConcerns: ['WRINKLE', 'PORE'],
  })
})

async function fillPassword(current: string, next: string, confirm: string) {
  const pw = within(await screen.findByRole('region', { name: '비밀번호 변경' }))
  await userEvent.type(pw.getByLabelText('현재 비밀번호'), current)
  await userEvent.type(pw.getByLabelText('새 비밀번호', { selector: 'input' }), next)
  await userEvent.type(pw.getByLabelText('새 비밀번호 확인'), confirm)
  await userEvent.click(pw.getByRole('button', { name: '비밀번호 변경' }))
  return pw
}

it('새 비밀번호 확인이 다르면 요청하지 않는다', async () => {
  const calls = setup()
  const pw = await fillPassword('oldpass1', 'newpass12', 'newpass13')
  expect(pw.getByText('새 비밀번호가 일치하지 않습니다.')).toBeInTheDocument()
  expect(calls).toHaveLength(0)
})

it('비밀번호 규칙을 먼저 검사한다', async () => {
  const calls = setup()
  const pw = await fillPassword('oldpass1', 'short', 'different')
  expect(pw.getByText('비밀번호는 영문과 숫자를 각각 1자 이상 포함한 8~64자여야 합니다.')).toBeInTheDocument()
  expect(pw.queryByText('새 비밀번호가 일치하지 않습니다.')).toBeNull()
  expect(calls).toHaveLength(0)
})

it('비밀번호를 바꾸면 로그아웃하고 로그인 화면에서 안내한다', async () => {
  const calls = setup()
  server.use(
    http.patch('*/api/members/me/password', async ({ request }) => {
      calls.push({ method: 'PATCH', url: '/password', body: await request.json() })
      return new HttpResponse(null, { status: 204 })
    }),
  )
  await fillPassword('oldpass1', 'newpass12', 'newpass12')
  expect(await screen.findByText(/login page guest 비밀번호를 바꿨습니다\. 다시 로그인해 주세요\./)).toBeInTheDocument()
  expect(calls.map((c) => c.url)).toEqual(['/password', '/api/auth/logout'])
  expect(calls[0].body).toEqual({ currentPassword: 'oldpass1', newPassword: 'newpass12' })
})

it('현재 비밀번호가 틀리면 그 입력 아래 메시지', async () => {
  setup()
  server.use(
    http.patch('*/api/members/me/password', () => error('PASSWORD_MISMATCH', '현재 비밀번호가 일치하지 않습니다.', 400)),
  )
  const pw = await fillPassword('wrongpw1', 'newpass12', 'newpass12')
  const msg = await pw.findByText('현재 비밀번호가 일치하지 않습니다.')
  expect(pw.getByLabelText('현재 비밀번호')).toHaveAccessibleDescription('현재 비밀번호가 일치하지 않습니다.')
  expect(msg).toBeInTheDocument()
})

async function openWithdraw(password: string) {
  await userEvent.click(await screen.findByRole('button', { name: '회원 탈퇴' }))
  const dialog = screen.getByRole('dialog', { name: '회원 탈퇴' })
  await userEvent.type(within(dialog).getByLabelText('비밀번호'), password)
  await userEvent.click(within(dialog).getByRole('button', { name: '탈퇴' }))
  return dialog
}

it('진행 중 주문이 있으면 탈퇴 창에 메시지를 보이고 로그인 상태를 유지한다', async () => {
  const calls = setup()
  server.use(
    http.delete('*/api/members/me', () =>
      error('MEMBER_HAS_ACTIVE_ORDER', '진행 중인 주문이 있어 탈퇴할 수 없습니다.', 409),
    ),
  )
  const dialog = await openWithdraw('password1')
  expect(await within(dialog).findByRole('alert')).toHaveTextContent('진행 중인 주문이 있어 탈퇴할 수 없습니다.')
  expect(screen.queryByText(/login page/)).toBeNull()
  expect(calls.some((c) => c.url === '/api/auth/logout')).toBe(false)
})

it('탈퇴하면 로그아웃하고 안내한다', async () => {
  const calls = setup()
  server.use(
    http.delete('*/api/members/me', async ({ request }) => {
      calls.push({ method: 'DELETE', url: '/me', body: await request.json() })
      return new HttpResponse(null, { status: 204 })
    }),
  )
  await openWithdraw('password1')
  expect(await screen.findByText(/login page guest 탈퇴가 완료되었습니다\./)).toBeInTheDocument()
  await vi.waitFor(() => expect(calls.map((c) => c.url)).toEqual(['/me', '/api/auth/logout']))
  expect(calls[0].body).toEqual({ password: 'password1' })
})

it('탈퇴 안내 문구를 보여 준다', async () => {
  setup()
  expect(
    await screen.findByText('탈퇴하면 장바구니·찜·최근 본 상품이 지워지고 같은 이메일로 다시 가입할 수 없습니다. 작성한 리뷰는 남습니다.'),
  ).toBeInTheDocument()
  expect(section('회원 탈퇴')).toBeInTheDocument()
})
