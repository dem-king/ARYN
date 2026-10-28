<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { delAddress, getPage, saveOrUpdateAddress } from '@/api/user/address'

definePage({
  name: 'address-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '收货地址',
  },
})
const pagingRef = ref()
const { t } = useI18n()

// 用于下单时选择收货地址
const placeChooseFlag = ref(false)
const placeChooseId = ref('')
const router = useRouter()
const { confirm } = useGlobalMessage()
const globalLoading = useGlobalLoading()
const state = reactive<{ addressList: Array<any> }>({
  addressList: [],
})

onLoad((options) => {
  placeChooseFlag.value = options?.placeChooseFlag
  placeChooseId.value = options?.placeChooseId
})
onShow(async () => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})
async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
    })
    pagingRef.value?.complete(response.records)
  }
  finally {
    globalLoading.close()
  }
}

function toForm(id: string) {
  router.push({
    name: 'address-form',
    params: {
      id,
    },
  })
}
function selectAddRess(row: any) {
  if (!placeChooseFlag.value) {
    return
  }
  uni.$emit('update:selectedAddress', row)
  uni.navigateBack()
}
async function handleChange(item: any, index: number) {
  globalLoading.loading(t('common.loading'))
  try {
    await saveOrUpdateAddress(item)
    state.addressList.forEach(v => (v.isDefault = '0'))
    state.addressList[index].isDefault = '1'
  }
  finally {
    globalLoading.close()
  }
}
// 删除
function del(id: string, index: number) {
  confirm({
    title: t('accountSettings.address.deleteTitle'),
    msg: t('accountSettings.address.deleteConfirm'),
    closeOnClickModal: false,
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    success: (res) => {
      if (res.action === 'confirm') {
        globalLoading.loading(t('common.loading'))
        delAddress(id).then(() => {
          state.addressList.splice(index, 1)
          globalLoading.close()
        }).catch(() => {
          globalLoading.close()
        })
      }
    },
  })
}
</script>

<template>
  <z-paging ref="pagingRef" v-model="state.addressList" :auto="false" @query="queryList">
    <template #top>
      <hr-navbar :title="t('accountSettings.address.navTitle')" />
    </template>
    <view
      v-for="(item, index) in state.addressList" :key="index" class="m-2 rounded-16rpx bg-white p-2"
      :class="placeChooseFlag && (placeChooseId === item.id) ? 'cursor-pointer' : ''" @click="selectAddRess(item)"
    >
      <view>
        <wd-text custom-class="p-r-10rpx" color="inherit" size="28rpx" :text="item.recipientName" />
        <wd-text custom-class="p-r-20rpx" color="inherit" size="26rpx" :text="item.telephone" /><wd-tag
          v-if="item.isDefault === '1'" type="primary" plain
        >
          {{ t('accountSettings.address.defaultTag') }}
        </wd-tag>
      </view>
      <view>
        <wd-text size="24rpx" :text="item.detailAddress" />
      </view>
      <view class="address-operation m-t-20rpx flex items-center justify-between">
        <wd-checkbox
          v-model="item.isDefault" shape="square" :disabled="item.isDefault === '1'" true-value="1"
          false-value="0" @tap.stop="" @change="handleChange(item, index)"
        >
          {{ item.isDefault === '1'
            ? t('accountSettings.address.alreadyDefault')
            : t('accountSettings.address.setDefault') }}
        </wd-checkbox>

        <view class="flex">
          <view class="rounded-10rpx bg-white p-2 text-12px" @tap.stop="toForm(item.id)">
            {{ t('accountSettings.address.edit') }}
          </view>
          <view class="rounded-10rpx bg-white p-2 text-12px" @tap.stop="del(item.id, index)">
            {{ t('accountSettings.address.delete') }}
          </view>
        </view>
      </view>
    </view>
    <template #bottom>
      <view class="address-bottom">
        <wd-button block type="primary" @click="toForm('')">
          {{ t('accountSettings.address.addNew') }}
        </wd-button>
      </view>
    </template>
  </z-paging>
</template>

<style lang="scss">
.address-bottom {
  border: solid 1rpx #f2f2f2;
  background-color: #ffffff;
  padding: 20rpx;
  padding-bottom: calc(env(safe-area-inset-bottom) / 2);
}
.cursor-pointer{
  border: var(--wot-color-theme-primary) 1px solid;
}
</style>
