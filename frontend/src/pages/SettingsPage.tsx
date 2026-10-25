import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useChangePassword, useUpdateProfile, useUpdateSkinProfile, useWithdraw } from '../api/members'
import type { Me, SkinConcern, SkinTone, SkinType } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { SKIN_CONCERN_LABEL, SKIN_CONCERNS, SKIN_TONE_LABEL, SKIN_TYPE_LABEL } from '../lib/labels'
import { isValidPassword, PASSWORD_RULE_MESSAGE } from '../lib/password'

const INPUT = 'mt-1 w-full rounded border border-ink/20 px-3 py-2'
const SAVE = 'rounded bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-50'
const apiMessage = (e: unknown) => (e instanceof ApiError ? e.message : '잠시 후 다시 시도해 주세요.')

// 페이지를 떠난 뒤에 로그아웃해야 RequireAuth가 guest 상태로 /login?redirect=로 다시 보내지 않는다.
function useLogoutOnLeave() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const leaving = useRef(false)
  useEffect(
    () => () => {
      if (leaving.current) void logout()
    },
    [logout],
  )
  return (notice: string) => {
    leaving.current = true
    navigate('/login', { replace: true, state: { notice } })
  }
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  const id = `settings-${title.replace(/\s/g, '-')}` // aria-labelledby는 공백으로 id를 나눈다.
  return (
    <section aria-labelledby={id} className="border-t border-ink/10 pt-6">
      <h2 id={id} className="mb-4 text-lg font-bold">
        {title}
      </h2>
      {children}
    </section>
  )
}

