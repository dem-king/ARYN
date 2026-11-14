<template>
  <view class="buyer-org-page">
    <hr-navbar :title="t('buyerOrg.title')" />

    <scroll-view class="page-scroll" scroll-y>
      <view class="tabs">
        <view
          class="tab"
          :class="{ 'tab--active': tab === 'my' }"
          @click="tab = 'my'"
        >
          {{ t('buyerOrg.myTitle') }}
        </view>
        <view
          class="tab"
          :class="{ 'tab--active': tab === 'join' }"
          @click="tab = 'join'"
        >
          {{ t('buyerOrg.join') }}
        </view>
      </view>

      <template v-if="tab === 'my'">
        <view v-if="loading" class="state-card">
          {{ t('common.loading') }}
        </view>

        <view v-else-if="orgs.length === 0" class="state-card">
          <view class="state-title">
            {{ t('buyerOrg.empty') }}
          </view>
          <view class="state-desc">
            {{ t('buyerOrg.emptyDesc') }}
          </view>
          <view class="primary-btn" @click="tab = 'join'">
            {{ t('buyerOrg.create') }}
          </view>
        </view>

        <template v-else>
          <view v-for="org in orgs" :key="org.id" class="org-card">
          <view class="org-head">
            <view class="org-name">
              {{ org.orgName }}
            </view>
            <view class="org-status" :class="statusClass(org.auditStatus)">
              {{ statusText(org.auditStatus) }}
            </view>
          </view>
          <view class="org-meta">
            <text>{{ orgTypeText(org.orgType) }}</text>
            <text v-if="org.city || org.district">
              {{ [org.city, org.district].filter(Boolean).join(' / ') }}
            </text>
            <text v-if="org.memberCount">
              {{ t('buyerOrg.memberCount', { count: org.memberCount }) }}
            </text>
          </view>
          <view v-if="org.auditStatus === '0'" class="org-hint">
            {{ t('buyerOrg.pendingHint') }}
          </view>
          <view v-else-if="org.auditStatus === '2'" class="org-hint">
            {{ t('buyerOrg.rejectedHint') }}
          </view>

          <view class="org-actions">
            <view class="ghost-btn" @click="toggleMembers(org)">
              {{ t('buyerOrg.members') }}
            </view>
            <view
              v-if="org.viewerIsOwner && org.auditStatus === '1'"
              class="ghost-btn"
              @click="generateInvite(org)"
            >
              {{ t('buyerOrg.inviteCode') }}
            </view>
            <view
              v-if="!org.viewerIsOwner"
              class="ghost-btn ghost-btn--danger"
              @click="leave(org)"
            >
              {{ t('buyerOrg.leave') }}
            </view>
          </view>

          <view v-if="expandedId === org.id" class="members">
            <view v-if="membersLoading" class="members-empty">
              {{ t('common.loading') }}
            </view>
            <view v-else-if="members.length === 0" class="members-empty">
              {{ t('buyerOrg.noMembers') }}
            </view>
            <template v-else>
              <view v-for="member in members" :key="member.id" class="member-row">
                <view class="member-main">
                  <text class="member-name">{{ member.nickname || member.userId }}</text>
                  <text class="member-role">{{ roleText(member.memberRole) }}</text>
                </view>
                <view
                  v-if="org.viewerIsOwner && member.memberRole !== '1'"
                  class="member-remove"
                  @click="removeMember(org, member)"
                >
                  {{ t('buyerOrg.removeMember') }}
                </view>
              </view>
            </template>
          </view>
        </view>
        </template>

        <view v-if="applies.length > 0" class="apply-box">
          <view class="apply-title">
            {{ t('buyerOrg.applyPending') }}
          </view>
          <view v-for="apply in applies" :key="apply.id" class="apply-row">
            <text class="apply-name">{{ apply.orgName || apply.applyNo }}</text>
            <text class="apply-status" :class="statusClass(apply.status)">
              {{ statusText(apply.status) }}
            </text>
          </view>
        </view>
      </template>

      <template v-else>
        <view class="panel">
          <view class="panel-title">
            {{ t('buyerOrg.join') }}
          </view>
          <view class="search-row">
            <input
              v-model="keyword"
              class="input"
              :placeholder="t('buyerOrg.searchPlaceholder')"
              @confirm="doSearch"
            >
            <view class="search-btn" @click="doSearch">
              {{ t('buyerOrg.search') }}
            </view>
          </view>
          <view v-if="searched && searchResults.length === 0" class="panel-hint">
            {{ t('buyerOrg.searchEmpty') }}
          </view>
          <view v-for="item in searchResults" :key="item.id" class="search-item">
            <view class="search-main">
              <view class="search-name">{{ item.orgName }}</view>
              <view class="search-meta">
                {{ orgTypeText(item.orgType) }}
                <text v-if="item.city"> · {{ item.city }}</text>
              </view>
            </view>
            <view class="mini-btn" @click="applyJoin(item)">
              {{ t('buyerOrg.joinAction') }}
            </view>
          </view>
        </view>

        <view class="panel">
          <view class="panel-title">
            {{ t('buyerOrg.inviteJoin') }}
          </view>
          <view class="search-row">
            <input
              v-model="inviteCode"
              class="input"
              :placeholder="t('buyerOrg.codePlaceholder')"
            >
            <view class="search-btn" @click="joinByCode">
              {{ t('buyerOrg.joinAction') }}
            </view>
          </view>
        </view>

        <view class="panel">
          <view class="panel-title">
            {{ t('buyerOrg.createTitle') }}
          </view>
          <view class="field">
            <text class="label">{{ t('buyerOrg.orgName') }} *</text>
            <input v-model="form.orgName" class="input" :placeholder="t('buyerOrg.orgName')">
          </view>
          <view class="field">
            <text class="label">{{ t('buyerOrg.orgType') }}</text>
            <picker :range="orgTypeLabels" @change="onOrgTypeChange">
              <view class="picker-value">
                {{ orgTypeLabels[orgTypeIndex] }}
              </view>
            </picker>
          </view>
          <view class="field">
            <text class="label">{{ t('buyerOrg.address') }}</text>
            <input v-model="form.address" class="input" :placeholder="t('buyerOrg.address')">
          </view>
          <view class="field">
            <text class="label">{{ t('buyerOrg.contactName') }}</text>
            <input v-model="form.contactName" class="input" :placeholder="t('buyerOrg.contactName')">
          </view>
          <view class="field">
            <text class="label">{{ t('buyerOrg.contactPhone') }}</text>
            <input v-model="form.contactPhone" class="input" type="number" :placeholder="t('buyerOrg.contactPhone')">
          </view>
          <view class="panel-hint">
            {{ t('buyerOrg.noLicenseHint') }}
          </view>
          <view class="primary-btn" @click="submitCreate">
            {{ t('buyerOrg.submitApply') }}
          </view>
        </view>
      </template>

      <view class="page-foot-space" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import type { BuyerOrg, BuyerOrgMember } from '@/api/order/buyerOrg'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { onLoad } from '@dcloudio/uni-app'
