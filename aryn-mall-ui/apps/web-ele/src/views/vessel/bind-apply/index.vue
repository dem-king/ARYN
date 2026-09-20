<script lang="ts" setup>
/**
 * 船舶绑定申请审核。
 *
 * 业务背景：销售业务员地推时经常遇到「船还没录入系统」的情况，
 * 而用户比运营更早接触到船。因此审核通过时支持三种落地方式：
 *   1. 选择系统内已有船舶；
 *   2. 填写新船名称——服务端先建船再绑定（覆盖上述场景）；
 *   3. 都不填——服务端按申请船名精确匹配在营船舶，匹配不到会返回可执行的提示。
 */
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
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  approveBindApply,
  getBindApplyPage,
  getVesselPage,
  rejectBindApply,
} from '#/api/vessel/vessel';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { status: '1', vesselName: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const statusLabel: Record<string, string> = {
  '1': '待审核',
  '2': '已通过',
  '3': '已驳回',
  '4': '已撤回',
};
const statusTag: Record<string, string> = {
  '1': 'warning',
  '2': 'success',
  '3': 'danger',
  '4': 'info',
};
const roleLabel: Record<string, string> = {
  '2': '普通船员',
  '4': '业务员',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getBindApplyPage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      status: state.query.status,
      vesselName: state.query.vesselName,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const resetQuery = () => {
  state.query.vesselName = '';
  state.page.currentPage = 1;
  initPage();
};

// ---------------------------------------------------------------------------
// 审核通过
// ---------------------------------------------------------------------------

const approveVisible = ref(false);
const approving = ref(false);
const currentApply = ref<any>({});
/** existing=选已有船 new=建新船 auto=按船名自动匹配 */
const approveMode = ref('auto');
const approveForm = reactive<any>({
  matchedVesselId: '',
  newVesselName: '',
  newVesselImo: '',
  newVesselType: '5',
  memberRole: '',
  auditRemark: '',
});
const vesselOptions = ref<any[]>([]);

const openApprove = async (row: any) => {
  currentApply.value = row;
  approveMode.value = 'auto';
  Object.assign(approveForm, {
    matchedVesselId: '',
    newVesselName: row.applyVesselName ?? '',
    newVesselImo: row.applyVesselImo ?? '',
    newVesselType: '5',
    memberRole: row.applyRole ?? '2',
    auditRemark: '',
  });
  approveVisible.value = true;
  const response = await getVesselPage({ current: 1, size: 200 });
  vesselOptions.value = response.records ?? [];
};

const submitApprove = () => {
  const payload: any = {
    auditRemark: approveForm.auditRemark,
    memberRole: approveForm.memberRole,
  };
  if (approveMode.value === 'existing') {
    if (!approveForm.matchedVesselId) {
      ElMessage.warning('请选择要绑定的船舶');
      return;
    }
    payload.matchedVesselId = approveForm.matchedVesselId;
  } else if (approveMode.value === 'new') {
    if (!approveForm.newVesselName) {
      ElMessage.warning('请填写新船名称');
      return;
    }
    payload.newVesselName = approveForm.newVesselName;
    payload.newVesselImo = approveForm.newVesselImo;
    payload.newVesselType = approveForm.newVesselType;
  }
  // auto 模式两个字段都不传，由服务端按申请船名精确匹配

  approving.value = true;
  approveBindApply(currentApply.value.id, payload)
    .then(() => {
      ElMessage.success('审核通过');
      approveVisible.value = false;
      initPage();
    })
    .catch(() => {})
    .finally(() => {
      approving.value = false;
    });
};

// ---------------------------------------------------------------------------
// 驳回
// ---------------------------------------------------------------------------

const submitReject = (row: any) => {
  ElMessageBox.prompt('驳回后申请人可重新提交，请填写驳回原因', '驳回申请', {
    confirmButtonText: '确认驳回',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：船名无法核实，请补充 IMO 或呼号',
    inputValidator: (value: string) =>
      (value && value.trim().length > 0) || '驳回必须填写原因',
  })
    .then(({ value }) =>
      rejectBindApply(row.id, { auditRemark: value }).then(() => {
        ElMessage.success('已驳回');
        initPage();
      }),
    )
    .catch(() => {});
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true" :model="state.query">
        <ElFormItem label="状态">
          <ElSelect v-model="state.query.status" style="width: 140px">
            <ElOption label="待审核" value="1" />
            <ElOption label="已通过" value="2" />
            <ElOption label="已驳回" value="3" />
            <ElOption label="已撤回" value="4" />
            <ElOption label="全部" value="" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="船舶名称">
          <ElInput
            v-model="state.query.vesselName"
            clearable
            placeholder="按申请填写船名搜索"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage">搜索</ElButton>
          <ElButton @click="resetQuery">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="applyNo" label="申请单号" min-width="180" />
        <ElTableColumn label="申请人" min-width="150">
          <template #default="scope">
            <div>{{ scope.row.userNickname ?? scope.row.userId }}</div>
            <div style="font-size: 12px; color: #909399">
              {{ scope.row.userPhone ?? '—' }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="身份" width="100" align="center">
          <template #default="scope">
            {{ roleLabel[scope.row.applyRole] ?? scope.row.applyRole }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="applyVesselName"
          label="申请船名"
          min-width="130"
        />
        <ElTableColumn prop="applyVesselImo" label="IMO/呼号" width="120" />
        <ElTableColumn prop="applyPortName" label="常靠港" width="110" />
        <ElTableColumn prop="realName" label="姓名" width="100" />
        <ElTableColumn prop="phone" label="联系电话" width="130" />
        <ElTableColumn label="状态" width="90" align="center">
          <template #default="scope">
            <ElTag :type="(statusTag[scope.row.status] as any) ?? 'info'">
              {{ statusLabel[scope.row.status] ?? scope.row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="matchedVesselName"
          label="绑定船舶"
          min-width="120"
        />
        <ElTableColumn prop="auditRemark" label="审核意见" min-width="150" />
        <ElTableColumn prop="createTime" label="提交时间" width="170" />
        <ElTableColumn label="操作" width="150" align="center" fixed="right">
          <template #default="scope">
            <template v-if="scope.row.status === '1'">
              <ElButton
                v-access:code="'vessel:bindapply:audit'"
                link
                type="primary"
                @click="openApprove(scope.row)"
              >
                通过
              </ElButton>
              <ElButton
                v-access:code="'vessel:bindapply:audit'"
                link
                type="danger"
                @click="submitReject(scope.row)"
              >
                驳回
              </ElButton>
            </template>
            <span v-else style="color: #909399">已处理</span>
          </template>
        </ElTableColumn>
      </ElTable>

      <Pagination
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />

      <!-- 审核通过 -->
      <ElDialog v-model="approveVisible" title="审核通过" width="620px">
        <ElAlert
          :title="`申请船名：${currentApply.applyVesselName ?? ''} 申请人：${currentApply.userNickname ?? currentApply.userId ?? ''}`"
          :closable="false"
          class="mb10"
        />

        <ElRadioGroup v-model="approveMode" class="mb10">
          <ElRadio value="existing">绑定已有船舶</ElRadio>
          <ElRadio value="new">新建船舶后绑定</ElRadio>
          <ElRadio value="auto">按船名自动匹配</ElRadio>
        </ElRadioGroup>

        <ElForm label-width="110px">
          <ElFormItem
            v-if="approveMode === 'existing'"
            label="选择船舶"
            required
          >
            <ElSelect
              v-model="approveForm.matchedVesselId"
              filterable
              placeholder="请选择系统内已有船舶"
              style="width: 100%"
            >
              <ElOption
                v-for="vessel in vesselOptions"
                :key="vessel.id"
                :label="vessel.vesselName"
                :value="vessel.id"
              />
            </ElSelect>
          </ElFormItem>

          <template v-if="approveMode === 'new'">
            <ElFormItem label="新船名称" required>
              <ElInput v-model="approveForm.newVesselName" />
            </ElFormItem>
            <ElFormItem label="IMO 编号">
              <ElInput v-model="approveForm.newVesselImo" />
            </ElFormItem>
          </template>

          <ElAlert
            v-if="approveMode === 'auto'"
            title="将按申请船名在系统内精确匹配在营船舶；匹配不到会提示，需改用前两种方式。"
            type="info"
            :closable="false"
            class="mb10"
          />

          <ElFormItem label="绑定角色">
            <ElSelect v-model="approveForm.memberRole" style="width: 100%">
              <ElOption label="普通船员" value="2" />
              <ElOption label="采购确认人" value="3" />
              <ElOption label="业务员" value="4" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="审核意见">
            <ElInput
              v-model="approveForm.auditRemark"
              type="textarea"
              :rows="2"
            />
          </ElFormItem>
        </ElForm>

        <template #footer>
          <ElButton @click="approveVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="approving" @click="submitApprove">
            确认通过
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
