import { screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { renderWithProviders } from '../test/render'
import { PaymentFailPage } from './PaymentFailPage'

it('메시지와 다시 결제하기 링크를 보여 준다', () => {
  renderWithProviders(<PaymentFailPage />, {
    member: {},
    route: '/payments/fail?code=PAY_PROCESS_ABORTED&message=%ED%95%9C%EB%8F%84+%EC%B4%88%EA%B3%BC&orderId=20261025-Y1GAX7TR',
  })
  expect(screen.getByText('한도 초과')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '주문 상세에서 다시 결제하기' })).toHaveAttribute(
    'href',
    '/mypage/orders/20261025-Y1GAX7TR',
  )
})

it('메시지가 없으면 기본 문구를 보여 준다', () => {
  renderWithProviders(<PaymentFailPage />, { member: {}, route: '/payments/fail' })
  expect(screen.getByText('결제가 완료되지 않았습니다.')).toBeInTheDocument()
})

it('orderId가 없으면 주문 내역 링크', () => {
  renderWithProviders(<PaymentFailPage />, { member: {}, route: '/payments/fail?orderId=../x' })
  expect(screen.getByRole('link', { name: '주문 내역 보기' })).toHaveAttribute('href', '/mypage/orders')
  expect(screen.queryByRole('link', { name: '주문 상세에서 다시 결제하기' })).not.toBeInTheDocument()
})
