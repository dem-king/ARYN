<script lang="ts" setup>
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
  ElAlert,
  ElButton,
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
const callVessel = ref<any>({});
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
const editingCallId = ref('');

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

const openCalls = (row: any) => {
  callVessel.value = row;
  callDialogVisible.value = true;
  getVesselCalls(row.id).then((list) => {
    callTableData.value = list;
  });
};

const saveCall = () => {
  if (!callForm.portCode || !callForm.eta || !callForm.etd) {
    ElMessage.warning('港口、ETA 和 ETD 必填');
    return;
  }
  const payload = { ...callForm, vesselId: callVessel.value.id };
  const request = editingCallId.value
    ? updateVesselCall(editingCallId.value, payload)
    : addVesselCall(callVessel.value.id, payload);
  request
    .then(() => {
      ElMessage.success('保存成功');
      editingCallId.value = '';
      return getVesselCalls(callVessel.value.id);
    })
    .then((list) => {
      callTableData.value = list;
    })
    .catch(() => {});
};

const editCall = (row: any) => {
  editingCallId.value = row.id;
  Object.assign(callForm, row);
};

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

const callStatusLabel: Record<string, string> = {
  '1': '计划中',
  '2': '靠泊中',
  '3': '已完成',
  '4': '已取消',
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

      <!-- 靠港计划 -->
      <ElDialog v-model="callDialogVisible" title="靠港计划" width="860px">
        <ElAlert
          :title="`船舶：${callVessel.vesselName ?? ''}`"
          :closable="false"
          class="mb10"
        />
        <ElForm :inline="true" class="mb10">
          <ElFormItem label="港口编码">
            <ElInput v-model="callForm.portCode" style="width: 110px" />
          </ElFormItem>
          <ElFormItem label="港口名称">
            <ElInput v-model="callForm.portName" style="width: 120px" />
          </ElFormItem>
          <ElFormItem label="泊位">
            <ElInput v-model="callForm.berth" style="width: 110px" />
          </ElFormItem>
          <ElFormItem label="ETA">
            <ElInput
              v-model="callForm.eta"
              placeholder="2026-09-15 08:00:00"
              style="width: 190px"
            />
          </ElFormItem>
          <ElFormItem label="ETD">
            <ElInput
              v-model="callForm.etd"
              placeholder="2026-09-15 20:00:00"
              style="width: 190px"
            />
          </ElFormItem>
          <ElFormItem label="时间窗">
            <ElInput
              v-model="callForm.deliveryWindowStart"
              placeholder="开始"
              style="width: 170px"
            />
            <span> ~ </span>
            <ElInput
              v-model="callForm.deliveryWindowEnd"
              placeholder="结束"
              style="width: 170px"
            />
          </ElFormItem>
          <ElFormItem>
            <ElButton
              type="primary"
              v-access:code="'vessel:call:save'"
              @click="saveCall"
            >
              {{ editingCallId ? '保存修改' : '新增靠港' }}
            </ElButton>
          </ElFormItem>
        </ElForm>
        <ElTable :data="callTableData" border>
          <ElTableColumn prop="portName" label="港口" width="130" />
          <ElTableColumn prop="berth" label="泊位" width="110" />
          <ElTableColumn prop="eta" label="ETA" width="170" />
          <ElTableColumn prop="etd" label="ETD" width="170" />
          <ElTableColumn
            prop="deliveryWindowStart"
            label="配送窗口"
            width="220"
          >
            <template #default="scope">
              {{ scope.row.deliveryWindowStart }} ~
              {{ scope.row.deliveryWindowEnd }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="90">
            <template #default="scope">
              {{ callStatusLabel[scope.row.status] ?? scope.row.status }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="80">
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
