interface TokenResponse {
  tokenValue?: unknown
}

export function requireTokenValue(response: unknown) {
  const tokenValue = (response as TokenResponse | null)?.tokenValue
  if (typeof tokenValue !== 'string' || !tokenValue.trim()) {
    throw new Error('登录响应缺少有效凭证')
  }
  return tokenValue.trim()
}
