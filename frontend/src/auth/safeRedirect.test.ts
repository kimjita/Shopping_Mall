import { describe, expect, it } from 'vitest'
import { safeRedirect } from './safeRedirect'

describe('safeRedirect', () => {
  it.each([
    ['/cart', '/cart'],
    ['/products?keyword=토너', '/products?keyword=토너'],
    [null, '/'],
    ['//evil.com', '/'],
    ['/\\evil.com', '/'],
    ['https://evil.com', '/'],
    ['/\tx', '/'],
    ['/a b', '/'],
  ])('%s -> %s', (raw, expected) => {
    expect(safeRedirect(raw)).toBe(expected)
  })
})
