<script setup lang="ts">
import type { SharedCart, SharedCartItem, SharedCartMember } from '@/api/order/sharedCart'
/**
 * 共享购物车详情：成员协作加购 → 确认人核定数量 → 提交生成整船订单。
 *
 * 权限一律以服务端返回的 viewer* 标记为准，前端不自行推断：
 * - viewerIsOwner    可邀请成员、关闭购物车
 * - viewerCanEdit    可维护自己的明细
 * - viewerCanConfirm 可提交整船订单
 *
 * 明细表只存 SPU/SKU ID，商品名通过商品域批量接口补齐（失败仅影响展示）。
 */
import { onLoad, onShareAppMessage, onShow } from '@dcloudio/uni-app'

import { computed, reactive, ref } from 'vue'
import {
  closeSharedCart,
  confirmSharedCart,
  getSharedCart,
  getSharedCartItems,
  getSharedCartMembers,
  inviteSharedCartMember,
  joinSharedCartByToken,
  removeSharedCartItem,
  reuseSharedCartFromHistory,
  setMyDisplayName,

  shareSharedCart,
  updateSharedCartItem,
} from '@/api/order/sharedCart'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useCountdownTicker } from '@/composables/useCountdown'
import { useShipContextStore } from '@/store/shipContextStore'
import { useUserStore } from '@/store/userStore'
import { resolveImageSrc } from '@/utils/image'
import {
  amountText,
  cartStatusLabel,
  cartStatusTheme,
  isCartCollecting,
  isCartReadonly,
  itemAmountOf,
  MEMBER_ROLE_MEMBER,
  memberRoleLabel,
  summarizeCartAmount,
  unitPriceText,
} from '@/utils/shared-cart'
import { formatCallTime, formatExpiryCountdown } from '@/utils/vessel-call-time'

definePage({
  name: 'shared-cart-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '共享购物车详情',
  },
})

const userStore = useUserStore()
const shipContextStore = useShipContextStore()

/** 每秒推进的时间基准：收集截止倒计时需要自己走，页面上没有别的定时器可用 */
const { now: tickNow } = useCountdownTicker()

const cartId = ref('')
const loading = ref(false)
const loadFailed = ref(false)
/** 正在凭分享令牌加入 */
const joining = ref(false)
/** 分享链接失效（过期、已撤销或购物车已提交） */
const joinFailed = ref(false)
const cart = ref<SharedCart | null>(null)
const members = ref<SharedCartMember[]>([])
const items = ref<SharedCartItem[]>([])

const currentUserId = computed(() => userStore.getUserId)
const collecting = computed(() => isCartCollecting(cart.value?.status))
const cartReadonly = computed(() => isCartReadonly(cart.value?.status))
const canConfirmNow = computed(() =>
  !!cart.value?.viewerCanConfirm && collecting.value && items.value.length > 0,
)

/** 收集截止：倒计时 + 具体时刻并列，用户既要知道「还有多久」也要核对「几点」 */
const expiryCountdown = computed(() =>
  collecting.value ? formatExpiryCountdown(cart.value?.expiresAt, new Date(tickNow.value)) : '',
)
const expiryPoint = computed(() =>
  collecting.value ? formatCallTime(cart.value?.expiresAt, new Date(tickNow.value)) : '',
)
/** 剩余不足 1 小时（或已给出「即将截止」）时转为警示色，提示马上要关门了 */
const expiryUrgent = computed(() =>
  !!expiryCountdown.value
  && (expiryCountdown.value === '即将截止'
    || /还剩 \d+ 分/.test(expiryCountdown.value)),
)

/**
 * 明细进度只用「申请数量」一个口径（已提交的单改用核定数量）：
 * 成员报多少就是多少，确认人在提交弹层里最终核定买多少（核定为 0 = 本次不采）。
 * 这里只统计项数与总量，不编造"已买多少"——系统里没有这个事实来源。
 *
 * 金额另起一行（见 cartAmount / cartAmountText）：它回答的是「这批大概多少钱」，
 * 与数量不是同一个问题，混成一句话会让两边的口径都变模糊。
 */
const itemSummaryText = computed(() => {
  const list = items.value
  if (list.length === 0)
    return ''
  // 已提交的单：核定数量已落库，报「核定合计」才是这批实际要买多少；
  // 收集中还没核定，只能报成员申请量。
  if (cartReadonly.value) {
    const totalApproved = list.reduce((sum, item) => sum + (item.approvedQuantity ?? item.requestedQuantity ?? 0), 0)
    return `${list.length} 项 · 核定合计 ${totalApproved}`
  }
  const totalRequested = list.reduce((sum, item) => sum + (item.requestedQuantity ?? 0), 0)
  return `${list.length} 项 · 申请合计 ${totalRequested}`
})

/** 整批金额：各明细单价 × 计价数量之和（未含促销，实付以订单为准） */
const cartAmount = computed(() => summarizeCartAmount(items.value))

/**
 * 预估总额文案。
 *
 * 有取不到价的明细时仍显示已算出的部分，并靠 `cartAmountIncomplete` 追加说明 ——
 * 直接省略金额会让用户以为这个页面不算钱，显示一个偏小的数却不说清楚更糟。
 *
 * 已提交的单不再叫「预估」：那是这批订单的构成金额，叫预估会让人以为还没定。
 * 但它按**当前**售价算，与下单时刻的价格可能不同，故不写成「实付」。
 */
const cartAmountText = computed(() => {
  if (cartAmount.value.pricedCount === 0)
    return ''
  const prefix = collecting.value ? '预估' : '合计'
  return `${prefix} ¥${amountText(cartAmount.value.amount)}`
})

/** 有商品取不到价（下架/已删除）：合计不完整，必须显式说明 */
const cartAmountIncomplete = computed(() => cartAmount.value.unpricedCount > 0)

/** 待核价的明细描述，如「2 项商品」 */
const unpricedItemText = computed(() => `${cartAmount.value.unpricedCount} 项商品`)

/**
 * 明细行金额文案（含「待核价」），列表与操作面板共用同一份口径。
 *
 * 被核定为「本次不采」的行返回空串：紧邻的徽标已经写明它不买，
 * 再补一个「¥0.00」或「不计入」都是同一件事说两遍。
 */
function itemAmountText(item: SharedCartItem): string {
  if (item.approvedQuantity === 0)
    return ''
  const amount = itemAmountOf(item)
  return amount === null ? '待核价' : `¥${amountText(amount)}`
}

/**
 * 成员加购汇总：每个成员报了几项、合计多少钱。
 *
 * 归集键是明细的 userId 而不是成员列表 —— 成员列表理论上覆盖全部归属，
 * 但两者的数据来源不同（成员行 vs 明细行），若有明细落在列表之外，
 * 按成员遍历会让它的金额从「成员合计」里凭空消失，用户对不上账。
 * 因此这里以明细为准分组，成员行只用来取展示名与角色。
 */
const memberSummaries = computed(() => {
  const byUser = new Map<string, { userId: string, attributedName: string, items: SharedCartItem[] }>()
  for (const item of items.value) {
    // 接龙代报的行挂在操作者名下、按归属姓名互相区分：同一人订同一种商品
    // 也各占一行，汇总必须按「userId + 归属姓名」分组，否则所有人的报货
    // 会并进操作者一栏
    const attributedName = item.attributedName || ''
    const key = attributedName ? `${item.userId}|${attributedName}` : item.userId
    const entry = byUser.get(key)
    if (entry)
      entry.items.push(item)
    else
      byUser.set(key, { userId: item.userId, attributedName, items: [item] })
  }
  // 先按成员列表顺序（加入时间）排列，再补上列表里没有的归属者，避免漏项
  const ordered = [
    ...members.value.map(member => member.userId),
    ...[...byUser.keys()].filter(key => !members.value.some(member => member.userId === key)),
  ]
  return ordered.flatMap((key) => {
    const group = byUser.get(key)
    if (!group)
      return []
    const summary = summarizeCartAmount(group.items)
    return [{
      key,
      userId: group.userId,
      attributedName: group.attributedName,
      member: group.attributedName ? null : members.value.find(item => item.userId === group.userId) ?? null,
      itemCount: group.items.length,
      quantity: group.items.reduce((sum, item) => sum + (item.requestedQuantity ?? 0), 0),
      amountText: summary.pricedCount === 0 ? '' : `¥${amountText(summary.amount)}`,
      unpricedCount: summary.unpricedCount,
    }]
  })
})

