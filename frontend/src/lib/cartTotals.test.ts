import { expect, it } from 'vitest'
import type { CartItem } from '../api/types'
import { selectedTotal } from './cartTotals'

const item = (id: number, lineTotal: number, status: CartItem['status'] = 'AVAILABLE'): CartItem => ({
  id,
  skuId: id,
  skuName: '옵션',
  productId: id,
  productName: `상품${id}`,
  brandName: '브랜드',
  thumbnailUrl: null,
  quantity: 1,
  unitPrice: lineTotal,
  unitSalePrice: lineTotal,
  lineTotal,
  status,
})

it('AVAILABLE이고 선택한 줄만 더한다', () => {
  const items = [item(1, 10000), item(2, 5000), item(3, 3000, 'SOLD_OUT')]
  expect(selectedTotal(items, new Set([1, 3]))).toEqual({ count: 1, amount: 10000 })
})

it('빈 선택이면 0', () => {
  expect(selectedTotal([item(1, 10000)], new Set())).toEqual({ count: 0, amount: 0 })
})
