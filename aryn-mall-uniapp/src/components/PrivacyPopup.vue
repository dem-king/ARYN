<script lang="ts" setup>
import { onBeforeMount, computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

interface Props {
  title?: string // 标题
  desc?: string // 描述
  subDesc?: string // 字描述
  protocol?: string // 协议名称
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  desc: '',
  subDesc: '',
  protocol: '',
})

const { t } = useI18n()

/**
 * 文案兜底走当前语言词条：页面可以覆盖标题/描述，但未传时不能固化中文。
 * 用 computed 而不是 props 默认值，切换语言后能立即重渲染。
 */
const titleText = computed(() => props.title || t('shared.privacy.title'))
const descText = computed(() => props.desc || t('shared.privacy.desc'))
const subDescText = computed(() => props.subDesc || t('shared.privacy.subDesc'))
const protocolText = computed(() => props.protocol || t('shared.privacy.protocol'))

const emit = defineEmits(['agree', 'disagree'])
const showPopup = ref<boolean>(false) // 是否展示popup

const privacyResolves = ref(new Set()) // onNeedPrivacyAuthorization的reslove

function privacyHandler(resolve: any) {
  showPopup.value = true
  privacyResolves.value.add(resolve)
}

onBeforeMount(() => {
  // 注册监听
  if (wx.onNeedPrivacyAuthorization) {
    wx.onNeedPrivacyAuthorization((resolve: any) => {
      if (typeof privacyHandler === 'function') {
        privacyHandler(resolve)
      }
    })
  }
})

/**
 * 同意隐私协议
 */
function handleAgree() {
  showPopup.value = false
  privacyResolves.value.forEach((resolve: any) => {
    resolve({
      event: 'agree',
      buttonId: 'agree-btn',
    })
  })
  privacyResolves.value.clear()
  emit('agree')
}

/**
 * 拒绝隐私协议
 */
function handleDisagree() {
  showPopup.value = false
  privacyResolves.value.forEach((resolve: any) => {
    resolve({
      event: 'disagree',
    })
  })
  privacyResolves.value.clear()
}

/**
 * 打开隐私协议
 */
function openPrivacyContract() {
  wx.openPrivacyContract({})
}

/**
 * 弹出框关闭时清空
 */
function handleClose() {
  privacyResolves.value.clear()
}
</script>

<script lang="ts">
export default {
  options: {
    virtualHost: true,
    addGlobalClass: true,
    styleIsolation: 'shared',
  },
}
</script>

<template>
  <view>
    <wd-popup v-model="showPopup" :close-on-click-modal="false" custom-class="wd-privacy-popup" @close="handleClose">
      <view class="wd-privacy-popup__header">
        <!-- 标题 -->
        <view class="wd-picker__title">
          {{ titleText }}
        </view>
      </view>
      <view class="wd-privacy-popup__container">
        <text>{{ descText }}</text>
        <text class="wd-privacy-popup__container-protocol" @click="openPrivacyContract">
          {{ protocolText }}
        </text>
        <text>{{ subDescText }}</text>
      </view>
      <view class="wd-privacy-popup__footer">
        <button id="disagree-btn" class="is-block is-round is-medium is-plain wd-privacy-popup__footer-disagree wd-button" @click="handleDisagree">
          {{ t('shared.privacy.disagree') }}
        </button>
        <button
          id="agree-btn"
          class="wd-button is-block is-round is-medium is-primary wd-privacy-popup__footer-agree"
          open-type="agreePrivacyAuthorization"
          @agreeprivacyauthorization="handleAgree"
        >
          {{ t('shared.privacy.agree') }}
        </button>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
@import 'wot-design-uni/components/wd-button/index.scss';
:deep(.wd-privacy-popup) {
  width: 600rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border-radius: 32rpx;
  overflow: hidden;
}

.wd-privacy-popup {
  &__header {
    width: 100%;
    height: 128rpx;
    line-height: 128rpx;
    color: rgba(0, 0, 0, 0.85);
    font-size: 30rpx;
    padding: 0 12rpx;
    box-sizing: border-box;
  }

  &__container {
    width: 100%;
    box-sizing: border-box;
    padding: 0 12rpx;
    margin-bottom: 32rpx;

    font-size: 28rpx;
    line-height: 1.8;
    color: #3e3e3e;
    text-align: left;
    font-weight: 550;
    &-protocol {
      color: #4d80f0;
    }
  }

  &__footer {
    display: flex;
    justify-content: space-between;
    padding-bottom: 36rpx;

    // 组件 wxss 禁止标签名选择器（微信编译告警），两个按钮本就有类名，按类名命中
    &-disagree,
    &-agree {
      border: none;
      outline: none;
    }
  }
}
</style>
