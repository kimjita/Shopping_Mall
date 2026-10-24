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

// jsdom에는 <dialog>의 showModal·close가 없어 open 속성만 바꾸는 최소 대체를 둔다.
HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
  this.setAttribute('open', '')
}
HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
  this.removeAttribute('open')
  this.dispatchEvent(new Event('close'))
}
