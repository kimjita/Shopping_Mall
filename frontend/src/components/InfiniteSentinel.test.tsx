import { render } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { InfiniteSentinel } from './InfiniteSentinel'

// 관찰을 시작하자마자 화면에 들어온 것으로 알린다.
class InViewObserver {
  cb: IntersectionObserverCallback
  constructor(cb: IntersectionObserverCallback) {
    this.cb = cb
  }
  observe() {
    this.cb([{ isIntersecting: true } as IntersectionObserverEntry], this as unknown as IntersectionObserver)
  }
  disconnect() {}
}

afterEach(() => vi.unstubAllGlobals())

it('화면에 들어오면 다음 페이지를 읽는다', () => {
  vi.stubGlobal('IntersectionObserver', InViewObserver)
  const onReach = vi.fn()
  render(<InfiniteSentinel onReach={onReach} hasNext loading={false} />)
  expect(onReach).toHaveBeenCalledTimes(1)
})

it('다음 페이지가 실패한 동안에는 자동으로 다시 읽지 않는다', () => {
  vi.stubGlobal('IntersectionObserver', InViewObserver)
  const onReach = vi.fn()
  render(<InfiniteSentinel onReach={onReach} hasNext loading={false} error />)
  expect(onReach).not.toHaveBeenCalled()
})
