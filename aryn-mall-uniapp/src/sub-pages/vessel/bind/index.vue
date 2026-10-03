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
 *  · 已绑定 → 「成员管理」：业务员现场拉人、生成邀请码给同事
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
  redeemInviteCode,
  removeVesselMember,
  revokeInviteCode,
  searchMemberCandidates,
  submitBindApply,

} from '@/api/vessel'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useUserStore } from '@/store/userStore'

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
  form: {
    applyRole: '2',
    applyVesselName: '',
    applyVesselImo: '',
    applyPortName: '',
    realName: '',
    phone: '',
    position: '',
    remark: '',
  },
})
const myApplies = ref<VesselBindApply[]>([])

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
  return fetchVessels().then(() => (hasVessel.value ? fetchMemberData() : fetchJoinData()))
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
  applyState.visible = true
}

function closeApply() {
  applyState.visible = false
}

function submitApply() {
  if (!applyState.form.applyVesselName.trim()) {
    uni.showToast({ title: '请填写船名', icon: 'none' })
    return
  }
  if (applyState.submitting)
    return
  applyState.submitting = true
  submitBindApply({
    applyRole: applyState.form.applyRole,
    applyVesselName: applyState.form.applyVesselName.trim(),
    applyVesselImo: applyState.form.applyVesselImo || undefined,
    applyPortName: applyState.form.applyPortName || undefined,
    realName: applyState.form.realName || undefined,
    phone: applyState.form.phone || undefined,
    position: applyState.form.position || undefined,
    remark: applyState.form.remark || undefined,
  })
    .then(() => {
      applyState.visible = false
      uni.showToast({ title: '已提交，等待运营审核', icon: 'success' })
      return fetchJoinData()
    })
    .catch(() => {})
    .finally(() => {
      applyState.submitting = false
    })
}

function handleCancelApply(apply: VesselBindApply) {
  uni.showModal({
    title: '撤回申请',
    content: '确定撤回这条申请吗？',
    success: (res) => {
      if (!res.confirm)
        return
      cancelBindApply(apply.id)
        .then(() => {
          uni.showToast({ title: '已撤回', icon: 'success' })
          return fetchJoinData()
        })
        .catch(() => {})
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
  addState.mode = 'confirm'
}

function backToSearch() {
  addState.mode = 'search'
}

function submitAdd() {
  if (!addState.target || addState.submitting)
    return
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
    .catch(() => {})
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

      <!-- 我的申请 -->
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
    </template>

    <view style="height: calc(40rpx + env(safe-area-inset-bottom))" />

    <!-- ================= 添加成员弹层：搜人 → 定角色 ================= -->
    <view v-if="addState.visible">
      <view class="fixed bottom-0 left-0 right-0 top-0" style="z-index: 900; background: rgba(0, 0, 0, 0.45)" @tap="addState.visible = false" />
      <view
        class="fixed bottom-0 left-0 right-0 flex flex-col rounded-t-24rpx bg-white"
        style="z-index: 901; height: 80vh"
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
      <view class="fixed bottom-0 left-0 right-0 top-0" style="z-index: 900; background: rgba(0, 0, 0, 0.45)" @tap="closeApply" />
      <view
        class="fixed bottom-0 left-0 right-0 flex flex-col rounded-t-24rpx bg-white"
        style="z-index: 901; height: 85vh"
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

        <scroll-view scroll-y style="flex: 1; min-height: 0">
          <view class="mx-30rpx text-26rpx">
            我的身份
          </view>
          <view class="mx-30rpx mt-16rpx flex gap-16rpx">
            <view
              v-for="option in [{ label: '船上人员', value: '2' }, { label: '公司业务员', value: '4' }]"
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
                IMO / 呼号
              </view>
              <input
                v-model="applyState.form.applyVesselImo"
                class="h-96rpx flex-1 bg-transparent text-right text-26rpx"
                placeholder="选填，便于运营匹配"
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
