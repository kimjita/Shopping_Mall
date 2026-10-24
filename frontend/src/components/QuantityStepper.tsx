import { useState } from 'react'

interface Props {
  value: number
  onChange: (value: number) => void
  min?: number
  max?: number
}

export function QuantityStepper({ value, onChange, min = 1, max = 99 }: Props) {
  // 입력 중에는 글자 그대로 보여 주고(빈 칸 허용), 포커스를 잃으면 확정된 값으로 되돌린다.
  const [draft, setDraft] = useState<string | null>(null)
  const set = (n: number) => {
    setDraft(null)
    onChange(Math.min(max, Math.max(min, Math.trunc(n))))
  }
  const btn = 'grid size-9 place-items-center rounded border border-ink/20 disabled:opacity-40'
  return (
    <div className="flex items-center gap-1">
      <button type="button" aria-label="수량 줄이기" disabled={value <= min} onClick={() => set(value - 1)} className={btn}>
        −
      </button>
      <input
        type="number"
        aria-label="수량"
        value={draft ?? value}
        min={min}
        max={max}
        onChange={(e) => {
          setDraft(e.target.value)
          if (!Number.isNaN(e.target.valueAsNumber)) onChange(Math.min(max, Math.max(min, Math.trunc(e.target.valueAsNumber))))
        }}
        onBlur={() => setDraft(null)}
        className="h-9 w-14 rounded border border-ink/20 text-center"
      />
      <button type="button" aria-label="수량 늘리기" disabled={value >= max} onClick={() => set(value + 1)} className={btn}>
        +
      </button>
    </div>
  )
}
