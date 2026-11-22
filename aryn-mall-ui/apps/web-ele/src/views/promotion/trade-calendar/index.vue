<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { Plus, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  addTradeCalendar,
  changeTradeCalendarStatus,
  deleteTradeCalendar,
  getTradeCalendars,
  updateTradeCalendar,
} from '#/api/promotion/trade-calendar';
import { getDeclaredVesselCalls } from '#/api/vessel/vessel';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

import {
  fmtPromoTime as fmtTime,
  PROMO_STATUS,
  PROMO_STATUS_TEXT as STATUS_TEXT,
  PROMO_STATUS_TYPE as STATUS_TYPE,
} from '../shared/promo-status';

/**
 * 采购档期管理：B2B 经营节奏锚点（周度/靠港/自然月/自定义时间窗）。
 * 活动归属档期后，档期停用/窗口外时其下活动（含档期特供）自动退出试算。
 */

const ANCHOR_TEXT: Record<string, string> = {
  1: '周度档期',
  2: '靠港档期',
  3: '自然月',
  4: '自定义',
};

const state = reactive({
  keyword: '',
  status: '',
  tableData: [] as any[],
});
const loading = ref(false);
const drawer = ref(false);
const saving = ref(false);
const editingId = ref<null | string>(null);
const range = ref<[] | [string, string]>([]);

const form = reactive({
  anchorRef: '',
  anchorType: '1',
  calendarName: '',
  remark: '',
});

/**
 * 靠港档期（anchorType=2）锚点引用：用靠港计划下拉替代手填 vessel_call_id。
 * 候选取「待处理靠港申报」列表（与档期特供同源）；其它靠港计划可经 allow-create 直接输入 ID。
 * 选中后把 ETA~ETD 预填进时间窗仅作展示——保存时后端按 anchorRef 重新派生窗口。
 */
const callOptions = ref<
  Array<{ eta?: string; etd?: string; label: string; value: string }>
>([]);
const callLoading = ref(false);

const toDateTime = (value?: string) => {
  if (!value) return '';
  const normalized = value.replace('T', ' ');
  return normalized.length === 10
    ? `${normalized} 00:00:00`
    : normalized.slice(0, 19);
};

const loadCallOptions = async () => {
  callLoading.value = true;
  try {
    const calls: any = (await getDeclaredVesselCalls()) ?? [];
    callOptions.value = (Array.isArray(calls) ? calls : []).map(
      (call: any) => ({
        etd: call.etd,
        eta: call.eta,
        label:
          `${call.portName ?? ''} ${String(call.eta ?? '').slice(0, 10)}`.trim(),
        value: call.id,
      }),
    );
  } catch {
    // 列表加载失败降级为空选项，仍可手动输入靠港计划 ID（allow-create）
    callOptions.value = [];
  } finally {
    callLoading.value = false;
  }
};

/** 切换锚点类型：靠港档期拉取候选，其它类型清空锚点引用（跨类型不通用） */
const onAnchorTypeChange = () => {
  form.anchorRef = '';
  if (form.anchorType === '2') {
    loadCallOptions();
  }
};

/** 选中靠港计划：把 ETA~ETD 预填进时间窗（仅展示，后端保存时派生） */
const onVesselCallChange = (callId: string) => {
  const call = callOptions.value.find((option) => option.value === callId);
  if (call?.eta && call?.etd) {
    range.value = [toDateTime(call.eta), toDateTime(call.etd)];
  }
};

const load = async () => {
  loading.value = true;
  try {
    state.tableData = (await getTradeCalendars({
      keyword: state.keyword || undefined,
      status: state.status || undefined,
    })) as any[];
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  editingId.value = null;
  Object.assign(form, {
    anchorRef: '',
    anchorType: '1',
    calendarName: '',
    remark: '',
  });
  range.value = [];
  drawer.value = true;
};

const openEdit = (row: any) => {
  editingId.value = row.id;
  Object.assign(form, {
    anchorRef: row.anchorRef ?? '',
    anchorType: row.anchorType,
    calendarName: row.calendarName,
    remark: row.remark ?? '',
  });
  range.value =
    row.startTime && row.endTime ? [row.startTime, row.endTime] : [];
  if (form.anchorType === '2') {
    loadCallOptions();
  }
  drawer.value = true;
};

const save = async () => {
  if (!form.calendarName) {
    ElMessage.warning('请填写档期名称');
    return;
  }
  // 靠港档期窗口由后端按 anchorRef 派生，只需校验关联了靠港计划；其它类型需手选时间窗
  if (form.anchorType === '2') {
    if (!form.anchorRef) {
      ElMessage.warning('请选择靠港计划');
      return;
    }
  } else if (!range.value || range.value.length !== 2) {
    ElMessage.warning('请选择档期时间窗');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      ...form,
      endTime: range.value[1],
      startTime: range.value[0],
    };
    if (editingId.value) {
      await updateTradeCalendar(editingId.value, payload);
      ElMessage.success('已保存');
    } else {
      await addTradeCalendar(payload);
      ElMessage.success('已创建（草稿，发布后生效）');
    }
    drawer.value = false;
    load();
  } finally {
    saving.value = false;
  }
};

