import type { DecorationLink, DecorationLinkType } from '../schema/types';

const targetTypes = new Set<DecorationLinkType>([
  'activity',
  'category',
  'coupon',
  'goods',
  'page',
]);
const pathTypes = new Set<DecorationLinkType>(['custom', 'mini-program']);

export function validateDecorationLink(link: Partial<DecorationLink>) {
  const errors: string[] = [];
  if (!link.type) return ['请选择链接类型'];
  if (targetTypes.has(link.type) && !link.targetId) errors.push('请选择目标');
  if (pathTypes.has(link.type) && !link.path) errors.push('请输入路径');
  return errors;
}

export function normalizeDecorationLink(input: unknown): DecorationLink {
  if (input && typeof input === 'object' && 'type' in input) {
    const link = input as Partial<DecorationLink>;
    return {
      params: link.params ?? {},
      path: link.path ?? '',
      targetId: link.targetId,
      type: link.type ?? 'custom',
    };
  }
  if (input && typeof input === 'object' && 'url' in input) {
    return {
      params: {},
      path: String((input as { url?: unknown }).url ?? ''),
      type: 'custom',
    };
  }
  return { params: {}, path: '', type: 'custom' };
}
