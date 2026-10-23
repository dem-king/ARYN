/**
 * 拼团活动名去重。
 *
 * 运营常把活动名写成「2人成团·农夫山泉天然水整箱」，而卡片图上另有人数徽标，
 * 名称里再带一遍就是同义重复（用户一眼看到两遍「2人成团」）。
 * 仅当 groupNum > 0（徽标确实会渲染成团人数）时剥离，避免误伤
 * 「3人份」「2人套餐」这类把规格写进名称的活动；剥离后为空则回退原名。
 */
const GROUP_PREFIX = /^\s*\d+\s*人成团\s*[·•・.\-—]?\s*/

export function stripGroupBadgePrefix(name: string, groupNum: number) {
  const raw = (name || '').trim()
  if (!raw || !(groupNum > 0))
    return raw
  const stripped = raw.replace(GROUP_PREFIX, '').trim()
  return stripped || raw
}