const publish = async (row: any) => {
  await changeTradeCalendarStatus(row.id, PROMO_STATUS.ACTIVE);
  ElMessage.success('已发布生效');
  load();
};

const pause = async (row: any) => {
  await changeTradeCalendarStatus(row.id, PROMO_STATUS.DISABLED);
  ElMessage.success('已停用（其下活动自动退出试算）');
  load();
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `删除档期「${row.calendarName}」？其下活动将退出试算（活动本身不动）。`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(async () => {
    await deleteTradeCalendar(row.id);
    ElMessage.success('删除成功');
    load();
  });
};

load();
useKeepAliveRefresh(load);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <PageToolbar total-label="">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'promotion:calendar:save'"
            :icon="Plus"
            @click="openCreate"
          >
            新建档期
          </ElButton>
        </template>
        <template #right>
          <div class="flex items-center gap-2">
            <ElInput
              v-model="state.keyword"
              placeholder="档期名称"
              clearable
              style="width: 180px"
              @keyup.enter="load"
            />
            <ElSelect
              v-model="state.status"
              placeholder="状态"
              clearable
              style="width: 120px"
            >
              <ElOption label="草稿" :value="PROMO_STATUS.DRAFT" />
              <ElOption label="生效中" :value="PROMO_STATUS.ACTIVE" />
              <ElOption label="已停用" :value="PROMO_STATUS.DISABLED" />
            </ElSelect>
            <ElButton :icon="Search" @click="load"> 查询 </ElButton>
          </div>
        </template>
      </PageToolbar>

      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无采购档期"
      >
        <ElTableColumn prop="calendarName" label="档期名称" min-width="180" />
        <ElTableColumn label="锚点类型" width="110">
          <template #default="scope">
            {{ ANCHOR_TEXT[scope.row.anchorType] || '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="anchorRef" label="锚点引用" width="180">
          <template #default="scope">
            {{ scope.row.anchorRef || '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="档期时间窗" width="240">
          <template #default="scope">
            {{ fmtTime(scope.row.startTime) }} ~
            {{ fmtTime(scope.row.endTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            <ElTag :type="STATUS_TYPE[scope.row.status] || 'info'">
              {{ STATUS_TEXT[scope.row.status] || '-' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="200" align="center">
          <template #default="scope">
            <ElButton
              v-if="scope.row.status !== PROMO_STATUS.ACTIVE"
              link
              type="primary"
              v-access:code="'promotion:calendar:save'"
              @click="publish(scope.row)"
            >
              发布
            </ElButton>
            <ElButton
              v-if="scope.row.status === PROMO_STATUS.ACTIVE"
              link
              type="warning"
              v-access:code="'promotion:calendar:save'"
              @click="pause(scope.row)"
            >
              停用
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'promotion:calendar:save'"
              @click="openEdit(scope.row)"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'promotion:calendar:del'"
              @click="handleDelete(scope.row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <ElDialog
        v-model="drawer"
        :title="editingId ? '编辑档期' : '新建档期'"
        width="560px"
        destroy-on-close
      >
        <ElForm label-width="92px">
          <ElFormItem label="档期名称">
            <ElInput
              v-model="form.calendarName"
              placeholder="如：11月第3周船供备货档期"
            />
          </ElFormItem>
          <ElFormItem label="锚点类型">
            <ElSelect v-model="form.anchorType" @change="onAnchorTypeChange">
              <ElOption label="周度档期" value="1" />
              <ElOption label="靠港档期" value="2" />
              <ElOption label="自然月" value="3" />
              <ElOption label="自定义时间窗" value="4" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="form.anchorType === '2'" label="靠港计划">
            <div class="w-full">
              <ElSelect
                v-model="form.anchorRef"
                filterable
                allow-create
                default-first-option
                clearable
                :loading="callLoading"
                placeholder="选择靠港计划（未列出的可直接输入ID）"
                style="width: 100%"
                @change="onVesselCallChange"
              >
                <ElOption
                  v-for="option in callOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </ElSelect>
              <div
                style="
                  margin-top: 4px;
                  font-size: 12px;
                  line-height: 1.6;
                  color: var(--el-text-color-secondary);
                "
              >
                列表为待处理靠港申报；选择后自动带出 ETA~ETD
                时间窗（仅展示，保存时由后端按靠港计划派生）。
              </div>
            </div>
          </ElFormItem>
          <ElFormItem label="档期时间窗">
            <ElDatePicker
              v-model="range"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput v-model="form.remark" type="textarea" :rows="2" />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="drawer = false"> 取消 </ElButton>
          <ElButton type="primary" :loading="saving" @click="save">
            保存
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
