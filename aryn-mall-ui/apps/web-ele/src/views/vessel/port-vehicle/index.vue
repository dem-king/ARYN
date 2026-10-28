<script lang="ts" setup>
/**
 * 港口报备车辆。
 *
 * 业务背景：船员靠港外出需要打车，只有完成港口报备的车辆才能进入港区。
 * 名单由本公司人员在后台按港口维护，C 端小程序按当前靠港展示并一键拨打。
 * 港口名称与靠港计划（vessel_call.port_name）同口径，录入时下拉建议可带出已有港口。
 *
 * 页面职责：按港口/车牌/司机/状态筛选名单，并维护车辆的启停与增删改。
 * 停用会立刻让车辆在小程序端消失，因此停用与删除都走二次确认。
 */
import type { FormInstance, FormRules } from 'element-plus';

import type { PortVehicle } from '#/api/vessel/portVehicle';

import { defineAsyncComponent, nextTick, onMounted, reactive, ref } from 'vue';

import { Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  addPortVehicle,
  deletePortVehicle,
  getPortVehiclePage,
  getPortVehiclePortOptions,
  updatePortVehicle,
} from '#/api/vessel/portVehicle';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

/** 表单模型：与后端实体字段一一对应，不夹带审计字段 */
interface PortVehicleForm {
  driverName: string;
  driverPhone: string;
  pickupPoint: string;
  plateNo: string;
  portName: string;
  remark: string;
  seatCount: number | undefined;
  sort: number;
  status: string;
  vehicleDesc: string;
}

const createEmptyForm = (): PortVehicleForm => ({
  driverName: '',
  driverPhone: '',
  pickupPoint: '',
  plateNo: '',
  portName: '',
  remark: '',
  seatCount: undefined,
  sort: 0,
  status: '1',
  vehicleDesc: '',
});

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: {
    driverName: '',
    plateNo: '',
    portName: undefined as string | undefined,
    status: undefined as string | undefined,
  },
  tableData: [] as PortVehicle[],
});
const loading = ref(false);
const showSearch = ref(true);
const portOptions = ref<string[]>([]);

const statusLabel: Record<string, string> = {
  '0': '停用',
  '1': '启用',
};
const statusTag: Record<string, 'info' | 'success'> = {
  '0': 'info',
  '1': 'success',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getPortVehiclePage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      driverName: state.query.driverName?.trim(),
      plateNo: state.query.plateNo?.trim(),
      portName: state.query.portName,
      status: state.query.status,
    });
    state.tableData = response.records ?? [];
    state.page.total = response.total ?? 0;
    // 删除末页最后一条后当前页可能越界，回退到最后一页重新拉取
    const maxPage = Math.max(
      1,
      Math.ceil(state.page.total / state.page.pageSize),
    );
    if (state.page.currentPage > maxPage) {
      state.page.currentPage = maxPage;
      await initPage();
    }
  } finally {
    loading.value = false;
  }
};

const loadPortOptions = async () => {
  try {
    portOptions.value = (await getPortVehiclePortOptions()) ?? [];
  } catch {
    portOptions.value = [];
  }
};

const handleSearch = () => {
  state.page.currentPage = 1;
  initPage();
};

const resetQuery = () => {
  state.query = {
    driverName: '',
    plateNo: '',
    portName: undefined,
    status: undefined,
  };
  state.page.currentPage = 1;
  initPage();
};

const handleCurrentChange = (page: number) => {
  state.page.currentPage = page;
};

const handleSizeChange = (size: number) => {
  state.page.pageSize = size;
  // 每页条数变化后原页码大概率越界，统一回到第一页
  state.page.currentPage = 1;
};

/** 「车型 + 座位数」合并成一列，窄屏下不再单独占用一整列 */
const vehicleSummary = (row: PortVehicle) => {
  const seat = row.seatCount ? `${row.seatCount} 座` : '';
  return [row.vehicleDesc, seat].filter(Boolean).join(' · ');
};

/** ElTable 的作用域行类型是 DefaultRow，模板里统一收敛回实体类型 */
const asVehicle = (row: any) => row as PortVehicle;

// ---------------------------------------------------------------------------
// 新增 / 编辑
// ---------------------------------------------------------------------------

