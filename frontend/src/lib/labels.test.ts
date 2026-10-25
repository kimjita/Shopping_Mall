import { expect, it } from 'vitest'
import {
  CANCEL_REASON_LABEL,
  couponDiscountText,
  minOrderText,
  ORDER_STATUS_LABEL,
  signedPoints,
  SKIN_CONCERNS,
  SKIN_TONE_LABEL,
} from './labels'

it('쿠폰 할인 문구는 정액과 정률을 구분한다', () => {
  expect(couponDiscountText({ discountType: 'FIXED', discountValue: 3000, maxDiscount: null })).toBe('3,000원 할인')
  expect(couponDiscountText({ discountType: 'RATE', discountValue: 10, maxDiscount: 5000 })).toBe(
    '10% 할인 (최대 5,000원)',
  )
})

it('최소 주문금액이 0이면 문구를 생략한다', () => {
  expect(minOrderText(15000)).toBe('15,000원 이상 구매 시')
  expect(minOrderText(0)).toBe('')
})

it('주문 상태와 취소 사유 문구를 가진다', () => {
  expect(ORDER_STATUS_LABEL.PREPARING).toBe('상품 준비 중')
  expect(CANCEL_REASON_LABEL.REPLACED).toBe('새 주문으로 대체')
})

it('적립금은 증가에 +, 감소에 -를 붙인다', () => {
  expect(signedPoints('USE', 500)).toBe('-500원')
  expect(signedPoints('RESTORE', 500)).toBe('+500원')
  expect(signedPoints('EARN', 1500)).toBe('+1,500원')
})

it('피부 고민은 13개이고 피부 톤 문구를 가진다', () => {
  expect(SKIN_CONCERNS).toHaveLength(13)
  expect(SKIN_CONCERNS[0]).toBe('BLEMISH')
  expect(SKIN_CONCERNS[12]).toBe('DARK_CIRCLE')
  expect(SKIN_TONE_LABEL.NEUTRAL).toBe('뉴트럴')
})
