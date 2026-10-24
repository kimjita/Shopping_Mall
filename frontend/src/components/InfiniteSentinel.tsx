import { useEffect, useRef } from 'react'

interface Props {
  onReach: () => void
  hasNext: boolean
  loading: boolean
  // 다음 페이지가 실패한 동안에는 자동으로 다시 부르지 않고 버튼으로만 다시 시도한다.
  error?: boolean
}

// 화면에 들어오면 다음 페이지를 읽고, 키보드 사용자를 위해 같은 자리에 "더보기" 버튼을 둔다.
export function InfiniteSentinel({ onReach, hasNext, loading, error = false }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const reach = useRef(onReach)
  useEffect(() => {
    reach.current = onReach
  })

  useEffect(() => {
    const el = ref.current
    // IntersectionObserver가 없는 환경(jsdom)에서는 버튼만 동작한다.
    if (!el || !hasNext || loading || error || typeof IntersectionObserver === 'undefined') return
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) reach.current()
    })
    io.observe(el)
    return () => io.disconnect()
  }, [hasNext, loading, error])

  if (!hasNext) return null
  return (
    <div ref={ref} className="flex flex-col items-center gap-2 py-6">
      {error && (
        <p role="alert" className="text-sm">
          다음 상품을 불러오지 못했습니다.
        </p>
      )}
      <button
        type="button"
        onClick={onReach}
        disabled={loading}
        className="rounded border border-ink/20 px-6 py-2 text-sm disabled:opacity-50"
      >
        더보기
      </button>
    </div>
  )
}
