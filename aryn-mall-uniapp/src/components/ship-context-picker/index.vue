<script setup lang="ts">
/**
 * 船舶与靠港计划选择器（底部弹层）。
 *
 * 背景：`switchVessel` 与 `getVesselCalls` 此前是死代码——多船用户被强制使用
 * vessels[0]，多靠港时被强制使用「下一靠港」，而购物车红字却提示「结算前请切换船舶」，
 * 结算页提示「请先在首页选择船舶和靠港计划」，引导用户做一件做不到的事。
 *
 * 本组件把这条链路补上：
 * 1. 加载我作为在船成员的在营船舶；
 * 2. 选择船舶后加载其**可用**靠港计划（服务端已按 ETA 升序并排除已离港/已完成/已取消）；
 * 3. 选中靠港后写入 shipContextStore，切换船舶时先 switchVessel 清空旧靠港防止串船。
 */
import { computed, ref, watch } from 'vue'

import { declareVesselCall, getMyVessels, getVesselCalls } from '@/api/vessel'
import { useShipContextStore } from '@/store/shipContextStore'
import { formatDeclareTime } from '@/utils/vessel-call-time'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'change'): void
  (e: 'update:modelValue', value: boolean): void
}>()

const shipContextStore = useShipContextStore()

const loadingVessels = ref(false)
const loadingCalls = ref(false)

/**
 * 靠港申报表单。
 *
 * 公司拿不到船期，ETA/ETD 只有船上的人知道，因此这里让海员自己申报；
 * 运营收到申报后再补配送时间窗并排产，不再由运营凭空录入靠港计划。
 *
 * ETA/ETD 走日历式日期时间选择器而非手输：此前是自由文本 input，placeholder 写着
 * `yyyy-MM-dd HH:mm:ss`——用户在手机上要逐字符敲 19 位，格式差一位就被后端
 * `LocalDateTime` 反序列化判 400，且没有任何提示说明格式错在哪。
 * 用日历而不是五列滚轮：ETA 常在几天到几周之外，日历能直接看到星期与月份结构，
 * 也避开了滚轮「各列顶格、上半屏整块空白」的观感问题。
 */
const declareState = ref({
  visible: false,
  submitting: false,
  portName: '',
  berth: '',
  /** 选择器回传的时间戳，null 表示未选（此时日历的确认按钮置灰） */
  eta: null as number | null,
  etd: null as number | null,
})

/**
 * 选择器可选范围。
 *
 * 下限取「今天零点」而不是此刻：海员常在靠港前补申报，把已过去的当天
 * 整段禁掉会让当天靠港无解。上限给两年，覆盖长航次排期。
 */
const declareNow = new Date()
const declareDateMin = new Date(declareNow.getFullYear(), declareNow.getMonth(), declareNow.getDate()).getTime()
const declareDateMax = new Date(declareNow.getFullYear() + 2, 11, 31, 23, 59, 59).getTime()

/**
 * 离港选择器的下限跟随到港时间：选完 ETA 后，ETD 只能选到 ETA 之后，
 * 从源头避免「到港晚于离港」这种提交时才被拒的组合。
 *
 * 加一分钟而不是直接用 ETA：后端要求 ETA **严格早于** ETD（`eta.isBefore(etd)`），
 * 下限取 ETA 本身时用户仍能选出两者相等的时刻，只能等提交才被拒。
 */
const etdMinDate = computed(() =>
  typeof declareState.value.eta === 'number' ? declareState.value.eta + 60_000 : declareDateMin)

/**
 * 日历选中某个日期后预填的时刻：从当前时刻向上取整 5 分钟，每次打开申报表单时刷新。
 *
 * 旧滚轮的毛病是空值会回退到 min-date——打开就停在「今天 00:00」这种已经过去的
 * 时刻，直接点完成就能申报出过去时间的 ETA。日历打开时不预置任何日期（确认按钮
 * 置灰直到选定），这个问题从根上消失；这里只决定点选日期后拼上的初始时分，
 * 取整到未来保证申报「今天」靠港时默认值不落在过去。
 *
 * 格式必须是 HH:mm:ss 三段：库内 `getDefaultTime` 按冒号拆段后逐段
 * setHours/setMinutes/setSeconds，两段式会在 setSeconds(undefined) 上变成
 * Invalid Date，点选日期后确认按钮永远置灰。
 */
const declareDefaultTime = ref('00:00:00')