/** 成员 userId → 展示姓名，供「来自 XX」使用（列表里不暴露原始用户 ID） */
const memberNames = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  for (const member of members.value) {
    if (member.displayName)
      map[member.userId] = member.displayName
  }
  return map
})

/**
 * 成员展示名：没填姓名时回落成短标识。
 *
 * 直接渲染 `member.userId` 会是一串 19 位数字，既不可读又占满整行；
 * 取尾号既能让同船的人对上是哪位，又不至于把内部 ID 完整暴露在界面上。
 */
function memberLabel(member: SharedCartMember) {
  if (member.displayName)
    return member.displayName
  return member.userId ? `成员 ${member.userId.slice(-4)}` : '成员'
}

/** 明细来源：归属姓名（接龙代报）/ 本人 / 成员姓名 / 短标识，不暴露原始用户 ID */
function contributorLabel(item: SharedCartItem) {
  // 接龙代报的行即使操作者是自己也不能写「我」：货是接龙里那个人的
  if (item.attributedName)
    return item.attributedName
  if (isMine(item))
    return '我'
  return memberNames.value[item.userId] || (item.userId ? `成员 ${item.userId.slice(-4)}` : '成员')
}

/**
 * 成员汇总行的展示名。
 *
 * 与「来自 XX」分开是因为这里还有明细归属者可能不在成员列表里的情况：
 * 那种行没有成员资料可读，只能同样回落到短标识，而不是渲染空行。
 */
function memberSummaryLabel(row: { member: null | SharedCartMember, userId: string, attributedName: string }) {
  // 接龙代报的人可能不是系统用户，标签就是接龙原文里那个名字
  if (row.attributedName)
    return row.attributedName
  if (row.member)
    return memberLabel(row.member)
  return row.userId ? `成员 ${row.userId.slice(-4)}` : '成员'
}

/**
 * 成员头像文字。
 *
 * 取姓名的末字而不是首字：中文姓名末字（「建国」的「国」）辨识度更高，
 * 也避免了「张/李/王」这类大姓撞头像。未填姓名时没有可靠的字可用，
 * 回落一个中性的人形图标字符，而不是用户 ID 的数字（数字头像没有意义）。
 */
function memberAvatarText(member: SharedCartMember) {
  if (member.displayName)
    return member.displayName.slice(-1)
  return '员'
}

/** 邀请成员表单 */
const inviteState = reactive({
  visible: false,
  submitting: false,
  memberUserId: '',
  memberRole: MEMBER_ROLE_MEMBER,
})

/** 分享令牌（转发微信群用；由发起人生成） */
const shareToken = ref('')

/** 我的展示姓名表单（配送贴标签用，加入时填写一次） */
const nameState = reactive({
  visible: false,
  submitting: false,
  displayName: '',
})

/** 当前用户在本购物车的成员记录 */
const myMember = computed(() =>
  members.value.find(member => member.userId === currentUserId.value),
)

/** 未填写姓名时提示补填：标签打印依赖姓名，缺失会导致仓库无法分拣 */
const needDisplayName = computed(() =>
  !!myMember.value && !myMember.value.displayName,
)

/**
 * 明细维护权被收回：加购/改数量/移除入口会一并消失。
 *
 * 不解释的话，用户看到的是「按钮凭空没了」，只会反复重进或找客服。
 * 判据用服务端下发的 viewerCanEdit（本页权限一律以 viewer* 为准，不自行推断）；
 * 发起人在服务端天然可编辑、拿不到 false，因此无需额外排除。
 */
const editRevoked = computed(() =>
  collecting.value && cart.value?.viewerCanEdit === false,
)

function openSetName() {
  nameState.displayName = myMember.value?.displayName || ''
  nameState.visible = true
}

function submitDisplayName() {
  const name = nameState.displayName.trim()
  if (!name) {
    uni.showToast({ title: '请填写姓名', icon: 'none' })
    return
  }
  if (nameState.submitting)
    return
  nameState.submitting = true
  setMyDisplayName(cartId.value, name)
    .then(() => {
      nameState.visible = false
      uni.showToast({ title: '已保存', icon: 'success' })
      return fetchDetail()
    })
    .catch(() => {})
    .finally(() => {
      nameState.submitting = false
    })
}

/** 编辑自己的明细 */
const editState = reactive({
  visible: false,
  submitting: false,
  itemId: '',
  quantity: '',
  remark: '',
})

/** 提交整船订单表单 */
const confirmState = reactive({
  visible: false,
  submitting: false,
  /** itemId → 核定数量（字符串，便于输入框绑定） */
  approved: {} as Record<string, string>,
  /** itemId → 本次不采（提交时核定 0，该行不进订单） */
  skipped: {} as Record<string, boolean>,
  recipientName: '',
  recipientPhone: '',
  agentName: '',
  agentPhone: '',
  remark: '',
  /** 支付方式：''=在线支付；'3'=货到付款（共享车固定内部配送，默认货到付款、收货后线下结算） */
  paymentType: '3',
})

