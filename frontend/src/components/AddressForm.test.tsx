import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it, vi } from 'vitest'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { AddressForm } from './AddressForm'

async function fill(zipCode = '06236') {
  await userEvent.type(screen.getByLabelText('받는 사람'), ' 홍길동 ')
  await userEvent.type(screen.getByLabelText('휴대폰 번호'), '010-1234-5678')
  await userEvent.type(screen.getByLabelText('우편번호'), zipCode)
  await userEvent.type(screen.getByLabelText('기본 주소'), '서울시 강남구 테헤란로 1')
}

it('우편번호가 5자리가 아니면 요청하지 않는다', async () => {
  const posts: unknown[] = []
  server.use(
    http.post('*/api/members/me/addresses', async ({ request }) => {
      posts.push(await request.json())
      return HttpResponse.json({}, { status: 201 })
    }),
  )
  renderWithProviders(<AddressForm onSaved={vi.fn()} onCancel={vi.fn()} />, { member: {} })
  await fill('0623')
  await userEvent.click(screen.getByRole('button', { name: '저장' }))
  expect(await screen.findByText('우편번호는 숫자 5자리입니다.')).toBeInTheDocument()
  expect(posts).toHaveLength(0)
})

it('저장하면 POST 본문을 보내고 onSaved를 부른다', async () => {
  const bodies: unknown[] = []
  const saved = { id: 7, recipient: '홍길동', phone: '01012345678', zipCode: '06236', address1: '서울시 강남구 테헤란로 1', address2: null, isDefault: true }
  server.use(
    http.post('*/api/members/me/addresses', async ({ request }) => {
      bodies.push(await request.json())
      return HttpResponse.json(saved, { status: 201 })
    }),
  )
  const onSaved = vi.fn()
  renderWithProviders(<AddressForm onSaved={onSaved} onCancel={vi.fn()} />, { member: {} })
  await fill()
  await userEvent.click(screen.getByLabelText('기본 배송지로 지정'))
  await userEvent.click(screen.getByRole('button', { name: '저장' }))
  await vi.waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved))
  expect(bodies).toEqual([
    { recipient: '홍길동', phone: '010-1234-5678', zipCode: '06236', address1: '서울시 강남구 테헤란로 1', address2: '', isDefault: true },
  ])
})

it('서버 필드 오류를 입력 아래에 보여 준다', async () => {
  server.use(
    http.post('*/api/members/me/addresses', () =>
      HttpResponse.json(
        { code: 'VALIDATION_FAILED', message: '입력값이 올바르지 않습니다.', errors: [{ field: 'phone', reason: '올바른 휴대폰 번호가 아닙니다.' }] },
        { status: 400 },
      ),
    ),
  )
  renderWithProviders(<AddressForm onSaved={vi.fn()} onCancel={vi.fn()} />, { member: {} })
  await fill()
  await userEvent.click(screen.getByRole('button', { name: '저장' }))
  expect(await screen.findByText('올바른 휴대폰 번호가 아닙니다.')).toBeInTheDocument()
  expect(screen.getByLabelText('휴대폰 번호')).toHaveAttribute('aria-invalid', 'true')
  expect(screen.getByRole('alert')).toHaveTextContent('입력값이 올바르지 않습니다.')
})
