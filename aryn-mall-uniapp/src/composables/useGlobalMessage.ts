import type { MessageOptions, MessageResult } from 'wot-design-uni/components/wd-message-box/types'
import { defineStore } from 'pinia'
import { getCurrentPath } from '@/utils/current-page'
import { OVERLAY_Z_INDEX } from '@/utils/overlay'

export type GlobalMessageOptions = MessageOptions & {
  success?: (res: MessageResult) => void
  fail?: (res: MessageResult) => void
}

interface GlobalMessage {
  messageOptions: GlobalMessageOptions | null
  currentPage: string
}

export const useGlobalMessage = defineStore('global-message', {
  state: (): GlobalMessage => ({
    messageOptions: null,
    currentPage: '',
  }),
  actions: {
    show(option: GlobalMessageOptions | string) {
      this.currentPage = getCurrentPath()
      this.messageOptions = {
        ...(CommonUtil.isString(option) ? { title: option } : option),
        // 层级在这里兜底而不是只靠 GlobalMessage 组件：部分入口（如 App.vue 的
        // 更新提示）直接调本 store，若依赖组件侧赋值，一旦组件挂载时序变化就会
        // 回落到 wd-message-box 默认的 99，被页面 900+ 弹层盖住。
        zIndex: OVERLAY_Z_INDEX.messageBox,
        cancelButtonProps: {
          round: false,
        },
        confirmButtonProps: {
          round: false,
        },
      }
    },
    alert(option: GlobalMessageOptions | string) {
      const messageOptions = CommonUtil.deepMerge({ type: 'alert' }, CommonUtil.isString(option) ? { title: option } : option) as MessageOptions
      messageOptions.showCancelButton = false
      this.show(messageOptions)
    },
    confirm(option: GlobalMessageOptions | string) {
      const messageOptions = CommonUtil.deepMerge({ type: 'confirm' }, CommonUtil.isString(option) ? { title: option } : option) as MessageOptions
      messageOptions.showCancelButton = true
      this.show(messageOptions)
    },
    prompt(option: GlobalMessageOptions | string) {
      const messageOptions = CommonUtil.deepMerge({ type: 'prompt' }, CommonUtil.isString(option) ? { title: option } : option) as MessageOptions
      messageOptions.showCancelButton = true
      this.show(messageOptions)
    },
    close() {
      this.messageOptions = null
      this.currentPage = ''
    },
  },
})
