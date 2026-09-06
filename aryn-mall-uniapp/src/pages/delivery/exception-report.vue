<script setup lang="ts">
import { ref } from 'vue'
import { reportException } from '@/api/delivery'
import { uploadDeliveryEvidence } from '@/api/upms/file'
import { useToast } from 'wot-design-uni'

const toast = useToast()

const props = defineProps<{
  taskId: string
}>()

const emit = defineEmits<{
  success: []
}>()

const reasonOptions = [
  { value: 'CUSTOMER_NOT_HOME', label: '客户不在' },
  { value: 'UNREACHABLE', label: '无法联系' },
  { value: 'ADDRESS_ERROR', label: '地址错误' },
  { value: 'CUSTOMER_REJECT', label: '客户拒收' },
  { value: 'PRODUCT_ISSUE', label: '商品问题' },
  { value: 'VEHICLE_ISSUE', label: '车辆/人员问题' },
  { value: 'OTHER', label: '其他' },
]

const reasonCode = ref('')
const reasonDesc = ref('')
const materialIds = ref<any[]>([])
const submitting = ref(false)

const handleSubmit = async () => {
  if (!reasonCode.value) {
    toast.warning('请选择异常原因')
    return
  }
  if (!reasonDesc.value) {
    toast.warning('请填写异常说明')
    return
  }
  submitting.value = true
  try {
    await reportException(
      props.taskId,
      reasonCode.value,
      reasonDesc.value,
      materialIds.value,
    )
    toast.success('异常上报成功')
    emit('success')
  } catch (e: any) {
    toast.error(e?.message ?? '上报失败')
  } finally {
    submitting.value = false
  }
}

async function handleChooseImage() {
  if (materialIds.value.length >= 6)
    return
  uni.chooseImage({
    count: 6 - materialIds.value.length,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async ({ tempFilePaths }) => {
      const paths = Array.isArray(tempFilePaths) ? tempFilePaths : [tempFilePaths]
      try {
        const ids = await Promise.all(paths.map(path => uploadDeliveryEvidence(path)))
        materialIds.value.push(...ids)
      }
      catch (error: any) {
        toast.error(error?.message ?? '图片上传失败')
      }
    },
  })
}
</script>

<template>
  <wd-popup position="bottom" :safe-area-inset-bottom="true" custom-class="exception-popup">
    <view class="exception-report">
      <view class="header">
        <text class="title">上报异常</text>
      </view>
      <view class="form">
        <view class="form-item">
          <text class="label">异常原因</text>
          <wd-picker
            v-model="reasonCode"
            :columns="reasonOptions"
            label-key="label"
            value-key="value"
            placeholder="请选择异常原因"
          />
        </view>
        <view class="form-item">
          <text class="label">异常说明（必填）</text>
          <wd-textarea
            v-model="reasonDesc"
            placeholder="请描述异常情况"
            :maxlength="500"
            show-count
          />
        </view>
        <view class="form-item">
          <text class="label">异常图片（选填）</text>
          <wd-upload
            :file-list="materialIds.map(id => ({ url: id }))"
            :limit="6"
            :before-upload="() => false"
            @click="handleChooseImage"
          />
        </view>
      </view>
      <view class="footer">
        <wd-button block type="info" @click="$emit('success')">取消</wd-button>
        <wd-button block type="error" :loading="submitting" @click="handleSubmit">提交异常</wd-button>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.exception-report {
  padding: 16px;

  .header {
    text-align: center;
    margin-bottom: 16px;

    .title {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .form {
    .form-item {
      margin-bottom: 16px;

      .label {
        display: block;
        font-size: 14px;
        margin-bottom: 8px;
        color: #333;
      }
    }
  }

  .footer {
    display: flex;
    gap: 12px;
    margin-top: 24px;
  }
}
</style>
