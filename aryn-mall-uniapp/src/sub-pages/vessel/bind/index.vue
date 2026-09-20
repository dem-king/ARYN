<script setup lang="ts">
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
 */
import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'

import {
  addVesselMember,
  cancelBindApply,
  generateInviteCode,
  getMyBindApplies,
  getMyVessels,
  getMyInviteCodes,
  getVesselMembers,
  redeemInviteCode,
  removeVesselMember,
  revokeInviteCode,
  searchMemberCandidates,
  submitBindApply,
  type VesselBindApply,
  type VesselInviteCode,
  type VesselMember,
} from '@/api/vessel'
import hrNavbar from '@/components/hr-navbar/index.vue'
import { useAuthStore } from '@/store/authStore'
import { useShipContextStore } from '@/store/shipContextStore'
import { useUserStore } from '@/store/userStore'
import { memberRoleLabel } from '@/utils/shared-cart'

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

/** 可管理成员的角色 */
const MANAGE_ROLES = ['1', '3', '4']

const members = ref<VesselMember[]>([])
const myRole = computed(() => members.value.find(m => m.userId === userStore.getUserId)?.memberRole ?? '')
const canManage = computed(() => MANAGE_ROLES.includes(myRole.value))

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
const addState = reactive({ visible: false, target: null as null | VesselMember, memberRole: '2', remark: '', submitting: false })

