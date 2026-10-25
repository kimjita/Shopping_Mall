// 회원가입과 비밀번호 변경이 같은 규칙을 쓴다.
export const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,64}$/
export const PASSWORD_RULE_MESSAGE = '비밀번호는 영문과 숫자를 각각 1자 이상 포함한 8~64자여야 합니다.'
export const isValidPassword = (s: string) => PASSWORD_RULE.test(s)
