import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import type { CursorPage, OrderSummary } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { OrdersPage } from './OrdersPage'

const summary = (n: number, over: Partial<OrderSummary> = {}): OrderSummary => ({
  orderNumber: `20261025-ORDER00${n}`,
  status: 'PAID',
  orderedAt: '2026-10-25T03:10:30Z',
  firstProductName: `상품${n}`,
  thumbnailUrl: null,
  itemCount: 1,
  paymentAmount: 12000,
  ...over,
})

function setup(pages: CursorPage<OrderSummary>[]) {
  server.use(
    http.get('*/api/members/me/orders', ({ request }) =>
      HttpResponse.json(new URL(request.url).searchParams.get('cursor') ? pages[1] : pages[0]),
    ),
  )
  renderWithProviders(<OrdersPage />, { route: '/mypage/orders', member: {} })
}

it('상태 문구와 첫 상품 외 N건을 보여 준다', async () => {
  setup([{ items: [summary(1, { itemCount: 3, status: 'DELIVERED' })], nextCursor: null }])
  expect(await screen.findByText('상품1 외 2건')).toBeInTheDocument()
  expect(screen.getByText('배송 완료')).toBeInTheDocument()
  expect(screen.getByText('12,000원')).toBeInTheDocument()
  expect(screen.getByText('2026. 10. 25. 12:10')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /상품1/ })).toHaveAttribute('href', '/mypage/orders/20261025-ORDER001')
})

it('다음 페이지를 이어 읽는다', async () => {
  setup([
    { items: [summary(1)], nextCursor: '1' },
    { items: [summary(2, { status: 'CANCELED' })], nextCursor: null },
  ])
  await screen.findByText('상품1')
  await userEvent.click(screen.getByRole('button', { name: '더보기' }))
  expect(await screen.findByText('상품2')).toBeInTheDocument()
  expect(screen.getByText('상품1')).toBeInTheDocument()
  expect(screen.getByText('취소')).toBeInTheDocument()
})

it('다음 페이지만 실패하면 읽은 목록을 두고 짧게 알린다', async () => {
  setup([{ items: [summary(1)], nextCursor: '1' }])
  await screen.findByText('상품1')
  server.use(http.get('*/api/members/me/orders', () => HttpResponse.json({ code: 'INTERNAL', message: '오류', errors: [] }, { status: 500 })))
  await userEvent.click(screen.getByRole('button', { name: '더보기' }))
  expect(await screen.findByText('다음 주문을 불러오지 못했습니다.')).toBeInTheDocument()
  expect(screen.getByText('상품1')).toBeInTheDocument()
})

it('주문이 없으면 안내 문구', async () => {
  setup([{ items: [], nextCursor: null }])
  expect(await screen.findByText('주문 내역이 없습니다.')).toBeInTheDocument()
})