import {
  applyBuyerOrg,
  BUYER_ORG_AUDIT_APPROVED,
  BUYER_ORG_AUDIT_DISABLED,
  BUYER_ORG_AUDIT_PENDING,
  BUYER_ORG_AUDIT_REJECTED,
  createBuyerOrgInviteCode,
  getBuyerOrgMembers,
  getMyBuyerOrgApplies,
  getMyBuyerOrgs,
  joinBuyerOrgByCode,
  leaveBuyerOrg,
  removeBuyerOrgMember,
  searchBuyerOrgs,
} from '@/api/order/buyerOrg'
import { useUserStore } from '@/store/userStore'

definePage({
  name: 'buyer-org',
  style: {
    enablePullDownRefresh: true,
    navigationStyle: 'custom',
    navigationBarTitleText: '采购主体',
  },
})

const { t } = useI18n()
const userStore = useUserStore()

const tab = ref<'join' | 'my'>('my')
const loading = ref(false)
const orgs = ref<BuyerOrg[]>([])
const applies = ref<Array<{ id: string, applyNo: string, orgName: string, status: string }>>([])
const expandedId = ref('')
const members = ref<BuyerOrgMember[]>([])
const membersLoading = ref(false)

const keyword = ref('')
const searchResults = ref<BuyerOrg[]>([])
const searched = ref(false)

