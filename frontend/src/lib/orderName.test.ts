import { expect, it } from 'vitest'
import { isOrderNumber, isPayable, orderNameOf } from './orderName'

it('상품이 하나면 이름, 여럿이면 "외 n건"', () => {
  expect(orderNameOf([{ productName: '토너' }])).toBe('토너')
  expect(orderNameOf([{ productName: '토너' }, { productName: '크림' }, { productName: '앰플' }])).toBe('토너 외 2건')
})

it('100자를 넘으면 100자로 자른다', () => {
  expect(orderNameOf([{ productName: '가'.repeat(150) }])).toHaveLength(100)
  expect(orderNameOf([{ productName: '가'.repeat(150) }, { productName: '나' }])).toHaveLength(100)
})

it('주문번호 형식을 검사한다', () => {
  expect(isOrderNumber('20261025-Y1GAX7TR')).toBe(true)
  expect(isOrderNumber('../x')).toBe(false)
  expect(isOrderNumber('abc')).toBe(false)
})

it('결제 가능 여부는 상태와 만료 시각으로 판단한다', () => {
  const expiresAt = '2026-10-25T03:25:00Z'
  expect(isPayable({ status: 'PENDING', expiresAt }, Date.parse('2026-10-25T03:24:59Z'))).toBe(true)
  expect(isPayable({ status: 'PENDING', expiresAt }, Date.parse('2026-10-25T03:25:00Z'))).toBe(false)
  expect(isPayable({ status: 'PAID', expiresAt }, Date.parse('2026-10-25T03:24:59Z'))).toBe(false)
})
