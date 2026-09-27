import type { SectionCondition, SectionConditionRule } from './types';

import { cloneDesignerValue } from './clone';

/**
 * 判断单条规则是否已完成配置。
 *
 * 空的 id 集合视为「未配置」：编辑器添加规则时先落一条空选项规则，历史上
 * 首页曾因 `memberLevelIds: []` 被保存发布，C 端整页区块对所有人不可见、
 * 只剩导航条。因此写回文档前必须剔除未配置规则，而不是原样保存。
 */
export function isConfiguredRule(rule: SectionConditionRule): boolean {
  if (rule.type === 'memberLevel') return rule.memberLevelIds.length > 0;
  if (rule.type === 'userTag') return rule.userTagIds.length > 0;
  if (rule.type === 'timeRange') {
    return rule.startTime !== '' && rule.endTime !== '';
  }
  return true; // login / guest 天然完整
}

/**
 * 把设置面板的组合条件写回文档：剔除未配置规则；
 * 全部被剔除时归一为 'always'，避免空组合条件上发布。
 */
export function sanitizeCondition(
  condition: SectionCondition,
): SectionCondition {
  if (typeof condition === 'string') return condition;
  const rules = condition.rules.filter((rule) => isConfiguredRule(rule));
  if (rules.length === 0) return 'always';
  return cloneDesignerValue({ logic: condition.logic, rules });
}
