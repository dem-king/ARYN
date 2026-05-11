<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { getById, orderAppraise } from '@/api/order/orderInfo'

definePage({
  name: 'order-appraise',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '发表评论',
  },
})
const loading = ref(true)
const { error } = useGlobalToast()
const router = useRouter()
const globalLoading = useGlobalLoading()
const authStore = useAuthStore()

const action = ref(`${import.meta.env.VITE_API_BASE_URL + (import.meta.env.VITE_OPEN_BOOT === 'true' ? '/boot' : '/upms')}/file/app/upload`)
const headers = ref({
  satoken: authStore.getToken,
})
const state = reactive<{ list: Array<any> }>({
  list: [],
})
onLoad(async (options) => {
  getOrder(options?.id)
})

async function getOrder(id: string) {
  globalLoading.loading('加载中...')
  try {
    const response = await getById(id)
    response.orderItemList.forEach((item: any) => {
      state.list.push({
        picUrl: item.picUrl,
        spuName: item.spuName,
        specsInfo: item.specsInfo,
        spuId: item.spuId,
        orderId: item.orderId,
        orderItemId: item.id,
        picUrls: [],
        uploadList: [],
        goodsScore: 5,
        content: '',
      })
    })
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}
async function onsubmit() {
  for (const item of state.list) {
    if (!item.content) {
      error('请填写评价')
      return
    }
    if (Array.isArray(item.uploadList) && item.uploadList.length > 0) {
      item.picUrls = []
      item.uploadList.forEach((up: any) => {
        let res: any = up.response
        if (typeof res === 'string') {
          res = JSON.parse(res)
        }
        if (res && res.data) {
          item.picUrls.push(res.data)
        }
      })
    }
  }
  try {
    globalLoading.loading('加载中...')
    await orderAppraise(state.list[0].orderId, state.list)
    router.push({
      name: 'user-appraise',
    })
  }
  finally {
    globalLoading.close()
  }
}
</script>

<template>
  <hr-navbar title="发表评论" />
  <view class="p-20rpx">
    <view v-for="(item, index) in state.list" :key="index" class="mb-2 rounded-xl bg-white p-2">
      <view class="flex p-20rpx">
        <image :src="item.picUrl" class="h-160rpx w-160rpx flex-none rounded-8rpx" />
        <view class="ml-20rpx h-full flex flex-1 flex-col overflow-hidden">
          <view>
            <wd-text :lines="2" size="26rpx" color="inherit" :text="item.spuName" />
          </view>
          <view v-if="item.specsInfo">
            <wd-text custom-class="pt-10rpx" size="24rpx" color="#909090" :text="item.specsInfo" />
          </view>
        </view>
      </view>
      <view class="flex items-center p-10rpx">
        <view class="pr-10rpx">
          商品评价
        </view>
        <wd-rate v-model="item.goodsScore" :num="5" />
      </view>
      <view class="p-10rpx">
        <wd-textarea v-model="item.content" custom-class="wd-textarea border border-gray-400! rounded-16rpx! m-0!" placeholder="请填写评价" />
      </view>
      <view>
        <wd-upload v-model:file-list="item.uploadList" multiple :header="headers" image-mode="aspectFill" :action="action" :limit="6" />
      </view>
    </view>
    <wd-gap height="50" />
    <view class="fixed bottom-0 left-0 right-0 bg-white p-10rpx pb-[max(env(safe-area-inset-bottom),16rpx)]">
      <wd-button block type="primary" @click="onsubmit">
        确认发表
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
:deep(.wd-textarea) {
  border: 1px solid #bfbfbf !important;
  border-radius: 10rpx !important;
}
</style>