function Field({
  name,
  label,
  error,
  ...input
}: { name: string; label: string; error?: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="block text-sm font-medium">
        {label}
        <input
          name={name}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${name}-error` : undefined}
          className={INPUT}
          {...input}
        />
      </label>
      {error && (
        <p id={`${name}-error`} className="mt-1 text-sm text-sale">
          {error}
        </p>
      )}
    </div>
  )
}

function Result({ done, error }: { done?: string | false; error?: string }) {
  return (
    <>
      {done && (
        <p role="status" className="text-sm text-brand-dark">
          {done}
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-sale">
          {error}
        </p>
      )}
    </>
  )
}

function ProfileSection({ me }: { me: Me }) {
  const update = useUpdateProfile()
  const [done, setDone] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    setDone(false)
    setErrors({})
    try {
      await update.mutateAsync({ name: String(form.get('name')).trim(), phone: String(form.get('phone')).trim() })
      setDone(true)
    } catch (err) {
      const fields = err instanceof ApiError ? err.fieldErrors : {}
      setErrors(Object.keys(fields).length > 0 ? fields : { form: apiMessage(err) })
    }
  }

  return (
    <Section title="프로필">
      <form onSubmit={onSubmit} onChange={() => setDone(false)} className="space-y-4">
        <Field name="name" label="이름" autoComplete="name" defaultValue={me.name} error={errors.name} />
        <Field name="phone" label="휴대폰 번호" type="tel" autoComplete="tel" defaultValue={me.phone} error={errors.phone} />
        <Result done={done && '프로필을 저장했습니다.'} error={errors.form} />
        <button type="submit" disabled={update.isPending} className={SAVE}>
          저장
        </button>
      </form>
    </Section>
  )
}

function Choice({ legend, children }: { legend: string; children: ReactNode }) {
  return (
    <div role="radiogroup" aria-label={legend} className="flex flex-wrap gap-x-4 gap-y-2 text-sm">
      {children}
    </div>
  )
}

function SkinSection({ me }: { me: Me }) {
  const update = useUpdateSkinProfile()
  const [skinType, setSkinType] = useState<SkinType | null>(me.skinType)
  const [skinTone, setSkinTone] = useState<SkinTone | null>(me.skinTone)
  const [concerns, setConcerns] = useState<SkinConcern[]>(me.skinConcerns)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string>()

  const radio = <T extends string>(group: string, value: T | null, label: string, current: T | null, set: (v: T | null) => void) => (
    <label key={label} className="flex items-center gap-1">
      <input
        type="radio"
        name={group}
        checked={current === value}
        onChange={() => {
          set(value)
          setDone(false)
        }}
      />
      {label}
    </label>
  )

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setDone(false)
    setError(undefined)
    try {
      // 고민은 클릭 순서와 무관하게 표시 순서로 보낸다.
      await update.mutateAsync({ skinType, skinTone, skinConcerns: SKIN_CONCERNS.filter((c) => concerns.includes(c)) })
      setDone(true)
    } catch (err) {
      setError(apiMessage(err))
    }
  }

  return (
    <Section title="피부 프로필">
      <form onSubmit={onSubmit} className="space-y-4">
        <Choice legend="피부 타입">
          {radio('skinType', null, '선택 안 함', skinType, setSkinType)}
          {(Object.keys(SKIN_TYPE_LABEL) as SkinType[]).map((v) => radio('skinType', v, SKIN_TYPE_LABEL[v], skinType, setSkinType))}
        </Choice>
        <Choice legend="피부 톤">
          {radio('skinTone', null, '선택 안 함', skinTone, setSkinTone)}
          {(Object.keys(SKIN_TONE_LABEL) as SkinTone[]).map((v) => radio('skinTone', v, SKIN_TONE_LABEL[v], skinTone, setSkinTone))}
        </Choice>
        <fieldset>
          <legend className="mb-1 text-sm font-medium">피부 고민</legend>
          <div className="flex flex-wrap gap-x-4 gap-y-2 text-sm">
            {SKIN_CONCERNS.map((c) => (
              <label key={c} className="flex items-center gap-1">
                <input
                  type="checkbox"
                  checked={concerns.includes(c)}
                  onChange={(e) => {
                    setConcerns(e.target.checked ? [...concerns, c] : concerns.filter((x) => x !== c))
                    setDone(false)
                  }}
                />
                {SKIN_CONCERN_LABEL[c]}
              </label>
            ))}
          </div>
        </fieldset>
        <Result done={done && '피부 프로필을 저장했습니다.'} error={error} />
        <button type="submit" disabled={update.isPending} className={SAVE}>
          저장
        </button>
      </form>
    </Section>
  )
}

function PasswordSection() {
  const change = useChangePassword()
  const leave = useLogoutOnLeave()
  const [errors, setErrors] = useState<Record<string, string>>({})

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    // 비밀번호는 입력한 그대로 보낸다.
    const currentPassword = String(form.get('currentPassword'))
    const newPassword = String(form.get('newPassword'))
    const local: Record<string, string> = {}
    if (!currentPassword) local.currentPassword = '필수 입력입니다.'
    if (!isValidPassword(newPassword)) local.newPassword = PASSWORD_RULE_MESSAGE
    else if (newPassword !== String(form.get('confirm'))) local.confirm = '새 비밀번호가 일치하지 않습니다.'
    setErrors(local)
    if (Object.keys(local).length > 0) return
    try {
      await change.mutateAsync({ currentPassword, newPassword })
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) setErrors({ ...err.fieldErrors, form: err.message })
      // 서버가 필드 없이 보내는 PASSWORD_MISMATCH는 현재 비밀번호 입력 아래에 붙인다.
      else if (err instanceof ApiError && err.code === 'PASSWORD_MISMATCH') setErrors({ currentPassword: err.message })
      else setErrors({ form: apiMessage(err) })
      return
    }
    leave('비밀번호를 바꿨습니다. 다시 로그인해 주세요.')
  }

  return (
    <Section title="비밀번호 변경">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <Field name="currentPassword" label="현재 비밀번호" type="password" autoComplete="current-password" error={errors.currentPassword} />
        <Field name="newPassword" label="새 비밀번호" type="password" autoComplete="new-password" error={errors.newPassword} />
        <Field name="confirm" label="새 비밀번호 확인" type="password" autoComplete="new-password" error={errors.confirm} />
        <Result error={errors.form} />
        <button type="submit" disabled={change.isPending || change.isSuccess} className={SAVE}>
          비밀번호 변경
        </button>
      </form>
    </Section>
  )
}

// 열 때마다 입력과 오류를 새로 시작하도록 open일 때만 폼을 그린다.
function WithdrawDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const withdraw = useWithdraw()
  const leave = useLogoutOnLeave()
  const [error, setError] = useState<string>()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(undefined)
    try {
      await withdraw.mutateAsync(String(new FormData(e.currentTarget).get('password')))
    } catch (err) {
      setError(apiMessage(err))
      return
    }
    leave('탈퇴가 완료되었습니다.')
  }

  const close = () => {
    setError(undefined)
    onClose()
  }

  return (
    <dialog
      ref={ref}
      aria-label="회원 탈퇴"
      onCancel={(e) => {
        e.preventDefault()
        close()
      }}
      className="m-auto w-80 max-w-[90vw] rounded p-4 backdrop:bg-ink/40"
    >
      {open && (
        <form onSubmit={onSubmit} className="space-y-4">
          <h2 className="font-bold">회원 탈퇴</h2>
          <Field name="password" label="비밀번호" type="password" autoComplete="current-password" required />
          <Result error={error} />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={close} className="rounded border border-ink/20 px-4 py-2 text-sm">
              취소
            </button>
            <button type="submit" disabled={withdraw.isPending || withdraw.isSuccess} className="rounded bg-sale px-4 py-2 text-sm text-white disabled:opacity-50">
              탈퇴
            </button>
          </div>
        </form>
      )}
    </dialog>
  )
}

export function SettingsPage() {
  const { me } = useAuth()
  const [withdrawing, setWithdrawing] = useState(false)
  if (!me) return null // RequireAuth 안에서만 쓰므로 me가 없는 순간은 전환 중이다.
  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <h1 className="text-xl font-bold">설정</h1>
      <ProfileSection key={`p${me.id}`} me={me} />
      <SkinSection key={`s${me.id}`} me={me} />
      <PasswordSection />
      <Section title="회원 탈퇴">
        <p className="mb-4 text-sm">탈퇴하면 장바구니·찜·최근 본 상품이 지워지고 같은 이메일로 다시 가입할 수 없습니다. 작성한 리뷰는 남습니다.</p>
        <button type="button" onClick={() => setWithdrawing(true)} className="rounded border border-sale px-4 py-2 text-sm text-sale">
          회원 탈퇴
        </button>
      </Section>
      <WithdrawDialog open={withdrawing} onClose={() => setWithdrawing(false)} />
    </div>
  )
}
