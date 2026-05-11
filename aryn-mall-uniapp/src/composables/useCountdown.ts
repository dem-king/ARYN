import { computed, onUnmounted, reactive } from 'vue'

export function useCountdown(targetTime: string | Date | null) {
  const state = reactive({
    days: 0,
    hours: 0,
    minutes: 0,
    seconds: 0,
    finished: false,
  })

  let timer: ReturnType<typeof setInterval> | null = null

  function update() {
    if (!targetTime) {
      state.finished = true
      return
    }
    const target = new Date(targetTime).getTime()
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

  function start() {
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

  onUnmounted(() => {
    stop()
  })

  const display = computed(() => {
    if (state.finished)
      return '已结束'
    const parts: string[] = []
    if (state.days > 0)
      parts.push(`${state.days}天`)
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
