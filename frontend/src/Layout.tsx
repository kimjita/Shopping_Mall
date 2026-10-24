import { Outlet } from 'react-router'
import { Header } from './components/Header'

export function Layout() {
  return (
    <>
      <Header />
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </>
  )
}
