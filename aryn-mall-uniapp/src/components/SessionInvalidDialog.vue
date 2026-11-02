<script setup lang="ts">
/**
 * 会话失效阻断弹窗（一期：被顶下线 / 登录过期；二期：KICK_OUT + subReason 细分）。
 *
 * 挂载点：根组件 `src/App.ku.vue`（每页一份实例，但状态在 Pinia 里是全局单例，
 * 所以任意页面都能弹、切页后新页面的实例会立刻接着显示）。
 *
 * 为什么不用 `uni.showModal`（README §7.2 明确排除）：
 *  - 原生 modal 只支持纯文本、按钮文案 ≤4 个汉字，放不下原因/设备信息；
 *  - 「重新登录」要 reLaunch，原生 modal 会连同页面一起被销毁，用户看到弹窗闪一下；
 *  - 无法做到「点遮罩/ESC 不关闭」。
 *
 * 不可关闭：无关闭按钮、`close-on-click-modal=false`；唯一出口是「重新登录」「退出」。
 * 弹窗期间**不清 storage、不跳转**（页面数据保持原样），用户点按钮后由
 * sessionStore 清凭证并 reLaunch。
 *
 * 二期按钮规则：`canRelogin=false`（账号停用/删除、租户停用、会员禁用）时重登必然失败，
 * 只渲染一个主按钮（「返回登录」/「退出」），点击走 `exitToLogin()`（清态→登录页、
 * 不写 redirectPath）。按钮计划由纯函数 `resolveSessionInvalidActions` 统一决定，渲染与点击
 * 分派同源，避免模板里再写一套分支。
 */
import { storeToRefs } from 'pinia'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSessionStore } from '@/store/sessionStore'
import type { SessionInvalidAction } from '@/utils/session-invalid'
import { resolveSessionInvalidActions, resolveSessionInvalidCopy } from '@/utils/session-invalid'
import { OVERLAY_Z_INDEX } from '@/utils/overlay'

const { t } = useI18n()
const sessionStore = useSessionStore()
const { invalid, reason, subReason, invalidAt, device, target } = storeToRefs(sessionStore)

const copy = computed(() => resolveSessionInvalidCopy(
  {
    reason: reason.value,
    subReason: subReason.value,
    invalidAt: invalidAt.value,
    device: device.value,
    target: target.value,
  },
  (key, named) => (named ? t(key, named) : t(key)),
))

const actions = computed(() => resolveSessionInvalidActions(copy.value))

const busy = ref(false)

/**
 * 唯一点击分派：
 *  - relogin → 一期两按钮的「重新登录」；
 *  - exit → 一期两按钮的「退出」（清态→首页/配送登录页，不写 redirectPath）；
 *  - exitToLogin → 二期单按钮（「返回登录」/「退出」）：清态→登录页，不写 redirectPath。
 *    不可重登的账号（停用/删除/租户停用/会员禁用）回首页语义不符，必须回登录页。
 */
function onAction(action: SessionInvalidAction) {
  if (busy.value)
    return
  busy.value = true
  try {
    if (action.action === 'relogin')
      sessionStore.confirmRelogin()
    else if (action.action === 'exitToLogin')
      sessionStore.exitToLogin()
    else
      sessionStore.exit()
  }
  finally {
    busy.value = false
  }
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
  <wd-popup
    :model-value="invalid"
    position="center"
    :closable="false"
    :close-on-click-modal="false"
    :z-index="OVERLAY_Z_INDEX.sessionInvalid"
    custom-class="session-invalid-popup"
    custom-style="width: 620rpx; border-radius: 28rpx; overflow: hidden;"
  >
    <view v-if="invalid" class="session-invalid" aria-role="dialog" aria-modal="true">
      <view class="session-invalid__icon-wrap">
        <text class="i-carbon:warning-alt-filled session-invalid__icon" />
      </view>

      <text class="session-invalid__title">
        {{ copy.title }}
      </text>

      <text class="session-invalid__content">
        {{ copy.lead }}
      </text>

      <!-- 设备行：仅当响应携带 data.device 时渲染，缺失整行隐藏（契约 §4） -->
      <text v-if="copy.deviceLine" class="session-invalid__device">
        {{ copy.deviceLine }}
      </text>

      <text v-if="copy.tail" class="session-invalid__content">
        {{ copy.tail }}
      </text>

      <view class="session-invalid__actions">
        <!--
          canRelogin=true：主「重新登录」+ 次「退出」；
          canRelogin=false：单主按钮（「返回登录」/「退出」），点击=退出，不渲染重登。
          两个按钮都走 onAction，动作由 copy.canRelogin 决定，模板不再分叉。
        -->
        <wd-button
          v-for="action in actions"
          :key="action.key"
          block
          :type="action.primary ? 'primary' : 'default'"
          :plain="!action.primary"
          :loading="busy && action.primary"
          :disabled="!action.primary && busy"
          :custom-class="action.primary ? 'session-invalid__primary' : 'session-invalid__exit'"
          @click="onAction(action)"
        >
          {{ action.text }}
        </wd-button>
      </view>
    </view>
  </wd-popup>
</template>

<style scoped>
.session-invalid {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 40rpx 36rpx;
}

.session-invalid__icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: rgba(255, 185, 0, 0.14);
}

.session-invalid__icon {
  font-size: 64rpx;
  color: #ff9f0a;
}

.session-invalid__title {
  margin-top: 28rpx;
  font-size: 34rpx;
  font-weight: 600;
  line-height: 1.4;
  color: var(--wot-color-title, #1f2937);
  text-align: center;
}

.session-invalid__content {
  margin-top: 18rpx;
  font-size: 27rpx;
  line-height: 1.6;
  color: var(--wot-color-content, #4b5563);
  text-align: center;
}

.session-invalid__device {
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--wot-color-secondary, #9ca3af);
  text-align: center;
}

.session-invalid__actions {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  width: 100%;
  margin-top: 44rpx;
}

:deep(.session-invalid__exit) {
  margin-left: 0 !important;
}
</style>
