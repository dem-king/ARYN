function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

function normalizePath(path: string) {
  return `/${path.replace(/^\/+/, '')}`
}

export function selectApiBaseUrl(
  configuredBaseUrl: string | undefined,
  h5DevelopmentBaseUrl: string | undefined,
  useH5DevelopmentBaseUrl: boolean,
) {
  const selected
    = useH5DevelopmentBaseUrl && h5DevelopmentBaseUrl
      ? h5DevelopmentBaseUrl
      : configuredBaseUrl
  return trimTrailingSlash(String(selected || '').trim())
}

let useH5DevelopmentBaseUrl = false
// #ifdef H5
useH5DevelopmentBaseUrl = import.meta.env.DEV
// #endif

export const apiBaseUrl = selectApiBaseUrl(
  import.meta.env.VITE_API_BASE_URL,
  import.meta.env.VITE_H5_API_BASE_URL,
  useH5DevelopmentBaseUrl,
)

export function buildApiUrl(path: string, baseUrl = apiBaseUrl) {
  const normalizedBaseUrl = trimTrailingSlash(baseUrl)
  const normalizedPath = normalizePath(path)
  return normalizedBaseUrl
    ? `${normalizedBaseUrl}${normalizedPath}`
    : normalizedPath
}

export function getPageOrigin() {
  // #ifdef H5
  return window.location.origin
  // #endif
  return undefined
}

export function buildWebSocketUrl(
  path: string,
  pageOrigin?: string,
  baseUrl = apiBaseUrl,
) {
  let absoluteBaseUrl = trimTrailingSlash(baseUrl)
  if (absoluteBaseUrl.startsWith('/')) {
    if (!pageOrigin) {
      throw new Error('Relative WebSocket API base URL requires a page origin')
    }
    absoluteBaseUrl = `${trimTrailingSlash(pageOrigin)}${absoluteBaseUrl}`
  }
  absoluteBaseUrl = absoluteBaseUrl
    .replace(/^http:/i, 'ws:')
    .replace(/^https:/i, 'wss:')
  return buildApiUrl(path, absoluteBaseUrl)
}
