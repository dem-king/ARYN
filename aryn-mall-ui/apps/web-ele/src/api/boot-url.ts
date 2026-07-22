export function parseOpenBoot(value: boolean | null | string | undefined) {
  if (typeof value === 'boolean') {
    return value;
  }
  return value?.trim().toLowerCase() === 'true';
}

export function rewriteBootUrl(url: string | undefined, openBoot: boolean) {
  if (!openBoot || !url || /^[a-z][a-z\d+.-]*:\/\//i.test(url)) {
    return url;
  }

  const suffixIndex = url.search(/[?#]/);
  const pathname = suffixIndex < 0 ? url : url.slice(0, suffixIndex);
  const suffix = suffixIndex < 0 ? '' : url.slice(suffixIndex);
  const segments = pathname.split('/').filter(Boolean);

  if (segments[0] === 'boot' || segments.length < 2) {
    return url;
  }

  return `/boot/${segments.slice(1).join('/')}${suffix}`;
}
