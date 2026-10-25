import type { Order } from '../api/types'

export function orderNameOf(items: { productName: string }[]): string {
  const name = items.length <= 1 ? (items[0]?.productName ?? '') : `${items[0].productName} 외 ${items.length - 1}건`
  return name.slice(0, 100) // 토스 orderName 상한
}

// 토스 orderId 규칙. 요청·이동에 쓰기 전에 항상 이 검사를 거친다.
export const isOrderNumber = (s: string) => /^[A-Za-z0-9_=-]{6,64}$/.test(s)

// 서버 만료 스케줄러가 최대 1분 늦으므로 화면에서도 시각으로 판단한다.
export const isPayable = (order: Pick<Order, 'status' | 'expiresAt'>, now: number) =>
  order.status === 'PENDING' && now < Date.parse(order.expiresAt)
