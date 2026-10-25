import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import type { CursorPage, PointHistoryItem } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { PointsPage } from './PointsPage'

const item = (id: number, over: Partial<PointHistoryItem> = {}): PointHistoryItem => ({
  id,
  type: 'EARN',
  amount: 300,
  balanceAfter: 1300,
  reason: null,
  orderId: null,
  createdAt: '2026-10-25T03:10:30Z',
  ...over,
})

function setup(pages: CursorPage<PointHistoryItem>[]) {
  server.use(
    http.get('*/api/members/me/points', () => HttpResponse.json({ balance: 1300 })),
    http.get('*/api/members/me/points/history', ({ request }) =>
      HttpResponse.json(new URL(request.url).searchParams.get('cursor') ? pages[1] : pages[0]),
    ),
  )
  renderWithProviders(<PointsPage />, { route: '/mypage/points', member: {} })
}

it('잔액과 내역을 부호와 함께 보여 준다', async () => {
  setup([
    {
      items: [item(2, { type: 'USE', amount: 500, balanceAfter: 1000, reason: '주문 결제' }), item(1)],
      nextCursor: null,
    },
  ])
  expect(await screen.findByText('보유 적립금')).toBeInTheDocument()
  expect(await screen.findByText('1,300원')).toBeInTheDocument()
  expect(await screen.findByText('-500원')).toBeInTheDocument()
  expect(screen.getByText('+300원')).toBeInTheDocument()
  expect(screen.getByText('주문 사용')).toBeInTheDocument()
  expect(screen.getByText('구매 적립')).toBeInTheDocument()
  expect(screen.getByText('주문 결제')).toBeInTheDocument()
  expect(screen.getByText('잔액 1,000원')).toBeInTheDocument()
  expect(screen.getAllByText('2026. 10. 25. 12:10')).toHaveLength(2)
})

it('다음 내역을 이어 읽는다', async () => {
  setup([
    { items: [item(1)], nextCursor: '1' },
    { items: [item(2, { type: 'ADMIN_GRANT', amount: 700 })], nextCursor: null },
  ])
  await screen.findByText('구매 적립')
  await userEvent.click(screen.getByRole('button', { name: '더보기' }))
  expect(await screen.findByText('관리자 지급')).toBeInTheDocument()
  expect(screen.getByText('구매 적립')).toBeInTheDocument()
})

it('다음 내역만 실패하면 읽은 목록을 두고 짧게 알린다', async () => {
  setup([{ items: [item(1)], nextCursor: '1' }])
  await screen.findByText('구매 적립')
  server.use(http.get('*/api/members/me/points/history', () => HttpResponse.json({ code: 'INTERNAL', message: '오류', errors: [] }, { status: 500 })))
  await userEvent.click(screen.getByRole('button', { name: '더보기' }))
  expect(await screen.findByText('다음 내역을 불러오지 못했습니다.')).toBeInTheDocument()
  expect(screen.getByText('구매 적립')).toBeInTheDocument()
})

it('내역이 없으면 안내', async () => {
  setup([{ items: [], nextCursor: null }])
  expect(await screen.findByText('적립금 내역이 없습니다.')).toBeInTheDocument()
})
