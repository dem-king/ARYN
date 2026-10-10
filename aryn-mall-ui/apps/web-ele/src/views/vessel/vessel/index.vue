<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
  ElAlert,
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
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  addVessel,
  addVesselCall,
  addVesselMember,
  getVesselCallImpact,
  getVesselCalls,
  getVesselMembers,
  getVesselPage,
  updateVessel,
  updateVesselCall,
} from '#/api/vessel/vessel';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { vesselName: '' },
  tableData: [] as any[],
});
const loading = ref(false);
const activeTab = ref('vessel');

// 船舶编辑
const vesselDialogVisible = ref(false);
const vesselForm = reactive<any>({
  callSign: '',
  imoCode: '',
  remark: '',
  vesselName: '',
  vesselNameEn: '',
  vesselType: '5',
});
const editingVesselId = ref('');

// 成员
const memberDialogVisible = ref(false);
const memberForm = reactive<any>({ memberRole: '2', userId: '' });
const memberTableData = ref<any[]>([]);
const memberVessel = ref<any>({});

// 靠港计划
const callDialogVisible = ref(false);
const callTableData = ref<any[]>([]);
const callTableLoading = ref(false);
const callVessel = ref<any>({});
const callFormRef = ref<FormInstance>();
const callForm = reactive<any>({
  berth: '',
  deliveryWindowEnd: '',
  deliveryWindowStart: '',
  eta: '',
  etd: '',
  portCode: '',
  portName: '',
  remark: '',
});
/** 配送时间窗在表单里以区间选择器编辑，保存时拆回 start/end 两个字段 */
const callWindowRange = ref<[string, string] | null>(null);
const editingCallId = ref('');
const callSaving = ref(false);

const callCount = computed(() => callTableData.value.length);
const editingCall = computed(() =>
  callTableData.value.find((item) => item.id === editingCallId.value),
);

const callRules: FormRules = {
  eta: [{ required: true, message: '请选择到港时间 ETA', trigger: 'change' }],
  etd: [
    { required: true, message: '请选择离港时间 ETD', trigger: 'change' },
    {
      trigger: 'change',
      validator: (_rule, value: string, callback) => {
        if (value && callForm.eta && value <= callForm.eta) {
          callback(new Error('离港时间需晚于到港时间'));
        } else {
          callback();
        }
      },
    },
  ],
  portCode: [{ required: true, message: '请输入港口编码', trigger: 'blur' }],
};

const callStatusMeta: Record<
  string,
  { label: string; type: 'danger' | 'info' | 'primary' | 'success' }