const formVisible = ref(false);
const submitting = ref(false);
const editingId = ref('');
const editingUpdateTime = ref('');
const formRef = ref<FormInstance>();
const form = reactive<PortVehicleForm>(createEmptyForm());

const rules: FormRules = {
  portName: [
    {
      message: '请选择已有港口或输入新港口',
      required: true,
      trigger: ['blur', 'change'],
    },
    { max: 64, message: '港口名称不超过 64 个字符', trigger: 'blur' },
  ],
  plateNo: [
    { message: '请填写车牌号', required: true, trigger: 'blur' },
    { max: 16, message: '车牌号不超过 16 个字符', trigger: 'blur' },
  ],
  driverPhone: [
    { message: '请填写司机联系电话', required: true, trigger: 'blur' },
    {
      message: '联系电话仅支持数字与 - + ( ) 空格',
      pattern: /^[\d\s+()-]{6,20}$/,
      trigger: 'blur',
    },
  ],
};

const applyForm = (source: Partial<PortVehicle>) => {
  Object.assign(form, createEmptyForm(), {
    driverName: source.driverName ?? '',
    driverPhone: source.driverPhone ?? '',
    pickupPoint: source.pickupPoint ?? '',
    plateNo: source.plateNo ?? '',
    portName: source.portName ?? '',
    remark: source.remark ?? '',
    seatCount: source.seatCount ?? undefined,
    sort: source.sort ?? 0,
    status: source.status ?? '1',
    vehicleDesc: source.vehicleDesc ?? '',
  });
};

const resetFormValidation = () => {
  // 弹窗开启动画结束后再清校验，避免旧校验结果闪现
  nextTick(() => formRef.value?.clearValidate());
};

const openCreate = () => {
  editingId.value = '';
  editingUpdateTime.value = '';
  applyForm({});
  formVisible.value = true;
  resetFormValidation();
};

const openEdit = (row: PortVehicle) => {
  editingId.value = row.id ?? '';
  editingUpdateTime.value = row.updateTime ?? row.createTime ?? '';
  applyForm(row);
  formVisible.value = true;
  resetFormValidation();
};

const closeDialog = (done?: () => void) => {
  if (submitting.value) return;
  formVisible.value = false;
  done?.();
};

const submitForm = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    const payload = { ...form };
    if (editingId.value) {
      await updatePortVehicle(editingId.value, payload);
      ElMessage.success('已保存修改');
    } else {
      await addPortVehicle(payload);
      ElMessage.success('已新增报备车辆');
    }
    formVisible.value = false;
    await initPage();
    void loadPortOptions();
  } catch {
    // 失败提示由请求层统一弹出，这里保持弹窗打开以便修改重试
  } finally {
    submitting.value = false;
  }
};

/** 启停只提交表单字段，避免把审计字段（delFlag/createTime…）回写给后端 */
const buildStatusPayload = (row: PortVehicle, status: string) => ({
  driverName: row.driverName,
  driverPhone: row.driverPhone,
  pickupPoint: row.pickupPoint,
  plateNo: row.plateNo,
  portName: row.portName,
  remark: row.remark,
  seatCount: row.seatCount,
  sort: row.sort,
  status,
  vehicleDesc: row.vehicleDesc,
});

const statusUpdatingId = ref('');
const toggleStatus = async (row: PortVehicle) => {
  const target = row.status === '1' ? '0' : '1';
  if (target === '0') {
    const confirmed = await ElMessageBox.confirm(
      `停用后 ${row.portName} · ${row.plateNo} 会立即从小程序端隐藏，船员将看不到该车及司机电话。确认停用？`,
      '停用报备车辆',
      {
        cancelButtonText: '取消',
        confirmButtonText: '停用',
        type: 'warning',
      },
    )
      .then(() => true)
      .catch(() => false);
    if (!confirmed) return;
  }

  statusUpdatingId.value = row.id ?? '';
  try {
    await updatePortVehicle(row.id ?? '', buildStatusPayload(row, target));
    ElMessage.success(target === '1' ? '已启用，小程序端可见' : '已停用');
    await initPage();
  } catch {
    // 失败提示由请求层统一弹出
  } finally {
    statusUpdatingId.value = '';
  }
};