function refreshDeclareDefaultTime() {
  const now = new Date()
  const minuteOfDay = now.getHours() * 60 + Math.ceil((now.getMinutes() + 1) / 5) * 5
  // 23:5x 时取整会滚过零点，钳回当天最后一分钟，避免「点今天、值却是明天」
  const clamped = Math.min(minuteOfDay, 23 * 60 + 59)
  const hour = Math.floor(clamped / 60)
  const minute = clamped % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  declareDefaultTime.value = `${pad(hour)}:${pad(minute)}:00`
}

/**
 * 先选离港、再改到港时，旧的 ETD 可能落到新 ETA 之前而变成非法值。
 * 这里直接清掉让它重选，而不是留到提交时才报错——用户此刻正看着表单，
 * 清空的框比一句「必须早于」更能说明该做什么。
 *
 * 判据与 `etdMinDate` 对齐（`<=`），否则刚好等于新 ETA 的旧值会被留下，
 * 而那个值在选择器里已经选不出来了。
 */
watch(() => declareState.value.eta, (eta) => {
  if (typeof eta === 'number' && typeof declareState.value.etd === 'number' && declareState.value.etd <= eta)
    declareState.value.etd = null
})

/** 选择器展示用的可读文案，未选时为占位符 */
const declareEtaText = computed(() =>
  typeof declareState.value.eta === 'number' ? formatDeclareTime(declareState.value.eta) : '')
const declareEtdText = computed(() =>
  typeof declareState.value.etd === 'number' ? formatDeclareTime(declareState.value.etd) : '')

const loadFailed = ref(false)
const vessels = ref<any[]>([])
const calls = ref<any[]>([])
/** 当前在弹层内选中的船舶（未必等于 store 中的当前船舶） */
const pickedVesselId = ref('')

const pickedVessel = computed(() => vessels.value.find(v => v.id === pickedVesselId.value) ?? null)

/** 后端返回 yyyy-MM-dd HH:mm:ss，列表里压缩成 MM-dd HH:mm */
function shortTime(value?: string) {
  if (!value) return ''
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/)
  if (!match) return value
  return `${match[2]}-${match[3]} ${match[4]}:${match[5]}`
}

function close() {
  emit('update:modelValue', false)
}

/**
 * 空态下的出路：没有船舶时引导去自助绑定页。
 * 此前这里只有一句「暂未关联船舶」，用户知道缺什么却无处可去——唯一的绑定入口在管理端后台。
 */
function goBind() {
  close()
  uni.navigateTo({ url: '/sub-pages/vessel/bind/index' })
}

function loadCalls(vesselId: string) {
  if (!vesselId) {
    calls.value = []
    return Promise.resolve()
  }
  loadingCalls.value = true
  return getVesselCalls(vesselId)
    .then((res) => {
      calls.value = res ?? []
    })
    .catch(() => {
      calls.value = []
      loadFailed.value = true
    })
    .finally(() => {
      loadingCalls.value = false
    })
}

function loadVessels() {
  loadingVessels.value = true
  loadFailed.value = false
  return getMyVessels()
    .then((res) => {
      vessels.value = res ?? []
      // 优先定位到 store 中已选船舶，否则取第一艘
      const preferred = vessels.value.find(v => v.id === shipContextStore.vesselId)
      pickedVesselId.value = preferred?.id ?? vessels.value[0]?.id ?? ''
      return loadCalls(pickedVesselId.value)
    })
    .catch(() => {
      vessels.value = []
      calls.value = []
      loadFailed.value = true
    })
    .finally(() => {
      loadingVessels.value = false
    })
}

function pickVessel(vesselId: string) {
  if (vesselId === pickedVesselId.value) return
  pickedVesselId.value = vesselId
  void loadCalls(vesselId)
}

function chooseCall(call: any) {
  // 换船必须先 switchVessel：它会清空靠港上下文，避免把 A 船的靠港带到 B 船
  if (pickedVesselId.value !== shipContextStore.vesselId) {
    shipContextStore.switchVessel(pickedVesselId.value, pickedVessel.value?.vesselName ?? '')
  }
  shipContextStore.setVesselCall({
    berth: call.berth,
    deliveryWindowEnd: call.deliveryWindowEnd,
    deliveryWindowStart: call.deliveryWindowStart,
    id: call.id,
    portCode: call.portCode,
    portName: call.portName,
  })
  emit('change')
  close()
}

watch(() => props.modelValue, (visible) => {
  if (visible) {
    void loadVessels()
  }
})

/** 打开靠港申报表单（无可用靠港计划时给出的可执行出路） */
function openDeclare() {
  refreshDeclareDefaultTime()
  declareState.value.portName = ''
  declareState.value.berth = ''
  declareState.value.eta = null
  declareState.value.etd = null
  declareState.value.visible = true
}

