import type { SharedCart } from '@/api/order/sharedCart'
import type { QuantityRule } from '@/utils/quick-cart'

/**
 * 加购确认：让浏览类加购入口（首页/分类/商详/商品卡片）在写入购物车前完成两件事——
 * 确认采购数量，以及（有进行中的共享购物车时）选择加入哪张车。
 *
 * 为什么要有数量这一步：船供采购的量普遍很大（青菜一次几十斤），原先点「+」只能
 * 按起订量一件件加，用户必须反复点击才凑够数量（个人购物车会累加数量，共享购物车
 * 则堆出一串重复行）。现在把数量与去向收敛进同一次确认，输入几十上百一次到位。
 *
 * 用户是某张「收集中」单的成员时，目的地选择同样必要——否则商品会被默认塞进
 * 个人购物车，而个人购物车与共享购物车两条链路互不相通，之后无法转入。
 *
 * 弹层 UI 在 components/shared-cart-destination-sheet，由根组件
 * `src/App.ku.vue` 按 `v-if="!!pendingChoice"` 全局懒挂载（每页恰好一份）。
 * 宿主一律不要再挂：quick-cart-button 这类宿主随商品卡重复渲染，各自挂载会
 * 让同屏出现 N 份弹层——遮罩叠成全黑、动画不同步抖动、按钮冒泡跳商详。
 * pendingChoice 是模块级单例：同屏多份加购组件共享同一状态，同一时刻只有
 * 一个加购流在等待确认。
 */
import { ref } from 'vue'
import { addSharedCartItem } from '@/api/order/sharedCart'
import { addShoppingCart } from '@/api/order/shoppingCart'
import { useSharedCartStore } from '@/store/sharedCartStore'
import { useShoppingCartStore } from '@/store/shoppingCartStore'
import { normalizeQuantity } from '@/utils/quick-cart'

/** 统一加购入参：与个人购物车接口对齐；写入共享车时只取 skuId/quantity/spuId */
export interface CartAddPayload {
  skuId: string
  quantity: number
  spuId?: string
  /** 个人购物车的归属标记（如常购清单回加传 '2'）；共享车无此概念，不透传 */
  addType?: string
  /** 商品名（仅用于确认弹层展示，让用户确认加的是哪件商品） */
  goodsName?: string
  /** 单价（元，仅用于确认弹层展示小计与合计） */
  unitPrice?: number | string | null
}

export type CartAddDestination = 'shared' | 'personal' | 'abort'

/** 一次等待中的加购确认 */
export interface PendingAddConfirm {
  /** 请求标识：超时兜底识别「是否仍停留在这一次确认」 */
  id: number
  /** 进行中的共享购物车；null 表示没有目的地可选（弹层只确认数量） */
  cart: SharedCart | null
  /** 数量规则；null 表示数量已由上游定好（SKU 弹层已选规格与数量），弹层只问去向 */
  rule: QuantityRule | null
  /** 采购数量，由弹层编辑后回传 */
  quantity: number
  /** 展示用：本次要加的商品与单价 */
  payload: CartAddPayload
  /** 用户仍在操作：重置兜底超时，避免数量输到一半被判超时 */
  touch: () => void
  /** 弹层回传用户的选择与最终数量 */
  answer: (dest: CartAddDestination, quantity?: number) => void
}

/** 一次加购确认的答复 */
export interface CartAddAnswer {
  dest: CartAddDestination
  quantity: number
}

/** 模块级单例：等待用户确认的加购请求（弹层与宿主都读它） */
export const pendingChoice = ref<PendingAddConfirm | null>(null)

/**
 * 兜底超时：宿主忘挂弹层或用户长时间不操作时放行，不能把加购按钮永久卡住。
 * 确认弹层里要输入数量（几十上百斤），比单纯选去向需要更长的停留时间，
 * 且每次数量变化都会重置（见 PendingAddConfirm.touch）。
 */
const CONFIRM_TIMEOUT_MS = 60_000

/** 自增请求号 */
let confirmSeq = 0

