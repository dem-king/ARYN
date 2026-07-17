import type { MaybeRefOrGetter } from 'vue'

import { computed, readonly, shallowRef, toValue, watch } from 'vue'

import type { RetailBaseProps } from '@/components/diy/retail-types'

export type RetailLoadStatus = 'empty' | 'error' | 'loading' | 'ready'

export function useRetailData<TProps extends RetailBaseProps, TItem>(
  source: MaybeRefOrGetter<TProps>,
  loader: (props: TProps) => Promise<TItem[]>,
) {
  const items = shallowRef<TItem[]>([])
  const status = shallowRef<RetailLoadStatus>('loading')

  watch(
    () => toValue(source),
    async (props, _previous, onCleanup) => {
      let active = true
      onCleanup(() => {
        active = false
      })
      status.value = 'loading'
      try {
        const result = await loader(props)
        if (!active)
          return
        items.value = result
        status.value = result.length > 0 ? 'ready' : 'empty'
      }
      catch (error) {
        if (!active)
          return
        items.value = []
        status.value = 'error'
        if (import.meta.env.DEV)
          console.warn('[decoration] retail component load failed', error)
      }
    },
    { deep: true, immediate: true },
  )

  const shouldRender = computed(() => {
    const props = toValue(source)
    if (status.value === 'empty')
      return props.emptyStrategy !== 'hide'
    if (status.value === 'error')
      return props.invalidStrategy !== 'hide'
    return true
  })

  return {
    items: readonly(items),
    shouldRender,
    status: readonly(status),
  }
}
