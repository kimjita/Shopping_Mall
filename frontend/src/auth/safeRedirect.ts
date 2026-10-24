// 로그인 뒤 이동 주소가 같은 사이트의 경로일 때만 받아 오픈 리다이렉트를 막는다.
export function safeRedirect(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return '/'
  // eslint-disable-next-line no-control-regex
  return /[\x00-\x20\x7F]/.test(raw) ? '/' : raw
}