const inviteCode = ref('')
const orgTypes = computed(() => [
  { label: t('buyerOrg.orgTypeFactory'), value: '2' },
  { label: t('buyerOrg.orgTypeEnterprise'), value: '1' },
  { label: t('buyerOrg.orgTypeRestaurant'), value: '3' },
])
const orgTypeIndex = ref(0)
const orgTypeLabels = computed(() => orgTypes.value.map(item => item.label))
const form = reactive({
  orgName: '',
  address: '',
  contactName: '',
  contactPhone: '',
})

const orgTypeMap = computed<Record<string, string>>(() => ({
  1: t('buyerOrg.orgTypeEnterprise'),
  2: t('buyerOrg.orgTypeFactory'),
  3: t('buyerOrg.orgTypeRestaurant'),
}))

onLoad(() => {
  // 联系人和手机号默认取当前账号，避免让用户把已有信息再填一遍
  form.contactName = userStore.getUserNickname || ''
  form.contactPhone = userStore.getUserPhone || ''
  void load()
})

async function load() {
  loading.value = true
  try {
    const [list, applyList] = await Promise.all([
      getMyBuyerOrgs(),
      getMyBuyerOrgApplies(),
    ])
    orgs.value = list ?? []
    applies.value = applyList ?? []
  }
  catch {
    uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
  }
  finally {
    loading.value = false
  }
}

function orgTypeText(type?: string) {
  return orgTypeMap.value[type ?? ''] ?? t('buyerOrg.orgType')
}

function statusText(status?: string) {
  if (status === BUYER_ORG_AUDIT_APPROVED)
    return t('buyerOrg.approved')
  if (status === BUYER_ORG_AUDIT_REJECTED)
    return t('buyerOrg.rejected')
  if (status === BUYER_ORG_AUDIT_DISABLED)
    return t('buyerOrg.disabled')
  if (status === BUYER_ORG_AUDIT_PENDING)
    return t('buyerOrg.applyPending')
  return ''
}

function statusClass(status?: string) {
  return {
    'status--bad': status === BUYER_ORG_AUDIT_REJECTED || status === BUYER_ORG_AUDIT_DISABLED,
    'status--pending': status === BUYER_ORG_AUDIT_PENDING,
    'status--ok': status === BUYER_ORG_AUDIT_APPROVED,
  }
}

function roleText(role?: string) {
  const map: Record<string, string> = {
    1: t('buyerOrg.roleOwner'),
    2: t('buyerOrg.roleBuyer'),
    3: t('buyerOrg.roleSales'),
    4: t('buyerOrg.roleViewer'),
  }
  return map[role ?? ''] ?? ''
}

async function toggleMembers(org: BuyerOrg) {
  if (expandedId.value === org.id) {
    expandedId.value = ''
    return
  }
  expandedId.value = org.id
  membersLoading.value = true
  members.value = []
  try {
    members.value = await getBuyerOrgMembers(org.id)
  }
  catch {
    uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
  }
  finally {
    membersLoading.value = false
  }
}

async function generateInvite(org: BuyerOrg) {
  try {
    const code = await createBuyerOrgInviteCode(org.id, { expireHours: 168 })
    uni.showModal({
      title: t('buyerOrg.inviteCode'),
      content: `${code.code}\n${t('buyerOrg.inviteCodeTip')}`,
      confirmText: t('buyerOrg.copyCode'),
      success: (res) => {
        if (res.confirm)
          uni.setClipboardData({ data: code.code })
      },
    })
  }
  catch {
    uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
  }
}

