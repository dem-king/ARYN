import { translate } from '@/locales'

interface TokenResponse {
  tokenValue?: unknown
}

export function requireTokenValue(response: unknown) {
  const tokenValue = (response as TokenResponse | null)?.tokenValue
  if (typeof tokenValue !== 'string' || !tokenValue.trim()) {
    throw new Error(translate('app.auth.tokenMissing'))
  }
  return tokenValue.trim()
}
