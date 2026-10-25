import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useSaveAddress } from '../api/addresses'
import type { Address } from '../api/types'

const FIELDS = [
  { name: 'recipient', label: '받는 사람', type: 'text', autoComplete: 'name' },
  { name: 'phone', label: '휴대폰 번호', type: 'tel', autoComplete: 'tel' },
  { name: 'zipCode', label: '우편번호', type: 'text', autoComplete: 'postal-code' },
  { name: 'address1', label: '기본 주소', type: 'text', autoComplete: 'address-line1' },
  { name: 'address2', label: '상세 주소', type: 'text', autoComplete: 'address-line2' },
] as const

interface Props {
  initial?: Address
  onSaved: (address: Address) => void
  onCancel: () => void
}

export function AddressForm({ initial, onSaved, onCancel }: Props) {
  const save = useSaveAddress()
  const [errors, setErrors] = useState<Record<string, string>>({})

  function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const v = Object.fromEntries(FIELDS.map((f) => [f.name, String(form.get(f.name) ?? '').trim()])) as Record<
      (typeof FIELDS)[number]['name'],
      string
    >
    const local: Record<string, string> = {}
    for (const f of FIELDS) if (f.name !== 'address2' && !v[f.name]) local[f.name] = '필수 입력입니다.'
    if (v.zipCode && !/^\d{5}$/.test(v.zipCode)) local.zipCode = '우편번호는 숫자 5자리입니다.'
    setErrors(local)
    if (Object.keys(local).length > 0) return

    save.mutate(
      { id: initial?.id, input: { ...v, isDefault: form.get('isDefault') === 'on' } },
      {
        onSuccess: (saved) => onSaved(saved),
        onError: (err) => {
          const api = err instanceof ApiError ? err : null
          // 입력이 없는 필드 오류도 놓치지 않도록 message를 함께 보인다.
          setErrors({ ...api?.fieldErrors, form: api?.message ?? '잠시 후 다시 시도해 주세요.' })
        },
      },
    )
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      {FIELDS.map((f) => (
        <div key={f.name}>
          <label className="block text-sm font-medium">
            {f.label}
            <input
              name={f.name}
              type={f.type}
              autoComplete={f.autoComplete}
              defaultValue={initial?.[f.name] ?? ''}
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
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" name="isDefault" defaultChecked={initial?.isDefault} className="size-5 accent-brand" />
        기본 배송지로 지정
      </label>
      {errors.form && (
        <p role="alert" className="text-sm text-sale">
          {errors.form}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="rounded border border-ink/20 px-4 py-2 text-sm">
          취소
        </button>
        <button type="submit" disabled={save.isPending} className="rounded bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">
          저장
        </button>
      </div>
    </form>
  )
}
