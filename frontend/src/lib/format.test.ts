import { expect, it } from 'vitest'
import { formatDateTime, formatRating, formatWon } from './format'

it('금액을 천 단위 구분과 원으로 표시한다', () => {
  expect(formatWon(12900)).toBe('12,900원')
  expect(formatWon(0)).toBe('0원')
})

it('평점은 소수 첫째 자리, 없으면 빈 문자열이다', () => {
  expect(formatRating(4.55)).toBe('4.6')
  expect(formatRating(null)).toBe('')
})

it('시각은 한국 시간으로 표시하고 잘못된 값은 빈 문자열이다', () => {
  expect(formatDateTime('2026-10-25T03:10:00Z')).toBe('2026. 10. 25. 12:10')
  expect(formatDateTime('nope')).toBe('')
})
