<script setup lang="ts">
import type {
  DeliveryGroup,
  GroupCandidate,
  GroupChange,
  GroupDetail,
} from '#/api/order/delivery-group';
import type { VesselCall, VesselInfo } from '#/api/vessel/vessel';

import { computed, onMounted, reactive, ref } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getWarehouseConfig } from '#/api/delivery/config';
import {
  addOrdersToWave,
  createWave,
  getWavePage,
  startPicking,
} from '#/api/fulfillment/wave';
import {
  changeDeliveryGroup,
  createDeliveryGroup,
  getDeliveryGroup,
  getDeliveryGroups,
  getGroupCandidates,
} from '#/api/order/delivery-group';
import { getVesselCalls, getVesselPage } from '#/api/vessel/vessel';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

import { canJoin, commandRequest, groupStatus } from './group-state';

const groups = ref<DeliveryGroup[]>([]);
const loading = ref(false);
const busy = ref(false);
const filter = reactive({ vesselCallId: '' });
const detail = ref<GroupDetail>();
const drawer = ref(false);
const candidates = ref<GroupCandidate[]>([]);
const selected = ref<GroupCandidate[]>([]);
const selectedOrderIds = ref<string[]>([]);
const warehouse = ref<{ id: string; warehouseName: string }>();
const vessels = ref<VesselInfo[]>([]);
const calls = ref<VesselCall[]>([]);
const createVisible = ref(false);
const form = reactive({
  vesselId: '',
  vesselCallId: '',
  warehouseId: '',
  windowSequence: 1,
  windowStart: '',
  windowEnd: '',
  cutoff: '',
  maxOrders: 20,
  reason: '',
});
const scheduleVisible = ref(false);
const schedule = reactive({
  windowStart: '',
  windowEnd: '',
  cutoff: '',
  maxOrders: 20,
  reason: '',
});
const allowLate = ref(false);
const lateCapacity = ref(20);
const waveId = ref('');
const waves = ref<{ id: string; waveNo: string }[]>([]);
let envelope: undefined | { id: string; signature: string };
let detailTicket = 0;
const totalOrders = computed(() =>
  groups.value.reduce((sum, group) => sum + (group.orderCount ?? 0), 0),
);
const unassigned = computed(() =>
  groups.value.reduce((sum, group) => sum + (group.unassignedCount ?? 0), 0),
);
const requestId = (data: unknown) => {
  envelope = commandRequest(envelope, data, () => crypto.randomUUID());
  return envelope.id;
};
async function refresh(append = false) {
  loading.value = true;
  try {
    const page = await getDeliveryGroups({
      vesselCallId: filter.vesselCallId || undefined,
      after: append ? groups.value.at(-1)?.id : undefined,
    });
    groups.value = append ? [...groups.value, ...page] : page;
  } finally {
    loading.value = false;
  }
}
async function open(id: string) {
  const ticket = ++detailTicket;
  drawer.value = true;
  detail.value = undefined;
  selected.value = [];
  selectedOrderIds.value = [];
  waveId.value = '';
  allowLate.value = false;
  const current = await getDeliveryGroup(id);
  const group = current.group;
  const [options, wavePage] = await Promise.all([
    getGroupCandidates(id),
    getWavePage({
      current: 1,
      size: 100,
      status: '1',
      portCode: group.portCode,
    }),
  ]);
  if (ticket !== detailTicket || !drawer.value) return;
  detail.value = current;
  candidates.value = options;
  lateCapacity.value = current.group.maxOrders;
  waves.value = (wavePage.records ?? []).filter(
    (wave: { vesselCallId?: string; warehouseId?: string }) =>
      wave.vesselCallId === group.vesselCallId &&
      wave.warehouseId === group.warehouseId,
  );
}
async function reloadDetail() {
  if (detail.value) await open(detail.value.group.id);
  await refresh();
}
async function command(
  action: GroupChange['action'],
  ids: string[] = [],
  extra: Partial<GroupChange> = {},
) {
  if (!detail.value || busy.value) return;
  const group = detail.value.group;
  let reason = extra.reason;
  if (!reason) {
    const response = await ElMessageBox.prompt(
      '填写本次操作原因；特批需说明已核定的运力及追加安排。',
      '确认配送安排',
      {
        inputType: 'textarea',
        inputValidator: (text) =>
          (!!text?.trim() && text.length <= 500) || '请填写1至500字原因',
      },
    ).catch(() => undefined);
    reason = response?.value;
  }
  if (!reason) return;
  const data = {
    action,
    expectedRevision: group.revision,
    orderIds: ids,
    reason,
    allowLate: false,
    ...extra,
  };
  busy.value = true;
  try {
    await changeDeliveryGroup(group.id, {
      ...data,
      requestId: requestId({ groupId: group.id, ...data }),
    });
    envelope = undefined;
    scheduleVisible.value = false;
    ElMessage.success('配送安排已保存');
    await reloadDetail();
  } finally {
    busy.value = false;
  }
}
async function openCreate() {
  createVisible.value = true;
  const [config, page] = await Promise.all([
    getWarehouseConfig(),
    getVesselPage({ current: 1, size: 100, status: '1' }),
  ]);
  warehouse.value = config;
  vessels.value = page.records ?? [];
  form.warehouseId = config?.id ?? '';
}
async function selectVessel() {
  form.vesselCallId = '';
  calls.value = [];
  form.windowStart = '';
  form.windowEnd = '';
  form.cutoff = '';
  const vesselId = form.vesselId;
  const response = vesselId ? await getVesselCalls(vesselId) : [];
  if (form.vesselId === vesselId) calls.value = response;
}
function selectCall() {
  const call = calls.value.find((item) => item.id === form.vesselCallId);
  form.windowStart = call?.deliveryWindowStart ?? '';
  form.windowEnd = call?.deliveryWindowEnd ?? '';
  form.cutoff = '';
}
async function saveCreate() {
  if (!form.warehouseId || !form.vesselCallId || !form.reason.trim()) {
    ElMessage.warning('请选择仓库、靠港并填写排程原因');
    return;
  }
  busy.value = true;
  const { vesselId: _vesselId, ...input } = form;
  const data = {
    ...input,
    windowStart: input.windowStart || null,
    windowEnd: input.windowEnd || null,
    cutoff: input.cutoff || null,
  };
  try {
    await createDeliveryGroup({ ...data, requestId: requestId(data) });
    envelope = undefined;
    createVisible.value = false;
    await refresh();
  } finally {
    busy.value = false;
  }
}
function editSchedule() {
  const group = detail.value?.group;
  if (!group) return;
  Object.assign(schedule, {
    windowStart: group.windowStart ?? '',
    windowEnd: group.windowEnd ?? '',
    cutoff: group.cutoff ?? '',
    maxOrders: group.maxOrders,
    reason: '',
  });
  scheduleVisible.value = true;
}
async function bindWave(create = false) {
  const current = detail.value;
  if (!current || busy.value) return;
  const ids = current.orders
    .filter(
      (order) =>
        !order.waveId && selectedOrderIds.value.includes(order.orderId),
    )
    .map((order) => order.orderId);
  if (ids.length === 0 || ids.length > 100) {
    ElMessage.warning('请选择待分配订单，每次最多100单');
    return;
  }
  busy.value = true;
  try {
    if (create && !waveId.value) {
      const wave = await createWave({
        warehouseId: current.group.warehouseId,
        portCode: current.group.portCode,
        vesselCallId: current.group.vesselCallId,
        planDeliveryTime: current.group.windowStart,
        remark: `合送窗口 ${current.group.windowSequence}`,
      });
      waveId.value = wave.id;
      waves.value.push({ id: wave.id, waveNo: wave.waveNo });
    }
    if (!waveId.value) {
      ElMessage.warning('请选择待拣货波次');
      return;
    }
    await addOrdersToWave(waveId.value, ids);
    ElMessage.success('已关联波次，请核对后明确开始拣货');
    await reloadDetail();
  } finally {
    busy.value = false;
  }
}
async function startWave(id: string) {
  await ElMessageBox.confirm(
    '开始后不能继续向此波次加单；请确认已截单、运力和订单安排。',
    '开始拣货',
  );
  busy.value = true;
  try {
    await startPicking(id);
    await reloadDetail();
  } finally {
    busy.value = false;
  }
}
function selectable(row: GroupCandidate) {
  return !row.assignedGroupId;
}
function selectOrders(rows: { orderId: string }[]) {
  selectedOrderIds.value = rows.map((row) => row.orderId);
}
function canSelectOrder(row: { waveId?: string }) {
  return !row.waveId;
}
onMounted(() => refresh());
useKeepAliveRefresh(() => refresh());
</script>

