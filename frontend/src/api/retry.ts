import { ApiError } from './client'

// 4xx는 다시 보내도 같은 결과이므로 재시도하지 않는다. 5xx와 네트워크 오류만 세 번까지 재시도한다.
export const shouldRetry = (failureCount: number, error: unknown) =>
  !(error instanceof ApiError && error.status < 500) && failureCount < 3
