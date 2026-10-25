import type { PriceBreakdown } from '../api/types'

const MAX_ITEMS = 50

// 콤마로 나눈 값이 모두 양의 정수이고 중복 없이 1~50개일 때만 배열을 돌려준다.
export function parseCheckoutItems(raw: string | null): number[] | null {
  const parts = raw ? raw.split(',') : []
  if (parts.length === 0 || parts.length > MAX_ITEMS || !parts.every((p) => /^[1-9]\d*$/.test(p))) return null
  const ids = parts.map(Number)
  return ids.every(Number.isSafeInteger) && new Set(ids).size === ids.length ? ids : null
}

// 서버 규칙: 결제금액이 100원 이상 남아야 한다.
export const maxUsablePoints = (p: PriceBreakdown, balance: number) =>
  Math.max(0, Math.min(balance, p.itemsSalePrice - p.couponDiscount + p.shippingFee - 100))

// 입력 문자열을 0 이상의 안전한 정수로 바꾼다. 너무 큰 값은 상한으로 두고 서버가 검증한다.
export const toPoints = (s: string) => Math.min(Number.MAX_SAFE_INTEGER, Math.max(0, Math.floor(Number(s)))) || 0