/** 提交靠港申报：成功后刷新可用靠港列表，用户即可直接选用 */
function submitDeclare() {
  const form = declareState.value
  // 以弹层内选中的船为准：store.vesselId 只在确认靠港（chooseCall）后写入，
  // 而「无靠港 → 申报」恰恰是弹层已自动选中船、store 仍为空的场景，读它必误报
  const vesselId = pickedVesselId.value
  if (!vesselId) {
    uni.showToast({ title: '请先选择船舶', icon: 'none' })
    return
  }
  if (!form.portName.trim()) {
    uni.showToast({ title: '请填写港口', icon: 'none' })
    return
  }
  if (typeof form.eta !== 'number' || typeof form.etd !== 'number') {
    uni.showToast({ title: '请选择预计到港与离港时间', icon: 'none' })
    return
  }
  if (form.eta >= form.etd) {
    uni.showToast({ title: '到港时间必须早于离港时间', icon: 'none' })
    return
  }
  if (form.submitting) return
  form.submitting = true
  declareVesselCall({
    vesselId,
    portCode: form.portName.trim(),
    portName: form.portName.trim(),
    berth: form.berth.trim() || undefined,
    eta: formatDeclareTime(form.eta),
    etd: formatDeclareTime(form.etd),
  })
    .then(() => {
      form.visible = false
      uni.showToast({ title: '已提交，运营将据此安排配送', icon: 'none' })
      return loadCalls(vesselId)
    })
    .catch(() => {})
    .finally(() => {
      form.submitting = false
    })
}

</script>

