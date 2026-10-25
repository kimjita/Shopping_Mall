import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useCart } from '../api/cart'
import { useAuth } from '../auth/AuthProvider'
import { CategoryDrawer } from './CategoryDrawer'

const linkClass = 'text-sm whitespace-nowrap'

export function Header() {
  const { status, logout } = useAuth()
  const navigate = useNavigate()
  const cart = useCart()
  const [keyword, setKeyword] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const cartCount = cart.data?.items.length
  const member = status === 'member'

  const onSearch = (e: FormEvent) => {
    e.preventDefault()
    const k = keyword.trim()
    if (k.length < 1 || k.length > 50) return
    navigate(`/products?keyword=${encodeURIComponent(k)}`)
  }

  const onLogout = async () => {
    await logout()
    navigate('/')
  }

  return (
    <header className="border-b border-ink/10">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2">
        <Link to="/" aria-label="dewy 홈" className="text-2xl font-bold tracking-tight text-brand-dark">
          dewy
        </Link>
        <form role="search" onSubmit={onSearch} className="order-last w-full sm:order-none sm:w-auto sm:flex-1">
          <input
            type="search"
            aria-label="상품 검색"
            placeholder="상품을 검색해 보세요"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="w-full rounded border border-ink/20 px-3 py-1.5 text-sm"
          />
        </form>
        <nav className="ml-auto flex items-center gap-3 sm:ml-0">
          <button type="button" onClick={() => setDrawerOpen(true)} className={linkClass}>
            카테고리
          </button>
          <Link to="/recent" className={linkClass}>
            최근 본
          </Link>
          {member ? (
            <>
              <Link to="/wishlist" className={linkClass}>
                찜
              </Link>
              <Link to="/mypage" className={linkClass}>
                마이
              </Link>
              <button type="button" onClick={onLogout} className={linkClass}>
                로그아웃
              </button>
            </>
          ) : (
            <Link to="/login" className={linkClass}>
              로그인
            </Link>
          )}
          <Link
            to="/cart"
            aria-label={cartCount === undefined ? '장바구니' : `장바구니 ${cartCount}개`}
            className={`${linkClass} relative`}
          >
            <svg viewBox="0 0 24 24" className="size-6" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 4h2l2.4 11h10.2L20 7H6" />
              <circle cx="9" cy="19" r="1.5" />
              <circle cx="17" cy="19" r="1.5" />
            </svg>
            {!!cartCount && (
              <span aria-hidden="true" className="absolute -right-2 -top-2 rounded-full bg-sale px-1.5 text-xs text-white">
                {cartCount}
              </span>
            )}
          </Link>
        </nav>
      </div>
      {drawerOpen && <CategoryDrawer onClose={() => setDrawerOpen(false)} />}
    </header>
  )
}
