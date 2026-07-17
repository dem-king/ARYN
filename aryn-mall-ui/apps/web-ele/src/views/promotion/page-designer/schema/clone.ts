import { isEqual } from '@vben/utils';

export function cloneDesignerValue<T>(value: T): T {
  const serialized = JSON.stringify(value);
  if (serialized === undefined) {
    throw new TypeError('Designer values must be JSON serializable');
  }
  return JSON.parse(serialized) as T;
}

export function isSameDesignerValue(left: unknown, right: unknown) {
  return isEqual(left, right);
}
