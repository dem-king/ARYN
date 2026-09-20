<script setup lang="ts">
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
  setMyDisplayName,
  shareSharedCart,
  type SharedCart,
  type SharedCartItem,
  type SharedCartMember,
  updateSharedCartItem,
} from '@/api/order/sharedCart'
import { getByIds } from '@/api/product/spu'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useShipContextStore } from '@/store/shipContextStore'
import { useUserStore } from '@/store/userStore'
import {
  cartStatusLabel,
  cartStatusTheme,
  isCartCollecting,
  isCartReadonly,
  memberRoleLabel,
  MEMBER_ROLE_CONFIRMATOR,
  MEMBER_ROLE_MEMBER,
} from '@/utils/shared-cart'

definePage({
  name: 'shared-cart-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '共享购物车详情',
  },
})

const userStore = useUserStore()
const shipContextStore = useShipContextStore()

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
/** spuId → 商品名 */
const spuNames = reactive<Record<string, string>>({})

const currentUserId = computed(() => userStore.getUserId)
const collecting = computed(() => isCartCollecting(cart.value?.status))
const cartReadonly = computed(() => isCartReadonly(cart.value?.status))
const canConfirmNow = computed(() =>
  !!cart.value?.viewerCanConfirm && collecting.value && items.value.length > 0,
)

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
  if (nameState.submitting) return
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
  recipientName: '',
  recipientPhone: '',
  agentName: '',
  agentPhone: '',
  remark: '',
})

function loadSpuNames(spuIds: string[]) {
  const pending = spuIds.filter(id => id && spuNames[id] === undefined)
  if (pending.length === 0) return
  getByIds(pending)
    .then((list) => {
      ;(list ?? []).forEach((spu: any) => {
        if (spu?.id) spuNames[spu.id] = spu.name ?? ''
      })
    })
    .catch(() => {
      // 商品名仅用于展示，失败不阻断购物车操作
    })
}

