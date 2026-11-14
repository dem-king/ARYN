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
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

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
const STATUS_TYPE: Record<string, 'danger' | 'info' | 'success'> = {
  0: 'info',
  1: 'success',
  2: 'danger',
};
const STATUS_TEXT: Record<string, string> = {
  0: '草稿',
  1: '生效中',
  2: '已停用',
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
  drawer.value = true;
};

const save = async () => {
  if (!form.calendarName) {
    ElMessage.warning('请填写档期名称');
    return;
  }
  if (!range.value || range.value.length !== 2) {
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
  await changeTradeCalendarStatus(row.id, '1');
  ElMessage.success('已发布生效');
  load();
};

const pause = async (row: any) => {
  await changeTradeCalendarStatus(row.id, '2');
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

const fmtTime = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 16) : '-';

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
              <ElOption label="草稿" value="0" />
              <ElOption label="生效中" value="1" />
              <ElOption label="已停用" value="2" />
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
              v-if="scope.row.status !== '1'"
              link
              type="primary"
              v-access:code="'promotion:calendar:save'"
              @click="publish(scope.row)"
            >
              发布
            </ElButton>
            <ElButton
              v-if="scope.row.status === '1'"
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
            <ElSelect v-model="form.anchorType">
              <ElOption label="周度档期" value="1" />
              <ElOption label="靠港档期" value="2" />
              <ElOption label="自然月" value="3" />
              <ElOption label="自定义时间窗" value="4" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="form.anchorType === '2'" label="靠港计划ID">
            <ElInput v-model="form.anchorRef" placeholder="vessel_call_id" />
          </ElFormItem>
          <ElFormItem label="档期时间窗">
            <ElDatePicker
              v-model="range"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
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
