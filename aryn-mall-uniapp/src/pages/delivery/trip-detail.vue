<script setup lang="ts">
import type { DeliveryCandidateOrder, DeliveryCandidateSource, DeliveryTask, DeliveryTaskStatus, DeliveryTrip } from '@/api/delivery'
import type { PickRow, PickRowGroup } from '@/utils/delivery-pick'
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import {
  arriveTask,
  batchPickItems,
  departTrip,
  getPullCandidates,
  getTaskStatusName,
  getTripDetail,
  pullOrdersIntoTrip,
  sortTripTasks,
  startLoading,
} from '@/api/delivery'
import { uploadDeliveryEvidence } from '@/api/upms/file'
import { openDeliveryNavigation, resolveDeliveryDestination } from '@/utils/delivery-navigation'
import {
  applyRouteOrder,
  buildPickRows,
  groupPickRows,
  isPickRowDone,
  moveItem,
  resolveTargetIndex,
  sortRowOffset,
} from '@/utils/delivery-pick'

definePage({
  name: 'delivery-trip-detail',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '出车单详情',
  },
})

const globalLoading = useGlobalLoading()
const { show: showToast } = useGlobalToast()

const loading = ref(true)
const trip = ref<DeliveryTrip | null>(null)
/** 操作锁，防止重复提交 */
const submitting = ref(false)

/**
 * 送达凭证弹层：后端 `arriveWithEvidence` 要求 1–6 张凭证，出车单页原先
 * 直接无参调用，必然被「送达凭证图片数量必须为1至6张」拦下 —— 司机看到的是
 * 与任务详情页同一个「确认送达」，这里也不能少凭证这一步。
 */
const evidenceVisible = ref(false)
/** 本次要送达的任务；弹层关闭后清空 */
const evidenceTask = ref<DeliveryTask | null>(null)
/**
 * 弹层会话号：每次开/关自增，迟到的上传结果据此丢弃。
 *
 * 只比任务 ID 不够——同一单关掉再打开，上一轮的在途上传会写进新一轮草稿。
 */
let evidenceSession = 0
/** 本地预览路径，与 evidenceIds 一一对应，便于删除后回显 */
const evidencePaths = ref<string[]>([])
/** 服务端素材 ID，提交给送达接口的就是它 */
const evidenceIds = ref<string[]>([])
const evidenceUploading = ref(false)

/** 是否为配货视图（status=1待配货 或 2配货中） */
const isPickView = computed(() => {
  const status = trip.value?.status
  return status === '1' || status === '2'
})

/**
 * 是否为送货视图（status=3配送中 或 4已完成）。
 *
 * 已完成后仍走送货视图：司机刚送完最后一单，页面会立刻重取详情变成 status=4，
 * 此时保留送达清单与「全部配送完成」提示，比只剩一张空卡片更符合收车时的预期。
 */
const isDeliverView = computed(() => trip.value?.status === '3' || trip.value?.status === '4')

/**
 * 配货汇总行：整趟车的明细按「分类 + 商品 + 规格」合并。
 *
 * 司机在仓库要的是「这趟车一共拿多少货」，而不是按订单分成 N 份清单
 * （同一件货出现在 5 个订单里就要跑 5 遍货架）。
 */
const pickRows = computed<PickRow[]>(() => buildPickRows(trip.value?.taskList ?? []))

/** 汇总行按分类分组（分类≈供应商批次，按批次走仓库） */
const pickGroups = computed<PickRowGroup[]>(() => groupPickRows(pickRows.value))

/** 已取件数：按明细统计（与后端 pickedItemCount 同口径） */
const pickedCount = computed(() => trip.value?.pickedItemCount ?? 0)

/** 总件数 */
const totalCount = computed(() => trip.value?.totalItemCount ?? 0)

/** 是否全部取完 */
const isAllPicked = computed(() => pickedCount.value >= totalCount.value && totalCount.value > 0)

/** 已送达单数 */
const arrivedCount = computed(() => trip.value?.arrivedTaskCount ?? 0)

/** 总单数 */
const totalTaskCount = computed(() => trip.value?.taskCount ?? 0)

/** 是否全部送达 */
const isAllArrived = computed(() => arrivedCount.value >= totalTaskCount.value && totalTaskCount.value > 0)

/** 送货视图的任务列表：本地顺序优先（拖拽即时生效），否则按 sortNo */
const routeOrderIds = ref<string[]>([])

/**
 * 配送中趟次里新并入、还没配货的任务（落点状态=配货中）。
 *
 * 车已开出后司机回车取货或顺路捎带加进来的单都停在这个状态，
 * 必须配齐、再次出发才会被 depart 推到待送达（订单才转待收货）。
 */
const pendingPickTasks = computed(() => (trip.value?.taskList ?? [])
  .filter(task => task.status === '3'))

/** 新加货的配货汇总行（同一件货只列一行，与首次配货同一口径） */
const pendingPickRows = computed<PickRow[]>(() => buildPickRows(pendingPickTasks.value))

/** 新加货按分类分组 */
const pendingPickGroups = computed<PickRowGroup[]>(() => groupPickRows(pendingPickRows.value))

/** 新加货的明细总数 */
const pendingItemCount = computed(() => pendingPickRows.value
  .reduce((sum, row) => sum + row.itemIds.length, 0))

/** 新加货已确认取货的明细数 */
const pendingPickedCount = computed(() => pendingPickRows.value
  .reduce((sum, row) => sum + row.pickedCount, 0))

/** 新加货是否已全部确认取货 */
const isPendingAllPicked = computed(() => pendingItemCount.value > 0
  && pendingPickedCount.value >= pendingItemCount.value)

