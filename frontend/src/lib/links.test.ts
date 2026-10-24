import { expect, it } from 'vitest'
import { isExternalLink, isSafeLink } from './links'

it('다른 출처의 http(s) 주소만 외부 링크다', () => {
  expect(isExternalLink('https://example.com/a')).toBe(true)
  expect(isExternalLink('/events/autumn')).toBe(false)
  expect(isExternalLink(window.location.origin + '/x')).toBe(false)
})

it('깨진 절대 주소는 예외 없이 외부 링크가 아니다', () => {
  expect(isExternalLink('http://')).toBe(false)
  expect(isExternalLink('//evil.com')).toBe(false)
})

it('해석되는 http(s) 주소와 단일 슬래시 경로만 안전한 링크다', () => {
  expect(isSafeLink('https://example.com/a')).toBe(true)
  expect(isSafeLink('/events/autumn')).toBe(true)
  expect(isSafeLink('http://')).toBe(false)
  expect(isSafeLink('//evil.com')).toBe(false)
  expect(isSafeLink('/\\evil.com')).toBe(false)
  expect(isSafeLink('javascript:alert(1)')).toBe(false)
  // 브라우저는 탭·줄바꿈을 지우므로 `//evil.com`이 된다.
  expect(isSafeLink('/\t/evil.com')).toBe(false)
  expect(isSafeLink('/\n/evil.com')).toBe(false)
})
