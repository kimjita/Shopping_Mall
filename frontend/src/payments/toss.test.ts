import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { Order } from '../api/types'
import { startTossPayment, TossCancelError } from './toss'

vi.mock('@tosspayments/tosspayments-sdk', () => ({ ANONYMOUS: '@@ANONYMOUS', loadTossPayments: vi.fn() }))

const order = {
  orderNumber: '20261025-Y1GAX7TR',
  items: [{ productName: '토너' }, { productName: '크림' }],
  price: { paymentAmount: 35000 },
} as Order
const customer = { email: 'user@example.com', name: '홍길동' }

const requestPayment = vi.fn()
const payment = vi.fn(() => ({ requestPayment }))

beforeEach(() => {
  vi.stubEnv('VITE_TOSS_CLIENT_KEY', 'test_ck_abc')
  vi.mocked(loadTossPayments).mockResolvedValue({ payment } as never)
  requestPayment.mockReset().mockResolvedValue(undefined)
  payment.mockClear()
})
afterEach(() => vi.unstubAllEnvs())

it('requestPayment에 주문 값을 넘긴다', async () => {
  await startTossPayment(order, customer)
  expect(loadTossPayments).toHaveBeenCalledWith('test_ck_abc')
  expect(payment).toHaveBeenCalledWith({ customerKey: ANONYMOUS })
  expect(requestPayment).toHaveBeenCalledWith({
    method: 'CARD',
    amount: { currency: 'KRW', value: 35000 },
    orderId: '20261025-Y1GAX7TR',
    orderName: '토너 외 1건',
    successUrl: location.origin + '/payments/success',
    failUrl: location.origin + '/payments/fail',
    customerEmail: 'user@example.com',
    customerName: '홍길동',
  })
})

it('USER_CANCEL은 TossCancelError로 바꾼다', async () => {
  requestPayment.mockRejectedValue({ code: 'USER_CANCEL', message: '취소됨' })
  await expect(startTossPayment(order, customer)).rejects.toBeInstanceOf(TossCancelError)
})

it('그 밖의 오류는 메시지를 유지해 다시 던진다', async () => {
  requestPayment.mockRejectedValue({ code: 'INVALID_CARD', message: '카드를 확인해 주세요.' })
  await expect(startTossPayment(order, customer)).rejects.toThrow('카드를 확인해 주세요.')
})

it('키가 없으면 결제 설정 오류', async () => {
  vi.stubEnv('VITE_TOSS_CLIENT_KEY', '')
  await expect(startTossPayment(order, customer)).rejects.toThrow('결제 설정이 없습니다. 관리자에게 문의해 주세요.')
  expect(loadTossPayments).not.toHaveBeenCalled()
})
