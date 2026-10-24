export function Spinner() {
  return (
    <div role="status" className="flex justify-center py-10">
      <div className="size-8 animate-spin rounded-full border-4 border-brand/30 border-t-brand" aria-hidden="true" />
      <span className="sr-only">불러오는 중입니다.</span>
    </div>
  )
}
