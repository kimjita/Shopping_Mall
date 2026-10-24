import { Link, Outlet } from 'react-router'

export function Layout() {
  return (
    <>
      <header className="border-b border-ink/10">
        <div className="mx-auto flex h-14 max-w-6xl items-center px-4">
          <Link to="/" aria-label="dewy 홈" className="text-2xl font-bold tracking-tight text-brand-dark">
            dewy
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </>
  )
}
