<script lang="ts" setup>
/**
 * 港口报备车辆。
 *
 * 业务背景：船员靠港外出需要打车，只有完成港口报备的车辆才能进入港区。
 * 名单由本公司人员在后台按港口维护，C 端小程序按当前靠港展示并一键拨打。
 * 港口名称与靠港计划（vessel_call.port_name）同口径，录入时下拉建议可带出已有港口。
 */
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
  ElButton,
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

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { driverName: '', plateNo: '', portName: '', status: '' },
  tableData: [] as any[],
});
const loading = ref(false);
const portOptions = ref<string[]>([]);

const statusLabel: Record<string, string> = {
  '0': '停用',
  '1': '启用',
};
const statusTag: Record<string, string> = {
  '0': 'info',
  '1': 'success',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getPortVehiclePage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      driverName: state.query.driverName,
      plateNo: state.query.plateNo,
      portName: state.query.portName,
      status: state.query.status,
    });
    state.tableData = response.records;
    state.page.total = response.total;
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

const resetQuery = () => {
  state.query = { driverName: '', plateNo: '', portName: '', status: '' };
  state.page.currentPage = 1;
  initPage();
};

// ---------------------------------------------------------------------------
// 新增 / 编辑
// ---------------------------------------------------------------------------

const formVisible = ref(false);
const submitting = ref(false);
const editingId = ref('');
const form = reactive<any>({
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

const openCreate = () => {
  editingId.value = '';
  Object.assign(form, {
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
  formVisible.value = true;
};

const openEdit = (row: any) => {
  editingId.value = row.id;
  Object.assign(form, {
    driverName: row.driverName ?? '',
    driverPhone: row.driverPhone ?? '',
    pickupPoint: row.pickupPoint ?? '',
    plateNo: row.plateNo ?? '',
    portName: row.portName ?? '',
    remark: row.remark ?? '',
    seatCount: row.seatCount ?? undefined,
    sort: row.sort ?? 0,
    status: row.status ?? '1',
    vehicleDesc: row.vehicleDesc ?? '',
  });
  formVisible.value = true;
};

const submitForm = () => {
  if (!form.portName?.trim()) {
    ElMessage.warning('请填写港口名称');
    return;
  }
  if (!form.plateNo?.trim()) {
    ElMessage.warning('请填写车牌号');
    return;
  }
  if (!form.driverPhone?.trim()) {
    ElMessage.warning('请填写司机联系电话');
    return;
  }
  submitting.value = true;
  const request = editingId.value
    ? updatePortVehicle(editingId.value, { ...form })
    : addPortVehicle({ ...form });
  request
    .then(() => {
      ElMessage.success(editingId.value ? '已保存' : '已新增');
      formVisible.value = false;
      initPage();
      loadPortOptions();
    })
    .catch(() => {})
    .finally(() => {
      submitting.value = false;
    });
};

const toggleStatus = (row: any) => {
  const target = row.status === '1' ? '0' : '1';
  updatePortVehicle(row.id, { ...row, status: target })
    .then(() => {
      ElMessage.success(target === '1' ? '已启用' : '已停用');
      initPage();
    })
    .catch(() => {});
};

const submitDelete = (row: any) => {
  ElMessageBox.confirm(
    `确认删除 ${row.portName} 的报备车辆 ${row.plateNo} 吗？删除后小程序端立即不可见。`,
    '删除车辆',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
  )
    .then(() =>
      deletePortVehicle(row.id).then(() => {
        ElMessage.success('已删除');
        initPage();
        loadPortOptions();
      }),
    )
    .catch(() => {});
};

onMounted(() => {
  initPage();
  loadPortOptions();
});
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true" :model="state.query">
        <ElFormItem label="港口">
          <ElSelect
            v-model="state.query.portName"
            clearable
            filterable
            placeholder="选择或输入港口名"
            style="width: 200px"
          >
            <ElOption
              v-for="port in portOptions"
              :key="port"
              :label="port"
              :value="port"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="车牌号">
          <ElInput
            v-model="state.query.plateNo"
            clearable
            placeholder="按车牌模糊搜索"
            style="width: 160px"
          />
        </ElFormItem>
        <ElFormItem label="司机">
          <ElInput
            v-model="state.query.driverName"
            clearable
            placeholder="按司机姓名模糊搜索"
            style="width: 160px"
          />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="state.query.status" style="width: 110px">
            <ElOption label="启用" value="1" />
            <ElOption label="停用" value="0" />
            <ElOption label="全部" value="" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage">搜索</ElButton>
          <ElButton @click="resetQuery">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <div class="mb-3 flex items-center justify-between">
        <div class="text-sm" style="color: #909399">
          只有完成港口报备的车辆才能进入港区；此处名单对小程序船员按港口展示，可直接拨打司机电话。
        </div>
        <ElButton
          v-access:code="'vessel:portvehicle:save'"
          type="primary"
          @click="openCreate"
        >
          新增车辆
        </ElButton>
      </div>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="portName"
          label="港口"
          min-width="140"
          show-overflow-tooltip
        />
        <ElTableColumn prop="plateNo" label="车牌号" width="110" />
        <ElTableColumn prop="driverName" label="司机" width="100" />
        <ElTableColumn prop="driverPhone" label="联系电话" width="130" />
        <ElTableColumn prop="vehicleDesc" label="车型" width="110" />
        <ElTableColumn
          prop="seatCount"
          label="座位数"
          width="80"
          align="center"
        />
        <ElTableColumn
          prop="pickupPoint"
          label="港内等候点"
          min-width="140"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="remark"
          label="备注"
          min-width="120"
          show-overflow-tooltip
        />
        <ElTableColumn prop="sort" label="排序" width="70" align="center" />
        <ElTableColumn label="状态" width="90" align="center">
          <template #default="scope">
            <ElTag :type="(statusTag[scope.row.status] as any) ?? 'info'">
              {{ statusLabel[scope.row.status] ?? scope.row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="updateTime" label="更新时间" width="170" />
        <ElTableColumn label="操作" width="170" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-access:code="'vessel:portvehicle:update'"
              link
              type="primary"
              @click="openEdit(scope.row)"
            >
              编辑
            </ElButton>
            <ElButton
              v-access:code="'vessel:portvehicle:update'"
              link
              :type="scope.row.status === '1' ? 'warning' : 'success'"
              @click="toggleStatus(scope.row)"
            >
              {{ scope.row.status === '1' ? '停用' : '启用' }}
            </ElButton>
            <ElButton
              v-access:code="'vessel:portvehicle:delete'"
              link
              type="danger"
              @click="submitDelete(scope.row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <Pagination
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />

      <ElDialog
        v-model="formVisible"
        :title="editingId ? '编辑报备车辆' : '新增报备车辆'"
        width="560px"
      >
        <ElForm label-width="110px">
          <ElFormItem label="港口名称" required>
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
          <ElFormItem label="车牌号" required>
            <ElInput v-model="form.plateNo" placeholder="如 沪A12345" />
          </ElFormItem>
          <ElFormItem label="司机姓名">
            <ElInput v-model="form.driverName" />
          </ElFormItem>
          <ElFormItem label="联系电话" required>
            <ElInput
              v-model="form.driverPhone"
              placeholder="船员将直接拨打此号码"
            />
          </ElFormItem>
          <ElFormItem label="车型">
            <ElInput v-model="form.vehicleDesc" placeholder="如 7座商务" />
          </ElFormItem>
          <ElFormItem label="座位数">
            <ElInputNumber
              v-model="form.seatCount"
              :max="60"
              :min="1"
              controls-position="right"
            />
          </ElFormItem>
          <ElFormItem label="港内等候点">
            <ElInput
              v-model="form.pickupPoint"
              placeholder="如 3号闸口外侧 50 米"
            />
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput v-model="form.remark" :rows="2" type="textarea" />
          </ElFormItem>
          <ElFormItem label="排序">
            <ElInputNumber
              v-model="form.sort"
              :min="0"
              controls-position="right"
            />
          </ElFormItem>
          <ElFormItem label="状态">
            <ElRadioGroup v-model="form.status">
              <ElRadio value="1">启用（小程序可见）</ElRadio>
              <ElRadio value="0">停用</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="formVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="submitting" @click="submitForm">
            保存
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
