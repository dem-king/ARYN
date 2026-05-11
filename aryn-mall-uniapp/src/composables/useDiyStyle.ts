import type { Ref } from 'vue'

import { computed } from 'vue'

import { getCommonStyle } from '@/utils/common-style'

/**
 * 用于计算组件样式
 */
export function useDiyStyle(style: Ref<any | null | undefined>) {
  return computed(() => getCommonStyle(style.value))
}
