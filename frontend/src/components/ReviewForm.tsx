import { useState, type FormEvent } from 'react'

interface Props {
  initial?: { rating: number; content: string }
  submitLabel: string
  pending: boolean
  error?: string
  onSubmit: (v: { rating: number; content: string }) => void
  onCancel: () => void
}

export function ReviewForm({ initial, submitLabel, pending, error, onSubmit, onCancel }: Props) {
  const [rating, setRating] = useState(initial?.rating ?? 0)
  const [content, setContent] = useState(initial?.content ?? '')
  const [local, setLocal] = useState<string | null>(null)
  const length = content.trim().length

  function submit(e: FormEvent) {
    e.preventDefault()
    const problem =
      rating === 0
        ? '별점을 골라 주세요.'
        : length < 10
          ? '리뷰는 10자 이상 써 주세요.'
          : length > 1000
            ? '리뷰는 1,000자 이하로 써 주세요.'
            : null
    setLocal(problem)
    // 서버가 앞뒤 공백을 떼므로 원문 그대로 넘긴다.
    if (!problem) onSubmit({ rating, content })
  }

  const shown = local ?? error
  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <div role="radiogroup" aria-label="별점" className="flex gap-3">
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className="flex items-center gap-1 text-sm">
            <input
              type="radio"
              name="rating"
              aria-label={`별점 ${n}점`}
              checked={rating === n}
              onChange={() => setRating(n)}
              className="size-5 accent-brand"
            />
            {`★ ${n}`}
          </label>
        ))}
      </div>
      <div>
        <textarea
          aria-label="리뷰 내용"
          rows={6}
          maxLength={2000}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          className="w-full rounded border border-ink/20 px-3 py-2"
        />
        <p className="text-right text-xs text-ink/60">{`${length}/1,000자`}</p>
      </div>
      {shown && (
        <p role="alert" className="text-sm text-sale">
          {shown}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="rounded border border-ink/20 px-4 py-2 text-sm">
          취소
        </button>
        <button type="submit" disabled={pending} className="rounded bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">
          {submitLabel}
        </button>
      </div>
    </form>
  )
}
