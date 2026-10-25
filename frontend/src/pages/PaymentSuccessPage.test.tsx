import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { StrictMode } from 'react'
import { Route, Routes, useLocation } from 'react-router'
import { expect, it } from 'vitest'
import type { Order } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { PaymentSuccessPage } from './PaymentSuccessPage'

const NO = '20261025-Y1GAX7TR'
const QUERY = `?paymentKey=pk_1&orderId=${NO}&amount=35000`
const order = { orderNumber: NO, status: 'PAID' } as Order

function Probe() {
  const { state } = useLocation()
  return <p>상세 {(state as { notice?: string } | null)?.notice}</p>
}

function setup(query: string, handler: Parameters<typeof http.post>[1]) {
  const bodies: unknown[] = []
  server.use(
    http.post(`*/api/members/me/orders/:no/payment`, async (info) => {
      bodies.push(await info.request.clone().json())
      return handler(info)
    }),
  )
  renderWithProviders(
    <StrictMode>
      <Routes>
        <Route path="/payments/success" element={<PaymentSuccessPage />} />
        <Route path="/mypage/orders/:orderNumber" element={<Probe />} />
      </Routes>
    </StrictMode>,
    { member: {}, route: '/payments/success' + query },
  )
  return bodies
}

it('StrictMode에서도 승인 요청은 한 번이다', async () => {
  const bodies = setup(QUERY, () => HttpResponse.json(order))
  expect(screen.getByText('결제를 확인하고 있습니다.')).toBeInTheDocument()
  expect(await screen.findByText('상세 결제가 완료되었습니다.')).toBeInTheDocument()
  expect(bodies).toEqual([{ paymentKey: 'pk_1', amount: 35000 }])
})

it.each([
  ['amount=1.5', `?paymentKey=pk_1&orderId=${NO}&amount=1.5`],
  ['paymentKey 없음', `?orderId=${NO}&amount=35000`],
  ['orderId=../x', '?paymentKey=pk_1&orderId=../x&amount=35000'],
])('쿼리가 잘못되면 요청하지 않는다 (%s)', async (_n, query) => {
  const bodies = setup(query, () => HttpResponse.json(order))
  expect(await screen.findByText('결제 정보가 올바르지 않습니다.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '홈으로' })).toHaveAttribute('href', '/')
  expect(bodies).toHaveLength(0)
})

it('502면 다시 확인 버튼으로 같은 요청을 다시 보낸다', async () => {
  let n = 0
  const bodies = setup(QUERY, () =>
    ++n === 1
      ? HttpResponse.json({ code: 'PAYMENT_GATEWAY_ERROR', message: '결제 승인에 실패했습니다.', errors: [] }, { status: 502 })
      : HttpResponse.json(order),
  )
  expect(await screen.findByText('결제 확인이 지연되고 있습니다. 잠시 후 다시 확인해 주세요.')).toBeInTheDocument()
  expect(screen.getByText('결제 승인에 실패했습니다.')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: '다시 확인' }))
  expect(await screen.findByText('상세 결제가 완료되었습니다.')).toBeInTheDocument()
  expect(bodies).toHaveLength(2)
  expect(bodies[1]).toEqual(bodies[0])
})

it('409면 주문 상세로 간다', async () => {
  setup(QUERY, () =>
    HttpResponse.json({ code: 'ORDER_NOT_PAYABLE', message: '결제할 수 없는 주문입니다.', errors: [] }, { status: 409 }),
  )
  expect(await screen.findByText('상세 결제할 수 없는 주문입니다.')).toBeInTheDocument()
})

it('그 밖의 오류는 메시지와 주문 상세 링크를 보여 준다', async () => {
  setup(QUERY, () =>
    HttpResponse.json({ code: 'PAYMENT_AMOUNT_MISMATCH', message: '결제 금액이 다릅니다.', errors: [] }, { status: 400 }),
  )
  expect(await screen.findByRole('alert')).toHaveTextContent('결제 금액이 다릅니다.')
  await waitFor(() => expect(screen.getByRole('link', { name: '주문 상세로' })).toHaveAttribute('href', `/mypage/orders/${NO}`))
})
