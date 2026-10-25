import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it, vi } from 'vitest'
import type { Address } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { AddressesPage } from './AddressesPage'

const addr = (id: number, isDefault = false): Address => ({
  id,
  recipient: `받는이${id}`,
  phone: '01012345678',
  zipCode: '06236',
  address1: `서울시 ${id}번지`,
  address2: '101호',
  isDefault,
})

function setup(list: Address[]) {
  const calls: { method: string; id?: string; body?: unknown }[] = []
  server.use(
    http.get('*/api/members/me/addresses', () => HttpResponse.json(list)),
    http.put('*/api/members/me/addresses/:id', async ({ params, request }) => {
      const body = await request.json()
      calls.push({ method: 'PUT', id: String(params.id), body })
      return HttpResponse.json({ ...list[0], ...(body as object) })
    }),
    http.delete('*/api/members/me/addresses/:id', ({ params }) => {
      calls.push({ method: 'DELETE', id: String(params.id) })
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderWithProviders(<AddressesPage />, { member: {} })
  return calls
}

it('기본 배송지에 기본 배지를 단다', async () => {
  setup([addr(1, true), addr(2)])
  const first = (await screen.findByText('받는이1')).closest('li')!
  expect(within(first).getByText('기본')).toBeInTheDocument()
  expect(within(first).queryByRole('button', { name: '기본으로 지정' })).toBeNull()
  expect(within(screen.getByText('받는이2').closest('li')!).queryByText('기본')).toBeNull()
})

it('기본으로 지정하면 isDefault true로 PUT한다', async () => {
  const calls = setup([addr(1, true), addr(2)])
  const second = (await screen.findByText('받는이2')).closest('li')!
  await userEvent.click(within(second).getByRole('button', { name: '기본으로 지정' }))
  await vi.waitFor(() => expect(calls).toHaveLength(1))
  expect(calls[0]).toEqual({
    method: 'PUT',
    id: '2',
    body: { recipient: '받는이2', phone: '01012345678', zipCode: '06236', address1: '서울시 2번지', address2: '101호', isDefault: true },
  })
})

it('삭제는 확인 뒤 DELETE한다', async () => {
  const calls = setup([addr(1, true), addr(2)])
  const second = (await screen.findByText('받는이2')).closest('li')!
  await userEvent.click(within(second).getByRole('button', { name: '삭제' }))
  expect(calls).toHaveLength(0)
  const dialog = screen.getByRole('dialog', { name: '배송지 삭제' })
  expect(dialog).toHaveTextContent('배송지를 삭제할까요?')
  await userEvent.click(within(dialog).getByRole('button', { name: '삭제' }))
  await vi.waitFor(() => expect(calls).toEqual([{ method: 'DELETE', id: '2' }]))
})

it('10개면 추가 버튼이 막힌다', async () => {
  setup(Array.from({ length: 10 }, (_, i) => addr(i + 1, i === 0)))
  expect(await screen.findByRole('button', { name: '배송지 추가' })).toBeDisabled()
  expect(screen.getByText('배송지는 최대 10개까지 등록할 수 있습니다.')).toBeInTheDocument()
})

it('비면 안내 문구를 보여 준다', async () => {
  setup([])
  expect(await screen.findByText('등록된 배송지가 없습니다.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '배송지 추가' })).toBeEnabled()
})
