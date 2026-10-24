import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useToggleWish } from '../api/wishlist'
import { loginPath } from '../auth/RequireAuth'
import { useAuth } from '../auth/AuthProvider'

interface Props {
  productId: number
  wished: boolean
  className?: string
}

export function WishButton({ productId, wished, className = '' }: Props) {
  const { status } = useAuth()
  const navigate = useNavigate()
  const { pathname, search } = useLocation()
  const toggle = useToggleWish()
  // 누르는 즉시 반영하는 낙관적 상태다. 없으면 서버에서 온 wished를 따른다.
  const [optimistic, setOptimistic] = useState<boolean | null>(null)
  const on = optimistic ?? wished

  const onClick = () => {
    if (status !== 'member') {
      navigate(loginPath(pathname + search))
      return
    }
    setOptimistic(!on)
    toggle.mutate({ productId, wished: !on }, { onError: () => setOptimistic(null) })
  }

  return (
    <button
      type="button"
      aria-pressed={on}
      aria-label={on ? '찜 취소' : '찜하기'}
      onClick={onClick}
      className={`grid size-9 place-items-center rounded-full bg-white/80 ${className}`}
    >
      <svg viewBox="0 0 24 24" className="size-5" aria-hidden="true" fill={on ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2">
        <path
          className={on ? 'text-sale' : 'text-ink'}
          d="M12 21s-7-4.4-9.3-9A5.4 5.4 0 0 1 12 6a5.4 5.4 0 0 1 9.3 6c-2.3 4.6-9.3 9-9.3 9z"
        />
      </svg>
    </button>
  )
}
