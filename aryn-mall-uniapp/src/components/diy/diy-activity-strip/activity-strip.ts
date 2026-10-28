import type { RetailActivityItem } from '../retail-normalizers'

import type {
  ActivityStripEntry,
  DiscountActivityItem,
  SeckillSessionItem,
} from '../retail-types'
import type { DecorationLink } from '../schema/types'
/**
 * 活动入口条（diy-activity-strip）的纯逻辑。
 *
 * 把「已归一化的活动数据 → 卡片名/钩子/角标/跳转」抽成纯函数，目的有二：
 * 1. 组件模板只做渲染，自动取数（loader）只管并发与降级，职责不混在一起；
 * 2. 这些口径能被单测钉死（见 `activity-strip.test.ts`）——折扣力度曾写成
 *    `discountValue * 10` 得到「10折」、省额在无原价时划出假优惠，都是纯逻辑
 *    层面的回归，靠人眼看模板很难拦住。
 *
 * 需求口径：docs/50-需求文档/2026-10-22-营销会场页与拼团详情视觉重设计/README.md §5.5.3/§5.5.5。
 */
import type { CategoryIconFallback } from '@/utils/category-icon'

import { translate } from '@/locales'
import { resolveCategoryFallback } from '@/utils/category-icon'
import { formatDiscountValue, formatStripAmount } from '@/utils/discount-price'

/**
 * 三类内置入口的固定会场路由（§5.5.3）。
 *
 * 入口**永远**指向会场聚合页而不是某一单场/单活动：活动结束后入口不失效，
 * 只会因为「该类无进行中活动」整张卡隐藏。
 */
export const ACTIVITY_STRIP_HALLS = {
  'discount': '/pages/promotion/discount',
  'group-buy': '/sub-pages/promotion/group-buy/group-buy-list/index',
  'seckill': '/pages/promotion/seckill',
} as const

// 折扣力度/金额口径收敛到 `utils/discount-price`（商详 / 购物车的限时折扣角标
// 与本入口条共用同一实现，避免「8折/9.5折/10折」在多处漂移）。
// 这里保留原导出名，入口条测试与调用方无需改动。
export { formatDiscountValue, formatStripAmount } from '@/utils/discount-price'

/**
 * 拼团卡名：2 人团是本项目主力形态，也就是需求里的默认名「2人拼团」；
 * 3 人及以上按真实人数表达，避免出现「标题 2 人拼团、钩子 3 人成团」的自相矛盾。
 */
export function formatGroupBuyTitle(
  activity: Pick<RetailActivityItem, 'groupNum'>,
): string {
  const groupNum = Number(activity.groupNum)
  return groupNum > 0
    ? translate('diy.activityStrip.groupBuyTitle', { groupNum })
    : translate('diy.activityStrip.groupBuyTitleFallback')
}

/** 拼团钩子：`{groupNum}人成团立省`；存量活动没有 groupNum 时退化为「多人成团」 */
export function formatGroupBuyHook(
  activity: Pick<RetailActivityItem, 'groupNum'>,
): string {
  const groupNum = Number(activity.groupNum)
  return groupNum > 0
    ? translate('diy.activityStrip.groupBuyHook', { groupNum })
    : translate('diy.activityStrip.groupBuyHookFallback')
}

/**
 * 拼团角标：原价严格大于拼团价才显示省额，否则退化为「拼」。
 * 不满足条件时绝不能按 0 兜底造出「省¥0」——那是编造的优惠。
 */
export function formatGroupBuyBadge(
  activity: Pick<RetailActivityItem, 'activityPrice' | 'originalPrice'>,
): string {
  const saving = Number(activity.originalPrice) - Number(activity.activityPrice)
  if (!(saving > 0))
    return translate('diy.activityStrip.groupBuyBadge')
  return translate('diy.common.saveAmount', { amount: formatStripAmount(saving) })
}

/** 秒杀钩子：`{sessionName}·整点开抢`；场次名缺失时只留节奏词 */
export function formatSeckillHook(
  session: Pick<SeckillSessionItem, 'sessionName'>,
): string {
  const name = (session.sessionName || '').trim()
  return name
    ? translate('diy.activityStrip.seckillHookWithSession', { name })
    : translate('diy.activityStrip.seckillHook')
}