> = {
  '1': { label: '计划中', type: 'primary' },
  '2': { label: '靠泊中', type: 'success' },
  '3': { label: '已完成', type: 'info' },
  '4': { label: '已取消', type: 'danger' },
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getVesselPage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      vesselName: state.query.vesselName,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const openVesselDialog = (row?: any) => {
  editingVesselId.value = row?.id ?? '';
  Object.assign(vesselForm, {
    callSign: row?.callSign ?? '',
    imoCode: row?.imoCode ?? '',
    remark: row?.remark ?? '',
    vesselName: row?.vesselName ?? '',
    vesselNameEn: row?.vesselNameEn ?? '',
    vesselType: row?.vesselType ?? '5',
  });
  vesselDialogVisible.value = true;
};

const saveVessel = () => {
  if (!vesselForm.vesselName) {
    ElMessage.warning('船舶名称不能为空');
    return;
  }
  const request = editingVesselId.value
    ? updateVessel(editingVesselId.value, vesselForm)
    : addVessel(vesselForm);
  request
    .then(() => {
      ElMessage.success('保存成功');
      vesselDialogVisible.value = false;
      initPage();
    })
    .catch(() => {});
};

const toggleVesselStatus = (row: any) => {
  const target = row.status === '1' ? '0' : '1';
  ElMessageBox.confirm(
    target === '0' ? '确认停用该船舶?' : '确认恢复在营?',
    '提示',
  )
    .then(() => updateVessel(row.id, { status: target }))
    .then(() => {
      ElMessage.success('操作成功');
      initPage();
    })
    .catch(() => {});
};

const openMembers = (row: any) => {
  memberVessel.value = row;
  memberDialogVisible.value = true;
  getVesselMembers(row.id).then((list) => {
    memberTableData.value = list;
  });
};

const saveMember = () => {
  if (!memberForm.userId) {
    ElMessage.warning('用户ID不能为空');
    return;
  }
  addVesselMember(memberVessel.value.id, memberForm)
    .then(() => {
      ElMessage.success('绑定成功');
      memberDialogVisible.value = false;
      return getVesselMembers(memberVessel.value.id);
    })
    .then((list) => {
      memberTableData.value = list;
    })
    .catch(() => {});
};

const resetCallForm = () => {
  Object.assign(callForm, {
    berth: '',
    deliveryWindowEnd: '',
    deliveryWindowStart: '',
    eta: '',
    etd: '',
    portCode: '',
    portName: '',
    remark: '',
  });
  callWindowRange.value = null;
  callFormRef.value?.clearValidate();
};

const exitCallEdit = () => {
  editingCallId.value = '';
  resetCallForm();
};

const loadCalls = async () => {
  if (!callVessel.value.id) return;
  callTableLoading.value = true;
  try {
    callTableData.value = await getVesselCalls(callVessel.value.id);
  } finally {
    callTableLoading.value = false;
  }
};

const openCalls = (row: any) => {
  callVessel.value = row;
  exitCallEdit();
  callDialogVisible.value = true;
  loadCalls();
};

const saveCall = () => {
  callFormRef.value?.validate((valid) => {
    if (!valid) return;
    const [windowStart = '', windowEnd = ''] = callWindowRange.value ?? [];
    const payload = {
      ...callForm,
      deliveryWindowEnd: windowEnd,
      deliveryWindowStart: windowStart,
      vesselId: callVessel.value.id,
    };
    const isEdit = Boolean(editingCallId.value);
    callSaving.value = true;
    const request = isEdit
      ? updateVesselCall(editingCallId.value, payload)
      : addVesselCall(callVessel.value.id, payload);
    request
      .then(() => {
        ElMessage.success(
          isEdit
            ? '靠港计划已更新，受影响用户将收到站内信提醒'
            : '靠港计划已新增',
        );
        exitCallEdit();
        return loadCalls();
      })
      .catch(() => {})
      .finally(() => {
        callSaving.value = false;
      });
  });
};

const editCall = (row: any) => {
  editingCallId.value = row.id;
  Object.assign(callForm, {
    berth: row.berth ?? '',
    deliveryWindowEnd: row.deliveryWindowEnd ?? '',
    deliveryWindowStart: row.deliveryWindowStart ?? '',
    eta: row.eta ?? '',
    etd: row.etd ?? '',
    portCode: row.portCode ?? '',
    portName: row.portName ?? '',
    remark: row.remark ?? '',
  });
  callWindowRange.value =
    row.deliveryWindowStart && row.deliveryWindowEnd
      ? [row.deliveryWindowStart, row.deliveryWindowEnd]
      : null;
  callFormRef.value?.clearValidate();
};

/** 展示时间统一截断到分钟 */
const fmtMin = (value?: string) => (value ? value.slice(0, 16) : '');

const callRowClass = ({ row }: { row: any }) =>
  row.id === editingCallId.value ? 'row-editing' : '';

// 变更影响查询
const impactDialogVisible = ref(false);
const impactData = ref<any>({});
const viewImpact = (row: any) => {
  getVesselCallImpact(row.id)
    .then((data) => {
      impactData.value = data;
      impactDialogVisible.value = true;
    })
    .catch(() => {});
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElTabs v-model="activeTab">
        <ElTabPane label="船舶档案" name="vessel">
          <ElForm :inline="true" :model="state.query">
            <ElFormItem label="船舶名称">
              <ElInput
                v-model="state.query.vesselName"
                clearable
                placeholder="请输入船舶名称"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" @click="initPage">搜索</ElButton>
              <ElButton
                type="success"
                v-access:code="'vessel:vessel:save'"
                @click="openVesselDialog()"
              >
                新增船舶
              </ElButton>
            </ElFormItem>
          </ElForm>
          <ElTable v-loading="loading" :data="state.tableData" border>
            <ElTableColumn prop="vesselName" label="船舶名称" min-width="140" />
            <ElTableColumn prop="vesselNameEn" label="英文名" min-width="140" />
            <ElTableColumn prop="imoCode" label="IMO" width="120" />
            <ElTableColumn prop="callSign" label="呼号" width="100" />
            <ElTableColumn prop="dwt" label="载重吨" width="100" />
            <ElTableColumn prop="crewCapacity" label="定员" width="80" />
            <ElTableColumn label="状态" width="90" align="center">
              <template #default="scope">
                {{ scope.row.status === '1' ? '在营' : '停用' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="240" align="center">
              <template #default="scope">
                <ElButton
                  link
                  type="primary"
                  v-access:code="'vessel:vessel:update'"
                  @click="openVesselDialog(scope.row)"
                >
                  修改
                </ElButton>
                <ElButton
                  link
                  type="primary"
                  v-access:code="'vessel:member:list'"
                  @click="openMembers(scope.row)"
                >
                  成员
                </ElButton>
                <ElButton
                  link
                  type="primary"
                  v-access:code="'vessel:call:list'"
                  @click="openCalls(scope.row)"
                >
                  靠港计划
                </ElButton>
                <ElButton
                  link
                  type="warning"
                  @click="toggleVesselStatus(scope.row)"
                >
                  {{ scope.row.status === '1' ? '停用' : '恢复' }}
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <Pagination
            :total="state.page.total"
            v-model:current="state.page.currentPage"
            v-model:size="state.page.pageSize"
            @change="initPage"
          />
        </ElTabPane>
      </ElTabs>

      <!-- 船舶编辑 -->
      <ElDialog
        v-model="vesselDialogVisible"
        :title="editingVesselId ? '修改船舶' : '新增船舶'"
        width="560px"
      >
        <ElForm label-width="100px">
          <ElFormItem label="船舶名称" required>
            <ElInput v-model="vesselForm.vesselName" />
          </ElFormItem>
          <ElFormItem label="英文名">
            <ElInput v-model="vesselForm.vesselNameEn" />
          </ElFormItem>
          <ElFormItem label="IMO 编号">
            <ElInput v-model="vesselForm.imoCode" />
          </ElFormItem>
          <ElFormItem label="呼号">
            <ElInput v-model="vesselForm.callSign" />
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput v-model="vesselForm.remark" type="textarea" :rows="2" />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="vesselDialogVisible = false">取消</ElButton>
          <ElButton type="primary" @click="saveVessel">保存</ElButton>
        </template>
      </ElDialog>

      <!-- 成员管理 -->
      <ElDialog v-model="memberDialogVisible" title="船舶成员" width="680px">
        <ElAlert
          :title="`船舶：${memberVessel.vesselName ?? ''}`"
          :closable="false"
          class="mb10"
        />
        <div class="mb10">
          <ElInput
            v-model="memberForm.userId"
            placeholder="商城用户ID（后台邀请授权绑定）"
            style="width: 320px; margin-right: 8px"
          />
          <ElSelect
            v-model="memberForm.memberRole"
            style="width: 160px; margin-right: 8px"
          >
            <ElOption label="普通船员" value="2" />
            <ElOption label="采购确认人" value="3" />
          </ElSelect>
          <ElButton
            type="primary"
            v-access:code="'vessel:member:save'"
            @click="saveMember"
          >
            绑定
          </ElButton>
        </div>
        <ElTable :data="memberTableData" border>
          <ElTableColumn prop="userId" label="用户ID" min-width="180" />
          <ElTableColumn label="角色" width="120">
            <template #default="scope">
              {{
                scope.row.memberRole === '1'
                  ? '发起人'
                  : scope.row.memberRole === '3'
                    ? '确认人'
                    : '船员'
              }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="在船" width="80">
            <template #default="scope">
              {{ scope.row.status === '1' ? '是' : '否' }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="joinTime" label="加入时间" width="170" />
        </ElTable>
      </ElDialog>

      <!-- 靠港计划：上下文 → 新增/编辑表单卡片 → 靠港列表卡片 -->
      <ElDialog
        v-model="callDialogVisible"
        title="靠港计划"
        width="960px"
        top="6vh"
        class="vessel-call-dialog"
      >
        <div class="call-context">
          <div class="call-context-name">
            <span class="call-context-label">船舶</span>
            {{ callVessel.vesselName ?? '—' }}
          </div>
          <div class="call-context-sub">共 {{ callCount }} 个靠港计划</div>
        </div>

        <div class="call-card">
          <div class="call-card-head">
            <span class="call-card-title">
              {{
                editingCallId
                  ? `编辑靠港 · ${editingCall?.portName || editingCall?.portCode || '原记录'}`
                  : '新增靠港'
              }}
            </span>
            <span v-if="editingCallId" class="call-card-tip">
              修改 ETA/ETD/泊位/时间窗并保存后，系统将自动提醒受影响用户
            </span>
          </div>
          <ElForm
            ref="callFormRef"
            :model="callForm"
            :rules="callRules"
            class="call-form"
            label-position="top"
            @submit.prevent
          >
            <div class="call-form-grid">
              <ElFormItem class="is-span2" label="港口编码" prop="portCode">
                <ElInput v-model="callForm.portCode" placeholder="如 CNSHA" />
              </ElFormItem>
              <ElFormItem class="is-span2" label="港口名称" prop="portName">
                <ElInput v-model="callForm.portName" placeholder="如 上海港" />
              </ElFormItem>
              <ElFormItem class="is-span2" label="泊位" prop="berth">
                <ElInput v-model="callForm.berth" placeholder="选填" />
              </ElFormItem>
              <ElFormItem class="is-span3" label="到港时间 ETA" prop="eta">
                <ElDatePicker
                  v-model="callForm.eta"
                  format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选择到港时间"
                  style="width: 100%"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </ElFormItem>
              <ElFormItem class="is-span3" label="离港时间 ETD" prop="etd">
                <ElDatePicker
                  v-model="callForm.etd"
                  format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选择离港时间"
                  style="width: 100%"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </ElFormItem>
              <ElFormItem class="is-span6" label="配送时间窗">
                <ElDatePicker
                  v-model="callWindowRange"
                  end-placeholder="窗口结束"
                  format="YYYY-MM-DD HH:mm:ss"
                  range-separator="~"
                  start-placeholder="窗口开始"
                  style="width: 100%"
                  type="datetimerange"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </ElFormItem>
            </div>
          </ElForm>
          <div class="call-card-actions">
            <ElButton v-if="editingCallId" @click="exitCallEdit">
              取消修改
            </ElButton>
            <ElButton
              type="primary"
              v-access:code="'vessel:call:save'"
              :loading="callSaving"
              @click="saveCall"
            >
              {{ editingCallId ? '保存修改' : '新增靠港' }}
            </ElButton>
          </div>
        </div>

        <div class="call-card call-list-card">
          <div class="call-card-head">
            <span class="call-card-title">靠港列表（{{ callCount }}）</span>
            <span v-if="editingCallId" class="call-card-tip is-highlight">
              正在编辑下方高亮行，保存或取消后恢复
            </span>
          </div>
          <ElTable
            v-loading="callTableLoading"
            :data="callTableData"
            :row-class-name="callRowClass"
            border
            empty-text="暂无靠港计划，填写上方表单新增"
            max-height="280"
          >
            <ElTableColumn label="港口" min-width="120">
              <template #default="scope">
                <div class="call-cell-port">
                  <span>
                    {{ scope.row.portName || scope.row.portCode || '—' }}
                  </span>
                  <span
                    v-if="scope.row.portName && scope.row.portCode"
                    class="call-cell-sub"
                  >
                    {{ scope.row.portCode }}
                  </span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="泊位" min-width="70">
              <template #default="scope">
                {{ scope.row.berth || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="靠港时间" min-width="175">
              <template #default="scope">
                <div class="call-cell-time">
                  <span>到 {{ fmtMin(scope.row.eta) || '—' }}</span>
                  <span>离 {{ fmtMin(scope.row.etd) || '—' }}</span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="配送窗口" min-width="175">
              <template #default="scope">
                <span
                  v-if="
                    scope.row.deliveryWindowStart && scope.row.deliveryWindowEnd
                  "
                >
                  {{ fmtMin(scope.row.deliveryWindowStart) }} ~
                  {{ fmtMin(scope.row.deliveryWindowEnd) }}
                </span>
                <ElTag
                  v-else-if="scope.row.source === '2'"
                  size="small"
                  type="warning"
                >
                  待排产
                </ElTag>
                <span v-else>—</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="来源" min-width="88">
              <template #default="scope">
                <!-- 海员申报的靠港需要运营补配送时间窗并排产；
                     运营自建的多为已知船期的大客户，两者处理方式不同 -->
                <ElTag
                  v-if="scope.row.source === '2'"
                  size="small"
                  type="warning"
                >
                  海员申报
                </ElTag>
                <ElTag v-else effect="plain" size="small" type="info">
                  运营维护
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" min-width="80">
              <template #default="scope">
                <ElTag
                  :type="callStatusMeta[scope.row.status]?.type ?? 'info'"
                  size="small"
                >
                  {{
                    callStatusMeta[scope.row.status]?.label ?? scope.row.status
                  }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" min-width="120">
              <template #default="scope">
                <ElButton
                  link
                  type="primary"
                  v-access:code="'vessel:call:update'"
                  @click="editCall(scope.row)"
                >
                  修改
                </ElButton>
                <ElButton link type="warning" @click="viewImpact(scope.row)">
                  影响面
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </ElDialog>

      <!-- 变更影响面 -->
      <ElDialog
        v-model="impactDialogVisible"
        title="靠港计划变更影响面"
        width="520px"
      >
        <ElAlert
          :title="`未完成订单 ${impactData.orderCount ?? 0} 单、共享购物车 ${impactData.sharedCartCount ?? 0} 个、拣货波次 ${impactData.waveCount ?? 0} 个`"
          :type="(impactData.orderCount ?? 0) > 0 ? 'warning' : 'success'"
          :closable="false"
          class="mb10"
        />
        <div v-if="(impactData.orderNos ?? []).length > 0" class="mb10">
          <div style="margin-bottom: 4px; font-weight: bold">
            涉及订单（前 10）
          </div>
          <div style="color: #606266">
            {{ (impactData.orderNos ?? []).join('、') }}
          </div>
        </div>
        <div v-if="(impactData.waveNos ?? []).length > 0">
          <div style="margin-bottom: 4px; font-weight: bold">
            涉及波次（前 10）
          </div>
          <div style="color: #606266">
            {{ (impactData.waveNos ?? []).join('、') }}
          </div>
        </div>
        <div style="margin-top: 8px; color: #909399">
          修改
          ETA/ETD/泊位/时间窗并保存后，系统将自动站内信提醒受影响用户重新确认配送安排。
        </div>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
/* 靠港计划弹窗：上下文条 + 录入卡片 + 列表卡片的三段式结构 */
.call-context {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  margin-bottom: 14px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.call-context-name {
  display: flex;
  gap: 8px;
  align-items: center;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.call-context-label {
  padding: 1px 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
}

.call-context-sub {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.call-card {
  padding: 14px 16px 16px;
  margin-bottom: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.call-list-card {
  margin-bottom: 0;
}

.call-card-head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  margin-bottom: 14px;
}

.call-card-title {
  position: relative;
  padding-left: 10px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.call-card-title::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 14px;
  content: '';
  background: var(--el-color-primary);
  border-radius: 2px;
  transform: translateY(-50%);
}

.call-card-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.call-card-tip.is-highlight {
  color: var(--el-color-primary);
}

.call-form-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 0 16px;
}

.call-form-grid :deep(.el-form-item) {
  margin-bottom: 14px;
}

.is-span2 {
  grid-column: span 2;
}

.is-span3 {
  grid-column: span 3;
}

.is-span6 {
  grid-column: span 6;
}

.call-card-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 编辑中的靠港行高亮，与上方表单卡片联动 */
.call-list-card :deep(tr.row-editing > td.el-table__cell) {
  background-color: var(--el-color-primary-light-9) !important;
}

.call-cell-port {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.call-cell-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.call-cell-time {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
  white-space: nowrap;
}
</style>
