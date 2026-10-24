import { useState } from 'react'

interface Props {
  src: string | null
  alt: string
  className?: string
}

export function ProductImage({ src, alt, className = '' }: Props) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return <div role="img" aria-label={alt} className={`bg-brand/20 ${className}`} />
  }
  return <img src={src} alt={alt} loading="lazy" onError={() => setFailed(true)} className={`object-cover ${className}`} />
}
