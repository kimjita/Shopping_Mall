import { expect, it } from 'vitest'
import { guestRecentViews } from './guestRecentViews'

it('최근 것을 맨 앞에 두고 중복을 없앤다', () => {
  guestRecentViews.record(1)
  guestRecentViews.record(2)
  guestRecentViews.record(1)
  expect(guestRecentViews.read()).toEqual([1, 2])
})

it('50개를 넘으면 오래된 것부터 버린다', () => {
  for (let i = 1; i <= 51; i++) guestRecentViews.record(i)
  const ids = guestRecentViews.read()
  expect(ids).toHaveLength(50)
  expect(ids[0]).toBe(51)
  expect(ids).not.toContain(1)
})

it.each(['{oops', '{"a":1}'])('깨진 값은 빈 목록으로 읽는다: %s', (raw) => {
  localStorage.setItem('dewy.recentViews', raw)
  expect(guestRecentViews.read()).toEqual([])
})

it('숫자가 아닌 원소는 버린다', () => {
  localStorage.setItem('dewy.recentViews', '[3,"x",-1,2.5,4]')
  expect(guestRecentViews.read()).toEqual([3, 4])
})

it('remove와 clear로 지운다', () => {
  guestRecentViews.record(1)
  guestRecentViews.record(2)
  guestRecentViews.remove(1)
  expect(guestRecentViews.read()).toEqual([2])
  guestRecentViews.clear()
  expect(guestRecentViews.read()).toEqual([])
})
