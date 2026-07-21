export function getCurrentPath() {
  return getCurrentPages().at(-1)?.route || ''
}
