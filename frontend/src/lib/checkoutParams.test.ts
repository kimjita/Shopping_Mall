import { expect, it } from 'vitest'
import type { PriceBreakdown } from '../api/types'
import { maxUsablePoints, parseCheckoutItems } from './checkoutParams'

it('콤마로 나눈 양의 정수 목록을 읽는다', () => {
  expect(parseCheckoutItems('1,2')).toEqual([1, 2])
  expect(parseCheckoutItems(Array.from({ length: 50 }, (_, i) => i + 1).join(','))).toHaveLength(50)
})

it.each([
  ['빈 값', ''],
  ['null', null],
  ['문자', 'abc'],
  ['중복', '1,1'],
  ['음수', '-3'],
  ['소수', '1.5'],
  ['0', '0'],
  ['빈 칸', '1,,2'],
  ['51개', Array.from({ length: 51 }, (_, i) => i + 1).join(',')],
])('잘못된 값은 null이다 (%s)', (_n, raw) => {
  expect(parseCheckoutItems(raw)).toBeNull()
})

const price = (over: Partial<PriceBreakdown>): PriceBreakdown => ({
  itemsPrice: 0,
  saleDiscount: 0,
  itemsSalePrice: 0,
  couponDiscount: 0,
  shippingFee: 0,
  pointsUsed: 0,
  paymentAmount: 0,
  ...over,
})

it('적립금은 결제금액 100원을 남기고 잔액을 넘지 않는다', () => {
  expect(maxUsablePoints(price({ itemsSalePrice: 20000 }), 50000)).toBe(19900)
  expect(maxUsablePoints(price({ itemsSalePrice: 20000 }), 3000)).toBe(3000)
  expect(maxUsablePoints(price({ itemsSalePrice: 100 }), 50000)).toBe(0)
  expect(maxUsablePoints(price({ itemsSalePrice: 15000, couponDiscount: 3000, shippingFee: 2500 }), 50000)).toBe(14400)
})
