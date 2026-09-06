export const PREVIEW_TTL_MS = 10 * 60 * 1000;

export type PreviewTerminal = 'h5' | 'weapp';

export function buildPreviewUrl(
  token: string,
  origin: string,
  terminal: PreviewTerminal = 'h5',
) {
  const normalizedOrigin = origin.replace(/\/$/, '');
  const query = terminal === 'weapp' ? '?terminal=weapp' : '';
  return `${normalizedOrigin}/#/page-preview/${encodeURIComponent(token)}${query}`;
}

export function getRemainingSeconds(expiresAt: number, now = Date.now()) {
  return Math.max(0, Math.ceil((expiresAt - now) / 1000));
}

export function formatRemainingTime(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`;
}