const APPLY_STATUS_LABEL: Record<string, string> = {
  1: '待审核',
  2: '已通过',
  3: '已驳回',
  4: '已撤回',
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function fetchVessels() {
  if (!authStore.isLoggedIn) return Promise.resolve()
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
  if (!activeVesselId.value) return Promise.resolve()
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
  if (joinState.submitting) return
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

function submitApply() {
  if (!applyState.form.applyVesselName.trim()) {
    uni.showToast({ title: '请填写船名', icon: 'none' })
    return
  }
  if (applyState.submitting) return
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
      if (!res.confirm) return
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

function openAdd(candidate: VesselMember) {
  addState.target = candidate
  addState.memberRole = '2'
  addState.remark = ''
  addState.visible = true
}

function submitAdd() {
  if (!addState.target || addState.submitting) return
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
      if (!res.confirm) return
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
    success: () => uni.showToast({ title: '邀请码已复制', icon: 'success' }),
  })
}

function handleRevokeCode(item: VesselInviteCode) {
  uni.showModal({
    title: '撤销邀请码',
    content: '撤销后该邀请码立即失效，确定吗？',
    success: (res) => {
      if (!res.confirm) return
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
  if (!activeVessel.value) return
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

    <view v-if="loading && vessels.length === 0" class="py-80rpx text-center text-26rpx text-gray-400">
      加载中...
    </view>

    <view v-else-if="loadFailed && vessels.length === 0" class="py-80rpx text-center">
      <view class="text-26rpx text-gray-400">
        加载失败，请检查网络后重试
      </view>
      <view class="mt-20rpx text-26rpx text-blue-500" @tap="refreshAll">
        重新加载
      </view>
    </view>

    <!-- ================= 未绑定：加入船舶 ================= -->
    <template v-else-if="!hasVessel">
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="text-30rpx font-bold">
          加入船舶
        </view>
        <view class="mt-8rpx text-24rpx text-gray-500">
          加入后即可使用船供采购与靠港配送
        </view>
      </view>

      <!-- 方式一：邀请码 -->
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          方式一：输入邀请码
        </view>
        <view class="mt-6rpx text-24rpx text-gray-500">
          向已在船上的同事索取 6 位邀请码
        </view>
        <input
          v-model="joinState.code"
          class="mt-16rpx h-80rpx rounded-12rpx bg-gray-50 px-24rpx text-32rpx tracking-widest"
          placeholder="如 A3K9MP"
          :maxlength="12"
        >
        <button
          class="!m-0 mt-20rpx h-72rpx text-28rpx leading-72rpx"
          type="primary"
          :disabled="joinState.submitting"
          @tap="submitRedeem"
        >
          加入
        </button>
      </view>

      <!-- 方式二：提交申请 -->
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          方式二：提交绑定申请
        </view>
        <view class="mt-6rpx text-24rpx text-gray-500">
          没有邀请码时提交申请，由运营审核。业务员认领新船也走这里。
        </view>
        <button class="!m-0 mt-20rpx h-72rpx text-28rpx leading-72rpx" @tap="openApply">
          填写申请
        </button>
      </view>

      <!-- 申请表单 -->
      <view v-if="applyState.visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          绑定申请
        </view>
        <view class="mt-16rpx text-24rpx text-gray-500">
          我的身份
        </view>
        <view class="mt-10rpx flex gap-16rpx">
          <view
            v-for="option in [{ label: '船上人员', value: '2' }, { label: '公司业务员', value: '4' }]"
            :key="option.value"
            class="rounded-30rpx px-24rpx py-8rpx text-24rpx"
            :style="applyState.form.applyRole === option.value
              ? 'background:#378ADD;color:#fff'
              : 'background:#F1EFE8;color:#5F5E5A'"
            @tap="applyState.form.applyRole = option.value"
          >
            {{ option.label }}
          </view>
        </view>

        <input
          v-model="applyState.form.applyVesselName"
          class="mt-20rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="船名（必填，如 悦航1号）"
        >
        <input
          v-model="applyState.form.applyVesselImo"
          class="mt-10rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="IMO 或呼号（选填，便于运营匹配）"
        >
        <input
          v-model="applyState.form.applyPortName"
          class="mt-10rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="常靠港口（选填，如 上海港）"
        >
        <input
          v-model="applyState.form.realName"
          class="mt-10rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="真实姓名"
        >
        <input
          v-model="applyState.form.phone"
          class="mt-10rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="联系电话"
        >
        <input
          v-model="applyState.form.position"
          class="mt-10rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="船上职务 / 业务员工号"
        >
        <input
          v-model="applyState.form.remark"
          class="mt-10rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="补充说明（选填）"
        >

        <view class="mt-20rpx flex gap-20rpx">
          <button class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx" @tap="applyState.visible = false">
            取消
          </button>
          <button
            class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
            type="primary"
            :disabled="applyState.submitting"
            @tap="submitApply"
          >
            提交
          </button>
        </view>
      </view>

      <!-- 我的申请 -->
      <view v-if="myApplies.length > 0" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          我的申请
        </view>
        <view v-for="apply in myApplies" :key="apply.id" class="mt-20rpx border-b border-gray-100 pb-20rpx">
          <view class="flex items-center justify-between">
            <view class="text-26rpx">
              {{ apply.applyVesselName }}
            </view>
            <text class="text-24rpx text-gray-500">
              {{ APPLY_STATUS_LABEL[apply.status] || apply.status }}
            </text>
          </view>
          <view class="mt-6rpx text-22rpx text-gray-400">
            申请单 {{ apply.applyNo }} · {{ apply.createTime }}
          </view>
          <view v-if="apply.auditRemark" class="mt-6rpx text-22rpx text-amber-600">
            审核意见：{{ apply.auditRemark }}
          </view>
          <view v-if="apply.status === '1'" class="mt-10rpx">
            <text class="text-24rpx text-red-500" @tap="handleCancelApply(apply)">撤回申请</text>
          </view>
        </view>
      </view>

      <view v-if="!authStore.isLoggedIn" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-26rpx text-gray-500">
          登录后可加入船舶
        </view>
        <button class="!m-0 mt-16rpx h-72rpx text-28rpx leading-72rpx" type="primary" @tap="goLogin">
          去登录
        </button>
      </view>
    </template>

    <!-- ================= 已绑定：成员管理 ================= -->
    <template v-else>
      <!-- 船舶切换 -->
      <view v-if="vessels.length > 1" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-24rpx text-gray-500">
          选择船舶
        </view>
        <view class="mt-12rpx flex flex-wrap gap-16rpx">
          <view
            v-for="vessel in vessels"
            :key="vessel.id"
            class="rounded-30rpx px-24rpx py-8rpx text-24rpx"
            :style="activeVesselId === vessel.id ? 'background:#378ADD;color:#fff' : 'background:#F1EFE8;color:#5F5E5A'"
            @tap="switchVessel(vessel.id)"
          >
            {{ vessel.vesselName }}
          </view>
        </view>
      </view>

      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-30rpx font-bold">
            {{ activeVessel?.vesselName || '我的船舶' }}
          </view>
          <text class="text-24rpx text-blue-500" @tap="useAsCurrent">
            设为当前船舶
          </text>
        </view>
        <view class="mt-8rpx text-24rpx text-gray-500">
          我的角色：{{ memberRoleLabel(myRole) || '加载中' }}
          <text v-if="!canManage && myRole" class="text-gray-400">（普通船员不能管理成员）</text>
        </view>
      </view>

      <!-- 成员列表 -->
      <view class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          成员（{{ members.length }}）
        </view>
        <view v-for="member in members" :key="member.id" class="mt-16rpx flex items-center justify-between">
          <view>
            <view class="text-26rpx">
              {{ member.nickname || member.userId }}
              <text v-if="member.userId === userStore.getUserId" class="text-blue-500">（我）</text>
            </view>
            <view class="mt-4rpx text-22rpx text-gray-400">
              {{ member.phone || '—' }} · {{ memberRoleLabel(member.memberRole) }}
            </view>
          </view>
          <text
            v-if="canManage && member.userId !== userStore.getUserId && member.memberRole !== '1'"
            class="text-24rpx text-red-500"
            @tap="handleRemoveMember(member)"
          >
            移除
          </text>
        </view>
      </view>

      <!-- 添加成员（业务员现场拉人） -->
      <view v-if="canManage" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          添加成员
        </view>
        <view class="mt-6rpx text-24rpx text-gray-500">
          输入对方的手机号或昵称，对方需已注册本商城
        </view>
        <view class="mt-16rpx flex items-center gap-16rpx">
          <input
            v-model="candidateState.keyword"
            class="h-72rpx flex-1 rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
            placeholder="手机号 / 昵称 / 用户ID"
          >
          <button
            class="!m-0 h-72rpx !px-28rpx text-26rpx leading-72rpx"
            type="primary"
            size="mini"
            :disabled="candidateState.searching"
            @tap="doSearchCandidates"
          >
            搜索
          </button>
        </view>

        <view v-for="candidate in candidateState.list" :key="candidate.userId" class="mt-16rpx flex items-center justify-between">
          <view>
            <view class="text-26rpx">
              {{ candidate.nickname || candidate.userId }}
            </view>
            <view class="mt-4rpx text-22rpx text-gray-400">
              {{ candidate.phone || '—' }}
            </view>
          </view>
          <text v-if="candidate.status === '1'" class="text-24rpx text-gray-400">已在船上</text>
          <text v-else class="text-24rpx text-blue-500" @tap="openAdd(candidate)">添加</text>
        </view>
      </view>

      <!-- 添加成员弹层 -->
      <view v-if="addState.visible" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="text-28rpx font-bold">
          添加 {{ addState.target?.nickname || addState.target?.userId }}
        </view>
        <view class="mt-16rpx text-24rpx text-gray-500">
          角色
        </view>
        <view class="mt-10rpx flex flex-wrap gap-16rpx">
          <view
            v-for="option in [{ label: '普通船员', value: '2' }, { label: '采购确认人', value: '3' }, { label: '业务员', value: '4' }]"
            :key="option.value"
            class="rounded-30rpx px-24rpx py-8rpx text-24rpx"
            :style="addState.memberRole === option.value
              ? 'background:#378ADD;color:#fff'
              : 'background:#F1EFE8;color:#5F5E5A'"
            @tap="addState.memberRole = option.value"
          >
            {{ option.label }}
          </view>
        </view>
        <input
          v-model="addState.remark"
          class="mt-20rpx h-72rpx rounded-12rpx bg-gray-50 px-24rpx text-26rpx"
          placeholder="备注（如：张工-轮机长）"
        >
        <view class="mt-20rpx flex gap-20rpx">
          <button class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx" @tap="addState.visible = false">
            取消
          </button>
          <button
            class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
            type="primary"
            :disabled="addState.submitting"
            @tap="submitAdd"
          >
            确认添加
          </button>
        </view>
      </view>

      <!-- 邀请码 -->
      <view v-if="canManage" class="mx-20rpx mt-20rpx rounded-20rpx bg-white p-24rpx">
        <view class="flex items-center justify-between">
          <view class="text-28rpx font-bold">
            邀请码
          </view>
          <text class="text-24rpx text-blue-500" @tap="handleGenerateCode">
            生成新码
          </text>
        </view>
        <view class="mt-6rpx text-24rpx text-gray-500">
          业务员不在场时，把码给同事让他们自助加入
        </view>
        <view v-for="item in inviteCodes" :key="item.id" class="mt-16rpx flex items-center justify-between">
          <view>
            <view class="text-32rpx font-bold tracking-widest">
              {{ item.code }}
            </view>
            <view class="mt-4rpx text-22rpx text-gray-400">
              已用 {{ item.usedCount }}{{ item.maxUses > 0 ? `/${item.maxUses}` : ' 次（不限）' }} · {{ item.expiresAt }} 过期
            </view>
          </view>
          <view class="flex items-center gap-20rpx">
            <text class="text-24rpx text-blue-500" @tap="copyCode(item.code)">复制</text>
            <text class="text-24rpx text-red-500" @tap="handleRevokeCode(item)">撤销</text>
          </view>
        </view>
        <view v-if="inviteCodes.length === 0" class="py-30rpx text-center text-24rpx text-gray-400">
          暂无有效邀请码
        </view>
      </view>
    </template>

    <view style="height: 40rpx" />
  </view>
</template>
