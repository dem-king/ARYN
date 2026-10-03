/**
 * 船舶与靠港上下文的统一装载入口。
 *
 * 背景（2026-09-29 购物车显示「当前船舶：未命名」/「未指定配送计划」）：
 * `shipContextStore` 刻意**不持久化**（见 store/persist.ts 的 EXCLUDED_STORE_IDS），
 * 冷启动后是空的；而此前只有首页装修组件 `diy-ship-workbench` 会写入它。
 *
 * 这不只是显示问题——`addShoppingCart` 会把上下文当作**加购时刻的归属快照**
 * 写进 `shopping_cart.vessel_id / vessel_call_id`，购物车正是按这个快照分组的。
 * 于是「没进过首页就加购」的行归属全是 NULL，进购物车一律落到
 * 「未指定配送计划」，防串船分组与跨靠港拦截跟着失效。
 *
 * 因此装载必须覆盖到写入路径，由 `addShoppingCart` / `batchAddShoppingCart`
 * 在读取上下文前调用本 composable 保证；需要的页面（购物车、共享购物车）
 * 也各自装载一次，用于即时渲染分组标签。
 *
 * 失败策略：任一步失败都静默返回，不弹错、不阻塞页面——上下文只影响分组
 * 与默认配送方式，缺失时回落到快递配送；服务异常时不打断加购主流程。
 * 与 `diy-ship-workbench` 的「服务异常整条状态条隐藏」口径一致。
 */
import { ref } from 'vue'

import { getMyVessels, getVesselCalls } from '@/api/vessel'
import { useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'

/** 进行中的请求，避免同页多处触发时重复拉取 */
let pending: Promise<void> | null = null

/**
 * 本次登录会话是否已装载过。
 *
 * 「无船舶」「纯零售租户」「服务异常」都属于已尝试但拿不到上下文，
 * 若不加这个标记，每次加购都会白打一次接口；登出时复位，
 * 换账号登录后能重新装载。
 */
let resolved = false

/** 装载进行中（供页面显示加载态） */
const loading = ref(false)

/**
 * 是否已登录。
 *
 * 刻意**不 import authStore**：本模块被 `api/order/shoppingCart` 引用，
 * 而 authStore → shoppingCartStore → 该 api 已构成依赖环，构建期会报
 * 「Circular chunk」。这里按 `api/core/instance.ts` 读取 token 的同一口径，
 * 直接读持久化的 auth 状态，等价于 `authStore.isLoggedIn`（isLogin && token）且无环。
 */
function hasCustomerSession() {
  const state = uni.getStorageSync('auth') as { isLogin?: boolean, token?: string } | undefined
  return !!state?.isLogin && !!state.token
}

/**
 * 是否具备船供场景：未登录或纯零售租户直接跳过，不发船舶请求。
 *
 * 未登录时必须提前返回：`/vessel/app/my-vessels` 需要登录态，若放任请求发出，
 * 401 会触发全局的「登录已过期」处理并清掉 token。
 */
async function canLoad() {
  if (!hasCustomerSession()) {
    // 登出即复位，换账号登录后需要重新装载
    resolved = false
    return false
  }
  const capabilityStore = useTenantCapabilityStore()
  await capabilityStore.ensureLoaded()
  return capabilityStore.shipSupplyEnabled
}

/**
 * 装载船舶与靠港上下文（模块级，可在组件外调用）。
 *
 * @param force 为 true 时忽略「已装载」标记与在途去重强制重拉（如切换船舶后）
 */
export async function ensureShipContextLoaded(force = false): Promise<void> {
  if (!force && resolved)
    return
  if (pending && !force)
    return pending

  const task = (async () => {
    const shipContextStore = useShipContextStore()
    loading.value = true
    try {
      if (!(await canLoad())) {
        shipContextStore.reset()
        return
      }

      const vessels = await getMyVessels()
      if (!vessels || vessels.length === 0) {
        shipContextStore.reset()
        return
      }

      // 保留用户显式选择的船，否则回落到第一艘
      const vessel = vessels.find(item => item.id === shipContextStore.vesselId) ?? vessels[0]
      shipContextStore.setVesselContext({ vesselId: vessel.id, vesselName: vessel.vesselName })

      const calls = await getVesselCalls(vessel.id)
      if (!calls || calls.length === 0) {
        // 有船但无可用靠港：清掉上一靠港，避免显示已失效的港口/时间窗
        shipContextStore.clearVesselCall()
        return
      }

      const picked = calls.find(call => call.id === shipContextStore.vesselCallId) ?? calls[0]
      shipContextStore.setVesselCall({
        berth: picked.berth,
        deliveryWindowEnd: picked.deliveryWindowEnd,
        deliveryWindowStart: picked.deliveryWindowStart,
        id: picked.id,
        portCode: picked.portCode,
        portName: picked.portName,
      })
    }
    catch {
      // 上下文装载失败不影响页面与加购主流程，按无船舶上下文降级
    }
    finally {
      loading.value = false
      resolved = true
    }
  })()

  pending = task
  try {
    await task
  }
  finally {
    pending = null
  }
}

/** 组件内使用的装载入口 */
export function useShipContextLoad() {
  return { loading, load: ensureShipContextLoaded }
}
