import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { signup } from '../api/member'
import { useAuth } from '../auth/AuthProvider'

const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,64}$/
const PASSWORD_MESSAGE = '비밀번호는 영문과 숫자를 각각 1자 이상 포함한 8~64자여야 합니다.'

const FIELDS = [
  { name: 'email', label: '이메일', type: 'email', autoComplete: 'email' },
  {
    name: 'password',
    label: '비밀번호',
    type: 'password',
    autoComplete: 'new-password',
  },
  { name: 'name', label: '이름', type: 'text', autoComplete: 'name' },
  { name: 'phone', label: '휴대폰 번호', type: 'tel', autoComplete: 'tel' },
] as const

export function SignupPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [pending, setPending] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const v = Object.fromEntries(FIELDS.map((f) => [f.name, String(form.get(f.name)).trim()])) as Record<
      (typeof FIELDS)[number]['name'],
      string
    >
    const local: Record<string, string> = {}
    for (const f of FIELDS) if (!v[f.name]) local[f.name] = '필수 입력입니다.'
    if (v.password && !PASSWORD_RULE.test(v.password)) local.password = PASSWORD_MESSAGE
    setErrors(local)
    if (Object.keys(local).length > 0) return

    setPending(true)
    try {
      await signup(v)
    } catch (err) {
      const apiError = err instanceof ApiError ? err : null
      // 이메일 중복은 필드 오류 없이 message만 오므로 이메일 입력 아래에 붙인다.
      if (apiError?.code === 'DUPLICATE_EMAIL') setErrors({ email: apiError.message })
      else if (apiError && Object.keys(apiError.fieldErrors).length > 0) setErrors(apiError.fieldErrors)
      else setErrors({ form: apiError?.message ?? '잠시 후 다시 시도해 주세요.' })
      setPending(false)
      return
    }
    try {
      await login(v.email, v.password)
      navigate('/', { replace: true })
    } catch {
      navigate('/login', { replace: true }) // 가입은 끝났으므로 직접 로그인하도록 안내한다.
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="mb-6 text-2xl font-bold">회원가입</h1>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {FIELDS.map((f) => (
          <div key={f.name}>
            <label className="block text-sm font-medium">
              {f.label}
              <input
                name={f.name}
                type={f.type}
                autoComplete={f.autoComplete}
                aria-invalid={errors[f.name] ? true : undefined}
                aria-describedby={errors[f.name] ? `${f.name}-error` : undefined}
                className="mt-1 w-full rounded border border-ink/20 px-3 py-2"
              />
            </label>
            {errors[f.name] && (
              <p id={`${f.name}-error`} className="mt-1 text-sm text-sale">
                {errors[f.name]}
              </p>
            )}
          </div>
        ))}
        {errors.form && (
          <p role="alert" className="text-sm text-sale">
            {errors.form}
          </p>
        )}
        <button
          type="submit"
          disabled={pending}
          className="w-full rounded bg-brand py-2 font-semibold text-white disabled:opacity-50"
        >
          가입하기
        </button>
      </form>
      <p className="mt-4 text-center text-sm">
        이미 회원이신가요?{' '}
        <Link to="/login" className="font-medium text-brand-dark underline">
          로그인
        </Link>
      </p>
    </div>
  )
}
