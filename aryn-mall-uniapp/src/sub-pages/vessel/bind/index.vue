<script setup lang="ts">
import type { VesselBindApply, VesselInviteCode, VesselMember } from '@/api/vessel'
/**
 * 船舶绑定与成员管理。
 *
 * 解决的核心问题：**未绑定船舶的新用户此前没有任何自助出路**——
 * C 端只有 3 个 GET 接口，唯一绑定入口是管理端后台。
 *
 * 页面按用户状态分两种形态：
 *  · 未绑定 → 「加入船舶」：输邀请码 或 提交绑定申请（业务员认领船舶也走这里）
 *  · 已绑定 → 「成员管理」：业务员现场拉人、生成邀请码给同事；
 *    同时保留「加入其他船舶」（邀请码 / 没码时提交申请）与「退出本船」——
 *    跑船换船的自助出路，不再依赖运营在管理端手工移除；
 *    多船模型下已绑定用户申请加入其他船也走同一申请通道（运营审核）。
 *
 * 成员管理权限与后端一致：发起人(1) / 采购确认人(3) / 业务员(4)；普通船员(2) 只能看。
 *
 * UI 结构（2026-09 重设计）：
 *  · 船舶信息升为品牌渐变 Hero 卡，直接呈现「当前购物船舶」状态——
 *    旧版「设为当前船舶」是常驻文字链，用户看不出现在购物用的到底是哪艘船；
 *  · 添加成员、绑定申请从页面内联卡改为底部弹层，页面只留信息与入口；
 *  · 角色文案改用船舶域映射——此前借用 shared-cart 的 memberRoleLabel，
 *    业务员(4) 会错显成「成员」。
 */
import { onLoad, onShow } from '@dcloudio/uni-app'

import { computed, reactive, ref } from 'vue'
import {
  addVesselMember,
  cancelBindApply,
  generateInviteCode,
  getMyBindApplies,
  getMyInviteCodes,
  getMyVessels,
  getVesselMembers,
  leaveVessel,
  redeemInviteCode,
  removeVesselMember,
  revokeInviteCode,
  searchMemberCandidates,
  submitBindApply,

} from '@/api/vessel'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { ensureShipContextLoaded } from '@/composables/useShipContextLoad'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useUserStore } from '@/store/userStore'
import { OVERLAY_Z_INDEX } from '@/utils/overlay'
import {
  findPendingApply,
  mismatchTip,
  pendingApplyTip,
  resolveApplyErrorMessage,
} from '@/utils/vessel-bind-apply'

definePage({
  name: 'vessel-bind',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '船舶绑定',
  },
})

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()
const userStore = useUserStore()

const loading = ref(false)
const loadFailed = ref(false)
const vessels = ref<any[]>([])
const activeVesselId = ref('')

const hasVessel = computed(() => vessels.value.length > 0)
const activeVessel = computed(() => vessels.value.find(v => v.id === activeVesselId.value) ?? null)

// ---------------------------------------------------------------------------
// 角色与徽章
// ---------------------------------------------------------------------------

/** 船舶域角色文案：1发起人 2普通船员 3采购确认人 4业务员 */
const VESSEL_ROLE_LABEL: Record<string, string> = {
  1: '发起人',
  2: '普通船员',
  3: '采购确认人',
  4: '业务员',
}

function vesselRoleLabel(role?: null | string): string {
  if (!role)
    return ''
  return VESSEL_ROLE_LABEL[role] ?? '成员'
}

const ROLE_BADGE_STYLES: Record<string, string> = {
  1: 'background:#378ADD;color:#fff',
  2: 'background:#F2F3F5;color:#4E5969',
  3: 'background:#E6F1FB;color:#2A79C6',
  4: 'background:#FBF0E1;color:#C87D2A',
}

const APPLY_STATUS_LABEL: Record<string, string> = {
  1: '待审核',
  2: '已通过',
  3: '已驳回',
  4: '已撤回',
}

const APPLY_STATUS_STYLES: Record<string, string> = {
  1: 'background:#FBF0E1;color:#C87D2A',
  2: 'background:#E8F5EE;color:#2E9E5B',
  3: 'background:#FDEEEE;color:#D54941',
  4: 'background:#F2F3F5;color:#86909C',
}

/** 成员头像：昵称首字 + 稳定取色，避免引入外部图片资源 */
const AVATAR_COLORS = ['#378ADD', '#5B9BD5', '#2E9E5B', '#C87D2A', '#8A72D8']

function avatarColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++)
    hash = (hash * 31 + seed.charCodeAt(i)) % 997
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

function avatarText(member: VesselMember): string {
  const name = member.nickname || member.userId || ''
  return Array.from(name)[0] ?? '?'
}

/** 可管理成员的角色 */
const MANAGE_ROLES = ['1', '3', '4']

const members = ref<VesselMember[]>([])
const myRole = computed(() => members.value.find(m => m.userId === userStore.getUserId)?.memberRole ?? '')
const canManage = computed(() => MANAGE_ROLES.includes(myRole.value))

/** 当前浏览的船是否就是购物上下文里的船 */
const isCurrentVessel = computed(() =>
  !!activeVessel.value && shipContextStore.vesselId === activeVessel.value.id)

/** 加入：邀请码 */
const joinState = reactive({ code: '', submitting: false })

/** 加入：绑定申请 */
const applyState = reactive({
  visible: false,
  submitting: false,
  /**
   * 提交失败的可见原因。
   *
   * 必须落在弹层内部：失败时后端返回业务错误（最常见的正是「您有一条待审核的
   * 申请」防重），而全局 toast 在小程序里可能被本页 900/901 的弹层盖住 ——
   * 2026-10-18 用户反馈「点提交申请没反应」就是这条链路。内联文案不依赖任何
   * 浮层层级，弹层是否关闭都不影响用户看到原因。
   */
  error: '',
  form: {
    applyRole: '2',
    applyVesselName: '',
    applyVesselImo: '',
    applyVesselCallSign: '',
    applyPortName: '',
    realName: '',
    phone: '',
    position: '',
    remark: '',
  },
})
const myApplies = ref<VesselBindApply[]>([])

/**
 * 当前待审核的申请（后端「同一用户仅一条待审核」防重的数据来源）。
 *
 * 有这个值时提交必然被后端拒绝，因此提前在本地拦下并把「撤回」入口摆出来：
 * 让用户能自己解开死结，而不是反复点提交等一条看不见的报错。
 */
const pendingApply = computed(() => findPendingApply(myApplies.value))

