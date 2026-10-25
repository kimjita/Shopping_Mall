import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk'
import type { Order } from '../api/types'
import { orderNameOf } from '../lib/orderName'

export class TossCancelError extends Error {}

export const isTossConfigured = () => !!import.meta.env.VITE_TOSS_CLIENT_KEY

export async function startTossPayment(order: Order, customer: { email: string; name: string }): Promise<void> {
  const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY
  if (!clientKey) throw new Error('결제 설정이 없습니다. 관리자에게 문의해 주세요.')
  try {
    const toss = await loadTossPayments(clientKey)
    await toss.payment({ customerKey: ANONYMOUS }).requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: order.price.paymentAmount },
      orderId: order.orderNumber,
      orderName: orderNameOf(order.items),
      successUrl: location.origin + '/payments/success',
      failUrl: location.origin + '/payments/fail',
      customerEmail: customer.email,
      customerName: customer.name,
    })
  } catch (e) {
    if ((e as { code?: unknown } | null)?.code === 'USER_CANCEL') throw new TossCancelError('결제를 취소했습니다.')
    throw e
  }
}
