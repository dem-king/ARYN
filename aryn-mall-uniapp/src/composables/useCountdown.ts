import {
  computed,
  onBeforeUnmount,
  onMounted,
  onUnmounted,
  reactive,
  shallowRef,
} from 'vue'
import { resolveTargetTimestamp } from '@/utils/datetime'

import { translate } from '@/locales'

export function useCountdown(targetTime: string | Date | null) {
  let currentTargetTime = targetTime
  const state = reactive({
    days: 0,
    hours: 0,
    minutes: 0,
    seconds: 0,
    finished: false,
  })

  let timer: ReturnType<typeof setInterval> | null = null

  function update() {
    if (!currentTargetTime) {
      state.finished = true
      return
    }
    const target = resolveTargetTimestamp(currentTargetTime)
    if (target === null) {
      // 解析不了的目标时刻按已结束处理，避免 NaN 计进倒计时
      state.finished = true
      return
    }
    const now = Date.now()
    const diff = target - now

    if (diff <= 0) {
      state.days = 0
      state.hours = 0
      state.minutes = 0
      state.seconds = 0
      state.finished = true
      stop()
      return
    }

    state.days = Math.floor(diff / (1000 * 60 * 60 * 24))
    state.hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
    state.minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
    state.seconds = Math.floor((diff % (1000 * 60)) / 1000)
    state.finished = false
  }

  function start(nextTargetTime?: string | Date | null) {
    if (nextTargetTime !== undefined)
      currentTargetTime = nextTargetTime
    stop()
    update()
    timer = setInterval(update, 1000)
  }

  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  onUnmounted(stop)

  const display = computed(() => {
    if (state.finished)
      return translate('app.countdown.finished')
    const parts: string[] = []
    if (state.days > 0)
      parts.push(translate('app.countdown.days', { count: state.days }))
    parts.push(`${String(state.hours).padStart(2, '0')}:${String(state.minutes).padStart(2, '0')}:${String(state.seconds).padStart(2, '0')}`)
    return parts.join(' ')
  })

  return {
    countdownState: state,
    countdownDisplay: display,
    startCountdown: start,
    stopCountdown: stop,
  }
}

export function formatRemainingTime(targetTime: string, now = Date.now()) {
  const target = resolveTargetTimestamp(targetTime)
  if (target === null)
    return ''
  const remaining = Math.max(0, target - now)
  if (remaining <= 0)
    return ''
  const totalSeconds = Math.floor(remaining / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const time = [hours, minutes, seconds]
    .map(value => String(value).padStart(2, '0'))
    .join(':')
  return days > 0 ? `${translate('app.countdown.days', { count: days })} ${time}` : time
}

export function useCountdownTicker() {
  const now = shallowRef(Date.now())
  let timer: ReturnType<typeof setInterval> | undefined

  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now()
    }, 1000)
  })
  onBeforeUnmount(() => {
    if (timer)
      clearInterval(timer)
  })
  return { now }
}