function leave(org: BuyerOrg) {
  uni.showModal({
    title: t('buyerOrg.leave'),
    content: org.orgName,
    success: async (res) => {
      if (!res.confirm)
        return
      try {
        await leaveBuyerOrg(org.id)
        await load()
      }
      catch {
        uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
      }
    },
  })
}

function removeMember(org: BuyerOrg, member: BuyerOrgMember) {
  uni.showModal({
    title: t('buyerOrg.removeMember'),
    content: member.nickname || member.userId,
    success: async (res) => {
      if (!res.confirm)
        return
      try {
        await removeBuyerOrgMember(org.id, member.userId)
        members.value = members.value.filter(item => item.userId !== member.userId)
      }
      catch {
        uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
      }
    },
  })
}

async function doSearch() {
  const value = keyword.value.trim()
  if (!value)
    return
  try {
    searchResults.value = await searchBuyerOrgs(value)
    searched.value = true
  }
  catch {
    uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
  }
}

function applyJoin(item: BuyerOrg) {
  uni.showModal({
    title: t('buyerOrg.joinAction'),
    content: item.orgName,
    success: async (res) => {
      if (!res.confirm)
        return
      try {
        await applyBuyerOrg({ applyType: '2', buyerOrgId: item.id })
        uni.showToast({ title: t('buyerOrg.joinSubmitted'), icon: 'success' })
        tab.value = 'my'
        await load()
      }
      catch {
        uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
      }
    },
  })
}

async function joinByCode() {
  const code = inviteCode.value.trim()
  if (!code) {
    uni.showToast({ title: t('buyerOrg.required'), icon: 'none' })
    return
  }
  try {
    await joinBuyerOrgByCode(code)
    uni.showToast({ title: t('buyerOrg.joinByCodeSuccess'), icon: 'success' })
    inviteCode.value = ''
    tab.value = 'my'
    await load()
  }
  catch {
    uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
  }
}

function onOrgTypeChange(event: { detail: { value: number | string } }) {
  orgTypeIndex.value = Number(event.detail.value)
}

