import type { AppSignInRecord } from '@/sub-pages/api/user/signIn'

import { ref } from 'vue'
import { useAuthStore } from '@/store/authStore'
import { useUserStore } from '@/store/userStore'
import { getTodaySignInRecord } from '@/sub-pages/api/user/signIn'
import { Local } from '@/utils/storage'

/**
 * 「今日是否已签到」的跨页复用状态（会员中心横幅 / 签到页共用）。
 *
 * 背景（真实缺陷）：会员中心「每日签到」横幅写死「去签到」CTA，已签到用户回到
 * 会员中心仍被引导去签到，点进签到页才看到「今日已签到」——两页口径不一致。
 *
 * 数据源不新增接口：复用签到页已有的 C 端记录接口
 * `/mall-user/app/signin/records`，按 `sign_date` 收窄到当天、`size=1`。
 * 表 `sign_in_record` 对 (user_id, sign_date) 有唯一约束，当天最多一条。
 *
 * 为什么还要本地快照：
 *   · 会员中心每次 onShow 都会重拉接口，纯接口方案会让已签到的用户先看到一瞬
 *     「去签到」再翻转；
 *   · 签到当天不可撤销，所以「同一用户 + 同一自然日」的快照可以安全地先渲染，
 *     再由接口按需校正。
 * 快照跨天、换账号一律作废（见 `resolveSignInSnapshot`），不会把 A 的状态带给 B。
 *
 * 注意：页面态（ref）由每个页面各自持有，跨页只共享 storage 快照；从签到页
 * 返回会员中心时靠 onShow 里的 `refresh()` 同步读快照完成翻转。
 * 「命中已签到快照当天不再请求」成立的前提是**签到记录不可撤销**：管理端目前
 * 只有记录查询，没有补签/删除接口；若未来新增该能力，必须回头调整这里的早退。
 */

/** 本地快照存储键：只存最近一次确认过的「用户 + 日期 + 签到态」 */
export const SIGN_IN_SNAPSHOT_KEY = 'signInStatusSnapshot'

export interface SignInStatusSnapshot {
  /** 快照所属用户；与当前登录用户不一致时整条作废 */
  userId: string
  /** 快照日期 yyyy-MM-dd；不是今天时整条作废（跨天必须重新确认） */
  date: string
  signed: boolean
  consecutiveDay: number
}

/**
 * 本地日期 yyyy-MM-dd。
 *
 * 与签到页日历、记录查询的 `beginDate/endDate`、后端 `sign_date` 同一口径。
 */
