import { useEffect, useRef } from 'react'

interface Props {
  onReach: () => void
  hasNext: boolean
  loading: boolean
}

// 화면에 들어오면 다음 페이지를 읽고, 키보드 사용자를 위해 같은 자리에 "더보기" 버튼을 둔다.
export function InfiniteSentinel({ onReach, hasNext, loading }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  const reach = useRef(onReach)
  useEffect(() => {
    reach.current = onReach
  })

  useEffect(() => {
    const el = ref.current
    // IntersectionObserver가 없는 환경(jsdom)에서는 버튼만 동작한다.
    if (!el || !hasNext || loading || typeof IntersectionObserver === 'undefined') return
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) reach.current()
    })
    io.observe(el)
    return () => io.disconnect()
  }, [hasNext, loading])

  if (!hasNext) return null
  return (
    <div ref={ref} className="flex justify-center py-6">
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