/** 与后端 `VesselBindApply.STATUS_APPROVED` 对齐 */
const APPROVED_APPLY_STATUS = '2'

/**
 * 已通过申请与实际绑定船名不一致时的警示文案（一致 / 未通过返回空串）。
 *
 * 审核通过后申请单就冻结了，但运营可以把申请「悦航8号」绑到另一条船
 * （跨船绑定需人工确认并填写理由）。卡片此前只渲染申请船名，用户看不出
 * 自己实际进了哪条船 —— 而船舶成员身份决定了他能看谁的成员、靠港计划与
 * 配送单，必须显式说清。判定与归一化规则见 `utils/vessel-bind-apply`。
 */
function applyMismatchTip(apply: VesselBindApply): string {
  return apply.status === APPROVED_APPLY_STATUS ? mismatchTip(apply) : ''
}

/** 邀请码 */
const inviteCodes = ref<VesselInviteCode[]>([])

/** 添加成员 */
const candidateState = reactive({ keyword: '', searching: false, list: [] as VesselMember[] })
/** 添加成员弹层：search 搜人 → confirm 定角色，两视图切换 */
const addState = reactive({
  visible: false,
  mode: 'search' as 'search' | 'confirm',
  target: null as null | VesselMember,
  memberRole: '2',
  remark: '',
  submitting: false,
  /** 加人失败原因：与申请弹层同理，底部弹层里的错误不能只靠全局 toast */
  error: '',
})

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function fetchVessels() {
  if (!authStore.isLoggedIn)
    return Promise.resolve()
  loading.value = true
  return getMyVessels()
    .then((res) => {
      vessels.value = res ?? []
      activeVesselId.value = activeVesselId.value || vessels.value[0]?.id || ''
      loadFailed.value = false
    })
    .catch(() => {
      loadFailed.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

function fetchJoinData() {
  return getMyBindApplies()
    .then((res) => {
      myApplies.value = res ?? []
    })
    .catch(() => {})
}

/**
 * 主动刷新「我的申请」状态（弹层里的「刷新状态」按钮 / 打开弹层时）。
 *
 * 必须同时清掉内联错误：`fetchJoinData` 只同步列表，如果运营刚审核通过，
 * 列表更新后横幅消失、但上一次提交失败留下的红块会继续常驻 —— 用户会被
 * 自己看到的旧错误劝退，而这次提交其实已经能成功。
 */
function refreshPendingState() {
  applyState.error = ''
  return fetchJoinData()
}

function fetchMemberData() {
  if (!activeVesselId.value)
    return Promise.resolve()
  return Promise.all([
    getVesselMembers(activeVesselId.value),
    getMyInviteCodes(activeVesselId.value),
  ])
    .then(([memberRes, codeRes]) => {
      members.value = memberRes ?? []
      inviteCodes.value = codeRes ?? []
    })
    .catch(() => {})
}

function refreshAll() {
  // 已绑定也要拉申请列表：多船模型下已绑定用户同样可能有待审核的加船申请
  return fetchVessels().then(() => Promise.all([hasVessel.value ? fetchMemberData() : Promise.resolve(), fetchJoinData()]))
}

// ---------------------------------------------------------------------------
// 加入
// ---------------------------------------------------------------------------

function submitRedeem() {
  const code = joinState.code.trim().toUpperCase()
  if (!code) {
    uni.showToast({ title: '请输入邀请码', icon: 'none' })
    return
  }
  if (joinState.submitting)
    return
  joinState.submitting = true
  redeemInviteCode(code)
    .then(() => {
      joinState.code = ''
      uni.showToast({ title: '已加入船舶', icon: 'success' })
      return refreshAll()
    })
    .catch(() => {})
    .finally(() => {
      joinState.submitting = false
    })
}

function openApply() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  // 每次打开都清掉上一次的提交错误：否则用户改了船名重进，旧错误还挂在表单上
  applyState.error = ''
  applyState.visible = true
  // 待审核拦截依赖「我的申请」列表：打开时重拉一次，避免运营刚审核完、
  // 本地还拿着旧列表把本可以成功的提交挡住（旧数据最多停留到本次打开为止）
  void refreshPendingState()
}

function closeApply() {
  applyState.visible = false
  applyState.error = ''
}

/** 已绑定后用其他船的邀请码继续加船（跑船换船场景） */
const joinAnother = reactive({ code: '', submitting: false })

function submitJoinAnother() {
  const code = joinAnother.code.trim().toUpperCase()
  if (!code) {
    uni.showToast({ title: '请输入邀请码', icon: 'none' })
    return
  }
  if (joinAnother.submitting)
    return
  joinAnother.submitting = true
  redeemInviteCode(code)
    .then(() => {
      joinAnother.code = ''
      uni.showToast({ title: '已加入新船，可在上方切换', icon: 'none' })
      return fetchVessels()
    })
    .catch(() => {})
    .finally(() => {
      joinAnother.submitting = false
    })
}

function submitApply() {
  if (!applyState.form.applyVesselName.trim()) {
    applyState.error = '请填写船名'
    uni.showToast({ title: '请填写船名', icon: 'none' })
    return
  }
  // 已有待审核申请时后端必然拒绝（同一用户仅一条待审核）：本地先拦下。
  // 头部横幅已经解释了原因与出路，这里只在操作点补一句**动作指引**，
  // 不重复整段文案（同屏出现两句一样的话会让用户以为点了两次都没用）。
  if (pendingApply.value) {
    applyState.error = '请先撤回上面那条待审核申请，或点「刷新状态」确认是否已审核通过'
    return
  }
  if (applyState.submitting)
    return
  applyState.error = ''
  applyState.submitting = true
  submitBindApply({
    applyRole: applyState.form.applyRole,
    applyVesselName: applyState.form.applyVesselName.trim(),
    applyVesselImo: applyState.form.applyVesselImo || undefined,
    applyVesselCallSign: applyState.form.applyVesselCallSign || undefined,
    applyPortName: applyState.form.applyPortName || undefined,
    realName: applyState.form.realName || undefined,
    phone: applyState.form.phone || undefined,
    position: applyState.form.position || undefined,
    remark: applyState.form.remark || undefined,
  })
    .then(() => {
      applyState.visible = false
      applyState.error = ''
      uni.showToast({ title: '已提交，等待运营审核', icon: 'success' })
      return fetchJoinData()
    })
    .catch((error) => {
      // 空 catch 是这次「点了没反应」的直接原因之一：错误只发给了全局 toast。
      // 现在同时写进弹层内联状态（不依赖任何浮层层级），并刷新申请列表，
      // 让待审核拦截能立刻带出真实的申请单号与撤回入口。
      applyState.error = resolveApplyErrorMessage(error, pendingApply.value)
      void fetchJoinData()
    })
    .finally(() => {
      applyState.submitting = false
    })
}

function handleCancelApply(apply: null | VesselBindApply | undefined) {
  if (!apply)
    return
  uni.showModal({
    title: '撤回申请',
    content: `确定撤回「${apply.applyVesselName || '这条申请'}」的绑定申请吗？撤回后可重新提交。`,
    success: (res) => {
      if (!res.confirm)
        return
      cancelBindApply(apply.id)
        .then(() => {
          // 撤回成功即解除「仅一条待审核」的死结：清掉内联拦截文案，
          // 用户可以就地改船名重新提交，不必先退出弹层再进来
          applyState.error = ''
          uni.showToast({ title: '已撤回', icon: 'success' })
          return fetchJoinData()
        })
        .catch((error) => {
          applyState.error = resolveApplyErrorMessage(error, null)
        })
    },
  })
}

// ---------------------------------------------------------------------------
// 成员管理
// ---------------------------------------------------------------------------

function openAddFlow() {
  candidateState.keyword = ''
  candidateState.list = []
  addState.mode = 'search'
  addState.error = ''
  addState.visible = true
}

function doSearchCandidates() {
  const keyword = candidateState.keyword.trim()
  if (!keyword) {
    uni.showToast({ title: '请输入手机号或昵称', icon: 'none' })
    return
  }
  candidateState.searching = true
  searchMemberCandidates(activeVesselId.value, keyword)
    .then((res) => {
      candidateState.list = res ?? []
      if (candidateState.list.length === 0) {
        uni.showToast({ title: '没找到匹配的商城用户', icon: 'none' })
      }
    })
    .catch(() => {})
    .finally(() => {
      candidateState.searching = false
    })
}

/** 从搜索结果进入角色确认视图 */
function openAdd(candidate: VesselMember) {
  addState.target = candidate
  addState.memberRole = '2'
  addState.remark = ''
  // 换了个人就把上一位的失败原因清掉，否则会显示成「这个人也加不进来」
  addState.error = ''
  addState.mode = 'confirm'
}

function backToSearch() {
  addState.mode = 'search'
  addState.error = ''
}

function submitAdd() {
  if (!addState.target || addState.submitting)
    return
  addState.error = ''
  addState.submitting = true
  addVesselMember(activeVesselId.value, {
    userId: addState.target.userId,
    memberRole: addState.memberRole,
    remark: addState.remark || undefined,
  })
    .then(() => {
      addState.visible = false
      candidateState.list = candidateState.list.filter(c => c.userId !== addState.target?.userId)
      uni.showToast({ title: '已添加', icon: 'success' })
      return fetchMemberData()
    })
    .catch((error) => {
      // 与绑定申请同一类问题：底部弹层里的失败必须就地可见，不能只发全局 toast
      addState.error = resolveApplyErrorMessage(error, null)
    })
    .finally(() => {
      addState.submitting = false
    })
}

function handleRemoveMember(member: VesselMember) {
  uni.showModal({
    title: '移除成员',
    content: `确定将「${member.nickname || member.userId}」移出本船吗？`,
    success: (res) => {
      if (!res.confirm)
        return
      removeVesselMember(activeVesselId.value, member.id)
        .then(() => {
          uni.showToast({ title: '已移除', icon: 'success' })
          return fetchMemberData()
        })
        .catch(() => {})
    },
  })
}

/** 本人退出当前浏览的船舶（发起人由后端拒绝，前端直接不显示入口） */
function handleLeaveVessel() {
  const vessel = activeVessel.value
  if (!vessel)
    return
  uni.showModal({
    title: '退出本船',
    content: `退出后将无法以船供方式在「${vessel.vesselName}」下单，确定退出吗？`,
    success: (res) => {
      if (!res.confirm)
        return
      leaveVessel(vessel.id)
        .then(() => {
          uni.showToast({ title: '已退出', icon: 'success' })
          activeVesselId.value = ''
          members.value = []
          inviteCodes.value = []
          // 退出的是当前购物船时强制重装上下文：剩余船舶自动回落，无船则清空
          if (shipContextStore.vesselId === vessel.id)
            return ensureShipContextLoaded(true)
        })
        .then(() => refreshAll())
        .catch(() => {})
    },
  })
}

function handleGenerateCode() {
  generateInviteCode(activeVesselId.value, { expireHours: 24 })
    .then(() => {
      uni.showToast({ title: '已生成（24 小时有效）', icon: 'success' })
      return fetchMemberData()
    })
    .catch(() => {})
}

function copyCode(code: string) {
  uni.setClipboardData({
    data: code,
    success: () => uni.showToast({ title: '邀请码已复制', icon: 'none' }),
  })
}

function handleRevokeCode(item: VesselInviteCode) {
  uni.showModal({
    title: '撤销邀请码',
    content: '撤销后该邀请码立即失效，确定吗？',
    success: (res) => {
      if (!res.confirm)
        return
      revokeInviteCode(item.id)
        .then(() => {
          uni.showToast({ title: '已撤销', icon: 'success' })
          return fetchMemberData()
        })
        .catch(() => {})
    },
  })
}

function switchVessel(vesselId: string) {
  activeVesselId.value = vesselId
  candidateState.list = []
  candidateState.keyword = ''
  void fetchMemberData()
}

/** 港口报备车辆：靠港外出打车的司机电话簿 */
function goPortVehicles() {
  uni.navigateTo({ url: '/sub-pages/vessel/port-vehicles/index' })
}

/** 加入成功后把该船设为当前上下文，用户可直接开始采购 */
function useAsCurrent() {
  if (!activeVessel.value)
    return
  shipContextStore.switchVessel(activeVessel.value.id, activeVessel.value.vesselName)
  uni.showToast({ title: '已设为当前船舶', icon: 'success' })
}

onLoad((options) => {
  if (options?.vesselId) {
    activeVesselId.value = options.vesselId
  }
})

onShow(() => {
  void refreshAll()
})
</script>

<template>
  <view>
    <hr-navbar title="船舶绑定" />

    <!-- 首次加载：骨架屏 -->
    <view v-if="loading && vessels.length === 0" class="px-20rpx pt-20rpx">
      <view class="skeleton h-220rpx rounded-24rpx" />
      <view class="skeleton mt-20rpx h-360rpx rounded-24rpx" />
      <view class="skeleton mt-20rpx h-240rpx rounded-24rpx" />
    </view>

    <!-- 加载失败 -->
    <view v-else-if="loadFailed && vessels.length === 0" class="px-40rpx pt-140rpx text-center">
      <text class="i-carbon-warning-alt block text-80rpx" style="color: #C87D2A" />
      <view class="mt-16rpx text-26rpx" style="color: #86909C">
        加载失败，请检查网络后重试
      </view>
      <button class="mt-32rpx h-80rpx w-320rpx rounded-16rpx text-28rpx leading-80rpx !m-0" type="primary" @tap="refreshAll">
        重新加载
      </button>
    </view>

    <!-- ================= 未绑定：加入船舶 ================= -->
    <template v-else-if="!hasVessel">
      <!-- 欢迎 Hero -->
      <view
        class="mx-20rpx mt-20rpx overflow-hidden rounded-24rpx"
        style="background: linear-gradient(135deg, #378ADD 0%, #6FB3EA 100%); box-shadow: 0 8rpx 24rpx rgba(55, 138, 221, 0.25)"
      >
        <view class="relative p-40rpx">
          <text class="i-carbon-anchor absolute right-20rpx top-16rpx text-140rpx" style="color: rgba(255, 255, 255, 0.14)" />
          <view class="text-40rpx text-white font-bold">
            加入船舶
          </view>
          <view class="mt-12rpx text-26rpx" style="color: rgba(255, 255, 255, 0.85)">
            加入后即可使用船供采购与靠港配送
          </view>
        </view>
      </view>

      <!-- 未登录引导 -->
      <view v-if="!authStore.isLoggedIn" class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx">
        <view class="flex items-center gap-12rpx text-28rpx font-bold">
          <text class="i-carbon-information text-30rpx" style="color: #C87D2A" />
          尚未登录
        </view>
        <view class="mt-8rpx text-24rpx" style="color: #86909C">
          登录后才能输入邀请码或提交绑定申请
        </view>
        <button class="mt-24rpx h-88rpx rounded-16rpx text-28rpx leading-88rpx !m-0" type="primary" @tap="goLogin">
          去登录
        </button>
      </view>

      <!-- 方式一：邀请码 -->
      <view class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx">
        <view class="flex items-center gap-10rpx text-30rpx font-bold">
          <text class="i-carbon-qr-code text-32rpx" style="color: #378ADD" />
          方式一 · 邀请码加入
        </view>
        <view class="mt-8rpx text-24rpx" style="color: #86909C">
          向已在船上的同事索取邀请码，输入即可加入
        </view>
        <view class="mt-24rpx rounded-16rpx" style="background: #F7F8FA">
          <input
            v-model="joinState.code"
            class="h-96rpx px-24rpx text-36rpx tracking-widest"
            style="text-transform: uppercase"
            placeholder="如 A3K9MP"
            placeholder-style="color: #A8ABB2"
            :maxlength="12"
          >
        </view>
        <button
          class="mt-24rpx h-88rpx rounded-16rpx text-30rpx leading-88rpx !m-0"
          type="primary"
          :disabled="joinState.submitting"
          @tap="submitRedeem"
        >
          {{ joinState.submitting ? '加入中…' : '加入船舶' }}
        </button>
      </view>

      <!-- 方式二：提交申请 -->
      <view class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx">
        <view class="flex items-center gap-10rpx text-30rpx font-bold">
          <text class="i-carbon-document-blank text-32rpx" style="color: #378ADD" />
          方式二 · 提交绑定申请
        </view>
        <view class="mt-8rpx text-24rpx" style="color: #86909C">
          没有邀请码时填写申请，由运营审核；业务员认领新船也走这里
        </view>
        <view
          class="mt-24rpx h-88rpx flex items-center justify-center gap-8rpx rounded-16rpx text-28rpx"
          style="border: 1rpx solid #378ADD; color: #378ADD"
          @tap="openApply"
        >
          <text class="i-carbon-edit text-30rpx" />
          填写申请
        </view>
      </view>
    </template>

    <!-- ================= 已绑定：成员管理 ================= -->
    <template v-else>
      <!-- 船舶 Hero 卡 -->
      <view
        class="mx-20rpx mt-20rpx overflow-hidden rounded-24rpx"
        style="background: linear-gradient(135deg, #378ADD 0%, #6FB3EA 100%); box-shadow: 0 8rpx 24rpx rgba(55, 138, 221, 0.25)"
      >
        <view class="relative p-32rpx">
          <text class="i-carbon-sailboat-coastal absolute right-20rpx top-16rpx text-140rpx" style="color: rgba(255, 255, 255, 0.14)" />
          <view class="flex items-center gap-16rpx">
            <text class="i-carbon-sailboat-coastal flex-none text-44rpx text-white" />
            <view class="min-w-0 flex-1 truncate text-40rpx text-white font-bold">
              {{ activeVessel?.vesselName || '我的船舶' }}
            </view>
          </view>
          <view class="mt-20rpx flex flex-wrap items-center gap-12rpx">
            <view v-if="myRole" class="rounded-full px-16rpx py-4rpx text-22rpx" style="background: rgba(255, 255, 255, 0.22); color: #fff">
              我的角色：{{ vesselRoleLabel(myRole) }}
            </view>
            <view class="rounded-full px-16rpx py-4rpx text-22rpx" style="background: rgba(255, 255, 255, 0.22); color: #fff">
              成员 {{ members.length }}
            </view>
          </view>
          <view class="mt-28rpx">
            <view v-if="isCurrentVessel" class="flex items-center gap-8rpx text-26rpx text-white">
              <text class="i-carbon-checkmark-filled text-30rpx" />
              当前购物船舶
            </view>
            <view
              v-else
              class="h-64rpx inline-flex items-center justify-center rounded-full bg-white px-36rpx text-26rpx font-medium"
              style="color: #2A79C6"
              @tap="useAsCurrent"
            >
              设为当前船舶
            </view>
          </view>
        </view>
      </view>

      <!-- 港口报备车辆：靠港外出打车电话簿 -->
      <view
        class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx"
        @tap="goPortVehicles"
      >
        <view class="flex items-center justify-between">
          <view class="flex items-center gap-10rpx text-30rpx font-bold">
            <text class="i-carbon-taxi text-32rpx" style="color: #378ADD" />
            港口报备车辆
          </view>
          <text class="i-carbon-chevron-right text-28rpx" style="color: #A8ABB2" />
        </view>
        <view class="mt-8rpx text-24rpx" style="color: #86909C">
          靠港外出打车：查看当前港口已报备车辆，一键拨打司机电话
        </view>
      </view>

      <!-- 多船切换 -->
      <view v-if="vessels.length > 1" class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-24rpx">
        <view class="text-24rpx" style="color: #86909C">
          我的船舶（{{ vessels.length }}）
        </view>
        <view class="mt-16rpx flex flex-wrap gap-16rpx">
          <view
            v-for="vessel in vessels"
            :key="vessel.id"
            class="rounded-full px-28rpx py-12rpx text-26rpx"
            :style="activeVesselId === vessel.id ? 'background:#378ADD;color:#fff' : 'background:#F2F3F5;color:#4E5969'"
            @tap="switchVessel(vessel.id)"
          >
            {{ vessel.vesselName }}
          </view>
        </view>
      </view>

      <!-- 加入其他船舶：跑船换船时用新船邀请码继续加船 -->
      <view class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx">
        <view class="flex items-center gap-10rpx text-30rpx font-bold">
          <text class="i-carbon-qr-code text-32rpx" style="color: #378ADD" />
          加入其他船舶
        </view>
        <view class="mt-8rpx text-24rpx" style="color: #86909C">
          经常换船跑？输入新船的邀请码加入，加入后可在上方随时切换
        </view>
        <view class="mt-24rpx flex items-center gap-16rpx">
          <view class="flex-1 rounded-16rpx" style="background: #F7F8FA">
            <input
              v-model="joinAnother.code"
              class="h-88rpx px-24rpx text-30rpx tracking-widest"
              style="text-transform: uppercase"
              placeholder="新船邀请码"
              placeholder-style="color: #A8ABB2"
              :maxlength="12"
            >
          </view>
          <button
            class="h-88rpx rounded-16rpx text-28rpx leading-88rpx !m-0"
            style="width: 176rpx"
            type="primary"
            :disabled="joinAnother.submitting"
            @tap="submitJoinAnother"
          >
            {{ joinAnother.submitting ? '加入中…' : '加入' }}
          </button>
        </view>
        <view
          class="mt-20rpx h-80rpx flex items-center justify-center gap-8rpx rounded-16rpx text-26rpx"
          style="border: 1rpx dashed #8FBBE3; color: #378ADD"
          @tap="openApply"
        >
          <text class="i-carbon-edit text-28rpx" />
          没有邀请码？提交申请，由运营审核加入
        </view>
      </view>

      <!-- 成员列表 -->
      <view class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx">
        <view class="flex items-center gap-10rpx text-30rpx font-bold">
          <text class="i-carbon-group text-32rpx" style="color: #378ADD" />
          船上成员（{{ members.length }}）
        </view>

        <view v-for="member in members" :key="member.id" class="mt-24rpx flex items-center">
          <view
            class="h-80rpx w-80rpx flex-none rounded-full text-center text-32rpx text-white font-bold"
            :style="`background:${avatarColor(member.nickname || member.userId)};line-height:80rpx`"
          >
            {{ avatarText(member) }}
          </view>
          <view class="ml-20rpx min-w-0 flex-1">
            <view class="flex items-center gap-12rpx">
              <text class="truncate text-28rpx font-medium">
                {{ member.nickname || member.userId }}
              </text>
              <view
                v-if="member.userId === userStore.getUserId"
                class="flex-none rounded-full px-14rpx py-2rpx text-20rpx"
                style="background: #E6F1FB; color: #378ADD"
              >
                我
              </view>
              <view
                class="flex-none rounded px-10rpx py-2rpx text-20rpx"
                :style="ROLE_BADGE_STYLES[member.memberRole] || ROLE_BADGE_STYLES['2']"
              >
                {{ vesselRoleLabel(member.memberRole) }}
              </view>
            </view>
            <view class="mt-6rpx text-22rpx" style="color: #86909C">
              {{ member.phone || '—' }}
            </view>
          </view>
          <view
            v-if="canManage && member.userId !== userStore.getUserId && member.memberRole !== '1'"
            class="ml-16rpx h-64rpx w-64rpx flex flex-none items-center justify-center rounded-full"
            style="background: #FDEEEE"
            @tap="handleRemoveMember(member)"
          >
            <text class="i-carbon-delete text-28rpx" style="color: #D54941" />
          </view>
        </view>

        <!-- 管理员添加成员入口 -->
        <view
          v-if="canManage"
          class="mt-28rpx h-88rpx flex items-center justify-center gap-8rpx rounded-16rpx text-28rpx"
          style="border: 1rpx dashed #8FBBE3; color: #378ADD"
          @tap="openAddFlow"
        >
          <text class="i-carbon-user-follow text-30rpx" />
          添加成员
        </view>
      </view>

      <!-- 邀请码 -->
      <view v-if="canManage" class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx">
        <view class="flex items-center justify-between">
          <view class="flex items-center gap-10rpx text-30rpx font-bold">
            <text class="i-carbon-ticket text-32rpx" style="color: #378ADD" />
            邀请码
          </view>
          <view
            class="h-60rpx flex items-center gap-4rpx rounded-full px-24rpx text-24rpx"
            style="background: #E6F1FB; color: #378ADD"
            @tap="handleGenerateCode"
          >
            <text class="i-carbon-add text-26rpx" />
            生成新码
          </view>
        </view>
        <view class="mt-8rpx text-24rpx" style="color: #86909C">
          业务员不在场时，把码发给同事自助加入本船
        </view>

        <view
          v-for="item in inviteCodes"
          :key="item.id"
          class="mt-20rpx rounded-16rpx p-24rpx"
          style="background: #F7F8FA"
        >
          <view class="flex items-center justify-between">
            <view class="min-w-0 flex-1 truncate text-40rpx font-bold tracking-widest" style="color: #1F2D3D">
              {{ item.code }}
            </view>
            <view class="ml-16rpx flex flex-none items-center gap-16rpx">
              <view class="h-64rpx w-64rpx flex items-center justify-center rounded-full bg-white" @tap="copyCode(item.code)">
                <text class="i-carbon-copy text-28rpx" style="color: #378ADD" />
              </view>
              <view class="h-64rpx w-64rpx flex items-center justify-center rounded-full bg-white" @tap="handleRevokeCode(item)">
                <text class="i-carbon-close text-28rpx" style="color: #86909C" />
              </view>
            </view>
          </view>
          <view class="mt-8rpx text-22rpx" style="color: #86909C">
            已用 {{ item.usedCount }}{{ item.maxUses > 0 ? `/${item.maxUses}` : ' 次（不限）' }} · {{ item.expiresAt }} 过期
          </view>
        </view>

        <view v-if="inviteCodes.length === 0" class="py-32rpx text-center text-24rpx" style="color: #A8ABB2">
          暂无有效邀请码，点上方「生成新码」创建
        </view>
      </view>

      <!-- 退出本船：发起人除外（后端同口径拒绝，避免该船失去管理角色） -->
      <view v-if="myRole && myRole !== '1'" class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx">
        <view class="flex items-center justify-between">
          <view class="min-w-0 flex-1">
            <view class="text-28rpx font-medium">
              退出「{{ activeVessel?.vesselName || '本船' }}」
            </view>
            <view class="mt-6rpx text-22rpx" style="color: #86909C">
              退出后不能再以该船下单，历史订单不受影响；换船时退出后可加入其他船
            </view>
          </view>
          <view
            class="ml-16rpx h-64rpx flex flex-none items-center rounded-full px-32rpx text-26rpx"
            style="background: #FDEEEE; color: #D54941"
            @tap="handleLeaveVessel"
          >
            退出
          </view>
        </view>
      </view>
    </template>

    <!-- 我的申请：未绑定/已绑定共用（已绑定时是待审核的加船申请） -->
    <view v-if="myApplies.length > 0" class="mx-20rpx mt-20rpx rounded-24rpx bg-white p-32rpx">
      <view class="flex items-center gap-10rpx text-30rpx font-bold">
        <text class="i-carbon-time text-32rpx" style="color: #378ADD" />
        我的申请
      </view>
      <view
        v-for="apply in myApplies"
        :key="apply.id"
        class="mt-20rpx rounded-16rpx p-24rpx"
        style="background: #F7F8FA"
      >
        <view class="flex items-center justify-between">
          <view class="text-28rpx font-medium">
            {{ apply.applyVesselName }}
          </view>
          <view class="rounded-full px-16rpx py-4rpx text-22rpx" :style="APPLY_STATUS_STYLES[apply.status] || APPLY_STATUS_STYLES['4']">
            {{ APPLY_STATUS_LABEL[apply.status] || apply.status }}
          </view>
        </view>
        <view class="mt-8rpx text-22rpx" style="color: #86909C">
          申请单 {{ apply.applyNo }} · {{ apply.createTime }}
        </view>
        <!-- 实际绑定船：审核通过后单独一行展示，避免只看申请船名误以为绑的是同一条 -->
        <view
          v-if="apply.status === '2' && apply.matchedVesselName"
          class="mt-8rpx text-22rpx"
          style="color: #4E5969"
        >
          实际绑定：{{ apply.matchedVesselName }}
        </view>
        <!-- 申请船名与实际绑定船名不一致：审核通过后申请单不再变化，必须显式告知 -->
        <view
          v-if="applyMismatchTip(apply)"
          class="mt-12rpx rounded-8rpx px-16rpx py-10rpx text-22rpx"
          style="background: #FBF0E1; color: #C87D2A"
        >
          {{ applyMismatchTip(apply) }}
        </view>
        <view
          v-if="apply.auditRemark"
          class="mt-12rpx rounded-8rpx px-16rpx py-10rpx text-22rpx"
          style="background: #FBF0E1; color: #C87D2A"
        >
          审核意见：{{ apply.auditRemark }}
        </view>
        <view v-if="apply.status === '1'" class="mt-16rpx flex justify-end">
          <view
            class="h-60rpx flex items-center rounded-full px-28rpx text-24rpx"
            style="background: #FDEEEE; color: #D54941"
            @tap="handleCancelApply(apply)"
          >
            撤回申请
          </view>
        </view>
      </view>
    </view>

    <view style="height: calc(40rpx + env(safe-area-inset-bottom))" />

    <!-- ================= 添加成员弹层：搜人 → 定角色 ================= -->
    <view v-if="addState.visible">
      <view class="fixed bottom-0 left-0 right-0 top-0" :style="`z-index: ${OVERLAY_Z_INDEX.sheetMask}; background: rgba(0, 0, 0, 0.45)`" @tap="addState.visible = false" />
      <view
        class="fixed bottom-0 left-0 right-0 flex flex-col rounded-t-24rpx bg-white"
        :style="`z-index: ${OVERLAY_Z_INDEX.sheet}; height: 80vh`"
      >
        <view class="mx-auto mt-14rpx h-8rpx w-72rpx rounded-full" style="background: #E5E6EB" />
        <view class="flex items-center px-24rpx pb-16rpx pt-18rpx">
          <view
            v-if="addState.mode === 'confirm'"
            class="mr-16rpx h-56rpx w-56rpx flex items-center justify-center rounded-full"
            style="background: #F2F3F5"
            @tap="backToSearch"
          >
            <text class="i-carbon-arrow-left text-28rpx" style="color: #4E5969" />
          </view>
          <view class="flex-1 text-32rpx font-bold">
            {{ addState.mode === 'confirm' ? '确认加入信息' : '添加成员' }}
          </view>
          <view class="h-56rpx w-56rpx flex items-center justify-center" @tap="addState.visible = false">
            <text class="i-carbon-close text-28rpx" style="color: #86909C" />
          </view>
        </view>

        <!-- 搜索视图 -->
        <scroll-view v-if="addState.mode === 'search'" scroll-y style="flex: 1; min-height: 0">
          <view class="mx-30rpx mb-20rpx rounded-12rpx px-24rpx py-16rpx text-22rpx" style="background: #F0F7FF; color: #4E5969">
            输入对方的手机号或昵称搜索，对方需已注册本商城
          </view>
          <view class="mx-30rpx flex items-center gap-16rpx">
            <view class="flex-1 rounded-16rpx" style="background: #F7F8FA">
              <input
                v-model="candidateState.keyword"
                class="h-88rpx px-24rpx text-26rpx"
                placeholder="手机号 / 昵称 / 用户ID"
                placeholder-style="color: #A8ABB2"
                confirm-type="search"
                @confirm="doSearchCandidates"
              >
            </view>
            <button
              class="h-88rpx rounded-16rpx text-26rpx leading-88rpx !m-0"
              style="width: 160rpx"
              type="primary"
              :disabled="candidateState.searching"
              @tap="doSearchCandidates"
            >
              {{ candidateState.searching ? '搜索中' : '搜索' }}
            </button>
          </view>

          <view v-if="candidateState.list.length > 0" class="mx-30rpx mt-12rpx pb-20rpx text-22rpx" style="color: #86909C">
            找到 {{ candidateState.list.length }} 位用户
          </view>
          <view
            v-for="candidate in candidateState.list"
            :key="candidate.userId"
            class="mx-30rpx mt-16rpx flex items-center rounded-16rpx p-24rpx"
            style="background: #F7F8FA"
          >
            <view
              class="h-80rpx w-80rpx flex-none rounded-full text-center text-32rpx text-white font-bold"
              :style="`background:${avatarColor(candidate.nickname || candidate.userId)};line-height:80rpx`"
            >
              {{ avatarText(candidate) }}
            </view>
            <view class="ml-20rpx min-w-0 flex-1">
              <view class="truncate text-28rpx font-medium">
                {{ candidate.nickname || candidate.userId }}
              </view>
              <view class="mt-6rpx text-22rpx" style="color: #86909C">
                {{ candidate.phone || '—' }}
              </view>
            </view>
            <view v-if="candidate.status === '1'" class="text-24rpx" style="color: #A8ABB2">
              已在船上
            </view>
            <view
              v-else
              class="h-64rpx flex items-center rounded-full px-32rpx text-26rpx"
              style="background: #378ADD; color: #fff"
              @tap="openAdd(candidate)"
            >
              添加
            </view>
          </view>
          <view style="height: calc(24rpx + env(safe-area-inset-bottom))" />
        </scroll-view>

        <!-- 角色确认视图 -->
        <template v-else>
          <scroll-view scroll-y style="flex: 1; min-height: 0">
            <view class="mx-30rpx mt-8rpx flex items-center rounded-16rpx p-24rpx" style="background: #F7F8FA">
              <view
                class="h-80rpx w-80rpx flex-none rounded-full text-center text-32rpx text-white font-bold"
                :style="`background:${avatarColor(addState.target?.nickname || addState.target?.userId || '')};line-height:80rpx`"
              >
                {{ addState.target ? avatarText(addState.target) : '?' }}
              </view>
              <view class="ml-20rpx min-w-0 flex-1">
                <view class="truncate text-28rpx font-medium">
                  {{ addState.target?.nickname || addState.target?.userId }}
                </view>
                <view class="mt-6rpx text-22rpx" style="color: #86909C">
                  {{ addState.target?.phone || '—' }}
                </view>
              </view>
            </view>

            <view class="mx-30rpx mt-28rpx text-26rpx">
              船上角色
            </view>
            <view class="mx-30rpx mt-16rpx flex flex-wrap gap-16rpx">
              <view
                v-for="option in [{ label: '普通船员', value: '2' }, { label: '采购确认人', value: '3' }, { label: '业务员', value: '4' }]"
                :key="option.value"
                class="rounded-full px-28rpx py-14rpx text-26rpx"
                :style="addState.memberRole === option.value ? 'background:#378ADD;color:#fff' : 'background:#F2F3F5;color:#4E5969'"
                @tap="addState.memberRole = option.value"
              >
                {{ option.label }}
              </view>
            </view>

            <view class="mx-30rpx mt-32rpx text-26rpx">
              备注
            </view>
            <view class="mx-30rpx mt-16rpx rounded-16rpx" style="background: #F7F8FA">
              <input
                v-model="addState.remark"
                class="h-96rpx px-24rpx text-26rpx"
                placeholder="如：张工-轮机长"
                placeholder-style="color: #A8ABB2"
                :maxlength="30"
              >
            </view>
            <view style="height: 24rpx" />
          </scroll-view>

          <!-- 加人失败原因：与绑定申请同口径，放在操作区上方，弹层开着就一定可见 -->
          <view
            v-if="addState.error"
            class="mx-30rpx mt-8rpx rounded-16rpx px-24rpx py-20rpx text-26rpx"
            style="background: #FDEEEE; color: #D54941"
          >
            <text class="i-carbon-error mr-8rpx text-28rpx" />
            {{ addState.error }}
          </view>

          <view class="flex items-center px-30rpx pt-16rpx" style="padding-bottom: calc(24rpx + env(safe-area-inset-bottom))">
            <button
              class="mr-20rpx h-88rpx flex-1 rounded-16rpx text-28rpx leading-88rpx !m-0"
              style="background: #F2F3F5; color: #4E5969"
              @tap="backToSearch"
            >
              上一步
            </button>
            <button
              class="h-88rpx flex-1 rounded-16rpx text-28rpx leading-88rpx !m-0"
              type="primary"
              :disabled="addState.submitting"
              @tap="submitAdd"
            >
              {{ addState.submitting ? '添加中…' : '确认添加' }}
            </button>
          </view>
        </template>
      </view>
    </view>

    <!-- ================= 绑定申请弹层 ================= -->
    <view v-if="applyState.visible">
      <view class="fixed bottom-0 left-0 right-0 top-0" :style="`z-index: ${OVERLAY_Z_INDEX.sheetMask}; background: rgba(0, 0, 0, 0.45)`" @tap="closeApply" />
      <view
        class="fixed bottom-0 left-0 right-0 flex flex-col rounded-t-24rpx bg-white"
        :style="`z-index: ${OVERLAY_Z_INDEX.sheet}; height: 85vh`"
      >
        <view class="mx-auto mt-14rpx h-8rpx w-72rpx rounded-full" style="background: #E5E6EB" />
        <view class="flex items-center px-24rpx pb-16rpx pt-18rpx">
          <view class="flex-1 text-32rpx font-bold">
            绑定申请
          </view>
          <view class="h-56rpx w-56rpx flex items-center justify-center" @tap="closeApply">
            <text class="i-carbon-close text-28rpx" style="color: #86909C" />
          </view>
        </view>

        <!--
          提交前的可见拦截：后端「同一用户仅一条待审核申请」是刻意保留的防重，
          用户从这里点提交必然失败。把原因和「撤回」出口放在头部（不随表单滚动，
          始终可见），用户不用先失败一次才知道被什么卡住。
        -->
        <view
          v-if="pendingApply"
          class="mx-30rpx rounded-16rpx px-24rpx py-20rpx"
          style="background: #FBF0E1"
        >
          <view class="text-26rpx" style="color: #C87D2A">
            <text class="i-carbon-warning-alt mr-8rpx text-28rpx" />
            {{ pendingApplyTip(pendingApply) }}
          </view>
          <view class="mt-8rpx text-22rpx" style="color: #A8ABB2">
            申请单 {{ pendingApply.applyNo }}
          </view>
          <view class="mt-16rpx flex items-center gap-16rpx">
            <view
              class="h-64rpx flex items-center rounded-full px-32rpx text-24rpx"
              style="background: #FDEEEE; color: #D54941"
              @tap="handleCancelApply(pendingApply)"
            >
              撤回这条申请
            </view>
            <!--
              拦截依赖本地缓存的申请列表：运营刚审核完时这里可能还是旧状态。
              给一个就地刷新入口，避免用户被过期状态挡住却无路可走。
            -->
            <view
              class="h-64rpx flex items-center rounded-full px-28rpx text-24rpx"
              style="background: #F2F3F5; color: #4E5969"
              @tap="refreshPendingState()"
            >
              刷新状态
            </view>
          </view>
        </view>

        <!--
          提交失败的内联原因。
          不依赖全局 toast：小程序里 wd-toast 的层级与页面弹层的层级是两套体系，
          一旦页面浮层更高，提示就被盖住（2026-10-18「提交申请没反应」）。
          内联文案与弹层同层，弹窗开着就一定看得见。
        -->
        <view
          v-if="applyState.error"
          class="mx-30rpx mt-16rpx rounded-16rpx px-24rpx py-20rpx text-26rpx"
          style="background: #FDEEEE; color: #D54941"
        >
          <text class="i-carbon-error mr-8rpx text-28rpx" />
          {{ applyState.error }}
        </view>

        <scroll-view scroll-y style="flex: 1; min-height: 0">
          <view class="mx-30rpx text-26rpx">
            我的身份
          </view>
          <view class="mx-30rpx mt-16rpx flex gap-16rpx">
            <view
              v-for="option in [{ label: '船上人员', value: '2' }, { label: '商城业务员', value: '4' }]"
              :key="option.value"
              class="rounded-full px-28rpx py-14rpx text-26rpx"
              :style="applyState.form.applyRole === option.value ? 'background:#378ADD;color:#fff' : 'background:#F2F3F5;color:#4E5969'"
              @tap="applyState.form.applyRole = option.value"
            >
              {{ option.label }}
            </view>
          </view>

          <view class="mx-30rpx mt-32rpx text-26rpx">
            船舶信息
          </view>
          <view class="mx-30rpx mt-16rpx rounded-16rpx px-24rpx" style="background: #F7F8FA">
            <view class="flex items-center" style="min-height: 96rpx; border-bottom: 1rpx solid #EDEEF0">
              <view class="w-190rpx text-26rpx">
                <text style="color: #D54941">
                  *
                </text>
                船名
              </view>
              <input
                v-model="applyState.form.applyVesselName"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="如 悦航1号"
                placeholder-style="color: #A8ABB2"
                :maxlength="30"
              >
            </view>
            <view class="flex items-center" style="min-height: 96rpx; border-bottom: 1rpx solid #EDEEF0">
              <view class="w-190rpx text-26rpx">
                IMO 编号
              </view>
              <input
                v-model="applyState.form.applyVesselImo"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="选填，IMO 为 7 位数字"
                placeholder-style="color: #A8ABB2"
                :maxlength="30"
              >
            </view>
            <view class="flex items-center" style="min-height: 96rpx; border-bottom: 1rpx solid #EDEEF0">
              <view class="w-190rpx text-26rpx">
                呼号
              </view>
              <input
                v-model="applyState.form.applyVesselCallSign"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="选填，如 3EABC"
                placeholder-style="color: #A8ABB2"
                :maxlength="30"
              >
            </view>
            <view class="flex items-center" style="min-height: 96rpx">
              <view class="w-190rpx text-26rpx">
                常靠港口
              </view>
              <input
                v-model="applyState.form.applyPortName"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="选填，如 上海港"
                placeholder-style="color: #A8ABB2"
                :maxlength="30"
              >
            </view>
          </view>

          <view class="mx-30rpx mt-32rpx text-26rpx">
            联系信息
          </view>
          <view class="mx-30rpx mt-16rpx rounded-16rpx px-24rpx" style="background: #F7F8FA">
            <view class="flex items-center" style="min-height: 96rpx; border-bottom: 1rpx solid #EDEEF0">
              <view class="w-190rpx text-26rpx">
                真实姓名
              </view>
              <input
                v-model="applyState.form.realName"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="选填"
                placeholder-style="color: #A8ABB2"
                :maxlength="20"
              >
            </view>
            <view class="flex items-center" style="min-height: 96rpx; border-bottom: 1rpx solid #EDEEF0">
              <view class="w-190rpx text-26rpx">
                联系电话
              </view>
              <input
                v-model="applyState.form.phone"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="选填"
                placeholder-style="color: #A8ABB2"
                type="number"
                :maxlength="20"
              >
            </view>
            <view class="flex items-center" style="min-height: 96rpx; border-bottom: 1rpx solid #EDEEF0">
              <view class="w-190rpx text-26rpx">
                职务/工号
              </view>
              <input
                v-model="applyState.form.position"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="选填，如 轮机长"
                placeholder-style="color: #A8ABB2"
                :maxlength="30"
              >
            </view>
            <view class="flex items-center" style="min-height: 96rpx">
              <view class="w-190rpx text-26rpx">
                补充说明
              </view>
              <input
                v-model="applyState.form.remark"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="选填"
                placeholder-style="color: #A8ABB2"
                :maxlength="50"
              >
            </view>
          </view>
          <view style="height: 24rpx" />
        </scroll-view>

        <view class="flex items-center px-30rpx pt-16rpx" style="padding-bottom: calc(24rpx + env(safe-area-inset-bottom))">
          <button
            class="mr-20rpx h-88rpx flex-1 rounded-16rpx text-28rpx leading-88rpx !m-0"
            style="background: #F2F3F5; color: #4E5969"
            @tap="closeApply"
          >
            取消
          </button>
          <button
            class="h-88rpx flex-1 rounded-16rpx text-28rpx leading-88rpx !m-0"
            type="primary"
            :disabled="applyState.submitting"
            @tap="submitApply"
          >
            {{ applyState.submitting ? '提交中…' : '提交申请' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.skeleton {
  background: linear-gradient(90deg, #EFF1F4 25%, #F7F8FA 37%, #EFF1F4 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.4s ease infinite;
}

@keyframes skeleton-loading {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}
</style>
