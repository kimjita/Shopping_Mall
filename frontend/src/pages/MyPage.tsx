import { Link } from 'react-router'
import { useAuth } from '../auth/AuthProvider'
import { GRADE_LABEL } from '../lib/labels'

const LINKS = [
  { to: '/mypage/orders', label: '주문 내역' },
  { to: '/mypage/reviews', label: '리뷰' },
  { to: '/mypage/coupons', label: '내 쿠폰' },
  { to: '/coupons', label: '쿠폰 받기' },
  { to: '/mypage/points', label: '적립금' },
  { to: '/mypage/addresses', label: '배송지 관리' },
  { to: '/wishlist', label: '찜' },
  { to: '/recent', label: '최근 본 상품' },
  { to: '/mypage/settings', label: '설정' },
]

export function MyPage() {
  const { me } = useAuth()
  if (!me) return null // RequireAuth 안에서만 쓰므로 me가 없는 순간은 전환 중이다.
  return (
    <section className="mx-auto max-w-2xl">
      <h1 className="text-xl font-bold">{me.name}님</h1>
      <p className="mt-1 text-sm text-ink/70">{GRADE_LABEL[me.grade]} 등급</p>
      <ul className="mt-6 divide-y divide-ink/10 border-y border-ink/10">
        {LINKS.map((l) => (
          <li key={l.to}>
            <Link to={l.to} className="block py-3">
              {l.label}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
