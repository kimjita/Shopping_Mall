// 해석되는 http(s) 주소만 다룬다. 깨진 주소는 예외 없이 false다.
function parseHttp(url: string): URL | null {
  if (!/^https?:\/\//.test(url)) return null
  try {
    return new URL(url)
  } catch {
    return null
  }
}

export function isExternalLink(url: string): boolean {
  const parsed = parseHttp(url)
  return parsed !== null && parsed.origin !== window.location.origin
}

// 공백·제어 문자가 없고(브라우저가 탭·줄바꿈을 지워 `//`가 되는 것을 막는다), 해석되는 http(s) 주소이거나 단일 슬래시 경로(`//`·`/\`는 프로토콜 상대 주소라 제외)만 링크로 그린다.
export function isSafeLink(url: string): boolean {
  if ([...url].some((c) => c <= ' ' || c === '\x7F')) return false
  return parseHttp(url) !== null || /^\/(?![/\\])/.test(url)
}
