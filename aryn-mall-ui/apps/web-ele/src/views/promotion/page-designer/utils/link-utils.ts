import type { DecorationLink, DecorationLinkType } from '../schema/types';

const targetTypes = new Set<DecorationLinkType>([
  'activity',
  'category',
  'coupon',
  'goods',
  'page',
]);
const pathTypes = new Set<DecorationLinkType>(['custom', 'mini-program']);

/** 与 C 端 `link-resolver.ts` 的站内路径规则保持一致：单斜杠开头且不含反斜杠 */
export function isSafeInternalPath(path: string) {
  return /^\/(?!\/)/.test(path) && !path.includes('\\');
}

export interface ValidateDecorationLinkOptions {
  /**
   * 严格模式（仅链接编辑器保存时开启）：
   * 把 C 端会静默不跳/跳错的配置提前拦下（小程序缺 AppID、站内路径非法等）。
   *
   * 发布校验（marketing-entry 等组件 validate）保持宽松，避免存量页面里的历史
   * 链接把整页发布卡死；编辑器负责在本次修改时把链接改对。
   */
  strict?: boolean;
}

export function validateDecorationLink(
  link: Partial<DecorationLink>,
  options: ValidateDecorationLinkOptions = {},
) {
  const strict = options.strict ?? false;
  const errors: string[] = [];
  if (!link.type) return ['请选择链接类型'];
  if (targetTypes.has(link.type) && !link.targetId) errors.push('请选择目标');
  if (pathTypes.has(link.type) && !link.path) errors.push('请输入路径');

  if (strict && link.type === 'mini-program') {
    // C 端 followDecorationLink 只有 appId 非空才 navigateToMiniProgram，缺了等于没配
    if (!link.params?.appId?.trim()) errors.push('请填写小程序 AppID');
    if (link.path?.startsWith('/')) errors.push('小程序页面路径不要以 / 开头');
  }
  if (
    strict &&
    link.type === 'custom' &&
    link.path &&
    !isSafeInternalPath(link.path)
  ) {
    errors.push('页面路径需以 / 开头，且不能包含 // 或反斜杠');
  }
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