const sortedTaskList = computed(() => {
  const tasks = trip.value?.taskList ?? []
  if (routeOrderIds.value.length > 0) {
    return applyRouteOrder(tasks, routeOrderIds.value)
  }
  return [...tasks].sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
})

// ===================== 拖拽排序 =====================

/** 排序面板是否展开（默认收起：多数时候顺序由派单决定，司机不天天调） */
const sortPanelVisible = ref(false)
/** 排序面板里的行高（rpx 转 px 由样式固定，这里按 px 记） */
const SORT_ROW_HEIGHT = 72

/** 正在拖拽的下标；-1 表示未拖拽 */
const dragFromIndex = ref(-1)
/** 拖拽目标下标（跟随位移换算） */
const dragToIndex = ref(-1)
/** 拖拽行的实时位移（px） */
const dragOffsetY = ref(0)
/** 拖拽起点（touchstart 的 pageY） */
let dragStartY = 0

/** 排序面板的行数据 */
const sortRows = computed(() => sortedTaskList.value)

/** 某行在拖拽态下的变形位移 */
function rowTransform(index: number): string {
  if (dragFromIndex.value < 0) {
    return ''
  }
  const offset = resolveTargetIndex(dragFromIndex.value, dragOffsetY.value, SORT_ROW_HEIGHT, sortRows.value.length)
  const y = sortRowOffset(index, dragFromIndex.value, offset, SORT_ROW_HEIGHT, dragOffsetY.value)
  return y === 0 ? '' : `transform: translateY(${y}px);`
}

/** 开始拖拽某一行 */
function handleSortTouchStart(index: number, event: any) {
  if (submitting.value) {
    return
  }
  dragFromIndex.value = index
  dragToIndex.value = index
  dragOffsetY.value = 0
  dragStartY = event?.touches?.[0]?.pageY ?? event?.changedTouches?.[0]?.pageY ?? 0
}

/** 拖拽中：跟手位移并实时换算落点 */
function handleSortTouchMove(event: any) {
  if (dragFromIndex.value < 0) {
    return
  }
  const pageY = event?.touches?.[0]?.pageY ?? event?.changedTouches?.[0]?.pageY
  if (typeof pageY !== 'number') {
    return
  }
  dragOffsetY.value = pageY - dragStartY
  dragToIndex.value = resolveTargetIndex(dragFromIndex.value, dragOffsetY.value, SORT_ROW_HEIGHT, sortRows.value.length)
}

/** 松手：落库并重排 */
async function handleSortTouchEnd() {
  const fromIndex = dragFromIndex.value
  const toIndex = dragToIndex.value
  dragFromIndex.value = -1
  dragToIndex.value = -1
  dragOffsetY.value = 0
  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) {
    return
  }
  await persistRouteOrder(moveItem(sortRows.value, fromIndex, toIndex).map(task => task.id))
}

/**
 * 保存送货顺序：整趟任务的排列一次性提交。
 *
 * 后端要求提交的是本趟全部任务的一个排列（旧快照的部分列表会被拒绝，
 * 否则未提交的会留在原序号上造成路线静默错乱）。
 */
async function persistRouteOrder(orderedIds: string[]) {
  if (!trip.value || submitting.value) {
    return
  }
  const previous = routeOrderIds.value
  routeOrderIds.value = orderedIds
  submitting.value = true
  try {
    await sortTripTasks(trip.value.id, orderedIds.map((id, index) => ({ taskId: id, sortNo: index + 1 }))).send()
  }
  catch (error: any) {
    // 失败回滚本地顺序，避免界面与库里的路线不一致
    routeOrderIds.value = previous
    showToast(error?.message || error?.msg || '调整顺序失败')
  }
  finally {
    submitting.value = false
  }
}

/** 上移/下移兜底：拖拽在小程序上手感不稳时仍能精确调整 */
async function handleMoveUp(index: number) {
  if (index <= 0) {
    return
  }
  await persistRouteOrder(moveItem(sortRows.value, index, index - 1).map(task => task.id))
}

async function handleMoveDown(index: number) {
  if (index >= sortRows.value.length - 1) {
    return
  }
  await persistRouteOrder(moveItem(sortRows.value, index, index + 1).map(task => task.id))
}

// ===================== 拉单（配货页加单入口） =====================

/**
 * 加单入口在数据上收敛为同一个动作：把已有订单的配送任务归属本趟车。
 *
 * - 我的任务：派给我但不在本趟的未完成任务（可能在别的趟次）
 * - 未派送订单：本租户已付款待发货、走商城/内部配送的单；受租户开关控制
 *
 * 两者都用订单本身的数据（商品、收货人、地址），司机不手填字段。
 */
const pullPanelVisible = ref(false)
/** 当前面板的来源 */
const pullSource = ref<DeliveryCandidateSource>('MINE')
/** 面板标题 */
const pullPanelTitle = ref('选择订单')
const pullCandidates = ref<DeliveryCandidateOrder[]>([])
const pullKeyword = ref('')
const pullLoading = ref(false)
/** 已勾选的订单ID */
const pullSelectedIds = ref<string[]>([])

/**
 * 本租户是否开放司机自助拉未派送订单。
 *
 * 后端按 order_config 下发；缺省（老接口/字段缺失）按允许处理，
 * 与后端「未配置即放行」的口径保持一致，避免升级期间司机入口凭空消失。
 */
const canSelfPullUnassigned = computed(() => trip.value?.selfPullUnassignedAllowed !== false)

/** 候选件数合计，帮司机确认拉了多少货 */
const pullTotalQuantity = computed(() => pullCandidates.value
  .filter(candidate => pullSelectedIds.value.includes(candidate.orderId))
  .reduce((sum, candidate) => sum + (candidate.itemCount ?? 0), 0))