<template>
  <div class="group-board p-4">
    <header class="board-heading">
      <div>
        <div class="eyebrow">船供履约 · 同靠港协同</div>
        <h1>合送排程</h1>
        <p>按已核定的仓库与靠港窗口归组，每张订单独立付款、退款和签收。</p>
      </div>
      <ElButton
        v-access:code="['fulfillment:wave:save']"
        type="primary"
        @click="openCreate"
      >
        建立配送窗口
      </ElButton>
    </header>
    <div class="board-metrics">
      <div>
        <strong>{{ groups.length }}</strong>
        <span>已加载窗口</span>
      </div>
      <div>
        <strong>{{ totalOrders }}</strong>
        <span>归组订单</span>
      </div>
      <div>
        <strong>{{ unassigned }}</strong>
        <span>待分配波次</span>
      </div>
    </div>
    <div class="flex gap-3 py-4">
      <ElInput
        v-model="filter.vesselCallId"
        clearable
        placeholder="按靠港计划 ID 筛选"
        class="max-w-sm"
        @keyup.enter="refresh()"
      />
      <ElButton :loading="loading" @click="refresh()">刷新排程</ElButton>
    </div>
    <ElAlert
      title="截单后的新订单尝试下一开放窗口；没有已核定窗口时保持待排程。订单数容量之外，重量与车辆运力仍需人工核定。"
      type="info"
      :closable="false"
      class="mb-4"
    />
    <ElTable
      v-loading="loading"
      :data="groups"
      @row-dblclick="(row) => open(row.id)"
    >
      <ElTableColumn label="配送窗口" min-width="190">
        <template #default="{ row }">
          <b>{{ row.portCode }} · 第 {{ row.windowSequence }} 窗口</b>
          <div class="muted">{{ row.vesselCallId }}</div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="计划窗口" min-width="200">
        <template #default="{ row }">
          {{ row.windowStart || '待排程' }}
          <div class="muted">
            {{ row.windowEnd || '尚未核定窗口' }}
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="cutoff" label="截单时间" min-width="180" />
      <ElTableColumn label="订单 / 容量" width="120">
        <template #default="{ row }">
          {{ row.orderCount }} / {{ row.maxOrders }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="unassignedCount" label="待分配" width="85" />
      <ElTableColumn label="状态" width="110">
        <template #default="{ row }">
          <ElTag :type="row.status === 'OPEN' ? 'success' : 'info'">
            {{ groupStatus[row.status] || row.status }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click="open(row.id)">
            安排与记录
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElButton
      v-if="groups.length > 0 && groups.length % 100 === 0"
      class="mt-3"
      :loading="loading"
      @click="refresh(true)"
    >
      加载更多窗口
    </ElButton>
    <ElDrawer
      v-model="drawer"
      title="配送安排"
      size="900px"
      @closed="detailTicket++"
    >
      <template v-if="detail">
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="合送组">
            {{ detail.group.id }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            {{ groupStatus[detail.group.status] }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="仓库">
            {{ detail.group.warehouseId }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="靠港">
            {{ detail.group.vesselCallId }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="计划窗口">
            {{ detail.group.windowStart || '待排程' }} ~
            {{ detail.group.windowEnd || '待排程' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="截单">
            {{ detail.group.cutoff || '待核定' }}
          </ElDescriptionsItem>
        </ElDescriptions>
        <div
          v-access:code="['fulfillment:wave:save']"
          class="flex flex-wrap gap-2 py-4"
        >
          <ElButton
            :disabled="
              busy || !['OPEN', 'CLOSED'].includes(detail.group.status)
            "
            @click="editSchedule"
          >
            调整窗口 / 延误
          </ElButton>
          <ElButton
            :disabled="busy || detail.group.status !== 'OPEN'"
            @click="command('CLOSE')"
          >
            立即截单
          </ElButton>
          <ElButton
            :disabled="busy || detail.orders.length > 0"
            @click="command('CANCEL')"
          >
            取消空组
          </ElButton>
          <ElButton
            :disabled="busy || detail.group.status !== 'FULFILLING'"
            @click="command('COMPLETE')"
          >
            核验完成
          </ElButton>
          <ElButton :disabled="busy" @click="reloadDetail">
            重载最新修订
          </ElButton>
        </div>
        <h2>独立订单 · {{ detail.orders.length }}</h2>
        <ElTable :data="detail.orders" @selection-change="selectOrders">
          <ElTableColumn
            type="selection"
            :selectable="canSelectOrder"
            width="45"
          />
          <ElTableColumn prop="orderId" label="订单 ID" min-width="180" />
          <ElTableColumn prop="waveId" label="拣货波次" min-width="180" />
          <ElTableColumn prop="taskId" label="配送任务" min-width="180" />
          <ElTableColumn label="操作" width="100">
            <template #default="{ row }">
              <ElButton
                v-access:code="['fulfillment:wave:save']"
                link
                type="danger"
                :disabled="busy || !!row.waveId"
                @click="command('REMOVE', [row.orderId])"
              >
                移出 / 拆组
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <div
          v-access:code="['fulfillment:wave:save']"
          class="flex flex-wrap gap-2 py-4"
        >
          <ElSelect
            v-model="waveId"
            placeholder="选择同仓同靠港的待拣波次"
            class="w-72"
          >
            <ElOption
              v-for="wave in waves"
              :key="wave.id"
              :label="wave.waveNo"
              :value="wave.id"
            />
          </ElSelect>
          <ElButton :disabled="busy || !waveId" @click="bindWave()">
            分配选中订单
          </ElButton>
          <ElButton :disabled="busy" @click="bindWave(true)">
            新建 / 继续分配选中订单
          </ElButton>
        </div>
        <div
          v-access:code="['fulfillment:wave:pick']"
          class="flex flex-wrap gap-2 pb-4"
        >
          <ElButton
            v-for="id in [
              ...new Set(
                detail.orders
                  .map((order) => order.waveId)
                  .filter(
                    (value): value is string =>
                      !!value && waves.some((wave) => wave.id === value),
                  ),
              ),
            ]"
            :key="id"
            :disabled="busy"
            @click="startWave(id)"
          >
            开始波次 {{ id }}
          </ElButton>
        </div>
        <h2>同靠港待发货订单</h2>
        <p class="muted">
          候选最多100单；提交时重验窗口、仓库、容量、波次与任务。
        </p>
        <ElTable
          :data="candidates"
          @selection-change="(rows: GroupCandidate[]) => (selected = rows)"
        >
          <ElTableColumn type="selection" :selectable="selectable" width="45" />
          <ElTableColumn prop="orderNo" label="订单号" min-width="160" />
          <ElTableColumn prop="id" label="订单 ID" min-width="180" />
          <ElTableColumn label="归组" min-width="160">
            <template #default="{ row }">
              {{ row.assignedGroupId ? '已归组' : '待归组' }}
            </template>
          </ElTableColumn>
        </ElTable>
        <div
          v-access:code="['fulfillment:wave:save']"
          class="flex flex-wrap items-center gap-3 py-4"
        >
          <ElCheckbox v-model="allowLate">截单特批追加</ElCheckbox>
          <ElInputNumber
            v-if="allowLate"
            v-model="lateCapacity"
            :min="1"
            :max="10000"
          />
          <ElButton
            type="primary"
            :disabled="
              busy || selected.length === 0 || !canJoin(detail.group, allowLate)
            "
            @click="
              command(
                'JOIN',
                selected.map((row) => row.id),
                { allowLate, maxOrders: allowLate ? lateCapacity : undefined },
              )
            "
          >
            归入本窗口
          </ElButton>
        </div>
        <h2>操作与通知记录</h2>
        <ElTable :data="detail.operations">
          <ElTableColumn prop="createTime" label="时间" min-width="180" />
          <ElTableColumn prop="action" label="动作" width="140" />
          <ElTableColumn prop="reason" label="原因" min-width="220" />
          <ElTableColumn prop="actorId" label="操作者" min-width="120" />
          <ElTableColumn prop="outboxStatus" label="通知" width="100" />
        </ElTable>
      </template>
      <ElEmpty v-else description="正在加载配送安排" />
    </ElDrawer>
    <ElDialog v-model="createVisible" title="建立配送窗口" width="620px">
      <ElForm label-width="100px">
        <ElFormItem label="仓库">
          <ElSelect v-model="form.warehouseId">
            <ElOption
              v-if="warehouse"
              :label="warehouse.warehouseName"
              :value="warehouse.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="船舶">
          <ElSelect v-model="form.vesselId" filterable @change="selectVessel">
            <ElOption
              v-for="vessel in vessels"
              :key="vessel.id"
              :label="vessel.vesselName"
              :value="vessel.id || ''"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="靠港计划">
          <ElSelect v-model="form.vesselCallId" @change="selectCall">
            <ElOption
              v-for="call in calls"
              :key="call.id"
              :label="`${call.portName} · ${call.eta} · ${call.status}`"
              :value="call.id || ''"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="窗口序号">
          <ElInputNumber v-model="form.windowSequence" :min="1" />
        </ElFormItem>
        <ElFormItem
          v-for="field in ['windowStart', 'windowEnd', 'cutoff'] as const"
          :key="field"
          :label="
            {
              windowStart: '开始时间',
              windowEnd: '结束时间',
              cutoff: '截单时间',
            }[field]
          "
        >
          <ElDatePicker
            v-model="form[field]"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
        <ElFormItem label="订单容量">
          <ElInputNumber v-model="form.maxOrders" :min="1" :max="10000" />
        </ElFormItem>
        <ElFormItem label="排程依据">
          <ElInput v-model="form.reason" type="textarea" :maxlength="500" />
        </ElFormItem>
      </ElForm>
      <ElAlert
        title="无明确时间时，将开始、结束和截单全部留空，建立待排程组；待排程组不能加单。"
        :closable="false"
      />
      <template #footer>
        <ElButton :loading="busy" type="primary" @click="saveCreate">
          保存窗口
        </ElButton>
      </template>
    </ElDialog>
    <ElDialog
      v-model="scheduleVisible"
      title="调整排程（保留合送组 ID）"
      width="580px"
    >
      <ElForm label-width="100px">
        <ElFormItem
          v-for="field in ['windowStart', 'windowEnd', 'cutoff'] as const"
          :key="field"
          :label="
            {
              windowStart: '开始时间',
              windowEnd: '结束时间',
              cutoff: '截单时间',
            }[field]
          "
        >
          <ElDatePicker
            v-model="schedule[field]"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
        <ElFormItem label="订单容量">
          <ElInputNumber v-model="schedule.maxOrders" :min="1" :max="10000" />
        </ElFormItem>
        <ElFormItem label="调整原因">
          <ElInput v-model="schedule.reason" type="textarea" :maxlength="500" />
        </ElFormItem>
      </ElForm>
      <ElAlert
        title="延误不会自动重开已截单组。既有波次及任务请分别核对。"
        :closable="false"
      />
      <template #footer>
        <ElButton
          type="primary"
          :disabled="busy || !schedule.reason.trim()"
          @click="command('SCHEDULE', [], schedule)"
        >
          保存调整
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.group-board {
  --board-accent: #0f766e;
}

.board-heading {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--el-bg-color);
  border-left: 4px solid var(--board-accent);
}

.eyebrow {
  font-size: 12px;
  color: var(--board-accent);
  letter-spacing: 0.12em;
}

h1 {
  margin: 6px 0;
  font-size: 28px;
  font-weight: 650;
}

h2 {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 650;
}

.board-heading p,
.muted {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.board-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  margin-top: 18px;
  background: var(--el-border-color);
  border: 1px solid var(--el-border-color);
}

.board-metrics div {
  display: flex;
  gap: 14px;
  align-items: baseline;
  padding: 18px 24px;
  background: var(--el-bg-color);
}

.board-metrics strong {
  font-size: 30px;
  font-variant-numeric: tabular-nums;
  color: var(--board-accent);
}

.board-metrics span {
  color: var(--el-text-color-secondary);
}
</style>
