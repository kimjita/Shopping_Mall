const KEY = 'dewy.recentViews'
const MAX = 50

const isId = (v: unknown): v is number => Number.isInteger(v) && (v as number) > 0

function read(): number[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter(isId) : []
  } catch {
    return []
  }
}

function write(ids: number[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(ids))
  } catch {
    // 저장소를 못 쓰는 환경에서는 기록을 포기한다.
  }
}

export const guestRecentViews = {
  read,
  record: (productId: number) => write([productId, ...read().filter((id) => id !== productId)].slice(0, MAX)),
  remove: (productId: number) => write(read().filter((id) => id !== productId)),
  clear: () => {
    try {
      localStorage.removeItem(KEY)
    } catch {
      // 저장소를 못 쓰면 지울 것도 없다.
    }
  },
}
