export function isExternalLink(url: string): boolean {
  return /^https?:\/\//.test(url) && new URL(url).origin !== window.location.origin
}
