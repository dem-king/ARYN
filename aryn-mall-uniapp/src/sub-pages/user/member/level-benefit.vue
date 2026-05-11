<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMemberLevelList, getLevelBenefits } from '@/api/user/benefit'

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
  upgradeCondition: string
  level: number
  benefits: MemberBenefit[]
}

const globalLoading = useGlobalLoading()
const levelList = ref<MemberLevel[]>([])
const expandedLevelId = ref<string>('')

onLoad(() => {
  loadLevelList()
})

async function loadLevelList() {
  globalLoading.loading('加载中...')
  try {
    const response = await getMemberLevelList()
    levelList.value = (response || []).map((level: any) => ({
      ...level,
      benefits: [],
    }))
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

  // 如果尚未加载权益，则请求
  if (level.benefits.length === 0) {
    try {
      const benefits = await getLevelBenefits(level.id)
      level.benefits = benefits || []
    }
    catch (error) {
      console.error('加载等级权益失败:', error)
    }
  }
}

function getBenefitTypeTag(type: string): string {
  const typeMap: Record<string, string> = {
    DISCOUNT: '折扣',
    GIFT: '赠品',
    POINT: '积分',
    PRIVILEGE: '特权',
    COUPON: '优惠券',
  }
  return typeMap[type] || type
}

function getBenefitTypeClass(type: string): string {
  const classMap: Record<string, string> = {
    DISCOUNT: 'bg-blue-50 text-blue-500',
    GIFT: 'bg-purple-50 text-purple-500',
    POINT: 'bg-green-50 text-green-500',
    PRIVILEGE: 'bg-orange-50 text-orange-500',
    COUPON: 'bg-red-50 text-red-500',
  }
  return classMap[type] || 'bg-gray-50 text-gray-500'
}
</script>

<template>
  <hr-navbar title="等级权益" />
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
            Lv.{{ level.level }}
          </view>
          <view class="ml-3">
            <view class="text-15px font-bold">{{ level.levelName }}</view>
            <view v-if="level.upgradeCondition" class="text-12px text-gray-400 mt-1">
              升级条件：{{ level.upgradeCondition }}
            </view>
          </view>
        </view>
        <view class="flex items-center">
          <text class="text-12px color-primary mr-1">
            {{ expandedLevelId === level.id ? '收起' : '查看权益' }}
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
          暂无权益
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
              {{ benefit.benefitValue }}
            </text>
          </view>
          <view v-if="benefit.description" class="text-12px text-gray-400 mt-1">
            {{ benefit.description }}
          </view>
        </view>
      </view>
    </view>

    <view v-if="levelList.length === 0" class="text-center text-gray-400 py-10">
      暂无等级信息
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

.rotate-180 {
  transform: rotate(180deg);
}

.color-primary {
  color: var(--wot-color-theme);
}
</style>
