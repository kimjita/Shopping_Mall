import { expect, it } from 'vitest'
import { parseListParams, toSearchParams, type ListParams } from './listParams'

const parse = (q: string) => parseListParams(new URLSearchParams(q))

it('잘못된 값은 버린다', () => {
  expect(parse('sort=FOO&minPrice=abc&maxPrice=-5&categoryId=-1&onSale=yes')).toStrictEqual({
    brandIds: [],
    onSale: false,
    sort: 'NEWEST',
  })
})

it('maxPrice가 minPrice보다 작으면 maxPrice를 버린다', () => {
  expect(parse('minPrice=5000&maxPrice=1000')).toStrictEqual({ brandIds: [], onSale: false, sort: 'NEWEST', minPrice: 5000 })
})

it('brandIds는 콤마와 반복을 합치고 20개로 자른다', () => {
  expect(parse('brandIds=1,2&brandIds=2&brandIds=3,x').brandIds).toEqual([1, 2, 3])
  const many = Array.from({ length: 25 }, (_, i) => i + 1).join(',')
  expect(parse(`brandIds=${many}`).brandIds).toHaveLength(20)
})

it('키워드는 trim하고 51자는 버린다', () => {
  expect(parse('keyword=%20토너%20').keyword).toBe('토너')
  expect(parse(`keyword=${'a'.repeat(50)}`).keyword).toHaveLength(50)
  expect(parse(`keyword=${'a'.repeat(51)}`).keyword).toBeUndefined()
  expect(parse('keyword=%20%20').keyword).toBeUndefined()
  expect(parse('keyword=a%00b').keyword).toBeUndefined()
  expect(parse('keyword=a%7Fb').keyword).toBeUndefined()
})

it('toSearchParams는 기본값을 생략한다', () => {
  expect(toSearchParams({ brandIds: [], onSale: false, sort: 'NEWEST' }).toString()).toBe('')
})

it('toSearchParams는 brandIds를 콤마 하나로 쓴다', () => {
  expect(toSearchParams({ brandIds: [1, 2], onSale: true, sort: 'SALES' }).get('brandIds')).toBe('1,2')
})

it('왕복해도 같다', () => {
  const p: ListParams = {
    keyword: '토너',
    categoryId: 3,
    brandIds: [1, 2],
    minPrice: 0,
    maxPrice: 9000,
    onSale: true,
    sort: 'PRICE_DESC',
  }
  expect(parseListParams(toSearchParams(p))).toStrictEqual(p)
})
