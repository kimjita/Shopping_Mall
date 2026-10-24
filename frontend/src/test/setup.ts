import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { resetRefreshForTest } from '../auth/refresh'
import { tokenStore } from '../auth/tokenStore'
import { server } from './server'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  localStorage.clear()
  tokenStore.setToken(null)
  resetRefreshForTest()
})
afterAll(() => server.close())