function fetchDetail() {
  if (!cartId.value) return Promise.resolve()
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
      loadSpuNames([...new Set(items.value.map(item => item.spuId))])
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

function openEditItem(item: SharedCartItem) {
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
  if (editState.submitting) return
  editState.submitting = true
  updateSharedCartItem(cartId.value, editState.itemId, {
    skuId: items.value.find(item => item.id === editState.itemId)?.skuId ?? '',
    requestedQuantity: quantity,
    memberRemark: editState.remark || undefined,
  })
    .then(() => {
      editState.visible = false
      uni.showToast({ title: '已更新', icon: 'success' })
      return fetchDetail()
    })
    .catch(() => {})
    .finally(() => {
      editState.submitting = false
    })
}

function handleRemoveItem(item: SharedCartItem) {
  uni.showModal({
    title: '移除明细',
    content: '确定移除这条明细吗？',
    success: (res) => {
      if (!res.confirm) return
      removeSharedCartItem(cartId.value, item.id)
        .then(() => {
          uni.showToast({ title: '已移除', icon: 'success' })
          return fetchDetail()
        })
        .catch(() => {})
    },
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
  if (inviteState.submitting) return
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

function handleClose() {
  uni.showModal({
    title: '关闭购物车',
    content: '关闭后成员将无法继续加购，确定关闭吗？',
    success: (res) => {
      if (!res.confirm) return
      closeSharedCart(cartId.value)
        .then(() => {
          uni.showToast({ title: '已关闭', icon: 'success' })
          return fetchDetail()
        })
        .catch(() => {})
    },
  })
}

function openConfirm() {
  // 核定数量默认取成员申请数量
  confirmState.approved = {}
  items.value.forEach((item) => {
    confirmState.approved[item.id] = String(item.requestedQuantity ?? '')
  })
  confirmState.remark = ''
  confirmState.visible = true
}

function submitConfirm() {
  if (confirmState.submitting) return
  const approvedQuantities = items.value.map((item) => {
    const raw = confirmState.approved[item.id]
    const quantity = Number(raw)
    return { itemId: item.id, quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : item.requestedQuantity }
  })
  confirmState.submitting = true
  confirmSharedCart(cartId.value, {
    approvedQuantities,
    recipientName: confirmState.recipientName || undefined,
    recipientPhone: confirmState.recipientPhone || undefined,
    agentName: confirmState.agentName || undefined,
    agentPhone: confirmState.agentPhone || undefined,
    remark: confirmState.remark || undefined,
  })
    .then((orderId) => {
      confirmState.visible = false
      uni.showToast({ title: '已提交整船订单', icon: 'success' })
      return fetchDetail().then(() => {
        if (orderId) {
          setTimeout(() => {
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
  if (!cartId.value) return
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
  <view>
    <hr-navbar title="共享购物车详情" />

    <!-- 凭分享卡片加入中 -->
    <view v-if="joining" class="py-80rpx text-center text-26rpx text-gray-400">
      正在加入本次采购...
    </view>

    <!-- 分享链接失效：给出可执行的出路，而不是只报错 -->
    <view v-else-if="joinFailed" class="mx-20rpx mt-40rpx rounded-20rpx bg-white p-30rpx text-center">
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
      <!-- 购物车概要 -->
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-30rpx font-bold">
            {{ cart.vesselName || '船舶信息待同步' }}
          </view>
          <view
            class="rounded-full px-16rpx py-4rpx text-22rpx"
            :style="`background:${cartStatusTheme(cart.status).bg};color:${cartStatusTheme(cart.status).text}`"
          >
            {{ cartStatusLabel(cart.status) }}
          </view>
        </view>
        <view class="mt-8rpx text-24rpx text-gray-500">
          {{ cart.portName || '港口待定' }} {{ cart.berth }}
        </view>
        <view class="mt-8rpx text-24rpx text-gray-500">
          编号 {{ cart.cartNo }}
        </view>
        <view v-if="collecting && cart.expiresAt" class="mt-8rpx text-24rpx text-amber-600">
          收集截止 {{ cart.expiresAt }}
        </view>
        <view v-if="cart.remark" class="mt-8rpx text-24rpx text-gray-500">
          备注：{{ cart.remark }}
        </view>
        <button
          v-if="cart.submitOrderId"
          class="!m-0 mt-20rpx h-68rpx text-26rpx leading-68rpx"
          type="primary"
          @tap="goOrder"
        >
          查看已生成的整船订单
        </button>
      </view>

      <!-- 成员 -->
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-28rpx font-bold">
            成员（{{ members.length }}）
          </view>
          <view class="flex items-center gap-20rpx">
            <text
              v-if="needDisplayName"
              class="text-24rpx"
              style="color:#C2410C"
              @tap="openSetName"
            >
              填写我的姓名
            </text>
            <text
              v-else-if="myMember"
              class="text-24rpx text-gray-400"
              @tap="openSetName"
            >
              修改姓名
            </text>
            <view
              v-if="cart.viewerIsOwner && collecting"
              class="text-24rpx text-blue-500"
              @tap="openInvite"
            >
              邀请成员
            </view>
            <view
              v-if="cart.viewerIsOwner && collecting"
              class="text-24rpx text-blue-500"
              @tap="handleShare"
            >
              分享到群
            </view>
          </view>
        </view>
        <view
          v-for="member in members"
          :key="member.id"
          class="mt-16rpx flex items-center justify-between"
        >
          <view class="text-24rpx text-gray-600">
            {{ member.displayName || member.userId }}
            <text v-if="member.userId === currentUserId" class="text-blue-500">（我）</text>
          </view>
          <view class="flex items-center gap-10rpx">
            <text class="text-22rpx text-gray-500">{{ memberRoleLabel(member.memberRole) }}</text>
            <text v-if="member.canConfirm === '1'" class="text-22rpx text-green-600">可提交</text>
          </view>
        </view>
      </view>

      <!-- 我的姓名表单（配送贴标签用） -->
      <view v-if="nameState.visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          我的姓名
        </view>
        <view class="mt-8rpx text-22rpx text-gray-500">
          用于配送到船时把你的商品单独贴标签。填写一次即可，之后可随时修改。
        </view>
        <input
          v-model="nameState.displayName"
          class="mt-16rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="请输入姓名，如：王建国"
          :maxlength="64"
        >
        <view class="mt-20rpx flex gap-20rpx">
          <button class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx" @tap="nameState.visible = false">
            取消
          </button>
          <button
            class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
            type="primary"
            :disabled="nameState.submitting"
            @tap="submitDisplayName"
          >
            保存
          </button>
        </view>
      </view>

      <!-- 邀请成员表单 -->
      <view v-if="inviteState.visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          邀请成员
        </view>
        <input
          v-model="inviteState.memberUserId"
          class="mt-16rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
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
        <view class="mt-20rpx flex gap-20rpx">
          <button class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx" @tap="inviteState.visible = false">
            取消
          </button>
          <button
            class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
            type="primary"
            :disabled="inviteState.submitting"
            @tap="submitInvite"
          >
            邀请
          </button>
        </view>
      </view>

      <!-- 明细 -->
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-28rpx font-bold">
            明细（{{ items.length }}）
          </view>
          <view v-if="collecting && cart.viewerCanEdit" class="text-24rpx text-blue-500" @tap="goAddGoods">
            添加商品
          </view>
        </view>

        <view
          v-for="item in items"
          :key="item.id"
          class="mt-20rpx border-b border-gray-100 pb-20rpx"
        >
          <view class="text-26rpx">
            {{ spuNames[item.spuId] || '商品加载中' }}
          </view>
          <view class="mt-6rpx text-22rpx text-gray-400">
            SKU {{ item.skuId }}
          </view>
          <view class="mt-8rpx flex items-center justify-between">
            <view class="text-24rpx text-gray-600">
              申请 {{ item.requestedQuantity }}
              <text v-if="item.approvedQuantity != null" class="text-amber-600">
                · 核定 {{ item.approvedQuantity }}
              </text>
            </view>
            <view v-if="canEditItem(item)" class="flex items-center gap-20rpx">
              <text class="text-24rpx text-blue-500" @tap="openEditItem(item)">编辑</text>
              <text class="text-24rpx text-red-500" @tap="handleRemoveItem(item)">移除</text>
            </view>
          </view>
          <view v-if="item.memberRemark" class="mt-6rpx text-22rpx text-gray-500">
            备注：{{ item.memberRemark }}
          </view>
          <view class="mt-6rpx text-22rpx text-gray-400">
            来自 {{ isMine(item) ? '我' : item.userId }}
          </view>
        </view>

        <view v-if="items.length === 0" class="py-40rpx text-center text-26rpx text-gray-400">
          <template v-if="collecting">还没有成员加购，点「添加商品」开始</template>
          <template v-else-if="cartReadonly">本次未提交任何明细</template>
          <template v-else>暂无明细</template>
        </view>
      </view>

      <!-- 编辑明细表单 -->
      <view v-if="editState.visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          修改我的明细
        </view>
        <input
          v-model="editState.quantity"
          class="mt-16rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          type="number"
          placeholder="申请数量"
        >
        <input
          v-model="editState.remark"
          class="mt-16rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="备注（选填）"
        >
        <view class="mt-20rpx flex gap-20rpx">
          <button class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx" @tap="editState.visible = false">
            取消
          </button>
          <button
            class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
            type="primary"
            :disabled="editState.submitting"
            @tap="submitEditItem"
          >
            保存
          </button>
        </view>
      </view>

      <!-- 提交整船订单 -->
      <view v-if="confirmState.visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          核定数量并提交
        </view>
        <view class="mt-8rpx text-22rpx text-gray-500">
          按成员分别生成订单明细，配送时可为每人单独贴标签
        </view>
        <view v-for="item in items" :key="item.id" class="mt-16rpx">
          <view class="text-24rpx text-gray-600">
            {{ spuNames[item.spuId] || item.skuId }}
            <text class="text-gray-400">（申请 {{ item.requestedQuantity }}）</text>
          </view>
          <input
            v-model="confirmState.approved[item.id]"
            class="mt-8rpx h-68rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
            type="number"
            placeholder="核定数量"
          >
        </view>

        <view class="mt-24rpx text-24rpx text-gray-500">
          收货人
        </view>
        <input
          v-model="confirmState.recipientName"
          class="mt-8rpx h-68rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="收货人姓名"
        >
        <input
          v-model="confirmState.recipientPhone"
          class="mt-10rpx h-68rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="收货人电话"
        >
        <input
          v-model="confirmState.agentName"
          class="mt-10rpx h-68rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="船上代理/经办人姓名（选填）"
        >
        <input
          v-model="confirmState.agentPhone"
          class="mt-10rpx h-68rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="船上代理/经办人电话（选填）"
        >
        <input
          v-model="confirmState.remark"
          class="mt-10rpx h-68rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="整单备注（选填）"
        >

        <view class="mt-20rpx flex gap-20rpx">
          <button class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx" @tap="confirmState.visible = false">
            取消
          </button>
          <button
            class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
            type="primary"
            :disabled="confirmState.submitting"
            @tap="submitConfirm"
          >
            提交整船订单
          </button>
        </view>
      </view>

      <!-- 底部操作 -->
      <view class="mx-20rpx my-30rpx flex gap-20rpx">
        <button
          v-if="canConfirmNow && !confirmState.visible"
          class="!m-0 flex-1 h-80rpx text-28rpx leading-80rpx"
          type="primary"
          @tap="openConfirm"
        >
          提交整船订单
        </button>
        <button
          v-if="cart.viewerIsOwner && !cartReadonly"
          class="!m-0 flex-1 h-80rpx text-28rpx leading-80rpx"
          @tap="handleClose"
        >
          关闭购物车
        </button>
      </view>

      <view
        v-if="!canConfirmNow && collecting && items.length === 0"
        class="px-40rpx pb-40rpx text-center text-24rpx text-gray-400"
      >
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
  </view>
</template>
