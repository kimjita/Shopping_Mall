import { expect, it } from 'vitest'
import { isValidPassword } from './password'

it('영문과 숫자를 모두 포함한 8~64자 공백 없는 문자열만 통과한다', () => {
  expect(isValidPassword('abcdefgh')).toBe(false)
  expect(isValidPassword('password1')).toBe(true)
  expect(isValidPassword('pass word1')).toBe(false)
  expect(isValidPassword('a1' + 'x'.repeat(63))).toBe(false)
})
