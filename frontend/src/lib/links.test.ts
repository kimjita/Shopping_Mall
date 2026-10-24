import { expect, it } from 'vitest'
import { isExternalLink } from './links'

it('다른 출처의 http(s) 주소만 외부 링크다', () => {
  expect(isExternalLink('https://example.com/a')).toBe(true)
  expect(isExternalLink('/events/autumn')).toBe(false)
  expect(isExternalLink(window.location.origin + '/x')).toBe(false)
})