/** 打开选单面板并拉取候选 */
async function openPullPanel(source: DeliveryCandidateSource, title: string) {
  if (!trip.value || submitting.value) {
    return
  }
  pullSource.value = source
  pullPanelTitle.value = title
  pullKeyword.value = ''
  pullSelectedIds.value = []
  pullPanelVisible.value = true
  await fetchPullCandidates()
}

/** 拉取候选订单（带关键字过滤交给后端，避免本地过滤与分页口径不一致） */
async function fetchPullCandidates() {
  if (!trip.value) {
    return
  }
  pullLoading.value = true
  try {
    pullCandidates.value = await getPullCandidates(
      trip.value.id,
      pullSource.value,
      pullKeyword.value.trim() || undefined,
    ).send() as DeliveryCandidateOrder[]
  }
  catch (error: any) {
    pullCandidates.value = []
    showToast(error?.message || '获取订单失败')
  }
  finally {
    pullLoading.value = false
  }
}

/** 勾选/取消勾选一张订单 */
function togglePullSelection(orderId: string) {
  const index = pullSelectedIds.value.indexOf(orderId)
  if (index >= 0) {
    pullSelectedIds.value.splice(index, 1)
  }
  else {
    pullSelectedIds.value.push(orderId)
  }
}

/** 关闭面板并清空勾选，避免下一轮带着上次的选择 */
function closePullPanel() {
  pullPanelVisible.value = false
  pullSelectedIds.value = []
  pullCandidates.value = []
  pullKeyword.value = ''
}

