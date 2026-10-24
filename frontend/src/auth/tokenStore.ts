let token: string | null = null
const listeners = new Set<(t: string | null) => void>()

export const tokenStore = {
  getToken: () => token,
  setToken(t: string | null) {
    token = t
    listeners.forEach((fn) => fn(t))
  },
  subscribe(fn: (t: string | null) => void) {
    listeners.add(fn)
    return () => {
      listeners.delete(fn)
    }
  },
}
