import type { DecorationLinkType } from '../../../../page-designer/schema/types';
import type { IdPickerKind } from '../id-picker-field/helpers';

/** 需要从后台实体里选目标的链接类型（targetId + 选择器） */
export const TARGET_LINK_TYPES: ReadonlySet<DecorationLinkType> = new Set([
  'activity',
  'category',
  'coupon',
  'goods',
  'page',
]);

/**
 * 结构化链接类型 → IdPickerField 选择器种类；非实体类型（自定义路径/小程序/客服）
 * 返回 null。分类不走 IdPickerField（分类树级联单独渲染），所以也返回 null。
 */
export function pickerKindOfType(
  type: DecorationLinkType,
): IdPickerKind | null {
  if (type === 'goods') return 'goods';
  if (type === 'coupon') return 'coupon';
  if (type === 'page') return 'page';
  // C 端 activity 链接固定落地到拼团活动详情页
  if (type === 'activity') return 'groupbuy-activity';
  return null;
}