const submitDelete = async (row: PortVehicle) => {
  const confirmed = await ElMessageBox.confirm(
    `确认删除 ${row.portName} 的报备车辆 ${row.plateNo} 吗？删除后小程序端立即不可见，且不可恢复。`,
    '删除报备车辆',
    {
      cancelButtonText: '取消',
      confirmButtonText: '删除',
      type: 'warning',
    },
  )
    .then(() => true)
    .catch(() => false);
  if (!confirmed) return;

  try {
    await deletePortVehicle(row.id ?? '');
    ElMessage.success('已删除');
    await initPage();
    void loadPortOptions();
  } catch {
    // 失败提示由请求层统一弹出
  }
};

onMounted(() => {
  initPage();
  loadPortOptions();
});

useKeepAliveRefresh(handleSearch);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 查询区 -->
      <PageFilterPanel
        :model="state.query"
        :visible="showSearch"
        @reset="resetQuery"
        @search="handleSearch"
      >
        <ElFormItem label="港口" prop="portName">
          <ElSelect
            v-model="state.query.portName"
            clearable
            filterable
            placeholder="全部港口"
          >
            <ElOption
              v-for="port in portOptions"
              :key="port"
              :label="port"
              :value="port"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="车牌号" prop="plateNo">
          <ElInput
            v-model="state.query.plateNo"
            clearable
            placeholder="按车牌模糊搜索"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="司机" prop="driverName">
          <ElInput
            v-model="state.query.driverName"
            clearable
            placeholder="按司机姓名模糊搜索"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.query.status"
            clearable
            placeholder="全部状态"
            @change="handleSearch"
          >
            <ElOption label="启用" value="1" />
            <ElOption label="停用" value="0" />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="辆报备车辆">
        <template #left>
          <div class="page-tip">
            只有完成港口报备的车辆才能进入港区；名单按港口展示在小程序「港口报备车辆」页，船员可直接拨打司机电话。
          </div>
        </template>
        <template #right>
          <ElButton
            v-access:code="'vessel:portvehicle:save'"
            :icon="Plus"
            type="primary"
            @click="openCreate"
          >
            新增车辆
          </ElButton>
        </template>
      </PageToolbar>

      <!-- 列表：窄屏下靠「车辆」「司机」两列的上下叠放容纳全部信息，
           避免横向滚动把「操作」列挤出可视区 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        border
        empty-text="暂无报备车辆"
      >
        <ElTableColumn
          label="港口"
          min-width="100"
          prop="portName"
          show-overflow-tooltip
        />
        <ElTableColumn label="车辆" min-width="160">
          <template #default="scope">
            <div class="cell-main">{{ scope.row.plateNo }}</div>
            <div class="cell-sub" :title="vehicleSummary(asVehicle(scope.row))">
              {{ vehicleSummary(asVehicle(scope.row)) || '未填写车型' }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="司机" min-width="160">
          <template #default="scope">
            <div class="cell-main">{{ scope.row.driverName || '未填写' }}</div>
            <div class="cell-sub">{{ scope.row.driverPhone }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="港内等候点 / 备注" min-width="180">
          <template #default="scope">
            <div class="cell-main" :title="scope.row.pickupPoint">
              {{ scope.row.pickupPoint || '未填写等候点' }}
            </div>
            <div
              v-if="scope.row.remark"
              class="cell-sub"
              :title="scope.row.remark"
            >
              {{ scope.row.remark }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="排序" prop="sort" width="70" />
        <ElTableColumn align="center" label="状态" width="80">
          <template #default="scope">
            <ElTag :type="statusTag[scope.row.status] ?? 'info'">
              {{ statusLabel[scope.row.status] ?? scope.row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="操作" width="170" fixed="right">
          <template #default="scope">
            <ElButton
              v-access:code="'vessel:portvehicle:update'"
              link
              type="primary"
              @click="openEdit(asVehicle(scope.row))"
            >
              编辑
            </ElButton>
            <ElButton
              v-access:code="'vessel:portvehicle:update'"
              :loading="statusUpdatingId === scope.row.id"
              link
              :type="scope.row.status === '1' ? 'warning' : 'success'"
              @click="toggleStatus(asVehicle(scope.row))"
            >
              {{ scope.row.status === '1' ? '停用' : '启用' }}
            </ElButton>
            <ElButton
              v-access:code="'vessel:portvehicle:delete'"
              link
              type="danger"
              @click="submitDelete(asVehicle(scope.row))"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty :image-size="80" description="没有符合条件的报备车辆">
            <ElButton
              v-access:code="'vessel:portvehicle:save'"
              :icon="Plus"
              type="primary"
              @click="openCreate"
            >
              新增车辆
            </ElButton>
          </ElEmpty>
        </template>
      </ElTable>

      <Pagination
        :current="state.page.currentPage"
        :size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
        @update:current="handleCurrentChange"
        @update:size="handleSizeChange"
      />

      <!-- 新增 / 编辑 -->
      <ElDialog
        v-model="formVisible"
        :before-close="closeDialog"
        :close-on-click-modal="false"
        :title="editingId ? '编辑报备车辆' : '新增报备车辆'"
        class="port-vehicle-dialog"
        top="6vh"
        width="560px"
      >
        <div v-if="editingId" class="dialog-meta">
          最近更新：{{ editingUpdateTime || '暂无记录' }}
        </div>
        <ElForm
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="auto"
          @submit.prevent
        >
          <ElFormItem label="港口名称" prop="portName">
            <ElSelect
              v-model="form.portName"
              allow-create
              default-first-option
              filterable
              placeholder="选择已有港口或输入新港口"
              style="width: 100%"
            >
              <ElOption
                v-for="port in portOptions"
                :key="port"
                :label="port"
                :value="port"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="车牌号" prop="plateNo">
            <ElInput
              v-model="form.plateNo"
              maxlength="16"
              placeholder="如 沪A12345"
            />
          </ElFormItem>
          <ElFormItem label="司机姓名" prop="driverName">
            <ElInput
              v-model="form.driverName"
              maxlength="20"
              placeholder="选填，便于船员核对"
            />
          </ElFormItem>
          <ElFormItem label="联系电话" prop="driverPhone">
            <ElInput
              v-model="form.driverPhone"
              maxlength="20"
              placeholder="船员将直接拨打此号码"
            />
          </ElFormItem>
          <ElFormItem label="车型" prop="vehicleDesc">
            <ElInput
              v-model="form.vehicleDesc"
              maxlength="30"
              placeholder="如 7座商务"
            />
          </ElFormItem>
          <ElFormItem label="座位数" prop="seatCount">
            <ElInputNumber
              v-model="form.seatCount"
              :max="60"
              :min="1"
              controls-position="right"
            />
          </ElFormItem>
          <ElFormItem label="港内等候点" prop="pickupPoint">
            <ElInput
              v-model="form.pickupPoint"
              maxlength="64"
              placeholder="如 3号闸口外侧 50 米"
            />
          </ElFormItem>
          <ElFormItem label="备注" prop="remark">
            <ElInput
              v-model="form.remark"
              :rows="2"
              maxlength="200"
              placeholder="如服务时间：全天可约"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>
          <ElFormItem label="排序" prop="sort">
            <ElInputNumber
              v-model="form.sort"
              :max="9999"
              :min="0"
              controls-position="right"
            />
            <span class="form-hint">数字越小越靠前</span>
          </ElFormItem>
          <ElFormItem label="状态" prop="status">
            <ElRadioGroup v-model="form.status">
              <ElRadio value="1">启用（小程序可见）</ElRadio>
              <ElRadio value="0">停用（小程序不可见）</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton :disabled="submitting" @click="closeDialog()">
              取消
            </ElButton>
            <ElButton :loading="submitting" type="primary" @click="submitForm">
              确定
            </ElButton>
          </div>
        </template>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
.page-tip {
  flex: 1;
  min-width: 0;
  margin-right: 12px;
  font-size: 13px;
  line-height: 22px;
  color: var(--el-text-color-secondary);
}

.cell-main {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.cell-sub {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.dialog-meta {
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 矮屏（1440x720、笔记本外接投影）下弹窗整体会顶到视口外，
   把表单体限制在视口内滚动，标题与底部按钮始终可点 */
.port-vehicle-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 220px);
  padding-top: 8px;
  padding-bottom: 8px;
  overflow-y: auto;
}
</style>
