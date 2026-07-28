const ABSOLUTE_URL_PATTERN = /^(?:[a-z][a-z\d+.-]*:)?\/\//i;

export function resolveResourceUrl(
  url: null | string | undefined,
  apiUrl: string,
) {
  if (!url || ABSOLUTE_URL_PATTERN.test(url) || /^(?:blob|data):/i.test(url)) {
    return url || '';
  }

  if (!url.startsWith('/upms/') && !url.startsWith('/boot/')) {
    return url;
  }

  return `${apiUrl.replace(/\/$/, '')}/${url.replace(/^\//, '')}`;
}
