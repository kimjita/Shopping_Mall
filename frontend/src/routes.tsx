import { useRoutes, type RouteObject } from 'react-router'
import { Layout } from './Layout'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SignupPage } from './pages/SignupPage'

// 테스트와 createBrowserRouter가 같은 라우트 정의를 쓰도록 한 파일에서 함께 내보낸다.
// eslint-disable-next-line react-refresh/only-export-components
export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export function AppRoutes() {
  return useRoutes(routes)
}
