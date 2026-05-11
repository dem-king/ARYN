<script setup lang="ts">
import type { GroupBuyRecord } from '@/api/promotion/groupBuyRecord'
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getRecordPage } from '@/api/promotion/groupBuyRecord'

definePage({
  name: 'group-buy-record',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的拼团',
  },
})

const pagingRef = ref()
const state = reactive<{ list: GroupBuyRecord[] }>({
  list: [],
})
const globalLoading = useGlobalLoading()

onLoad(async () => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading('加载中...')
  try {
    const response = await getRecordPage({
      current: pageNo,
      size: pageSize,
      desc: 'create_time',
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}

function getGroupStatusLabel(status?: string) {
  if (status === '0')
    return '拼团中'
  if (status === '1')
    return '拼团成功'
  if (status === '2')
    return '拼团失败'
  return ''
}

function getGroupStatusClass(status?: string) {
  if (status === '0')
    return 'status-pending'
  if (status === '1')
    return 'status-success'
  if (status === '2')
    return 'status-fail'
  return ''
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.list" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar title="我的拼团" />
    </template>
    <view class="record-list">
      <view v-for="item in state.list" :key="item.id" class="record-card">
        <view class="card-top">
          <view class="record-id">
            拼团ID: {{ item.id }}
          </view>
          <view class="record-status" :class="[getGroupStatusClass(item.groupStatus)]">
            {{ getGroupStatusLabel(item.groupStatus) }}
          </view>
        </view>
        <view class="card-body">
          <view class="info-row">
            <text class="label">
              拼团价
            </text>
            <text class="value price">
              ¥{{ item.groupPrice }}
            </text>
          </view>
          <view class="info-row">
            <text class="label">
              成团进度
            </text>
            <text class="value">
              {{ item.currentNum }}/{{ item.groupNum }}人
            </text>
          </view>
          <view class="info-row">
            <text class="label">
              过期时间
            </text>
            <text class="value">
              {{ item.expireAt }}
            </text>
          </view>
        </view>
      </view>
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
.record-list {
  padding: 24rpx;
}

.record-card {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);

  .card-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16rpx;

    .record-id {
      font-size: 24rpx;
      color: #999;
    }

    .record-status {
      font-size: 24rpx;
      padding: 4rpx 12rpx;
      border-radius: 4rpx;

      &.status-pending { color: #ff8c00; background: #fff7e6; }
      &.status-success { color: #52c41a; background: #f6ffed; }
      &.status-fail { color: #999; background: #f5f5f5; }
    }
  }

  .card-body {
    .info-row {
      display: flex;
      justify-content: space-between;
      padding: 8rpx 0;
      font-size: 26rpx;

      .label { color: #999; }
      .value { color: #333; }
      .value.price { color: #ff4500; font-weight: 600; }
    }
  }
}
</style>