function fetchDetail() {
  if (!cartId.value)
    return Promise.resolve()
  loading.value = true
  return Promise.all([
    getSharedCart(cartId.value),
    getSharedCartMembers(cartId.value),
    getSharedCartItems(cartId.value),
  ])
    .then(([cartRes, memberRes, itemRes]) => {
      cart.value = cartRes ?? null
      members.value = memberRes ?? []
      items.value = itemRes ?? []
      loadFailed.value = false
      // 发起人预取分享令牌：微信分享卡片内容需在转发时同步可得，无法在转发回调里异步请求
      if (cartRes?.viewerIsOwner && isCartCollecting(cartRes.status) && !shareToken.value) {
        shareSharedCart(cartRes.id)
          .then((token) => {
            shareToken.value = token || ''
          })
          .catch(() => {
            // 令牌获取失败仅影响分享，不阻断购物车查看与加购
          })
      }
    })
    .catch(() => {
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

function isMine(item: SharedCartItem) {
  return !!currentUserId.value && item.userId === currentUserId.value
}

/** 只能维护自己的明细；发起人也不越权改动他人明细 */
function canEditItem(item: SharedCartItem) {
  return collecting.value && !!cart.value?.viewerCanEdit && isMine(item)
}

function copyCartNo() {
  if (!cart.value?.cartNo)
    return
  uni.setClipboardData({
    data: cart.value.cartNo,
    success: () => uni.showToast({ title: '编号已复制', icon: 'success' }),
  })
}

/**
 * 明细行操作面板。
 *
 * 成员只能改自己报的那一行（服务端 updateItem 同样校验归属）。
 * 「这一项本次不采」不放这里：那由确认人在提交弹层里核定 0 表达 ——
 * 移除是删掉别人的需求且不可追溯，核定为 0 则留痕，成员能看到自己的申请被怎么处理。
 */
const rowActionState = reactive<{ item: null | SharedCartItem, visible: boolean }>({
  item: null,
  visible: false,
})

function openRowActions(item: SharedCartItem) {
  // 无可执行动作时不弹空面板
  if (!canEditItem(item))
    return
  rowActionState.item = item
  rowActionState.visible = true
}

/**
 * 收起明细操作面板，并把里面挂的那一行一起放掉。
 *
 * 面板里的两个动作（改数量、移除）都会让这一行数据变样，留着旧 item 只会让
 * 重新打开时先闪一帧过期文案。收起一律走这里，避免只改 visible 漏掉 item。
 */
function closeRowActions() {
  rowActionState.visible = false
  rowActionState.item = null
}

function openEditItem(item: SharedCartItem) {
  // 编辑面板叠在明细操作面板之上，先把下面那层收掉再开，避免两个弹层叠着
  closeRowActions()
  editState.itemId = item.id
  editState.quantity = String(item.requestedQuantity ?? '')
  editState.remark = item.memberRemark ?? ''
  editState.visible = true
}

function submitEditItem() {
  const quantity = Number(editState.quantity)
  if (!quantity || quantity < 1) {
    uni.showToast({ title: '请填写数量', icon: 'none' })
    return
  }
  if (editState.submitting)
    return
  editState.submitting = true
  updateSharedCartItem(cartId.value, editState.itemId, {
    skuId: items.value.find(item => item.id === editState.itemId)?.skuId ?? '',
    requestedQuantity: quantity,
    memberRemark: editState.remark || undefined,
  })
    .then(() => {
      // 先收起弹层再拉详情：fetchDetail 会把明细行重建一遍，
      // 若此时弹层还开着，用户在等待期间点到的那一行 item 已经指向被替换掉的旧对象
      editState.visible = false
      uni.showToast({ title: '已更新', icon: 'success' })
      return fetchDetail()
    })
    .catch(() => {})
    .finally(() => {
      editState.submitting = false
    })
}

/**
 * 移除明细前先收起操作面板，再弹确认框。
 *
 * 确认框（原生 modal）会盖在弹层之上，弹层不先收就会出现「面板停在屏幕上、
 * 用户点确定、明细已经删掉，面板还留着」——这时面板里挂的 item 已不在列表里，
 * 点「修改我的申请数量」会基于一条不存在的明细发请求。
 */
function handleRemoveItem(item: SharedCartItem) {
  closeRowActions()
  uni.showModal({
    title: '移除明细',
    content: '确定移除这条明细吗？',
    success: (res) => {
      if (!res.confirm)
        return
      removeSharedCartItem(cartId.value, item.id)
        .then(() => {
          uni.showToast({ title: '已移除', icon: 'success' })
          return fetchDetail()
        })
        .catch(() => {})
    },
  })
}

/** 导入补给清单：独立的导入分包页承载「选文件 → 报告」两步 */
function goImport() {
  uni.navigateTo({
    url: `/sub-pages/order/shared-cart/import?cartId=${cartId.value}`,
  })
}

/** 粘贴接龙报货：微信群接龙整段粘贴 → 解析报告 → 并入明细 */
function goChainImport() {
  uni.navigateTo({
    url: `/sub-pages/order/shared-cart/chain-import?cartId=${cartId.value}`,
  })
}

/** 添加商品：进入船供目录的「共享购物车选货」模式 */
function goAddGoods() {
  uni.navigateTo({
    url: `/sub-pages/product/ship-supply/index?scene=2&sharedCartId=${cartId.value}`,
  })
}

function openInvite() {
  inviteState.memberUserId = ''
  inviteState.memberRole = MEMBER_ROLE_MEMBER
  inviteState.visible = true
}

function submitInvite() {
  const memberUserId = inviteState.memberUserId.trim()
  if (!memberUserId) {
    uni.showToast({ title: '请填写成员用户ID', icon: 'none' })
    return
  }
  if (inviteState.submitting)
    return
  inviteState.submitting = true
  inviteSharedCartMember(cartId.value, memberUserId, inviteState.memberRole)
    .then(() => {
      inviteState.visible = false
      uni.showToast({ title: '已邀请', icon: 'success' })
      return fetchDetail()
    })
    .catch(() => {})
    .finally(() => {
      inviteState.submitting = false
    })
}

/** 正在关闭购物车（防重复点击） */
const closing = ref(false)

function handleClose() {
  if (closing.value)
    return
  uni.showModal({
    title: '关闭购物车',
    content: '关闭后成员将无法继续加购，确定关闭吗？',
    success: (res) => {
      if (!res.confirm)
        return
      closing.value = true
      closeSharedCart(cartId.value)
        .then(() => {
          uni.showToast({ title: '已关闭', icon: 'success' })
          return fetchDetail()
        })
        .catch(() => {})
        .finally(() => {
          closing.value = false
        })
    },
  })
}

/** 船舶与靠港选择器：复用时必须有当前靠港上下文 */
const shipPickerVisible = ref(false)
const reusing = ref(false)

/**
 * 历史单复用：仅对已结束的单开放（进行中的单本身就是本轮清单）。
 *
 * 另外要求当前船舶上下文与源单一致 —— 跨船复用没有业务意义（服务端也会拒），
 * 与其让用户点完再看到失败提示，不如直接不显示这个按钮。
 * 尚无上下文时不拦，由用户在选择器里选（选错船时服务端仍会兜底拒绝）。
 */
const canReuse = computed(() => {
  if (!cartReadonly.value || !cart.value) {
    return false
  }
  const currentVesselId = shipContextStore.vesselId
  return !currentVesselId || currentVesselId === cart.value.vesselId
})

/**
 * 靠港快照失效：整车的 vesselCallId 是创建时刻的快照，收集中可能跨越靠港 ETD。
 * 仅收集中的车提示（已提交/关闭的车顺延无意义）；提交时服务端会自动顺延到本船
 * 最新可用靠港，这里预告避免用户在提交时才撞到报错。
 *
 * 判据用服务端下发的 callOrderable，**不能**用「vesselName 为空」反推：
 * 展示字段取自展示快照，靠港结束后照样有船名（2026-10-09 修正），
 * 用名称判断会让真正失效的清单反而不提示。undefined 视为未知，不提示。
 */
const callSnapshotStale = computed(() =>
  !!cart.value
  && !cartReadonly.value
  && cart.value.callOrderable === false,
)

/**
 * 一键复用历史补给单。
 *
 * 复用出来的是**本轮**采购，因此靠港计划取当前上下文，而不是源单那个已结束的靠港；
 * 船必须与源单一致（服务端也会校验，这里先做一次本地提示以免白跑一趟）。
 */
function handleReuse() {
  if (!cart.value || reusing.value) {
    return
  }
  // 兜底：按钮已按船舶过滤，这里再挡一次（例如上下文在页面停留期间被切走）
  if (shipContextStore.vesselId && shipContextStore.vesselId !== cart.value.vesselId) {
    uni.showToast({ title: '只能复用到同一条船', icon: 'none' })
    return
  }
  if (!shipContextStore.hasVesselContext) {
    shipPickerVisible.value = true
    return
  }
  reusing.value = true
  reuseSharedCartFromHistory(cart.value.id, {
    vesselId: cart.value.vesselId,
    vesselCallId: shipContextStore.vesselCallId,
  })
    .then((result) => {
      if (!result?.cartId) {
        return
      }
      // 部分成功要如实告知：跳过项往往是「本次清单已有该商品」或原明细没有可用数量
      const skipped = result.skippedCount ?? 0
      uni.showToast({
        title: skipped > 0
          ? `已复用 ${result.reusedCount} 项，跳过 ${skipped} 项`
          : `已复用 ${result.reusedCount} 项`,
        icon: 'none',
      })
      uni.navigateTo({ url: `/sub-pages/order/shared-cart/detail?id=${result.cartId}` })
    })
    .catch(() => {})
    .finally(() => {
      reusing.value = false
    })
}

function openConfirm() {
  // 核定数量默认取成员申请数量；默认全部参与本次采购
  confirmState.approved = {}
  confirmState.skipped = {}
  items.value.forEach((item) => {
    confirmState.approved[item.id] = String(item.requestedQuantity ?? '')
    confirmState.skipped[item.id] = false
  })
  confirmState.remark = ''
  confirmState.paymentType = '3'
  confirmState.visible = true
}

/** 切换「本次不采」：采与不采互斥，不采时数量输入框置灰（提交按 0 走） */
function toggleSkipped(item: SharedCartItem) {
  confirmState.skipped[item.id] = !confirmState.skipped[item.id]
}

/** 参与本次采购的项数：底部按钮与提示都用它，避免用户提交了才知道全被排除 */
const confirmBuyCount = computed(() =>
  items.value.filter(item => !confirmState.skipped[item.id]).length,
)

/**
 * 提交弹层里某一行最终的计价数量。
 *
 * 输入框是字符串，可能为空或非法；这两种情况与提交逻辑同口径地回落到申请量
 * （见 submitConfirm），否则弹层里的金额会和实际下单数量对不上。
 */
function confirmQuantityOf(item: SharedCartItem): number {
  if (confirmState.skipped[item.id])
    return 0
  const raw = Number(confirmState.approved[item.id])
  return Number.isFinite(raw) && raw > 0 ? raw : (item.requestedQuantity ?? 0)
}

/**
 * 提交前的预估金额：跟随核定数量与「本次不采」实时变化。
 *
 * 这正是用户提交时最想知道的那个数 —— 改完数量却看不到总额变化，
 * 只能点提交之后去订单里才发现金额不对。
 */
const confirmAmount = computed(() => {
  let amount = 0
  let unpricedCount = 0
  for (const item of items.value) {
    const quantity = confirmQuantityOf(item)
    if (quantity <= 0)
      continue
    if (item.unitPrice === null || item.unitPrice === undefined) {
      unpricedCount += 1
      continue
    }
    amount += Number(item.unitPrice) * quantity
  }
  return { amount: Math.round(amount * 100) / 100, unpricedCount }
})

const confirmAmountText = computed(() =>
  confirmAmount.value.amount > 0 || confirmAmount.value.unpricedCount === 0
    ? `¥${amountText(confirmAmount.value.amount)}`
    : '',
)

/** 提交弹层里某行的金额文案，跟随核定数量；排除与待核价分别标示，不留空 */
function confirmLineAmountText(item: SharedCartItem): string {
  const quantity = confirmQuantityOf(item)
  if (quantity <= 0)
    return ''
  if (item.unitPrice === null || item.unitPrice === undefined)
    return '待核价'
  return `¥${amountText(Number(item.unitPrice) * quantity)}`
}

/**
 * 底部主操作：同一时刻只强调一件事。
 *
 * 已生成订单 → 查看订单；可提交 → 提交整船订单；两者都不满足时不出主按钮。
 * 「关闭购物车」不再与提交并排等权，避免误触一个不可逆动作。
 */
const primaryAction = computed<null | { handler: () => void, text: string }>(() => {
  if (cart.value?.submitOrderId) {
    return { text: '查看整船订单', handler: goOrder }
  }
  if (canConfirmNow.value) {
    return { text: `提交整船订单（${items.value.length} 项）`, handler: openConfirm }
  }
  return null
})

/**
 * 底部条左侧的金额数值：用户不必点开提交弹层就知道这批大概多少钱。
 *
 * 只在收集中显示（已提交的单实付在订单里，再挂一个按申请量算的数会打架），
 * 且一项都算不出时不占位（宁可空着，也不显示一个骗人的 0）。
 */
const footerAmountValue = computed(() => {
  if (!collecting.value || cartAmount.value.pricedCount === 0)
    return ''
  return `¥${amountText(cartAmount.value.amount)}`
})

/**
 * 底部金额的标签：有商品取不到价时写明「部分待核价」。
 *
 * 不加这句的话，偏小的合计在底部条上完全没有解释，
 * 而底部条恰恰是用户最后看到金额的地方。
 */
const footerAmountLabel = computed(() =>
  cartAmountIncomplete.value ? '预估 · 部分待核价' : '预估',
)

/** 底部次要操作：历史单的一键复用（进行中的单不给，复用会命中同一张车） */
const secondaryAction = computed<null | { disabled: boolean, handler: () => void, text: string }>(() => {
  if (canReuse.value) {
    return { text: '复用为本次补给单', handler: handleReuse, disabled: reusing.value }
  }
  return null
})

function submitConfirm() {
  if (confirmState.submitting)
    return
  // 「本次不采」的行显式提交核定 0，由服务端从订单里剔除并留痕；
  // 已提交前再兜一次：全部排除时服务端也会拒绝，但本地先拦住能少一次无效请求。
  if (confirmBuyCount.value === 0) {
    uni.showToast({ title: '至少要保留一项', icon: 'none' })
    return
  }
  // 数量口径统一走 confirmQuantityOf（弹层金额也是同一份实现），
  // 否则「界面显示 3 件 × 单价」和「实际提交 5 件」会各自算各自的。
  const approvedQuantities = items.value.map(item => ({
    itemId: item.id,
    quantity: confirmQuantityOf(item),
  }))
  confirmState.submitting = true
  confirmSharedCart(cartId.value, {
    approvedQuantities,
    recipientName: confirmState.recipientName || undefined,
    recipientPhone: confirmState.recipientPhone || undefined,
    agentName: confirmState.agentName || undefined,
    agentPhone: confirmState.agentPhone || undefined,
    remark: confirmState.remark || undefined,
    paymentType: confirmState.paymentType || undefined,
  })
    .then((orderId) => {
      confirmState.visible = false
      uni.showToast({ title: '已提交整船订单', icon: 'success' })
      return fetchDetail().then(() => {
        if (orderId) {
          // 延时跳转期间用户可能已自行离开本页：对已销毁页面发路由会触发
          // 微信 `routeDone with a webviewId xxx is not found`，故先记住当前页再确认。
          const currentPage = getCurrentPages().at(-1)
          setTimeout(() => {
            if (getCurrentPages().at(-1) !== currentPage)
              return
            uni.navigateTo({ url: `/sub-pages/order/order-detail/index?id=${orderId}` })
          }, 800)
        }
      })
    })
    .catch(() => {})
    .finally(() => {
      confirmState.submitting = false
    })
}

function goOrder() {
  if (cart.value?.submitOrderId) {
    uni.navigateTo({ url: `/sub-pages/order/order-detail/index?id=${cart.value.submitOrderId}` })
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function goSharedCartList() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}

onLoad((options) => {
  cartId.value = options?.id ?? ''
  const token = options?.token ?? ''
  // 群里点开分享卡片：凭 token 自助加入（服务端会自动补建该船成员关系），
  // 成功后直接落到购物车详情，无需用户再手填邀请码或用户ID。
  if (token && !cartId.value) {
    joining.value = true
    joinSharedCartByToken(token)
      .then((joinedCartId) => {
        if (joinedCartId) {
          cartId.value = joinedCartId
          shareToken.value = token
          uni.showToast({ title: '已加入本次采购', icon: 'success' })
        }
      })
      .catch(() => {
        joinFailed.value = true
      })
      .finally(() => {
        joining.value = false
      })
  }
})

onShareAppMessage(() => {
  const id = cartId.value || cart.value?.id || ''
  const vessel = cart.value?.vesselName || shipContextStore.vesselName || '本船'
  return {
    title: `${vessel}正在收集采购需求，点此选购`,
    path: `/sub-pages/order/shared-cart/detail?id=${id}&token=${shareToken.value}`,
  }
})

/**
 * 发起人分享到微信群。
 *
 * 微信要求分享卡片内容在转发动作发生时即刻可得（onShareAppMessage 不能异步），
 * 因此必须先取到令牌再触发转发；令牌生成后缓存，后续转发无需重复请求。
 */
function handleShare() {
  if (!cartId.value)
    return
  if (shareToken.value) {
    uni.showToast({ title: '请点右上角「···」发送到群', icon: 'none' })
    return
  }
  shareSharedCart(cartId.value)
    .then((token) => {
      shareToken.value = token || ''
      uni.showToast({ title: '请点右上角「···」发送到群', icon: 'none' })
    })
    .catch(() => {})
}

onShow(() => {
  void fetchDetail()
})
</script>

<template>
  <view class="shared-cart-detail">
    <hr-navbar title="共享购物车详情" />

    <!-- 凭分享卡片加入中 -->
    <view v-if="joining" class="py-80rpx text-center text-26rpx text-gray-400">
      正在加入本次采购...
    </view>

    <!-- 分享链接失效：给出可执行的出路，而不是只报错 -->
    <view v-else-if="joinFailed" class="mx-24rpx mt-40rpx rounded-24rpx bg-white p-32rpx text-center">
      <view class="text-28rpx font-bold">
        该分享链接已失效
      </view>
      <view class="mt-10rpx text-24rpx text-gray-500">
        采购收集超过 24 小时会自动关闭，或已被发起人提交。请联系发起人重新分享。
      </view>
      <button class="mt-24rpx h-72rpx text-26rpx leading-72rpx" type="primary" @tap="goSharedCartList">
        查看我参与的采购
      </button>
    </view>

    <view v-else-if="loading && !cart" class="py-80rpx text-center text-26rpx text-gray-400">
      加载中...
    </view>

    <view v-else-if="loadFailed && !cart" class="py-80rpx text-center">
      <view class="text-26rpx text-gray-400">
        加载失败，请检查网络后重试
      </view>
      <view class="mt-20rpx text-26rpx text-blue-500" @tap="fetchDetail">
        重新加载
      </view>
    </view>

    <template v-else-if="cart">
      <!-- 概览：船舶 + 靠港标识「哪一次采购」，收集截止单独强调 -->
      <view class="mx-24rpx mt-24rpx overflow-hidden rounded-28rpx bg-white">
        <view class="p-32rpx">
          <view class="flex items-start justify-between">
            <view class="mr-16rpx min-w-0 flex-1">
              <view class="truncate text-38rpx font-bold">
                {{ cart.vesselName || '船舶信息待同步' }}
              </view>
              <view class="mt-8rpx text-26rpx text-gray-500">
                {{ cart.portName || '港口待定' }} {{ cart.berth }}
              </view>
            </view>
            <view
              class="flex-none rounded-full px-16rpx py-6rpx text-24rpx"
              :style="`background:${cartStatusTheme(cart.status).bg};color:${cartStatusTheme(cart.status).text}`"
            >
              {{ cartStatusLabel(cart.status) }}
            </view>
          </view>

          <!--
            靠港快照失效预告：整车的靠港是创建时刻的快照，收集中可能跨越靠港 ETD；
            快照失效时服务端补不出上下文（船舶/港口显示占位文案），提交时后端会自动
            顺延到本船最新可用靠港。这里提前说明，别等提交报「靠港计划不可用」才暴露。
          -->
          <view
            v-if="callSnapshotStale"
            class="mt-24rpx rounded-16rpx bg-orange-50 px-24rpx py-18rpx text-24rpx text-orange-600"
          >
            该车的靠港计划已结束或暂不可用，提交时将自动顺延到本船最新可用靠港
          </view>

          <!--
            收集截止是全页最时效的信息，单独成块：
            倒计时回答「还有多久」，绝对时刻用于核对「几点截止」。
            不足 1 小时转警示色（含「即将截止」），提示马上要关门。
          -->
          <view
            v-if="collecting && cart.expiresAt"
            class="mt-24rpx rounded-20rpx px-24rpx py-22rpx"
            :style="expiryUrgent ? 'background:#FEF2F2' : 'background:#FFFBEB'"
          >
            <view class="flex items-center justify-between">
              <text class="text-26rpx" :style="expiryUrgent ? 'color:#B91C1C' : 'color:#92400E'">
                收集截止
              </text>
              <text
                class="text-28rpx font-bold"
                :style="expiryUrgent ? 'color:#B91C1C' : 'color:#92400E'"
              >
                {{ expiryCountdown || '即将截止' }}
              </text>
            </view>
            <view
              v-if="expiryPoint"
              class="mt-6rpx text-24rpx"
              :style="expiryUrgent ? 'color:#DC2626' : 'color:#B45309'"
            >
              {{ expiryPoint }}
            </view>
          </view>

          <view
            v-if="cart.remark"
            class="mt-20rpx rounded-16rpx bg-gray-50 px-24rpx py-18rpx text-26rpx text-gray-600"
          >
            备注：{{ cart.remark }}
          </view>

          <!-- 编号是受理号性质的参考信息：弱化展示，但要能一键复制 -->
          <view class="mt-20rpx flex items-center justify-between">
            <text class="truncate text-24rpx text-gray-400">
              编号 {{ cart.cartNo }}
            </text>
            <text class="ml-16rpx flex-none text-24rpx text-blue-500" @tap="copyCartNo">
              复制
            </text>
          </view>
        </view>

        <!-- 发起人的协作入口单独成行：分享到群是拉人进来的主路径，值得一个明确的落点 -->
        <view v-if="cart.viewerIsOwner && collecting" class="flex border-t border-gray-100">
          <view class="flex flex-1 items-center justify-center py-26rpx text-28rpx" @tap="handleShare">
            分享到群
          </view>
          <view class="flex flex-1 items-center justify-center border-l border-gray-100 py-26rpx text-28rpx" @tap="openInvite">
            邀请成员
          </view>
        </view>
      </view>

      <!-- 姓名缺失会挡住配送贴标签，用横幅提醒，而不是塞进成员列表里当一行小字 -->
      <view
        v-if="needDisplayName"
        class="mx-24rpx mt-20rpx flex items-center justify-between rounded-20rpx px-24rpx py-20rpx"
        style="background:#FFF7ED"
        @tap="openSetName"
      >
        <view class="mr-16rpx min-w-0 flex-1">
          <view class="text-26rpx font-bold" style="color:#C2410C">
            填写你的姓名
          </view>
          <view class="mt-4rpx text-22rpx" style="color:#9A3412">
            配送到船时按姓名给你的商品单独贴标签
          </view>
        </view>
        <text class="flex-none text-26rpx" style="color:#C2410C">
          去填写
        </text>
      </view>

      <!--
        明细维护权被收回：入口被隐藏是最容易被当成故障的一种，必须给出原因，
        否则用户只会反复重进或找客服（无「去处理」按钮，本人无法自助恢复）。
      -->
      <view
        v-if="editRevoked"
        class="mx-24rpx mt-20rpx rounded-20rpx px-24rpx py-20rpx"
        style="background:#F1EFE8"
      >
        <view class="text-26rpx font-bold" style="color:#5F5E5A">
          你当前只能查看清单
        </view>
        <view class="mt-4rpx text-22rpx" style="color:#888780">
          明细维护权限已被收回，暂时不能加购或修改数量；如需恢复请联系发起人
        </view>
      </view>

      <!--
        明细是本页主内容，因此排在成员之前：成员是行政信息，
        不该把清单挤到首屏之外（原来 12 行明细要滚动才看得到）。
      -->
      <view class="mx-24rpx mt-24rpx rounded-28rpx bg-white p-32rpx">
        <view class="flex items-center justify-between">
          <view class="flex items-baseline">
            <text class="text-32rpx font-bold">
              明细
            </text>
            <text class="ml-8rpx text-24rpx text-gray-400">
              {{ items.length }} 项
            </text>
          </view>
          <view class="flex items-center gap-28rpx">
            <!-- 导入是「排计划」的批量形态：确认人/发起人职责，与服务端 requireConfirmer 一致 -->
            <text
              v-if="collecting && cart.viewerCanConfirm"
              class="text-26rpx text-emerald-600"
              @tap="goChainImport"
            >
              粘贴接龙
            </text>
            <text
              v-if="collecting && cart.viewerCanConfirm"
              class="text-26rpx text-emerald-600"
              @tap="goImport"
            >
              导入 Excel
            </text>
            <text v-if="collecting && cart.viewerCanEdit" class="text-26rpx text-blue-500" @tap="goAddGoods">
              添加商品
            </text>
          </view>
        </view>

        <!--
          数量与金额分两行：数量回答「买了多少」，金额回答「大概多少钱」。
          合计行只在能算出金额时出现（全部待核价时给空字符串，不留一个孤零零的「预估」）。
        -->
        <view v-if="itemSummaryText" class="mt-20rpx flex items-baseline justify-between">
          <text class="text-24rpx text-gray-500">
            {{ itemSummaryText }}
          </text>
          <text v-if="cartAmountText" class="text-26rpx font-bold" style="color:#C2410C">
            {{ cartAmountText }}
          </text>
        </view>
        <!--
          有商品取不到价时合计偏小：必须说出来，否则用户会把这个数当成真实总价。
        -->
        <view v-if="cartAmountIncomplete" class="mt-8rpx text-22rpx" style="color:#B45309">
          {{ unpricedItemText }}暂时取不到售价，未计入预估金额
        </view>

        <view v-if="items.length === 0" class="py-48rpx text-center text-26rpx text-gray-400">
          <template v-if="collecting">
            还没有成员加购，点「添加商品」开始
          </template>
          <template v-else-if="cartReadonly">
            本次未提交任何明细
          </template>
          <template v-else>
            暂无明细
          </template>
        </view>
        <!--
          明细行只展示「谁报了什么、报了多少」。行内操作收进「···」面板，
          否则 12 行 × 3 个彩色文字按钮会把清单刷成一片噪声。
        -->
        <view v-else class="mt-12rpx">
          <view
            v-for="(item, index) in items"
            :key="item.id"
            class="flex py-28rpx"
            :class="index > 0 ? 'border-t border-gray-100' : ''"
            @tap="openRowActions(item)"
          >
            <image
              class="h-104rpx w-104rpx flex-none rounded-16rpx bg-gray-100"
              mode="aspectFill"
              :src="resolveImageSrc(item.picUrl)"
            />
            <view class="ml-24rpx min-w-0 flex-1">
              <view class="flex items-start justify-between">
                <view class="row-name">
                  {{ item.spuName || `SKU ${item.skuId}` }}
                </view>
                <!--
                  行金额放品名右侧：这是用户扫清单时最想看的数，
                  埋在数量行里要逐行读才知道多少钱。
                -->
                <text
                  v-if="itemAmountText(item)"
                  class="ml-12rpx flex-none text-26rpx font-bold"
                  :style="itemAmountOf(item) === null ? 'color:#9AA4B2' : 'color:#111827'"
                >
                  {{ itemAmountText(item) }}
                </text>
                <!-- 已被核定为「本次不采」的行：标出来，成员才知道自己报的这项没买 -->
                <text
                  v-if="item.approvedQuantity === 0"
                  class="ml-12rpx flex-none rounded-8rpx px-12rpx py-4rpx text-22rpx"
                  style="background:#F1EFE8;color:#5F5E5A"
                >
                  本次不采
                </text>
              </view>

              <view class="mt-10rpx flex items-center text-24rpx text-gray-500">
                <text>
                  申请 {{ item.requestedQuantity }}
                  <text v-if="item.approvedQuantity != null && item.approvedQuantity > 0" class="text-amber-600">
                    · 核定 {{ item.approvedQuantity }}
                  </text>
                </text>
                <!-- 单价与行金额并列，用户能自己核对「这个数是怎麼来的」 -->
                <text class="ml-16rpx text-gray-400">
                  单价 {{ unitPriceText(item) }}
                </text>
              </view>
              <view v-if="item.memberRemark" class="mt-10rpx text-24rpx text-gray-400">
                备注：{{ item.memberRemark }}
              </view>

              <view class="mt-14rpx flex items-center justify-between">
                <text class="min-w-0 flex-1 truncate text-24rpx text-gray-400">
                  来自 {{ contributorLabel(item) }}
                </text>
                <view class="ml-16rpx flex flex-none items-center gap-28rpx">
                  <text
                    v-if="canEditItem(item)"
                    class="text-26rpx text-gray-400"
                    @tap.stop="openRowActions(item)"
                  >
                    ···
                  </text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 成员：行政信息，放在明细之后 -->
      <view class="mx-24rpx mt-24rpx rounded-28rpx bg-white p-32rpx">
        <view class="flex items-center justify-between">
          <view class="flex items-baseline">
            <text class="text-32rpx font-bold">
              成员
            </text>
            <text class="ml-8rpx text-24rpx text-gray-400">
              {{ members.length }} 人
            </text>
          </view>
          <text v-if="myMember" class="text-26rpx text-blue-500" @tap="openSetName">
            {{ myMember.displayName ? '修改姓名' : '填写姓名' }}
          </text>
        </view>
        <view
          v-for="row in memberSummaries"
          :key="row.key"
          class="mt-20rpx flex items-center justify-between"
        >
          <view class="mr-16rpx min-w-0 flex flex-1 items-center">
            <!-- 姓名首字做头像：成员通常只有 2~3 人，头像比一行纯文字更好辨认 -->
            <view
              class="mr-20rpx h-64rpx w-64rpx flex flex-none items-center justify-center rounded-full text-26rpx text-white font-bold"
              :style="`background:${row.userId === currentUserId ? '#378ADD' : '#9AA4B2'}`"
            >
              {{ row.attributedName ? row.attributedName.slice(-1) : (row.member ? memberAvatarText(row.member) : '员') }}
            </view>
            <view class="min-w-0 flex-1">
              <view class="truncate text-28rpx text-gray-700">
                {{ memberSummaryLabel(row) }}
                <text v-if="!row.attributedName && row.userId === currentUserId" class="text-blue-500">
                  （我）
                </text>
              </view>
              <!--
                每人加购了几项、多少钱：人数相同的清单，真正要对比的是
                「谁报了多少」；只显示角色，发起人得自己数明细行才知道。
              -->
              <view class="mt-4rpx text-22rpx text-gray-400">
                <text v-if="row.attributedName">
                  接龙代报
                </text>
                <text v-else-if="row.member">
                  {{ memberRoleLabel(row.member.memberRole) }}
                </text>
                <text v-if="row.itemCount > 0" class="ml-8rpx">
                  · {{ row.itemCount }} 项
                </text>
                <text v-if="row.quantity > 0" class="ml-8rpx">
                  · 共 {{ row.quantity }}
                </text>
                <!-- 该成员有商品取不到价：金额偏小，别让用户以为他报得少 -->
                <text v-if="row.unpricedCount > 0" class="ml-8rpx" style="color:#B45309">
                  · {{ row.unpricedCount }} 项待核价
                </text>
              </view>
            </view>
          </view>
          <view class="flex flex-none items-center gap-12rpx">
            <text v-if="row.amountText" class="text-26rpx text-gray-700">
              {{ row.amountText }}
            </text>
            <text
              v-if="row.member && row.member.canConfirm === '1'"
              class="flex-none rounded-8rpx px-12rpx py-4rpx text-22rpx"
              style="background:#E1F5EE;color:#0F6E56"
            >
              可提交
            </text>
          </view>
        </view>
      </view>

      <!-- 整船订单入口：已生成订单后这是查看结果的主路径 -->
      <view
        v-if="cart.submitOrderId"
        class="mx-24rpx mt-24rpx flex items-center justify-between rounded-28rpx p-32rpx"
        style="background:linear-gradient(135deg,#0F6E56,#0B8A6B)"
        @tap="goOrder"
      >
        <view class="min-w-0 flex-1">
          <view class="text-30rpx text-white font-bold">
            整船订单已生成
          </view>
          <view class="mt-6rpx text-24rpx text-white opacity-85">
            本次提交的商品已按成员分别生成订单明细
          </view>
        </view>
        <text class="i-carbon:chevron-right ml-16rpx flex-none text-white opacity-85" />
      </view>

      <!--
        关闭购物车：不可逆且低频，放在内容末尾的独立区域，
        与主操作拉开距离，避免和「提交」并排造成误触。
      -->
      <view
        v-if="cart.viewerIsOwner && !cartReadonly"
        class="mx-24rpx mt-24rpx rounded-28rpx bg-white p-32rpx"
      >
        <view class="text-26rpx text-gray-500">
          关闭后成员无法继续加购，已收集的明细会保留
        </view>
        <button
          class="mt-24rpx h-76rpx text-28rpx leading-76rpx"
          :disabled="closing"
          @tap="handleClose"
        >
          关闭购物车
        </button>
      </view>

      <!-- 底部固定条占位：不遮住最后一张卡片 -->
      <view v-if="primaryAction || secondaryAction" class="footer-spacer" />
      <view v-if="!canConfirmNow && collecting && items.length === 0" class="px-40rpx pb-40rpx text-center text-26rpx text-gray-400">
        至少需要一条明细才能提交
      </view>
    </template>

    <view v-else class="py-80rpx text-center">
      <view class="text-26rpx text-gray-400">
        购物车不存在或你无权访问
      </view>

      <view class="mt-20rpx text-26rpx text-blue-500" @tap="goLogin">
        去登录
      </view>
    </view>

    <!--
      底部固定主操作：提交/查看订单/复用三者互斥或主次分明，同一时刻只强调一个，
      避免原来「提交整船订单 + 关闭购物车」两个等权按钮并排让人难以抉择。
    -->
    <view v-if="primaryAction || secondaryAction" class="action-bar">
      <button
        v-if="secondaryAction"
        class="action-bar__ghost mr-20rpx flex-none !m-0"
        :disabled="secondaryAction.disabled"
        @tap="secondaryAction.handler"
      >
        {{ secondaryAction.text }}
      </button>
      <!--
        金额放主 CTA 左侧：这是全页最想让人看到的一个数（这批多少钱），
        又不用和「提交」抢注意力 —— 点它不触发任何操作。
      -->
      <view v-if="footerAmountValue" class="mr-20rpx flex-none">
        <view class="action-bar__amount">
          {{ footerAmountValue }}
        </view>
        <view class="action-bar__amount-label">
          {{ footerAmountLabel }}
        </view>
      </view>
      <!--
        主 CTA 用品牌橙红渐变，与购物车「去结算」同款：
        原生 button 的 type=primary 走 uni 默认蓝，与全站品牌色不一致。
      -->
      <view
        v-if="primaryAction"
        class="action-bar__primary"
        @tap="primaryAction.handler"
      >
        {{ primaryAction.text }}
      </view>
    </view>

    <!--
      明细行操作面板：按行归属与服务端权限动态生成，不呈现不可用的动作。
      它是叠在编辑面板下面的一层，两个动作都会把自己收掉（见 closeRowActions），
      面板本身不提供确认语义，因此这里的「收起」只表示放弃操作。
    -->
    <wd-popup
      v-model="rowActionState.visible"
      position="bottom"
      :safe-area-inset-bottom="true"
      custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
      @close="rowActionState.item = null"
    >
      <view class="px-32rpx pb-32rpx pt-28rpx">
        <view class="text-32rpx font-bold">
          明细操作
        </view>
        <view class="mt-8rpx truncate text-24rpx text-gray-500">
          {{ rowActionState.item ? (rowActionState.item.spuName || `SKU ${rowActionState.item.skuId}`) : '' }}
        </view>
        <view
          v-if="rowActionState.item"
          class="mt-4rpx text-22rpx text-gray-400"
        >
          申请 {{ rowActionState.item.requestedQuantity }}
          · 单价 {{ unitPriceText(rowActionState.item) }}
          <text v-if="itemAmountText(rowActionState.item)">
            · {{ itemAmountText(rowActionState.item) }}
          </text>
        </view>
        <view class="mt-24rpx">
          <view
            v-if="rowActionState.item && canEditItem(rowActionState.item)"
            class="sheet-row"
            @tap="openEditItem(rowActionState.item)"
          >
            修改我的申请数量
          </view>
          <view
            v-if="rowActionState.item && canEditItem(rowActionState.item)"
            class="sheet-row sheet-row--danger"
            @tap="handleRemoveItem(rowActionState.item)"
          >
            移除这条明细
          </view>
        </view>
      </view>
    </wd-popup>

    <!-- 我的姓名（配送贴标签用） -->
    <wd-popup
      v-model="nameState.visible"
      position="bottom"
      :safe-area-inset-bottom="true"
      custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    >
      <view class="px-32rpx pb-32rpx pt-28rpx">
        <view class="text-32rpx font-bold">
          我的姓名
        </view>
        <view class="mt-8rpx text-22rpx text-gray-500">
          用于配送到船时把你的商品单独贴标签。填写一次即可，之后可随时修改。
        </view>
        <input
          v-model="nameState.displayName"
          class="mt-20rpx h-80rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
          placeholder="请输入姓名，如：王建国"
          :maxlength="64"
        >
        <view class="sheet-actions">
          <wd-button type="info" custom-class="sheet-actions__cancel" @click="nameState.visible = false">
            取消
          </wd-button>
          <wd-button
            type="primary"
            :loading="nameState.submitting"
            custom-class="sheet-actions__ok"
            @click="submitDisplayName"
          >
            保存
          </wd-button>
        </view>
      </view>
    </wd-popup>

    <!-- 邀请成员 -->
    <wd-popup
      v-model="inviteState.visible"
      position="bottom"
      :safe-area-inset-bottom="true"
      custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    >
      <view class="px-32rpx pb-32rpx pt-28rpx">
        <view class="text-32rpx font-bold">
          邀请成员
        </view>
        <view class="mt-8rpx text-22rpx text-gray-500">
          让对方在微信里点开你的分享卡片即可加入，也可以直接填对方的商城用户 ID
        </view>
        <input
          v-model="inviteState.memberUserId"
          class="mt-20rpx h-80rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
          placeholder="成员商城用户ID"
        >
        <view class="mt-16rpx flex gap-16rpx">
          <view
            v-for="option in [{ label: '普通成员', value: MEMBER_ROLE_MEMBER }, { label: '采购确认人', value: MEMBER_ROLE_CONFIRMATOR }]"
            :key="option.value"
            class="rounded-30rpx px-24rpx py-8rpx text-24rpx"
            :style="inviteState.memberRole === option.value
              ? 'background:#378ADD;color:#fff'
              : 'background:#F1EFE8;color:#5F5E5A'"
            @tap="inviteState.memberRole = option.value"
          >
            {{ option.label }}
          </view>
        </view>
        <view class="sheet-actions">
          <wd-button type="info" custom-class="sheet-actions__cancel" @click="inviteState.visible = false">
            取消
          </wd-button>
          <wd-button
            type="primary"
            :loading="inviteState.submitting"
            custom-class="sheet-actions__ok"
            @click="submitInvite"
          >
            邀请
          </wd-button>
        </view>
      </view>
    </wd-popup>

    <!-- 编辑明细 -->
    <wd-popup
      v-model="editState.visible"
      position="bottom"
      :safe-area-inset-bottom="true"
      custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    >
      <view class="px-32rpx pb-32rpx pt-28rpx">
        <view class="text-32rpx font-bold">
          修改我的明细
        </view>
        <view class="mt-8rpx text-22rpx text-gray-500">
          只改你要申请的数量，最终采购量由确认人排计划时核定
        </view>
        <input
          v-model="editState.quantity"
          class="mt-20rpx h-80rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
          type="number"
          placeholder="申请数量"
        >
        <input
          v-model="editState.remark"
          class="mt-16rpx h-80rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
          placeholder="备注（选填）"
        >
        <view class="sheet-actions">
          <wd-button type="info" custom-class="sheet-actions__cancel" @click="editState.visible = false">
            取消
          </wd-button>
          <wd-button
            type="primary"
            :loading="editState.submitting"
            custom-class="sheet-actions__ok"
            @click="submitEditItem"
          >
            保存
          </wd-button>
        </view>
      </view>
    </wd-popup>

    <!--
      提交整船订单：明细多时表单会很长，用可滚动的弹层body承载，
      避免弹层顶到屏幕外（原来内联在页面里，点完还要往下滚很久才看到）。
    -->
    <wd-popup
      v-model="confirmState.visible"
      position="bottom"
      :safe-area-inset-bottom="true"
      custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    >
      <view class="px-32rpx pt-28rpx">
        <view class="text-32rpx font-bold">
          核定数量并提交
        </view>
        <view class="mt-8rpx text-22rpx text-gray-500">
          按成员分别生成订单明细，配送时可为每人单独贴标签。这次不买的，点「本次不采」。
        </view>
      </view>
      <scroll-view scroll-y class="confirm-body">
        <view class="px-32rpx">
          <view v-for="item in items" :key="item.id" class="mt-20rpx">
            <view class="flex items-center justify-between">
              <view class="min-w-0 flex-1 truncate text-26rpx text-gray-700">
                {{ item.spuName || `SKU ${item.skuId}` }}
              </view>
              <!-- 显式排除项：不留空、不猜，点一下就是"这次不买" -->
              <view
                class="ml-16rpx flex-none rounded-30rpx px-20rpx py-6rpx text-22rpx"
                :style="confirmState.skipped[item.id]
                  ? 'background:#F1EFE8;color:#5F5E5A'
                  : 'background:#E6F1FB;color:#185FA5'"
                @click="toggleSkipped(item)"
              >
                {{ confirmState.skipped[item.id] ? '已排除' : '本次不采' }}
              </view>
            </view>
            <view class="mt-4rpx flex items-center justify-between text-22rpx text-gray-400">
              <text class="min-w-0 flex-1 truncate">
                申请 {{ item.requestedQuantity }} · 单价 {{ unitPriceText(item) }} · 来自 {{ contributorLabel(item) }}
              </text>
              <!-- 行金额跟着核定数量实时走，确认人改完数量立刻能核对 -->
              <text
                v-if="confirmLineAmountText(item)"
                class="ml-12rpx flex-none font-bold"
                :style="item.unitPrice == null ? 'color:#B45309' : 'color:#111827'"
              >
                {{ confirmLineAmountText(item) }}
              </text>
            </view>
            <input
              v-model="confirmState.approved[item.id]"
              class="mt-8rpx h-76rpx rounded-12rpx px-24rpx text-28rpx"
              :class="confirmState.skipped[item.id] ? 'bg-gray-100 text-gray-400' : 'bg-gray-50'"
              :disabled="confirmState.skipped[item.id]"
              type="number"
              :placeholder="confirmState.skipped[item.id] ? '本次不采' : '核定数量'"
            >
          </view>

          <!--
            整单预估金额：下单前必须能回答「这批多少钱」。实付以订单/结算页为准，
            这里明确写「预估」，避免用户把它当成最终扣款金额。
          -->
          <view class="mt-28rpx rounded-16rpx bg-gray-50 px-24rpx py-20rpx">
            <view class="flex items-baseline justify-between">
              <text class="text-26rpx text-gray-600">
                本次采购预估金额
              </text>
              <text v-if="confirmAmountText" class="text-34rpx font-bold" style="color:#C2410C">
                {{ confirmAmountText }}
              </text>
            </view>
            <view v-if="confirmAmount.unpricedCount > 0" class="mt-6rpx text-22rpx" style="color:#B45309">
              {{ confirmAmount.unpricedCount }} 项商品暂时取不到售价，未计入；实付以订单为准
            </view>
            <view v-else class="mt-6rpx text-22rpx text-gray-400">
              未含促销与运费，实付以订单为准
            </view>
          </view>

          <!-- 支付方式：共享车固定内部配送（way=4），默认货到付款；选 COD 下单即进待发货，无 30 分钟付款限制 -->
          <view class="mt-28rpx text-24rpx text-gray-500">
            支付方式
          </view>
          <view class="mt-8rpx flex gap-16rpx">
            <view
              class="h-76rpx flex flex-1 items-center justify-center rounded-12rpx text-28rpx"
              :style="confirmState.paymentType === ''
                ? 'background:#FFF7ED;color:#F2741D;border:1rpx solid #F2741D'
                : 'background:#F5F5F5;color:#374151'"
              @click="confirmState.paymentType = ''"
            >
              在线支付
            </view>
            <view
              class="h-76rpx flex flex-1 items-center justify-center rounded-12rpx text-28rpx"
              :style="confirmState.paymentType === '3'
                ? 'background:#FFF7ED;color:#F2741D;border:1rpx solid #F2741D'
                : 'background:#F5F5F5;color:#374151'"
              @click="confirmState.paymentType = '3'"
            >
              货到付款
            </view>
          </view>
          <view class="mt-4rpx text-22rpx text-gray-400">
            {{ confirmState.paymentType === '3' ? '收货后线下结算，由公司在订单里确认收款' : '提交后需在 30 分钟内完成在线支付' }}
          </view>

          <view class="mt-28rpx text-24rpx text-gray-500">
            收货人
          </view>
          <input
            v-model="confirmState.recipientName"
            class="mt-8rpx h-76rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
            placeholder="收货人姓名"
          >
          <input
            v-model="confirmState.recipientPhone"
            class="mt-10rpx h-76rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
            placeholder="收货人电话"
          >
          <view class="mt-28rpx text-24rpx text-gray-500">
            船上代理 / 经办人（选填）
          </view>
          <input
            v-model="confirmState.agentName"
            class="mt-8rpx h-76rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
            placeholder="姓名"
          >
          <input
            v-model="confirmState.agentPhone"
            class="mt-10rpx h-76rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
            placeholder="电话"
          >
          <view class="mt-28rpx text-24rpx text-gray-500">
            整单备注（选填）
          </view>
          <input
            v-model="confirmState.remark"
            class="mt-8rpx h-76rpx rounded-12rpx bg-gray-50 px-24rpx text-28rpx"
            placeholder="如：本次补给含甲板部需求"
          >
        </view>
      </scroll-view>
      <view class="px-32rpx pb-32rpx pt-20rpx">
        <!-- 提交前把「买几项」说清：全部排除时按钮置灰并给原因，不让人点完才报错 -->
        <view v-if="confirmBuyCount < items.length" class="mb-16rpx text-22rpx text-gray-500">
          本次采购 {{ confirmBuyCount }} 项，{{ items.length - confirmBuyCount }} 项已排除
        </view>
        <view class="sheet-actions">
          <wd-button type="info" custom-class="sheet-actions__cancel" @click="confirmState.visible = false">
            取消
          </wd-button>
          <wd-button
            type="primary"
            :loading="confirmState.submitting"
            :disabled="confirmBuyCount === 0"
            custom-class="sheet-actions__ok"
            @click="submitConfirm"
          >
            提交整船订单
          </wd-button>
        </view>
      </view>
    </wd-popup>

    <!-- 复用时缺船舶/靠港上下文，先让用户选 -->
    <ShipContextPicker v-model="shipPickerVisible" />
  </view>
</template>

<style lang="scss" scoped>
// 底部固定条要给内容留出等高空白，否则最后一张卡片会被压住
.footer-spacer {
  height: calc(160rpx + env(safe-area-inset-bottom));
}

.action-bar {
  position: fixed;
  right: 0;
  // H5 的 --window-bottom 为 TabBar 高度；本页是分包页无 TabBar，取 0 兜底
  bottom: var(--window-bottom, 0px);
  left: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgb(0 0 0 / 4%);
}

.action-bar__ghost {
  height: 80rpx;
  padding: 0 28rpx;
  font-size: 26rpx;
  line-height: 80rpx;
  color: #303133;

  &[disabled] {
    color: #c0c4cc;
  }
}

// 底部条金额：数字为主、标签为辅，标签用灰色小字说明它是预估
.action-bar__amount {
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.1;
  color: #c2410c;
  text-align: center;
}

.action-bar__amount-label {
  margin-top: 2rpx;
  font-size: 20rpx;
  line-height: 1.2;
  color: #9aa4b2;
  text-align: center;
}

// 与全站主 CTA 同款（购物车「去结算」、拼团「立即拼团」均为这套橙红渐变）
.action-bar__primary {
  height: 80rpx;
  flex: 1;
  border-radius: 999rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  box-shadow: 0 6rpx 16rpx rgb(255 77 46 / 30%);
  font-size: 28rpx;
  font-weight: 500;
  line-height: 80rpx;
  text-align: center;
  color: #fff;
}

// 品名最多两行：小程序不支持 unocss 的 line-clamp 简写，显式写 box 属性
.row-name {
  display: -webkit-box;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 26rpx;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.sheet-row {
  padding: 28rpx 0;
  border-bottom: 1rpx solid #f3f4f6;
  font-size: 28rpx;
  text-align: center;
  color: #303133;

  &:last-child {
    border-bottom: 0;
  }
}

.sheet-row--danger {
  color: #dc2626;
}

// 弹层底部双按钮：grid 两列等宽，取消描边、确认走品牌色
.sheet-actions {
  display: grid;
  gap: 20rpx;
  grid-template-columns: 1fr 1fr;
  margin-top: 28rpx;
}

:deep(.sheet-actions__cancel) {
  --wot-button-default-bg-color: #f5f6f8;

  font-size: 28rpx;
}

:deep(.sheet-actions__ok) {
  font-size: 28rpx;
}

// 提交表单：明细多时只让 body 滚，标题与按钮始终可见
.confirm-body {
  max-height: 56vh;
  margin-top: 4rpx;
}
</style>
