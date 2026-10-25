import { Link, useRoutes, type RouteObject } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { Layout } from './Layout'
import { CartPage } from './pages/CartPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { MyPage } from './pages/MyPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProductDetailPage } from './pages/ProductDetailPage'
import { ProductListPage } from './pages/ProductListPage'
import { RecentViewsPage } from './pages/RecentViewsPage'
import { SignupPage } from './pages/SignupPage'
import { WishlistPage } from './pages/WishlistPage'

function RootError() {
  return (
    <div className="py-16 text-center">
      <p>문제가 발생했습니다. 잠시 후 다시 시도해 주세요.</p>
      <Link to="/" className="mt-4 inline-block rounded border border-ink/20 px-4 py-2 text-sm">
        홈으로
      </Link>
    </div>
  )
}

// 테스트와 createBrowserRouter가 같은 라우트 정의를 쓰도록 한 파일에서 함께 내보낸다.
// eslint-disable-next-line react-refresh/only-export-components
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    errorElement: <RootError />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'products', element: <ProductListPage /> },
      { path: 'products/:productId', element: <ProductDetailPage /> },
      { path: 'wishlist', element: <RequireAuth><WishlistPage /></RequireAuth> },
      { path: 'cart', element: <RequireAuth><CartPage /></RequireAuth> },
      { path: 'mypage', element: <RequireAuth><MyPage /></RequireAuth> },
      { path: 'recent', element: <RecentViewsPage /> },
      { path: 'signup', element: <SignupPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export function AppRoutes() {
  return useRoutes(routes)
}
