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

import { resolveCategoryFallback } from '@/utils/category-icon'

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

/**
 * 金额文案：最多两位小数并去掉无意义的尾零（4.00 → 4、4.5 → 4.5）。
 *
 * 角标只有几十 rpx 宽，`¥4.00` 比 `¥4` 多占两个字宽却没有任何信息增量；
 * 同时 `30.8 - 26.8` 这类浮点差会算出 `4.000000000000002`，必须按两位收敛。
 */
export function formatStripAmount(value: number): string {
  const amount = Number(value)
  if (!Number.isFinite(amount))
    return '0'
  return String(Math.round(amount * 100) / 100)
}

/**
 * 折扣力度文案，与折扣会场页/楼层同口径（§3.2 / R4）。
 *
 * 打折：`Math.round(discountValue * 100) / 10` —— 0.95 → 9.5折、0.8 → 8折；
 * 力度 ≥10 折说明折后价不低于原价（后端也把 >1 视为非法），既没有「省」的信息量，
 * 又会拼出不成词的「10折」，因此退化为中性「折扣」，保证角标永不出现「10折」。
 * 减价 / 固定价沿用后端语义：`减¥X` / `一口价`。
 */
export function formatDiscountValue(
  activity: Pick<DiscountActivityItem, 'discountType' | 'discountValue'>,
): string {
  switch (Number(activity.discountType)) {
    case 1: {
      const zhe = Math.round(Number(activity.discountValue) * 100) / 10
      if (!Number.isFinite(zhe) || zhe <= 0 || zhe >= 10)
        return '折扣'
      return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`
    }
    case 2:
      return `减¥${formatStripAmount(activity.discountValue)}`
    case 3:
      return '一口价'
    default:
      return '折扣'
  }
}

/**
 * 拼团卡名：2 人团是本项目主力形态，也就是需求里的默认名「2人拼团」；
 * 3 人及以上按真实人数表达，避免出现「标题 2 人拼团、钩子 3 人成团」的自相矛盾。
 */
export function formatGroupBuyTitle(
  activity: Pick<RetailActivityItem, 'groupNum'>,
): string {
  const groupNum = Number(activity.groupNum)
  return groupNum > 0 ? `${groupNum}人拼团` : '2人拼团'
}

/** 拼团钩子：`{groupNum}人成团立省`；存量活动没有 groupNum 时退化为「多人成团」 */
export function formatGroupBuyHook(
  activity: Pick<RetailActivityItem, 'groupNum'>,
): string {
  const groupNum = Number(activity.groupNum)
  return groupNum > 0 ? `${groupNum}人成团立省` : '多人成团'
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
    return '拼'
  return `省¥${formatStripAmount(saving)}`
}

/** 秒杀钩子：`{sessionName}·整点开抢`；场次名缺失时只留节奏词 */
export function formatSeckillHook(
  session: Pick<SeckillSessionItem, 'sessionName'>,
): string {
  const name = (session.sessionName || '').trim()
  return name ? `${name}·整点开抢` : '整点开抢'
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
      badge: '抢',
      hook: formatSeckillHook(seckill),
      imageUrl: seckill.goodsList[0]?.goodsImage || '',
      key: 'seckill',
      link: hallLink(ACTIVITY_STRIP_HALLS.seckill),
      title: '限时秒杀',
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
      hook: `${value}专区`,
      imageUrl: discount.goodsList[0]?.goodsImage || '',
      key: 'discount',
      link: hallLink(ACTIVITY_STRIP_HALLS.discount),
      title: '限时折扣',
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
