<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { getMemberLevelList, getLevelBenefits } from '@/sub-pages/api/user/benefit'

definePage({
  name: 'member-level-benefit',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '等级权益',
  },
})

interface MemberBenefit {
  id: string
  benefitName: string
  benefitType: string
  benefitValue: string
  description: string
}

interface MemberLevel {
  id: string
  levelName: string
  levelIcon: string
  conditionType: string
  conditionValue: number
  sortOrder: number
  benefits: MemberBenefit[]
}

const { t } = useI18n()
const globalLoading = useGlobalLoading()
const userStore = useUserStore()
const levelList = ref<MemberLevel[]>([])
const expandedLevelId = ref<string>('')

onLoad(() => {
  loadLevelList()
})

async function loadLevelList() {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getMemberLevelList()
    levelList.value = (response || []).map((level: any) => ({
      ...level,
      benefits: [],
    }))
    const currentLevel = levelList.value.find(level => level.id === userStore.getLevelId)
    if (currentLevel) {
      expandedLevelId.value = currentLevel.id
      await loadLevelBenefits(currentLevel)
    }
  }
  catch (error) {
    console.error('加载等级列表失败:', error)
  }
  finally {
    globalLoading.close()
  }
}

async function toggleLevelBenefits(level: MemberLevel) {
  if (expandedLevelId.value === level.id) {
    expandedLevelId.value = ''
    return
  }

  expandedLevelId.value = level.id
  await loadLevelBenefits(level)
}

async function loadLevelBenefits(level: MemberLevel) {
  if (level.benefits.length > 0) {
    return
  }
  try {
    const benefits = await getLevelBenefits(level.id)
    level.benefits = benefits || []
  }
  catch (error) {
    console.error('加载等级权益失败:', error)
  }
}

const benefitTypeKeyMap: Record<string, string> = {
  1: 'typeDiscount',
  2: 'typeFreeShipping',
  3: 'typeExclusiveCoupon',
  4: 'typePointsMultiplier',
}

function getBenefitTypeTag(type: string): string {
  const key = benefitTypeKeyMap[type]
  return key ? t(`accountMember.levelBenefit.${key}`) : type
}

/** 升级门槛由等级条件换算，随语言切换实时刷新 */
function formatUpgradeCondition(level: MemberLevel): string {
  return level.conditionType === '1'
    ? t('accountMember.levelBenefit.spendCondition', { amount: level.conditionValue })
    : t('accountMember.levelBenefit.pointsCondition', { points: level.conditionValue })
}

function getBenefitTypeClass(type: string): string {
  const classMap: Record<string, string> = {
    1: 'bg-blue-50 text-blue-500',
    2: 'bg-green-50 text-green-500',
    3: 'bg-red-50 text-red-500',
    4: 'bg-orange-50 text-orange-500',
  }
  return classMap[type] || 'bg-gray-50 text-gray-500'
}

function formatBenefitValue(benefit: MemberBenefit): string {
  if (benefit.benefitType === '1') {
    const rate = Number(benefit.benefitValue)
    return t('accountMember.levelBenefit.valueDiscount', {
      rate: rate * 10,
      percent: Number(((1 - rate) * 100).toFixed(1)),
    })
  }
  if (benefit.benefitType === '2')
    return t('accountMember.levelBenefit.valueFreeShipping')
  if (benefit.benefitType === '3')
    return t('accountMember.levelBenefit.valueExclusive')
  if (benefit.benefitType === '4')
    return t('accountMember.levelBenefit.valueMultiplier', { value: benefit.benefitValue })
  return ''
}
</script>

<template>
  <hr-navbar :title="t('accountMember.levelBenefit.title')" />
  <view class="m-2">
    <view v-for="level in levelList" :key="level.id" class="level-card">
      <!-- 等级头部 -->
      <view class="level-header" @click="toggleLevelBenefits(level)">
        <view class="flex items-center">
          <image
            v-if="level.levelIcon"
            :src="level.levelIcon"
            class="h-40rpx w-40rpx"
            mode="aspectFit"
          />
          <view v-else class="level-icon-placeholder">
            Lv.{{ level.sortOrder }}
          </view>
          <view class="ml-3">
            <view class="flex items-center">
              <text class="text-15px font-bold">{{ level.levelName }}</text>
              <text v-if="level.id === userStore.getLevelId" class="current-level-tag">
                {{ t('accountMember.levelBenefit.currentLevel') }}
              </text>
            </view>
            <view v-if="level.conditionType" class="text-12px text-gray-400 mt-1">
              {{ t('accountMember.levelBenefit.upgradeCondition', { condition: formatUpgradeCondition(level) }) }}
            </view>
          </view>
        </view>
        <view class="flex items-center">
          <text class="text-12px color-primary mr-1">
            {{ expandedLevelId === level.id ? t('accountMember.levelBenefit.collapse') : t('accountMember.levelBenefit.viewBenefits') }}
          </text>
          <text
            class="i-carbon:chevron-down text-sm color-primary transition-transform"
            :class="{ 'rotate-180': expandedLevelId === level.id }"
          />
        </view>
      </view>

      <!-- 权益列表 -->
      <view v-if="expandedLevelId === level.id" class="benefit-list">
        <view v-if="level.benefits.length === 0" class="text-center text-gray-400 py-4">
          {{ t('accountMember.levelBenefit.noBenefits') }}
        </view>
        <view
          v-for="benefit in level.benefits"
          :key="benefit.id"
          class="benefit-item"
        >
          <view class="flex items-center justify-between">
            <view class="flex items-center">
              <text class="text-14px">{{ benefit.benefitName }}</text>
              <text class="ml-2 text-11px rounded px-1 py-0.5" :class="getBenefitTypeClass(benefit.benefitType)">
                {{ getBenefitTypeTag(benefit.benefitType) }}
              </text>
            </view>
            <text v-if="benefit.benefitValue" class="text-13px color-primary font-bold">
              {{ formatBenefitValue(benefit) }}
            </text>
          </view>
          <view v-if="benefit.description" class="text-12px text-gray-400 mt-1">
            {{ benefit.description }}
          </view>
        </view>
      </view>
    </view>

    <view v-if="levelList.length === 0" class="text-center text-gray-400 py-10">
      {{ t('accountMember.levelBenefit.noLevels') }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
.level-card {
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.level-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 30rpx;
}

.level-icon-placeholder {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 20rpx;
  font-weight: bold;
}

.benefit-list {
  padding: 0 30rpx 24rpx;
  border-top: 1rpx solid #f5f5f5;
}

.benefit-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }
}

.current-level-tag {
  margin-left: 10rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
  background: #fff1f0;
  font-size: 18rpx;
  color: var(--wot-color-theme);
}

.rotate-180 {
  transform: rotate(180deg);
}

.color-primary {
  color: var(--wot-color-theme);
}
</style>
