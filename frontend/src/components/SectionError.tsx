interface Props {
  onRetry: () => void
  message?: string
}

export function SectionError({ onRetry, message = '불러오지 못했습니다.' }: Props) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 py-10 text-center">
      <p>{message}</p>
      <button type="button" onClick={onRetry} className="rounded border border-ink/20 px-4 py-2 text-sm">
        다시 시도
      </button>
    </div>
  )
}
