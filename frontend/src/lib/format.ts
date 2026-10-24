export const formatWon = (n: number) => n.toLocaleString('ko-KR') + '원'

export const formatRating = (avg: number | null) => (avg === null ? '' : (Math.round(avg * 10) / 10).toFixed(1))