/**
 * 内置三类卡的自动取数结果。
 *
 * 某类没有进行中活动（或该类 loader 失败）时传空，`buildActivityStripEntries`
 * 直接不产出该卡 —— 不做「暂无」占位，剩余卡由渲染侧等宽重排（§5.5.6）。
 */
export interface ActivityStripSources {
  discount?: DiscountActivityItem | null
  groupBuy?: RetailActivityItem | null
  seckill?: SeckillSessionItem | null
}

/**
 * 组装内置三类卡，顺序固定为「秒杀 → 拼团 → 折扣」（与 §5.5.3 表格一致）。
 *
 * 只认「进行中」：秒杀场次 status=1、拼团 activityStatus=active、折扣 status=1；
 * 未开始/已结束的数据即使被 loader 带回来也不会出现在入口条上。
 */
export function buildActivityStripEntries(
  sources: ActivityStripSources,
): ActivityStripEntry[] {
  const entries: ActivityStripEntry[] = []

  const seckill = sources.seckill
  if (seckill && Number(seckill.status) === 1) {
    entries.push({
      badge: translate('diy.activityStrip.seckillBadge'),
      hook: formatSeckillHook(seckill),
      imageUrl: seckill.goodsList[0]?.goodsImage || '',
      key: 'seckill',
      link: hallLink(ACTIVITY_STRIP_HALLS.seckill),
      title: translate('diy.activityStrip.seckillTitle'),
    })
  }

  const groupBuy = sources.groupBuy
  if (groupBuy && groupBuy.status === 'active') {
    entries.push({
      badge: formatGroupBuyBadge(groupBuy),
      hook: formatGroupBuyHook(groupBuy),
      imageUrl: groupBuy.imageUrl || '',
      key: 'group-buy',
      link: hallLink(ACTIVITY_STRIP_HALLS['group-buy']),
      title: formatGroupBuyTitle(groupBuy),
    })
  }

  const discount = sources.discount
  if (discount && Number(discount.status) === 1) {
    const value = formatDiscountValue(discount)
    entries.push({
      badge: value,
      hook: translate('diy.activityStrip.discountHook', { value }),
      // 指定商品活动取首件商品图；全场活动取取数层补的代表商品图
      // （loadDiscounts 已并入 coverImage，这里保留商品图优先，缺图才回落首字色块）
      imageUrl: discount.goodsList[0]?.goodsImage || discount.coverImage || '',
      key: 'discount',
      link: hallLink(ACTIVITY_STRIP_HALLS.discount),
      title: translate('diy.activityStrip.discountTitle'),
    })
  }

  return entries
}

/**
 * 缺图兜底：沿用类目图标的首字色块色板（浅底 + 同色系深字）。
 *
 * 哈希键用卡片类型而不是标题：活动名变化时色块颜色不应该跳变。
 */
export function resolveStripFallback(
  entry: Pick<ActivityStripEntry, 'key' | 'title'>,
): CategoryIconFallback {
  return resolveCategoryFallback(entry.title, entry.key)
}

/**
 * 角标强度：秒杀是「强促销」（主题渐变实心红），拼团/折扣是「弱促销」（主题浅底）。
 * 首页一条入口里不能出现三块同强度大红，促销强度要分三级（§2.4 设计主线）。
 */
export function stripBadgeTone(key: ActivityStripEntry['key']): 'soft' | 'strong' {
  return key === 'seckill' ? 'strong' : 'soft'
}

/** 一行最多三张卡：三列是 750rpx 屏宽下文案仍可读的上限 */
export const MAX_STRIP_COLUMNS = 3

/**
 * 实际排布列数：卡数就是展示数（自动模式最多三类活动各一张，
 * 手配模式就是运营添加的卡片数），不再读取「每行列数 / 展示数量」。
 *
 * 一行最多三张：1~3 张等宽铺满整行；手配超过三张时按三列换行，
 * 所有卡片都展示，多余卡片不会被截断。
 */
export function stripGridColumns(cardCount: number): number {
  const count = Math.max(0, Math.floor(Number(cardCount)) || 0)
  return Math.min(Math.max(count, 1), MAX_STRIP_COLUMNS)
}

function hallLink(path: string): DecorationLink {
  return { params: {}, path, type: 'custom' }
}