export function formatSignInDate(now: Date = new Date()): string {
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** 连签天数归一：负数 / NaN / 缺失一律按 0（=未知）处理，避免渲染出「-1 天」 */
function normalizeStreak(value: unknown): number {
  const days = Number(value)
  return Number.isFinite(days) && days > 0 ? Math.trunc(days) : 0
}

/**
 * 本地快照的可用性判定。
 *
 * 只认「当前用户 + 今天」：跨天、换号、脏数据一律返回 null，调用方回退到接口结果。
 * 抽成纯函数是为了让这条边界可单测 —— 一旦把别人的「已签到」显示给新登录用户，
 * 用户会以为系统替他签了到，比多显示一个按钮更严重。
 */
export function resolveSignInSnapshot(
  raw: unknown,
  userId: string,
  date: string,
): SignInStatusSnapshot | null {
  if (!userId || !raw || typeof raw !== 'object')
    return null
  const snapshot = raw as Partial<SignInStatusSnapshot>
  if (snapshot.userId !== userId || snapshot.date !== date || typeof snapshot.signed !== 'boolean')
    return null
  return {
    userId,
    date,
    signed: snapshot.signed,
    consecutiveDay: normalizeStreak(snapshot.consecutiveDay),
  }
}

/** 接口结果 → 快照：有当天记录即已签到，连签天数取记录上的值 */
export function buildSignInSnapshot(
  userId: string,
  date: string,
  record?: AppSignInRecord | null,
): SignInStatusSnapshot {
  return {
    userId,
    date,
    signed: Boolean(record),
    consecutiveDay: normalizeStreak(record?.consecutiveDay),
  }
}

function readSnapshot(userId: string, now: Date): SignInStatusSnapshot | null {
  try {
    return resolveSignInSnapshot(Local.get(SIGN_IN_SNAPSHOT_KEY), userId, formatSignInDate(now))
  }
  catch {
    // 存储损坏/不可用只影响「首帧不闪」这一优化，按无快照处理，页面照常走接口
    return null
  }
}

function writeSnapshot(snapshot: SignInStatusSnapshot) {
  try {
    Local.set(SIGN_IN_SNAPSHOT_KEY, snapshot)
  }
  catch {
    // 写失败只影响下次进入的免请求优化，不影响本次状态展示
  }
}

export function useSignInStatus() {
  const authStore = useAuthStore()
  const userStore = useUserStore()

  /** 今天是否已签到（未登录恒 false） */
  const signedToday = ref(false)
  /** 当前连签天数（已签到时用于横幅副文案） */
  const consecutiveDay = ref(0)

  function currentUserId(): string {
    return userStore.getUserId || ''
  }

  function apply(snapshot: SignInStatusSnapshot | null) {
    signedToday.value = Boolean(snapshot?.signed)
    consecutiveDay.value = snapshot?.consecutiveDay ?? 0
  }

  /**
   * 同步读本地快照：首帧（setup）先渲染正确状态，不等网络。
   *
   * @returns 是否命中「当前用户今天已签到」的快照
   */
  function syncFromCache(now: Date = new Date()): boolean {
    // 未登录/会话已失效：快照里的用户态一律不采用，避免退出后仍显示「已签到」
    if (!authStore.isLoggedIn) {
      apply(null)
      return false
    }
    const snapshot = readSnapshot(currentUserId(), now)
    apply(snapshot)
    return Boolean(snapshot?.signed)
  }

  /**
   * 以接口结果校正今日签到状态（失败时保留当前快照态，不阻断页面）。
   *
   * 命中「今天已签到」的快照时不再发请求：签到当天不可逆，快照已足够；
   * 其余情况（未签 / 无快照 / 跨天换号）必须请求 —— 否则「另一台设备已签」时
   * 横幅会一直诱导用户去签到。未签用户因此每次 onShow 多一次 `size=1` 查询
   * （走 (user_id, sign_date) 唯一索引，成本可忽略），换取跨设备状态及时纠正。
   */
  async function refresh(now: Date = new Date()) {
    // 未登录绝不发 /app/** 请求：C 端鉴权接口在游客态会走全局 401 链路，不能由状态增强触发
    if (!authStore.isLoggedIn) {
      apply(null)
      return
    }
    const userId = currentUserId()
    if (userId && syncFromCache(now))
      return

    const date = formatSignInDate(now)
    try {
      const record = await getTodaySignInRecord(date)
      // 迟到响应保护：请求期间退出登录或换了账号，这次结果不属于当前页面
      if (!authStore.isLoggedIn || currentUserId() !== userId)
        return
      const snapshot = buildSignInSnapshot(userId, date, record)
      apply(snapshot)
      // 仅在能归属到具体用户时才落快照：没有 userId 的快照对任何人都无效
      if (userId)
        writeSnapshot(snapshot)
    }
    catch (error) {
      // 签到状态是增强信息：接口失败不弹错、不阻断页面，用户点进签到页仍能看到真实状态
      console.error('加载今日签到状态失败:', error)
    }
  }

  /** 签到成功后由签到页调用：更新状态并落快照，返回会员中心时不再闪「去签到」 */
  function markSigned(days: number, now: Date = new Date()) {
    const userId = currentUserId()
    if (!userId)
      return
    const snapshot: SignInStatusSnapshot = {
      userId,
      date: formatSignInDate(now),
      signed: true,
      consecutiveDay: normalizeStreak(days),
    }
    apply(snapshot)
    writeSnapshot(snapshot)
  }

  /** 未登录 / 退出登录时清空页面态（storage 里的快照仍按用户 + 日期自校验） */
  function reset() {
    apply(null)
  }

  return { signedToday, consecutiveDay, syncFromCache, refresh, markSigned, reset }
}
