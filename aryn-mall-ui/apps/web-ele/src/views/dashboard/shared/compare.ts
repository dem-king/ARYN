export function calculateDelta(
  current?: null | number,
  previous?: null | number,
): null | number {
  if (current === null || current === undefined) {
    return null;
  }
  if (previous === null || previous === undefined) {
    return null;
  }
  if (previous === 0) {
    return current === 0 ? 0 : null;
  }
  return ((current - previous) / Math.abs(previous)) * 100;
}