<template>
  <view v-if="modelValue">
    <!-- 遮罩 -->
    <view
      class="fixed bottom-0 left-0 right-0 top-0"
      style="z-index: 900; background: rgba(0, 0, 0, 0.45)"
      @tap="close"
    />

    <!-- 弹层 -->
    <view
      class="fixed bottom-0 left-0 right-0 rounded-t-24rpx bg-white"
      style="z-index: 901; max-height: 76vh; display: flex; flex-direction: column"
    >
      <view class="flex items-center justify-between px-30rpx pb-16rpx pt-30rpx">
        <view class="text-30rpx font-bold">
          选择船舶与靠港计划
        </view>
        <text class="text-26rpx text-gray-400" @tap="close">
          关闭
        </text>
      </view>

      <view v-if="loadingVessels" class="py-60rpx text-center text-26rpx text-gray-400">
        加载中...
      </view>

      <view v-else-if="loadFailed" class="py-60rpx text-center">
        <view class="text-26rpx text-gray-400">
          加载失败，请检查网络后重试
        </view>
        <view class="mt-16rpx text-26rpx text-blue-500" @tap="loadVessels">
          重新加载
        </view>
      </view>

      <view v-else-if="vessels.length === 0" class="px-40rpx py-60rpx text-center">
        <view class="text-26rpx text-gray-400">
          当前账号暂未关联船舶，关联后即可选择靠港计划
        </view>
        <view
          class="mt-24rpx inline-block rounded-40rpx bg-blue-500 px-40rpx py-14rpx text-26rpx text-white"
          @tap="goBind"
        >
          去绑定船舶
        </view>
      </view>

      <template v-else>
        <!-- 船舶：多船时才需要选择 -->
        <view v-if="vessels.length > 1" class="px-30rpx pb-10rpx">
          <scroll-view scroll-x class="whitespace-nowrap">
            <view
              v-for="vessel in vessels"
              :key="vessel.id"
              class="mr-16rpx inline-block rounded-30rpx px-24rpx py-10rpx text-24rpx"
              :style="pickedVesselId === vessel.id
                ? 'background:#378ADD;color:#fff'
                : 'background:#F1EFE8;color:#5F5E5A'"
              @tap="pickVessel(vessel.id)"
            >
              {{ vessel.vesselName }}
            </view>
          </scroll-view>
        </view>
        <view v-else class="px-30rpx pb-10rpx text-24rpx text-gray-500">
          {{ pickedVessel?.vesselName }}
        </view>

        <!-- 靠港计划 -->
        <view class="px-30rpx pb-10rpx text-24rpx text-gray-500">
          可用靠港计划
        </view>
        <scroll-view scroll-y style="flex: 1; max-height: 46vh">
          <view
            v-for="call in calls"
            :key="call.id"
            class="mx-30rpx mb-16rpx rounded-16rpx p-24rpx"
            :style="call.id === shipContextStore.vesselCallId
              ? 'background:#E6F1FB;border:1rpx solid #378ADD'
              : 'background:#F7F8FA;border:1rpx solid #F7F8FA'"
            @tap="chooseCall(call)"
          >
            <view class="flex items-center justify-between">
              <view class="text-26rpx font-bold">
                {{ call.portName || call.portCode }}
                <text v-if="call.berth" class="text-24rpx text-gray-500">
                  {{ call.berth }}
                </text>
              </view>
              <text v-if="call.id === shipContextStore.vesselCallId" class="text-22rpx text-blue-500">
                当前
              </text>
            </view>
            <view class="mt-6rpx text-22rpx text-gray-500">
              预计到港 {{ shortTime(call.eta) }} · 离港 {{ shortTime(call.etd) }}
            </view>
            <view v-if="call.deliveryWindowStart" class="mt-4rpx text-22rpx text-green-600">
              配送时间窗 {{ shortTime(call.deliveryWindowStart) }} ~ {{ shortTime(call.deliveryWindowEnd) }}
            </view>
          </view>

          <!-- 靠港申报表单 -->
          <view v-if="declareState.visible" class="mt-20rpx rounded-16rpx bg-gray-50 p-24rpx">
            <view class="text-26rpx font-bold">
              申报本次靠港
            </view>
            <view class="mt-6rpx text-22rpx text-gray-500">
              公司无法获取船期，请填写你船本次的到离港信息；运营将据此安排备货与配送。
            </view>
            <input
              v-model="declareState.portName"
              class="mt-16rpx h-72rpx rounded-12rpx bg-white px-24rpx text-26rpx"
              placeholder="港口，如：上海港"
            >
            <input
              v-model="declareState.berth"
              class="mt-12rpx h-72rpx rounded-12rpx bg-white px-24rpx text-26rpx"
              placeholder="泊位（选填）"
            >
            <view class="mt-12rpx text-24rpx text-gray-500">
              预计到港时间
            </view>
            <!--
              日历式日期时间选择器替代手输：先翻日历选日期，再在面板内调时分。
              命中区域是下面这个 72rpx 高的白框（with-cell=false 走 slot 分支）。
              必须 root-portal —— 申报表单嵌在 scroll-view 里，小程序端 fixed 元素
              会被 scroll-view 裁掉；z-index 抬到 1000 才压得住本弹层（900/901）
              与 H5 原生 tabBar（998）。
            -->
            <wd-calendar
              v-model="declareState.eta"
              type="datetime"
              title="选择预计到港时间"
              :min-date="declareDateMin"
              :max-date="declareDateMax"
              :default-time="declareDefaultTime"
              hide-second
              :with-cell="false"
              root-portal
              :z-index="1000"
            >
              <view class="mt-6rpx flex h-72rpx items-center rounded-12rpx bg-white px-24rpx text-26rpx">
                <text v-if="declareEtaText">{{ declareEtaText }}</text>
                <text v-else class="text-gray-400">
                  请选择到港时间
                </text>
              </view>
            </wd-calendar>
            <view class="mt-12rpx text-24rpx text-gray-500">
              预计离港时间
            </view>
            <!-- 离港下限跟随到港，早于到港的日期在日历上直接置灰 -->
            <wd-calendar
              v-model="declareState.etd"
              type="datetime"
              title="选择预计离港时间"
              :min-date="etdMinDate"
              :max-date="declareDateMax"
              :default-time="declareDefaultTime"
              hide-second
              :with-cell="false"
              root-portal
              :z-index="1000"
            >
              <view class="mt-6rpx flex h-72rpx items-center rounded-12rpx bg-white px-24rpx text-26rpx">
                <text v-if="declareEtdText">{{ declareEtdText }}</text>
                <text v-else class="text-gray-400">
                  请选择离港时间
                </text>
              </view>
            </wd-calendar>
            <view class="mt-20rpx flex gap-20rpx">
              <button
                class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
                @tap="declareState.visible = false"
              >
                取消
              </button>
              <button
                class="!m-0 flex-1 h-68rpx text-26rpx leading-68rpx"
                type="primary"
                :disabled="declareState.submitting"
                @tap="submitDeclare"
              >
                提交申报
              </button>
            </view>
          </view>

          <view v-if="loadingCalls" class="py-40rpx text-center text-26rpx text-gray-400">
            加载中...
          </view>
          <view
            v-else-if="calls.length === 0"
            class="px-40rpx py-40rpx text-center"
          >
            <view class="text-26rpx text-gray-400">
              该船舶暂无靠港计划
            </view>
            <!-- 公司拿不到船期，只有船上的人知道；因此无可用计划时由海员自行申报 -->
            <button
              class="mt-24rpx h-72rpx text-26rpx leading-72rpx"
              type="primary"
              @tap="openDeclare"
            >
              申报本次靠港
            </button>
          </view>
        </scroll-view>
      </template>

      <view style="height: 20rpx" />
    </view>
  </view>
</template>