/** 确认拉入：成功后刷新详情，本趟立刻多出这些单 */
async function confirmPullOrders() {
  const orderIds = [...pullSelectedIds.value]
  if (!trip.value || orderIds.length === 0 || submitting.value) {
    return
  }
  submitting.value = true
  globalLoading.loading('添加中...')
  try {
    const pulled = await pullOrdersIntoTrip(trip.value.id, orderIds).send() as number
    showToast(`已加入 ${pulled} 单`)
    closePullPanel()
    await fetchTripDetail(trip.value.id)
  }
  catch (error: any) {
    showToast(error?.message || '添加订单失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}

onLoad((options) => {
  if (options?.id) {
    fetchTripDetail(options.id)
  }
})

/** 获取出车单详情（含任务与取货明细） */
async function fetchTripDetail(id: string) {
  globalLoading.loading('加载中...')
  loading.value = true
  try {
    const res = await getTripDetail(id).send()
    trip.value = res as DeliveryTrip
    // 重新拉取后以服务端 sortNo 为准，清掉本地拖拽残留的顺序
    routeOrderIds.value = []
  }
  catch (error) {
    console.error('获取出车单详情失败:', error)
  }
  finally {
    loading.value = false
    globalLoading.close()
  }
}

/** 开始配货（status: 1 -> 2） */
async function handleStartLoading() {
  if (!trip.value || submitting.value)
    return
  submitting.value = true
  globalLoading.loading('处理中...')
  try {
    await startLoading(trip.value.id).send()
    showToast('已开始配货')
    await fetchTripDetail(trip.value.id)
  }
  catch (error: any) {
    showToast(error?.message || error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}

/** 装货完毕出发（status: 2 -> 3）；配送中的趟次用它把新加货带走 */
async function handleDepart() {
  if (!trip.value || submitting.value)
    return
  const isDelivering = trip.value.status === '3'
  if (!isDelivering && !isAllPicked.value) {
    const remaining = totalCount.value - pickedCount.value
    showToast(`还有${remaining}件商品未确认取货`)
    return
  }
  // 已出发的趟次：新拉上车的货必须先配齐，否则后端会拒
  if (isDelivering && !isPendingAllPicked.value) {
    const remaining = pendingItemCount.value - pendingPickedCount.value
    showToast(`新加货还有${remaining}件未确认取货`)
    return
  }
  submitting.value = true
  globalLoading.loading('处理中...')
  try {
    await departTrip(trip.value.id).send()
    showToast(isDelivering ? '已再次出发，新加货已上路' : '已出发配送')
    await fetchTripDetail(trip.value.id)
  }
  catch (error: any) {
    showToast(error?.message || error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}

/**
 * 切换汇总行的取货状态。
 *
 * 汇总行是「商品 + 规格」的合计，一次勾选要把这条货在整趟车里的所有明细
 * 一起改掉 —— 司机拿货的动作就是一把抓走合计数量，不会按订单分次确认。
 */
async function handleToggleRow(row: PickRow) {
  if (!trip.value || submitting.value)
    return
  if (trip.value.status === '1') {
    showToast('请先开始配货')
    return
  }
  const shouldPick = !isPickRowDone(row)
  submitting.value = true
  try {
    await batchPickItems(trip.value.id, row.itemIds, shouldPick).send()
    await fetchTripDetail(trip.value.id)
  }
  catch (error: any) {
    showToast(error?.message || error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
  }
}

/**
 * 导航到收货地址：有坐标走微信内置地图，没有坐标则复制地址给司机粘贴到导航软件。
 *
 * 出车单卡片上还要显示船供目的地（收货地址为空时回落港口/泊位），
 * 与递交给导航的口径同源，避免「卡片显示空白但导航能跳」。
 */
function handleNavigate(task: DeliveryTask) {
  openDeliveryNavigation(task, showToast)
}

/** 卡片上展示的目的地文本；无可用目的地时返回空串 */
function taskDestination(task: DeliveryTask): string {
  return resolveDeliveryDestination(task)
}

/** 拨打电话 */
function handleCallPhone(phone: string) {
  if (!phone) {
    showToast('暂无电话号码')
    return
  }
  uni.makePhoneCall({
    phoneNumber: phone,
    fail: () => {
      showToast('拨打电话失败')
    },
  })
}

/** 送达某单：先选凭证，确认后再提交 */
function handleArriveTask(task: DeliveryTask) {
  if (submitting.value || evidenceUploading.value)
    return
  evidenceSession += 1
  evidenceTask.value = task
  evidencePaths.value = []
  evidenceIds.value = []
  evidenceVisible.value = true
}

/** 关闭弹层并重置草稿，避免下一单带上一单的图 */
function closeEvidence() {
  evidenceSession += 1
  evidenceVisible.value = false
  evidenceTask.value = null
  evidencePaths.value = []
  evidenceIds.value = []
}

/** 选择并上传送达凭证，服务端只接收素材 ID */
function chooseEvidence() {
  if (evidencePaths.value.length >= 6) {
    showToast('最多上传6张图片')
    return
  }
  // 弹层可能在上传途中被关掉（取消/返回），上传结果按会话号比对后丢弃，
  // 否则这一单的照片会落到下一单的草稿里。
  const session = evidenceSession
  uni.chooseImage({
    count: 6 - evidencePaths.value.length,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async ({ tempFilePaths }) => {
      const paths = Array.isArray(tempFilePaths) ? tempFilePaths : [tempFilePaths]
      evidenceUploading.value = true
      globalLoading.loading('上传凭证中...')
      try {
        const ids = await Promise.all(paths.map(path => uploadDeliveryEvidence(path)))
        if (session !== evidenceSession)
          return
        evidencePaths.value.push(...paths)
        evidenceIds.value.push(...ids)
      }
      catch (error: any) {
        showToast(error?.message || '凭证上传失败')
      }
      finally {
        evidenceUploading.value = false
        globalLoading.close()
      }
    },
  })
}

function removeEvidence(index: number) {
  evidencePaths.value.splice(index, 1)
  evidenceIds.value.splice(index, 1)
}

/** 确认送达：凭证是后端的硬性要求，缺图不给提交 */
async function confirmArrive() {
  const task = evidenceTask.value
  if (!task || submitting.value)
    return
  if (evidenceIds.value.length === 0) {
    showToast('请先上传至少一张送达凭证')
    return
  }
  submitting.value = true
  globalLoading.loading('处理中...')
  try {
    await arriveTask(task.id, evidenceIds.value).send()
    showToast('已确认送达')
    closeEvidence()
    if (trip.value) {
      await fetchTripDetail(trip.value.id)
    }
  }
  catch (error: any) {
    // 提交失败保留草稿：图片已上传，让司机补一张或直接重试，不用重新拍照
    showToast(error?.message || error?.msg || '操作失败')
  }
  finally {
    submitting.value = false
    globalLoading.close()
  }
}
</script>

<template>
  <hr-navbar title="出车单详情" />
  <view v-if="!loading && trip" class="min-h-screen bg-gray-50 pb-160rpx">
    <!-- ============ 配货视图 ============ -->
    <template v-if="isPickView">
      <!-- 顶部进度条 -->
      <view class="m-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="mb-20rpx flex items-center justify-between">
          <text class="text-30rpx font-bold">
            配货进度
          </text>
          <text class="text-26rpx text-primary">
            {{ pickedCount }} / {{ totalCount }} 件
          </text>
        </view>
        <view class="progress-bar">
          <view
            class="progress-bar-inner"
            :style="{ width: `${totalCount > 0 ? (pickedCount / totalCount) * 100 : 0}%` }"
          />
        </view>
        <view class="mt-16rpx text-24rpx text-gray-500">
          下面是把这趟车 {{ trip.taskCount }} 个订单合并后的配货单：同一件货只列一行，数量是合计。
          按清单一次配齐再出发。
        </view>
      </view>

      <!-- 仓库地址卡片 -->
      <view class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="flex items-center">
          <text class="i-carbon:building text-40rpx text-primary" />
          <view class="ml-20rpx flex-1">
            <text class="text-28rpx font-bold">
              {{ trip.warehouseName || '取货仓库' }}
            </text>
            <view class="mt-10rpx text-26rpx text-gray-500">
              {{ trip.warehouseAddress }}
            </view>
          </view>
        </view>
      </view>

      <!-- 加单入口：还有哪些货要拉上这趟车 -->
      <view class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="mb-16rpx flex items-center justify-between">
          <text class="text-28rpx font-bold">
            加单
          </text>
          <text class="text-22rpx text-gray-400">
            把这趟车还该送的货拉进来
          </text>
        </view>
        <view class="grid grid-cols-2 gap-16rpx">
          <view class="pull-entry" @click="openPullPanel('MINE', '我的待送订单')">
            <text class="i-carbon:list-boxes text-44rpx text-primary" />
            <text class="mt-8rpx text-24rpx">
              我的任务
            </text>
          </view>
          <!-- 未派送池受租户开关控制：关掉后司机不能自助拉，入口直接不显示 -->
          <view
            v-if="canSelfPullUnassigned"
            class="pull-entry"
            @click="openPullPanel('UNASSIGNED', '未派送订单')"
          >
            <text class="i-carbon:document-add text-44rpx text-primary" />
            <text class="mt-8rpx text-24rpx">
              未派送订单
            </text>
          </view>
        </view>
        <view v-if="!canSelfPullUnassigned" class="mt-12rpx text-22rpx text-gray-400">
          本租户未开放司机自助拉单，新订单请由管理端派单后再配货
        </view>
      </view>

      <!-- 配货清单（按分类分节，节内按商品合并） -->
      <view
        v-for="group in pickGroups"
        :key="group.categoryName"
        class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx"
      >
        <view class="mb-16rpx flex items-center justify-between border-b border-gray-100 pb-16rpx">
          <text class="text-28rpx font-bold">
            {{ group.categoryName }}
          </text>
          <text class="text-24rpx text-gray-400">
            {{ group.rows.length }} 项
          </text>
        </view>

        <view
          v-for="row in group.rows"
          :key="row.key"
          class="pick-item"
          :class="{ 'pick-item-done': isPickRowDone(row) }"
          @click="handleToggleRow(row)"
        >
          <image :src="row.picUrl" class="h-120rpx w-120rpx flex-none rounded-lg" mode="aspectFill" />
          <view class="ml-20rpx flex flex-1 flex-col overflow-hidden">
            <wd-text :lines="2" size="26rpx" color="inherit" :text="row.spuName" />
            <view v-if="row.specsInfo" class="pt-6rpx">
              <wd-text size="24rpx" color="#909090" :text="row.specsInfo" />
            </view>
            <view class="pt-6rpx text-24rpx text-gray-500">
              共 {{ row.quantity }} 件
              <text v-if="row.itemIds.length > 1" class="text-gray-400">
                （{{ row.itemIds.length }} 个订单）
              </text>
            </view>
          </view>
          <!-- 确认状态 -->
          <view class="flex flex-none flex-col items-end pl-20rpx">
            <text
              v-if="isPickRowDone(row)"
              class="i-carbon:checkmark-filled text-48rpx text-green-500"
            />
            <text
              v-else
              class="i-carbon:checkbox text-48rpx text-gray-300"
            />
            <text
              v-if="row.pickedCount > 0 && !isPickRowDone(row)"
              class="mt-4rpx text-20rpx text-primary"
            >
              {{ row.pickedCount }}/{{ row.itemIds.length }}
            </text>
          </view>
        </view>
      </view>
    </template>

    <!-- ============ 送货视图 ============ -->
    <template v-if="isDeliverView">
      <!-- 顶部进度 -->
      <view class="m-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="mb-20rpx flex items-center justify-between">
          <text class="text-30rpx font-bold">
            送货进度
          </text>
          <text class="text-26rpx text-primary">
            {{ arrivedCount }} / {{ totalTaskCount }} 单
          </text>
        </view>
        <view class="progress-bar">
          <view
            class="progress-bar-inner"
            :style="{ width: `${totalTaskCount > 0 ? (arrivedCount / totalTaskCount) * 100 : 0}%` }"
          />
        </view>
        <!-- 顺序调整入口：默认收起，需要时展开 -->
        <view class="mt-20rpx flex items-center justify-between border-t border-gray-100 pt-20rpx">
          <text class="text-24rpx text-gray-500">
            按下方顺序送货，长按拖动可调整
          </text>
          <view class="flex items-center" @click="sortPanelVisible = !sortPanelVisible">
            <text class="text-24rpx text-primary">
              {{ sortPanelVisible ? '收起排序' : '调整顺序' }}
            </text>
            <text
              class="i-carbon:chevron-right ml-4rpx text-24rpx text-primary transition-transform"
              :class="{ 'rotate-90': sortPanelVisible }"
            />
          </view>
        </view>
      </view>

      <!-- 加单入口：车已开出也能继续加货，加进来立即成为本趟的一站 -->
      <view v-if="trip.status !== '4'" class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="mb-16rpx flex items-center justify-between">
          <text class="text-28rpx font-bold">
            再加一单
          </text>
          <text class="text-22rpx text-gray-400">
            客户临时加货，拉上车顺路送
          </text>
        </view>
        <view class="grid grid-cols-2 gap-16rpx">
          <view class="pull-entry" @click="openPullPanel('MINE', '我的待送订单')">
            <text class="i-carbon:list-boxes text-44rpx text-primary" />
            <text class="mt-8rpx text-24rpx">
              我的任务
            </text>
          </view>
          <!-- 未派送池受租户开关控制：关掉后司机不能自助拉，入口直接不显示 -->
          <view
            v-if="canSelfPullUnassigned"
            class="pull-entry"
            @click="openPullPanel('UNASSIGNED', '未派送订单')"
          >
            <text class="i-carbon:document-add text-44rpx text-primary" />
            <text class="mt-8rpx text-24rpx">
              未派送订单
            </text>
          </view>
        </view>
        <view class="mt-12rpx text-22rpx text-gray-400">
          {{ canSelfPullUnassigned
            ? '加入后该单进入本趟待配货，配齐后再点「配货完毕，再次出发」才通知买家发货'
            : '本租户未开放司机自助拉单，新订单请由管理端派单' }}
        </view>
      </view>

      <!--
        新加货配货清单：配送中趟次里新并入的单停在「配货中」，
        司机要先照清单配齐这些货，再次出发才会把它们变成待送达。
      -->
      <view v-if="pendingPickGroups.length > 0" class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="mb-16rpx flex items-center justify-between border-b border-gray-100 pb-16rpx">
          <text class="text-28rpx font-bold">
            新加货待配
          </text>
          <text class="text-24rpx" :class="isPendingAllPicked ? 'text-green-500' : 'text-primary'">
            {{ pendingPickedCount }} / {{ pendingItemCount }} 件
          </text>
        </view>
        <view class="mb-16rpx text-24rpx text-gray-400">
          这些是本次新拉上车的货，配齐后再点下方「配货完毕，再次出发」
        </view>

        <view v-for="group in pendingPickGroups" :key="group.categoryName" class="mb-16rpx">
          <view class="mb-8rpx text-24rpx text-gray-500">
            {{ group.categoryName }}
          </view>
          <view
            v-for="row in group.rows"
            :key="row.key"
            class="pick-item"
            :class="{ 'pick-item-done': isPickRowDone(row) }"
            @click="handleToggleRow(row)"
          >
            <image :src="row.picUrl" class="h-120rpx w-120rpx flex-none rounded-lg" mode="aspectFill" />
            <view class="ml-20rpx flex flex-1 flex-col overflow-hidden">
              <wd-text :lines="2" size="26rpx" color="inherit" :text="row.spuName" />
              <view v-if="row.specsInfo" class="pt-6rpx">
                <wd-text size="24rpx" color="#909090" :text="row.specsInfo" />
              </view>
              <view class="pt-6rpx text-24rpx text-gray-500">
                共 {{ row.quantity }} 件
              </view>
            </view>
            <view class="flex flex-none flex-col items-end pl-20rpx">
              <text
                v-if="isPickRowDone(row)"
                class="i-carbon:checkmark-filled text-48rpx text-green-500"
              />
              <text v-else class="i-carbon:checkbox text-48rpx text-gray-300" />
            </view>
          </view>
        </view>
      </view>

      <!-- 排序面板：拖动即改路线，松手落库 -->
      <view v-if="sortPanelVisible" class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
        <view class="mb-16rpx text-24rpx text-gray-400">
          按住右侧手柄拖动到目标位置，松手即保存送货顺序
        </view>
        <view
          v-for="(task, index) in sortRows"
          :key="task.id"
          class="sort-row"
          :style="rowTransform(index)"
          :class="{ 'sort-row-dragging': dragFromIndex === index }"
        >
          <view class="sort-row-index">
            {{ index + 1 }}
          </view>
          <view class="ml-16rpx flex-1 truncate">
            <text class="text-26rpx font-bold">
              {{ task.recipientName || '未填收货人' }}
            </text>
            <text class="ml-10rpx text-22rpx text-gray-400">
              {{ taskDestination(task) || '暂无收货地址' }}
            </text>
          </view>
          <!-- 上移/下移兜底：拖拽不灵时仍能精确调整 -->
          <view class="flex flex-none items-center">
            <text
              class="i-carbon:arrow-up text-32rpx"
              :class="index === 0 ? 'text-gray-300' : 'text-primary'"
              @click.stop="handleMoveUp(index)"
            />
            <text
              class="i-carbon:arrow-down ml-16rpx text-32rpx"
              :class="index === sortRows.length - 1 ? 'text-gray-300' : 'text-primary'"
              @click.stop="handleMoveDown(index)"
            />
            <view
              class="sort-handle ml-16rpx"
              @touchstart.stop="handleSortTouchStart(index, $event)"
              @touchmove.stop.prevent="handleSortTouchMove($event)"
              @touchend.stop="handleSortTouchEnd"
              @touchcancel.stop="handleSortTouchEnd"
            >
              <text class="i-carbon:draggable text-36rpx text-gray-400" />
            </view>
          </view>
        </view>
      </view>

      <!-- 送货路线列表 -->
      <view
        v-for="(task, index) in sortedTaskList"
        :key="task.id"
        class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx"
        :class="{ 'task-done': task.status === '5' || task.status === '6' }"
      >
        <!-- 卡片头部：序号 + 状态 -->
        <view class="mb-20rpx flex items-center justify-between">
          <view class="flex items-center">
            <view class="task-index">
              {{ index + 1 }}
            </view>
            <text class="ml-20rpx text-28rpx font-bold">
              {{ task.recipientName }}
            </text>
          </view>
          <!--
            状态标签：非「待送达」的单一状态直接显示名称。
            8异常/9待退回 的任务没有「已送达」按钮（后端也不允许），
            不标出来会是一张既无操作也无说明的卡片。
          -->
          <text
            v-if="task.status !== '4'"
            class="text-24rpx text-gray-400"
          >
            {{ getTaskStatusName(task.status as DeliveryTaskStatus) }}
          </text>
        </view>

        <!-- 联系电话 -->
        <view class="mb-10rpx flex items-center">
          <text class="i-carbon:phone mr-10rpx text-28rpx text-gray-400" />
          <text
            class="text-26rpx text-primary"
            @click.stop="handleCallPhone(task.recipientPhone)"
          >
            {{ task.recipientPhone }}
          </text>
        </view>

        <!-- 目的地：收货地址为空时回落港口/泊位（与导航递交的口径同源） -->
        <view class="mb-10rpx flex items-start">
          <text class="i-carbon:location mr-10rpx mt-4rpx flex-none text-28rpx text-gray-400" />
          <text class="flex-1 text-26rpx">
            {{ taskDestination(task) || '暂无收货地址' }}
          </text>
        </view>

        <!-- 船供单补充船舶信息，便于司机在港区核对 -->
        <view v-if="task.vesselName" class="mb-10rpx pl-38rpx text-24rpx text-gray-400">
          配送船舶：{{ task.vesselName }}
        </view>

        <!-- 送达时间 -->
        <view v-if="task.arriveTime" class="mb-10rpx text-24rpx text-gray-400">
          送达时间：{{ task.arriveTime }}
        </view>

        <!-- 操作按钮 -->
        <view v-if="task.status !== '5' && task.status !== '6' && task.status !== '7'" class="flex items-center gap-20rpx border-t border-gray-100 pt-20rpx">
          <wd-button
            size="small"
            plain
            type="info"
            @click.stop="handleNavigate(task)"
          >
            导航
          </wd-button>
          <wd-button
            v-if="task.status === '4'"
            size="small"
            type="primary"
            @click.stop="handleArriveTask(task)"
          >
            已送达
          </wd-button>
          <text class="ml-auto text-22rpx text-gray-400">
            第 {{ index + 1 }} 站
          </text>
        </view>
      </view>

      <!-- 全部送达提示：已完成视图同样给出「收车」结论 -->
      <view v-if="isAllArrived" class="mx-20rpx my-40rpx rounded-20rpx bg-green-50 p-40rpx text-center">
        <text class="i-carbon:checkmark-filled text-80rpx text-green-500" />
        <view class="mt-20rpx text-30rpx text-green-600 font-bold">
          全部配送完成
        </view>
        <view v-if="trip.status === '4'" class="mt-10rpx text-26rpx text-gray-500">
          出车单已完成{{ trip.completeTime ? `：${trip.completeTime}` : '' }}
        </view>
      </view>
    </template>

    <!-- ============ 已完成视图：不适用（走送货视图，见 isDeliverView） ============ -->
  </view>

  <!-- 底部操作按钮 -->
  <view
    v-if="!loading && trip"
    class="fixed bottom-0 left-0 right-0 bg-white p-20rpx"
    style="padding-bottom: max(env(safe-area-inset-bottom), 16rpx);"
  >
    <!-- status=1：开始配货 -->
    <wd-button
      v-if="trip.status === '1'"
      type="primary"
      block
      :loading="submitting"
      @click="handleStartLoading"
    >
      开始配货
    </wd-button>

    <!-- status=2：装货完毕出发 -->
    <wd-button
      v-if="trip.status === '2'"
      type="primary"
      block
      :disabled="!isAllPicked"
      :loading="submitting"
      @click="handleDepart"
    >
      {{ isAllPicked ? '装货完毕出发' : `还有${totalCount - pickedCount}件未确认` }}
    </wd-button>

    <!-- status=3 且有新加货：配齐后再次出发，这单才会转待送达 -->
    <wd-button
      v-if="trip.status === '3' && pendingItemCount > 0"
      type="primary"
      block
      :disabled="!isPendingAllPicked"
      :loading="submitting"
      @click="handleDepart"
    >
      {{ isPendingAllPicked
        ? '配货完毕，再次出发'
        : `新加货还有${pendingItemCount - pendingPickedCount}件未确认` }}
    </wd-button>
  </view>

  <!-- 选单面板：三个加单入口共用，勾选后一次性拉进本趟 -->
  <wd-popup
    v-model="pullPanelVisible"
    position="bottom"
    :safe-area-inset-bottom="true"
    :z-index="1020"
    :close-on-click-modal="false"
    custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    @close="closePullPanel"
  >
    <view class="max-h-70vh flex flex-col px-32rpx pb-32rpx pt-28rpx">
      <view class="text-32rpx font-bold">
        {{ pullPanelTitle }}
      </view>
      <view class="mt-8rpx text-24rpx text-gray-400">
        勾选要加入本趟车的订单，商品与收货信息按订单自动带出
      </view>
      <!-- 搜索：交给后端按订单号/收货人/电话匹配 -->
      <view class="mt-16rpx">
        <wd-search
          v-model="pullKeyword"
          placeholder="搜索订单号 / 收货人 / 电话"
          hide-cancel
          @search="fetchPullCandidates"
          @clear="fetchPullCandidates"
        />
      </view>

      <!-- 候选列表 -->
      <scroll-view scroll-y class="mt-16rpx flex-1" style="max-height: 46vh;">
        <view v-if="pullLoading" class="py-60rpx text-center text-26rpx text-gray-400">
          加载中...
        </view>
        <view v-else-if="pullCandidates.length === 0" class="py-60rpx text-center">
          <text class="i-carbon:document text-60rpx text-gray-300" />
          <view class="mt-16rpx text-26rpx text-gray-400">
            没有可加入的订单
          </view>
        </view>
        <view
          v-for="candidate in pullCandidates"
          v-else
          :key="candidate.orderId"
          class="pull-row"
          :class="{ 'pull-row-active': pullSelectedIds.includes(candidate.orderId) }"
          @click="togglePullSelection(candidate.orderId)"
        >
          <text
            class="flex-none text-44rpx"
            :class="pullSelectedIds.includes(candidate.orderId)
              ? 'i-carbon:checkmark-filled text-primary'
              : 'i-carbon:checkbox text-gray-300'"
          />
          <view class="ml-16rpx flex-1 overflow-hidden">
            <!-- 收货人 + 货到付款标记（司机送达时要收款） -->
            <view class="flex items-center">
              <text class="text-26rpx font-bold">
                {{ candidate.recipientName || '未填收货人' }}
              </text>
              <text
                v-if="candidate.paymentType === '3'"
                class="ml-10rpx rounded-4rpx bg-orange-100 px-8rpx text-20rpx text-orange-600"
              >
                货到付款
              </text>
              <text v-if="candidate.vesselName" class="ml-10rpx text-22rpx text-gray-400">
                {{ candidate.vesselName }}
              </text>
            </view>
            <view class="mt-4rpx truncate text-24rpx text-gray-500">
              {{ candidate.recipientAddress || '暂无收货地址' }}
            </view>
            <view class="mt-4rpx truncate text-22rpx text-gray-400">
              单号 {{ candidate.orderNo }} · {{ candidate.itemCount ?? 0 }} 件
              <text v-if="candidate.tripNo">
                · 现挂在 {{ candidate.tripNo }}
              </text>
            </view>
            <!-- 商品明细：司机勾选前要能看清这单到底要送什么，否则无法判断要不要装车 -->
            <view v-if="candidate.items && candidate.items.length > 0" class="candidate-items">
              <view
                v-for="(item, itemIndex) in candidate.items"
                :key="itemIndex"
                class="candidate-item"
              >
                <text class="flex-1 truncate text-22rpx text-gray-600">
                  {{ item.spuName }}{{ item.specsInfo ? ` / ${item.specsInfo}` : '' }}
                </text>
                <text class="ml-10rpx flex-none text-22rpx text-gray-400">
                  ×{{ item.quantity ?? 0 }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>

      <view class="mt-20rpx flex gap-20rpx border-t border-gray-100 pt-20rpx">
        <wd-button type="info" plain block :disabled="submitting" @click="closePullPanel">
          取消
        </wd-button>
        <wd-button
          type="primary"
          block
          :loading="submitting"
          :disabled="pullSelectedIds.length === 0"
          @click="confirmPullOrders"
        >
          {{ pullSelectedIds.length === 0
            ? '请选择订单'
            : `加入本趟(${pullSelectedIds.length}单/${pullTotalQuantity}件)` }}
        </wd-button>
      </view>
    </view>
  </wd-popup>

  <!-- 送达凭证弹层：司机端唯一提交凭证的入口，与任务详情页的上传口径一致 -->
  <wd-popup
    v-model="evidenceVisible"
    position="bottom"
    :safe-area-inset-bottom="true"
    :z-index="1020"
    :close-on-click-modal="false"
    custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    @close="closeEvidence"
  >
    <view class="px-32rpx pb-32rpx pt-28rpx">
      <view class="text-32rpx font-bold">
        送达凭证
      </view>
      <view v-if="evidenceTask" class="mt-12rpx text-26rpx text-gray-500">
        {{ evidenceTask.recipientName }} · {{ taskDestination(evidenceTask) || '暂无收货地址' }}
      </view>

      <view class="mt-20rpx text-24rpx text-gray-400">
        请拍摄或上传 1–6 张送达现场照片，凭证会随任务留档供客户与管理员查看。
      </view>

      <!-- 凭证列表：缩略图右上角 × 删除，末位留「+」添加入口 -->
      <view class="mt-20rpx flex flex-wrap gap-16rpx">
        <view v-for="(path, index) in evidencePaths" :key="path" class="relative">
          <image :src="path" class="h-150rpx w-150rpx rounded-lg" mode="aspectFill" />
          <text
            class="absolute right-0 top-0 rounded-bl-lg bg-black/60 px-8rpx text-white"
            @click="removeEvidence(index)"
          >
            ×
          </text>
        </view>
        <view
          v-if="evidencePaths.length < 6"
          class="h-150rpx w-150rpx flex items-center justify-center border border-gray-300 rounded-lg border-dashed"
          @click="chooseEvidence"
        >
          <text class="i-carbon:add text-44rpx text-gray-400" />
        </view>
      </view>

      <view class="mt-32rpx flex gap-20rpx">
        <wd-button type="info" plain block :disabled="submitting" @click="closeEvidence">
          取消
        </wd-button>
        <wd-button
          type="primary"
          block
          :loading="submitting"
          :disabled="evidenceUploading || evidenceIds.length === 0"
          @click="confirmArrive"
        >
          {{ evidenceIds.length === 0 ? '请上传凭证' : `确认送达(${evidenceIds.length})` }}
        </wd-button>
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
.progress-bar {
  width: 100%;
  height: 16rpx;
  border-radius: 8rpx;
  background-color: #e5e6eb;
  overflow: hidden;

  .progress-bar-inner {
    height: 100%;
    border-radius: 8rpx;
    background: linear-gradient(90deg, #0084ff, #07c160);
    transition: width 0.3s ease;
  }
}

.pick-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1px solid #f5f5f5;

  &:last-of-type {
    border-bottom: none;
  }
}

.pick-item-done {
  opacity: 0.6;
}

.task-done {
  opacity: 0.7;
  background-color: #f7f8fa;
}

.task-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background-color: var(--wot-color-theme, #0084ff);
  color: #fff;
  font-size: 26rpx;
  font-weight: bold;
}

.sort-row {
  display: flex;
  align-items: center;
  /* 固定行高：拖拽落点按行高换算，变高行长会让插入位置判定漂移 */
  height: 72px;
  padding: 0 8rpx;
  border-bottom: 1px solid #f5f5f5;
  background-color: #fff;
  transition: transform 0.15s ease;

  &:last-of-type {
    border-bottom: none;
  }
}

.sort-row-dragging {
  transition: none;
  background-color: #f7f8fa;
  border-radius: 12rpx;
}

.sort-row-index {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background-color: #eef3ff;
  color: var(--wot-color-theme, #0084ff);
  font-size: 24rpx;
  font-weight: bold;
}

.sort-handle {
  display: flex;
  align-items: center;
  padding: 10rpx 6rpx;
}

.pull-entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24rpx 0;
  border-radius: 12rpx;
  background-color: #f7f8fa;
}

.pull-row {
  display: flex;
  align-items: center;
  padding: 20rpx 16rpx;
  border-bottom: 1px solid #f5f5f5;

  &:last-of-type {
    border-bottom: none;
  }
}

.pull-row-active {
  background-color: #f0f7ff;
  border-radius: 12rpx;
}

.candidate-items {
  margin-top: 10rpx;
  padding: 12rpx 16rpx;
  border-radius: 10rpx;
  background-color: #fafbfc;
}

.candidate-item {
  display: flex;
  align-items: center;
  padding: 4rpx 0;
}
</style>
