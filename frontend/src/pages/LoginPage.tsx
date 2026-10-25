import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import { safeRedirect } from '../auth/safeRedirect'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const notice = (useLocation().state as { notice?: string } | null)?.notice
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setPending(true)
    setError(null)
    try {
      await login(String(form.get('email')), String(form.get('password')))
      navigate(safeRedirect(params.get('redirect')), { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '잠시 후 다시 시도해 주세요.')
      setPending(false)
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="mb-6 text-2xl font-bold">로그인</h1>
      {notice && (
        <p role="status" className="mb-4 text-sm text-brand-dark">
          {notice}
        </p>
      )}
      <form onSubmit={onSubmit} className="space-y-4">
        <label className="block text-sm font-medium">
          이메일
          <input
            name="email"
            type="email"
            required
            autoComplete="email"
            className="mt-1 w-full rounded border border-ink/20 px-3 py-2"
          />
        </label>
        <label className="block text-sm font-medium">
          비밀번호
          <input
            name="password"
            type="password"
            required
            autoComplete="current-password"
            className="mt-1 w-full rounded border border-ink/20 px-3 py-2"
          />
        </label>
        {error && (
          <p role="alert" className="text-sm text-sale">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={pending}
          className="w-full rounded bg-brand py-2 font-semibold text-white disabled:opacity-50"
        >
          로그인
        </button>
      </form>
      <p className="mt-4 text-center text-sm">
        아직 회원이 아니신가요?{' '}
        <Link to="/signup" className="font-medium text-brand-dark underline">
          회원가입
        </Link>
      </p>
    </div>
  )
}
