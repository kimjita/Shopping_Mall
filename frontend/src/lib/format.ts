export const formatWon = (n: number) => n.toLocaleString('ko-KR') + '원'

export const formatRating = (avg: number | null) => (avg === null ? '' : (Math.round(avg * 10) / 10).toFixed(1))

const dateTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

// 파싱할 수 없는 값은 던지지 않고 빈 문자열로 둔다.
export const formatDateTime = (iso: string) => {
  const t = Date.parse(iso)
  return Number.isNaN(t) ? '' : dateTime.format(t)
}