export function useCartDestination() {
  const sharedCartStore = useSharedCartStore()
  const shoppingCartStore = useShoppingCartStore()

  /**
   * 弹出加购确认并等待用户答复（数量 + 去向）。
   * 关闭弹层视为放弃本次加购（'abort'），不静默写进个人购物车；
   * 离开所在页面（切 Tab / 返回）同样视为放弃，由弹层 onHide/onUnload 自关。
   */
  function askConfirmation(init: {
    cart: SharedCart | null
    rule: QuantityRule | null
    payload: CartAddPayload
  }): Promise<CartAddAnswer> {
    return new Promise((resolve) => {
      let timer: ReturnType<typeof setTimeout> | undefined
      const id = ++confirmSeq

      /** 落定本次确认：清理超时、关掉弹层、把答复交给等待方 */
      function finish(dest: CartAddDestination, quantity: number) {
        if (timer) {
          clearTimeout(timer)
          timer = undefined
        }
        // 只关掉还停留在本次的确认，避免上一笔的延迟答复关掉新弹层
        if (pendingChoice.value?.id === id)
          pendingChoice.value = null
        resolve({ dest, quantity })
      }

      /** 超时 = 用户长时间没操作，等同关闭弹层 */
      function onTimeout() {
        if (pendingChoice.value?.id === id)
          finish('abort', pendingChoice.value.quantity)
      }

      const request: PendingAddConfirm = {
        id,
        cart: init.cart,
        rule: init.rule,
        quantity: init.payload.quantity,
        payload: init.payload,
        touch: () => {
          if (!timer)
            return
          clearTimeout(timer)
          timer = setTimeout(onTimeout, CONFIRM_TIMEOUT_MS)
        },
        answer: (dest, quantity) => {
          // 回传数量兜底为正整数；非法值不覆盖上游算好的初始量
          const raw = quantity == null ? request.quantity : Math.floor(Number(quantity))
          const finalQuantity = Number.isFinite(raw) && raw > 0 ? raw : request.quantity
          finish(dest, finalQuantity)
        },
      }
      timer = setTimeout(onTimeout, CONFIRM_TIMEOUT_MS)
      // 已有等待中的确认时先按「放弃」结掉它：多个宿主各有自己的加购流，
      // 被顶掉的那笔若不落定，它的 await 会永远悬着，按钮一直停在 pending 态。
      pendingChoice.value?.answer('abort')
      pendingChoice.value = request
    })
  }

  /** 弹层回传用户的选择 */
  function answerDestination(dest: CartAddDestination, quantity?: number) {
    pendingChoice.value?.answer(dest, quantity)
  }

  /** 写入个人购物车 + 成功提示 + 角标刷新（原来散在各调用方的三件事收敛到一处） */
  async function addPersonal(payload: CartAddPayload) {
    await addShoppingCart({
      skuId: payload.skuId,
      quantity: payload.quantity,
      spuId: payload.spuId,
      addType: payload.addType,
    })
    uni.showToast({ title: '已加入购物车', icon: 'success' })
    shoppingCartStore.fetchCartCount().catch(() => {})
  }

  /**
   * 统一加购入口：确认数量/去向并写入对应购物车，返回实际去向。
   *
   * - 传 `rule`：弹层里出现数量步进器与键盘输入（预填上游按 MOQ/步长算好的合法量），
   *   无论有没有共享购物车都会停下来确认一次——这正是「一次买几十斤」需要的那一步；
   * - 不传 `rule`：数量已由上游确定（SKU 弹层已选规格与数量），行为与原先一致——
   *   有共享车时问去向，没有则直接入个人购物车，零打扰；
   * - 'abort'：用户关闭了弹层/超时未操作，未写任何购物车；
   * - 失败时抛出，由调用方的既有 catch 兜底（请求拦截器已提示具体原因）。
   */
  async function submitCartAdd(
    payload: CartAddPayload,
    options: { rule?: QuantityRule | null } = {},
  ): Promise<CartAddDestination> {
    const rule = options.rule ?? null
    // 入口预填值先归一化一次：上游算出的值通常已合法，这里防调用方传错；
    // 用户后续改动由弹层按同一份 normalizeQuantity 处理。
    const initialPayload = rule
      ? { ...payload, quantity: normalizeQuantity(payload.quantity, rule) }
      : payload

    await sharedCartStore.refreshActive()
    const cart = sharedCartStore.activeCart

    // 无需选去向、也无需改数量：保持原来的零打扰直加
    if (!cart && !rule) {
      await addPersonal(initialPayload)
      return 'personal'
    }

    const answer = await askConfirmation({ cart, rule, payload: initialPayload })
    if (answer.dest === 'abort')
      return 'abort'

    const finalPayload = { ...initialPayload, quantity: answer.quantity }

    if (answer.dest === 'personal' || !cart) {
      await addPersonal(finalPayload)
      return 'personal'
    }

    await addSharedCartItem(cart.id, {
      spuId: finalPayload.spuId,
      skuId: finalPayload.skuId,
      requestedQuantity: finalPayload.quantity,
    })
    uni.showToast({ title: '已加入共享购物车', icon: 'success' })
    // 明细已变化：作废摘要缓存，回到首页补给单卡片时强制取最新
    sharedCartStore.invalidate()
    return 'shared'
  }

  return { answerDestination, submitCartAdd }
}
