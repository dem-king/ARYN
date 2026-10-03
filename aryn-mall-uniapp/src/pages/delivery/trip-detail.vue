<script setup lang="ts">
import type { DeliveryTask, DeliveryTaskStatus, DeliveryTrip, PickGroup, PickItem } from '@/api/delivery'
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import {
  arriveTask,
  departTrip,
  getTaskStatusName,
  getTripDetail,
  pickItem,
  sortTripTasks,
  startLoading,
  unpickItem,
} from '@/api/delivery'
import { uploadDeliveryEvidence } from '@/api/upms/file'
import { openDeliveryNavigation, resolveDeliveryDestination } from '@/utils/delivery-navigation'

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

/** 任务明细（itemList: image/skuName）映射为取货清单视图模型，按任务（一单一任务）归组 */
function buildPickGroups(tasks: DeliveryTask[]): PickGroup[] {
  return (tasks ?? []).map(task => ({
    orderId: task.orderId,
    orderNo: task.orderNo,
    items: (task.itemList ?? []).map(item => ({
      id: item.id,
      orderId: task.orderId,
      orderNo: task.orderNo,
      spuName: item.spuName ?? '',
      picUrl: item.image ?? '',
      specsInfo: item.skuName ?? '',
      quantity: item.quantity ?? 0,
      picked: item.picked ?? '0',
      categoryName: item.categoryName ?? '',
    })),
  }))
}

/** 订单内按分类分组的小节（分类≈供应商批次） */
interface PickSection {
  name: string
  items: PickItem[]
}

/** 订单内按分类再分节，保持清单出现顺序；未回填分类的明细归「未分类」兜底组 */
function buildPickSections(items: PickItem[]): PickSection[] {
  const sections: PickSection[] = []
  const sectionByName = new Map<string, PickSection>()
  for (const item of items) {
    const name = item.categoryName || '未分类'
    let section = sectionByName.get(name)
    if (!section) {
      section = { name, items: [] }
      sectionByName.set(name, section)
      sections.push(section)
    }
    section.items.push(item)
  }
  return sections
}

/** 取货清单（来自详情响应的任务明细） */
const pickGroups = computed<PickGroup[]>(() => buildPickGroups(trip.value?.taskList ?? []))

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

/** 已取件数 */
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

/** 送货视图按 sortNo 排序的任务列表 */
const sortedTaskList = computed(() => {
  if (!trip.value?.taskList)
    return []
  return [...trip.value.taskList].sort((a, b) => a.sortNo - b.sortNo)
})

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

/** 装货完毕出发（status: 2 -> 3） */
async function handleDepart() {
  if (!trip.value || submitting.value)
    return
  if (!isAllPicked.value) {
    const remaining = totalCount.value - pickedCount.value
    showToast(`还有${remaining}件商品未确认取货`)
    return
  }
  submitting.value = true
  globalLoading.loading('处理中...')
  try {
    await departTrip(trip.value.id).send()
    showToast('已出发配送')
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

/** 切换商品取货确认状态 */
async function handleTogglePick(item: PickItem) {
  if (!trip.value || submitting.value)
    return
  // status=1时不允许操作，需先开始配货
  if (trip.value.status === '1') {
    showToast('请先开始配货')
    return
  }
  submitting.value = true
  try {
    if (item.picked === '1') {
      await unpickItem(trip.value.id, item.id).send()
      item.picked = '0'
    }
    else {
      await pickItem(trip.value.id, item.id).send()
      item.picked = '1'
    }
    // 更新已取件数
    if (trip.value) {
      trip.value.pickedItemCount = pickGroups.value
        .reduce((sum, g) => sum + g.items.filter(i => i.picked === '1').length, 0)
    }
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

/** 上移任务 */
async function handleMoveUp(task: DeliveryTask, index: number) {
  if (index === 0 || submitting.value)
    return
  await handleSortTask(index, index - 1)
}

/** 下移任务 */
async function handleMoveDown(task: DeliveryTask, index: number) {
  if (index === sortedTaskList.value.length - 1 || submitting.value)
    return
  await handleSortTask(index, index + 1)
}

/** 调整任务顺序 */
async function handleSortTask(fromIndex: number, toIndex: number) {
  if (!trip.value || submitting.value)
    return
  const list = [...sortedTaskList.value]
  const [moved] = list.splice(fromIndex, 1)
  list.splice(toIndex, 0, moved)
  const taskSort = list.map((t, idx) => ({ taskId: t.id, sortNo: idx + 1 }))
  submitting.value = true
  globalLoading.loading('调整顺序中...')
  try {
    await sortTripTasks(trip.value.id, taskSort).send()
    // 更新本地排序
    if (trip.value.taskList) {
      trip.value.taskList.forEach((t) => {
        const found = taskSort.find(s => s.taskId === t.id)
        if (found)
          t.sortNo = found.sortNo
      })
    }
  }
  catch (error: any) {
    showToast(error?.message || error?.msg || '调整顺序失败')
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
            取货进度
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

      <!-- 取货清单（按订单分组） -->
      <view
        v-for="group in pickGroups"
        :key="group.orderId"
        class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx"
      >
        <!-- 订单号 -->
        <view class="mb-20rpx flex items-center justify-between border-b border-gray-100 pb-20rpx">
          <text class="text-28rpx font-bold">
            订单 {{ group.orderNo }}
          </text>
          <text class="text-24rpx text-gray-400">
            共{{ group.items.length }}件
          </text>
        </view>

        <!-- 商品列表（订单内按分类分节：分类≈供应商批次，司机按供货来源整段取货） -->
        <view
          v-for="section in buildPickSections(group.items)"
          :key="section.name"
          class="mb-16rpx last:mb-0"
        >
          <view class="mb-6rpx flex items-center justify-between">
            <text class="text-26rpx text-gray-700 font-bold">
              {{ section.name }}
            </text>
            <text class="text-22rpx text-gray-400">
              共{{ section.items.length }}件
            </text>
          </view>

          <view
            v-for="item in section.items"
            :key="item.id"
            class="pick-item"
            :class="{ 'pick-item-done': item.picked === '1' }"
            @click="handleTogglePick(item)"
          >
            <image :src="item.picUrl" class="h-120rpx w-120rpx flex-none rounded-lg" mode="aspectFill" />
            <view class="ml-20rpx flex flex-1 flex-col overflow-hidden">
              <wd-text :lines="2" size="26rpx" color="inherit" :text="item.spuName" />
              <view v-if="item.specsInfo" class="pt-6rpx">
                <wd-text size="24rpx" color="#909090" :text="item.specsInfo" />
              </view>
              <view class="pt-6rpx text-24rpx text-gray-500">
                数量：{{ item.quantity }}
              </view>
            </view>
            <!-- 确认状态 -->
            <view class="flex-none pl-20rpx">
              <text
                v-if="item.picked === '1'"
                class="i-carbon:checkmark-filled text-48rpx text-green-500"
              />
              <text
                v-else
                class="i-carbon:checkbox text-48rpx text-gray-300"
              />
            </view>
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
          <!-- 上移/下移按钮 -->
          <view class="ml-auto flex flex-col items-center">
            <text
              class="i-carbon:arrow-up text-32rpx"
              :class="index === 0 ? 'text-gray-300' : 'text-primary'"
              @click.stop="handleMoveUp(task, index)"
            />
            <text
              class="i-carbon:arrow-down mt-10rpx text-32rpx"
              :class="index === sortedTaskList.length - 1 ? 'text-gray-300' : 'text-primary'"
              @click.stop="handleMoveDown(task, index)"
            />
          </view>
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
  </view>

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

  &:last-child {
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
</style>