async function submitCreate() {
  if (!form.orgName.trim()) {
    uni.showToast({ title: t('buyerOrg.required'), icon: 'none' })
    return
  }
  try {
    await applyBuyerOrg({
      applyType: '1',
      orgName: form.orgName.trim(),
      orgType: orgTypes.value[orgTypeIndex.value]?.value,
      address: form.address.trim() || undefined,
      contactName: form.contactName.trim() || undefined,
      contactPhone: form.contactPhone.trim() || undefined,
    })
    uni.showToast({ title: t('buyerOrg.createSuccess'), icon: 'success' })
    form.orgName = ''
    form.address = ''
    form.contactName = userStore.getUserNickname || ''
    form.contactPhone = userStore.getUserPhone || ''
    tab.value = 'my'
    await load()
  }
  catch {
    uni.showToast({ title: t('buyerOrg.loadFailed'), icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.buyer-org-page {
  display: flex;
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background: var(--app-bg);
}

.page-scroll {
  min-height: 0;
  flex: 1;
}

.tabs {
  display: flex;
  margin: 20rpx 24rpx;
  padding: 8rpx;
  background: var(--app-card);
  border-radius: 999rpx;
}

.tab {
  flex: 1;
  padding: 16rpx 0;
  color: var(--app-text-2);
  font-size: 28rpx;
  text-align: center;
  border-radius: 999rpx;
}

.tab--active {
  color: #fff;
  font-weight: 600;
  background: var(--wot-color-theme-primary, #ff2237);
}

.state-card,
.org-card,
.panel,
.apply-box {
  margin: 20rpx 24rpx;
  padding: 28rpx;
  background: var(--app-card);
  border-radius: 20rpx;
}

.state-title {
  color: var(--app-text-1);
  font-size: 32rpx;
  font-weight: 600;
}

.state-desc {
  margin-top: 12rpx;
  color: var(--app-text-2);
  font-size: 26rpx;
  line-height: 1.6;
}

.primary-btn {
  margin-top: 28rpx;
  padding: 20rpx 0;
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  text-align: center;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 999rpx;
}

.org-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.org-name {
  flex: 1;
  color: var(--app-text-1);
  font-size: 32rpx;
  font-weight: 600;
}

.org-status {
  padding: 6rpx 14rpx;
  color: var(--app-text-2);
  font-size: 22rpx;
  background: var(--app-card-2);
  border-radius: 999rpx;
}

.status--ok {
  color: #1f9d55;
  background: rgba(31, 157, 85, 0.1);
}

.status--pending {
  color: #c07a00;
  background: rgba(192, 122, 0, 0.1);
}

.status--bad {
  color: #d33a3a;
  background: rgba(211, 58, 58, 0.1);
}

.org-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx 20rpx;
  margin-top: 14rpx;
  color: var(--app-text-2);
  font-size: 24rpx;
}

.org-hint {
  margin-top: 16rpx;
  padding: 14rpx 16rpx;
  color: var(--app-text-2);
  font-size: 24rpx;
  background: var(--app-card-2);
  border-radius: 10rpx;
}

.org-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 20rpx;
}

.ghost-btn,
.mini-btn {
  padding: 12rpx 24rpx;
  color: var(--app-text-1);
  font-size: 24rpx;
  background: var(--app-card-2);
  border-radius: 999rpx;
}

.ghost-btn--danger {
  color: #d33a3a;
}

.members {
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--app-border);
}

.member-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 0;
}

.member-main {
  display: flex;
  gap: 16rpx;
  align-items: center;
}

.member-name {
  color: var(--app-text-1);
  font-size: 26rpx;
}

.member-role {
  color: var(--app-text-3);
  font-size: 22rpx;
}

.member-remove {
  color: #d33a3a;
  font-size: 24rpx;
}

.members-empty {
  padding: 20rpx 0;
  color: var(--app-text-3);
  font-size: 24rpx;
}

.panel-title {
  margin-bottom: 20rpx;
  color: var(--app-text-1);
  font-size: 30rpx;
  font-weight: 600;
}

.search-row {
  display: flex;
  gap: 12rpx;
  align-items: center;
}

.input {
  flex: 1;
  min-width: 0;
  padding: 18rpx 20rpx;
  color: var(--app-text-1);
  font-size: 26rpx;
  background: var(--app-card-2);
  border-radius: 12rpx;
}

.search-btn {
  padding: 18rpx 24rpx;
  color: #fff;
  font-size: 26rpx;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 12rpx;
}

.panel-hint {
  margin-top: 16rpx;
  color: var(--app-text-3);
  font-size: 24rpx;
}

.search-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 18rpx;
  background: var(--app-card-2);
  border-radius: 12rpx;
}

.search-main {
  flex: 1;
  min-width: 0;
}

.search-name {
  color: var(--app-text-1);
  font-size: 28rpx;
  font-weight: 600;
}

.search-meta {
  margin-top: 6rpx;
  color: var(--app-text-3);
  font-size: 22rpx;
}

.field {
  margin-top: 18rpx;
}

.label {
  display: block;
  margin-bottom: 10rpx;
  color: var(--app-text-2);
  font-size: 24rpx;
}

.picker-value {
  padding: 18rpx 20rpx;
  color: var(--app-text-1);
  font-size: 26rpx;
  background: var(--app-card-2);
  border-radius: 12rpx;
}

.apply-box {
  background: var(--app-card-2);
}

.apply-title {
  margin-bottom: 12rpx;
  color: var(--app-text-2);
  font-size: 24rpx;
}

.apply-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 0;
}

.apply-name {
  color: var(--app-text-1);
  font-size: 26rpx;
}

.apply-status {
  padding: 4rpx 12rpx;
  font-size: 22rpx;
  border-radius: 999rpx;
}

.page-foot-space {
  height: 60rpx;
}
</style>
