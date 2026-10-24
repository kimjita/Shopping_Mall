export interface AuthMessage {
  type: 'login' | 'logout'
}

// BroadcastChannel이 없는 환경에서는 아무것도 하지 않는다.
let channel: BroadcastChannel | null | undefined
const get = () => (channel ??= typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('dewy-auth'))

export const authChannel = {
  broadcast(msg: AuthMessage) {
    get()?.postMessage(msg)
  },
  onMessage(fn: (msg: AuthMessage) => void) {
    const ch = get()
    if (!ch) return () => {}
    const handler = (e: MessageEvent<AuthMessage>) => fn(e.data)
    ch.addEventListener('message', handler)
    return () => ch.removeEventListener('message', handler)
  },
}
