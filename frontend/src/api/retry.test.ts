import { expect, it } from 'vitest'
import { ApiError } from './client'
import { shouldRetry } from './retry'

it('4xx는 다시 시도하지 않는다', () => {
  expect(shouldRetry(0, new ApiError(404, 'X', 'x'))).toBe(false)
})

it('5xx와 네트워크 오류는 세 번까지 다시 시도한다', () => {
  expect(shouldRetry(0, new ApiError(503, 'X', 'x'))).toBe(true)
  expect(shouldRetry(3, new ApiError(503, 'X', 'x'))).toBe(false)
  expect(shouldRetry(0, new TypeError('Failed to fetch'))).toBe(true)
})
