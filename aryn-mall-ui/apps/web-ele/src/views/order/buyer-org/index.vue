<script lang="ts" setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
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
  ElTag,
} from 'element-plus';

import {
  addBuyerOrgMember,
  auditBuyerOrg,
  auditBuyerOrgApply,
  batchAuditBuyerOrg,
  createBuyerOrg,
  createBuyerOrgInviteCode,
  getBuyerOrgApplyPage,
  getBuyerOrgDetail,
  getBuyerOrgMembers,
  getBuyerOrgPage,
  removeBuyerOrgMember,
  updateBuyerOrg,
} from '#/api/order/buyer-org';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const activeTab = ref<'applies' | 'orgs'>('orgs');
const loading = ref(false);
const applyLoading = ref(false);

const orgState = reactive({
  page: { currentPage: 1, pageSize: 10, total: 0 },
  query: {
    orgName: '',
    orgType: '',
    auditStatus: '',
    status: '',
  },
  rows: [] as any[],
  selected: [] as any[],
});

const applyState = reactive({
  page: { currentPage: 1, pageSize: 10, total: 0 },
  query: { status: '0', orgName: '', applyType: '' },
  rows: [] as any[],
});

const orgTypeOptions = [
  { label: '企业', value: '1' },
  { label: '工厂', value: '2' },
  { label: '园区餐厅', value: '3' },
];
const auditOptions = [
  { label: '待审核', value: '0' },
  { label: '已通过', value: '1' },
  { label: '已驳回', value: '2' },
  { label: '已停用', value: '3' },
];
const statusOptions = [
  { label: '正常', value: '1' },
  { label: '停用', value: '0' },
];

const orgTypeText = (value?: string) =>
  orgTypeOptions.find((item) => item.value === value)?.label ?? value ?? '-';
const auditMeta = (value?: string) => {
  if (value === '1') return { label: '已通过', type: 'success' as const };
  if (value === '2') return { label: '已驳回', type: 'danger' as const };
  if (value === '3') return { label: '已停用', type: 'info' as const };
  return { label: '待审核', type: 'warning' as const };
};
const applyStatusText = (value?: string) => {
  if (value === '1') return '已通过';
  if (value === '2') return '已驳回';
  if (value === '3') return '已取消';
  return '待审核';
};
const applyStatusType = (value?: string) => {
  if (value === '1') return 'success';
  if (value === '2') return 'danger';
  if (value === '3') return 'info';
  return 'warning';
};
const memberRoleText = (value?: string) => {
  if (value === '1') return '厂长/管理员';
  if (value === '2') return '采购员';
  if (value === '3') return '业务员';
  if (value === '4') return '查看者';
  return value ?? '-';
};

async function loadOrgs() {
  loading.value = true;
  try {
    const data = await getBuyerOrgPage({
      current: orgState.page.currentPage,
      size: orgState.page.pageSize,
      ...orgState.query,
    });
    orgState.rows = data.records ?? [];
    orgState.page.total = data.total ?? 0;
  } finally {
    loading.value = false;
  }
}

async function loadApplies() {
  applyLoading.value = true;
  try {
    const data = await getBuyerOrgApplyPage({
      current: applyState.page.currentPage,
      size: applyState.page.pageSize,
      ...applyState.query,
    });
    applyState.rows = data.records ?? [];
    applyState.page.total = data.total ?? 0;
  } finally {
    applyLoading.value = false;
  }
}

function resetOrgQuery() {
  Object.assign(orgState.query, {
    orgName: '',
    orgType: '',
    auditStatus: '',
    status: '',
  });
  orgState.page.currentPage = 1;
  loadOrgs();
}

function resetApplyQuery() {
  Object.assign(applyState.query, { status: '0', orgName: '', applyType: '' });
  applyState.page.currentPage = 1;
  loadApplies();
}

function onOrgSelection(rows: any[]) {
  orgState.selected = rows;
}

function onTabChange(name: number | string) {
  if (name === 'applies') loadApplies();
  else loadOrgs();
}

const AUDIT_ACTION_TEXT: Record<string, string> = { 1: '通过', 3: '停用' };
const auditActionText = (auditStatus: string) =>
  AUDIT_ACTION_TEXT[auditStatus] ?? '驳回';

async function auditOne(row: any, auditStatus: string) {
  const actionText = auditActionText(auditStatus);
  const remark = await ElMessageBox.prompt(
    `确认${actionText}「${row.orgName}」？`,
    `${actionText}采购主体`,
    {
      inputPlaceholder: '审核意见（驳回时建议填写）',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    },
  )
    .then((res) => res.value)
    .catch(() => null);
  if (remark === null) return;
  await auditBuyerOrg(row.id, { auditRemark: remark, auditStatus });
  ElMessage.success(`已${actionText}`);
  loadOrgs();
}

async function batchAudit(auditStatus: string) {
  if (orgState.selected.length === 0) {
    ElMessage.warning('请先勾选主体');
    return;
  }
  const actionText = `批量${auditActionText(auditStatus)}`;
  try {
    await ElMessageBox.confirm(
      `确认${actionText}选中的 ${orgState.selected.length} 个主体？`,
      actionText,
      { type: 'warning' },
    );
  } catch {
    return;
  }
  const count = await batchAuditBuyerOrg({
    ids: orgState.selected.map((row) => row.id),
    auditStatus,
  });
  ElMessage.success(`已处理 ${count} 个主体`);
  loadOrgs();
}

// 详情 / 成员
const detailVisible = ref(false);
const detail = ref<any>({});
const members = ref<any[]>([]);
const membersLoading = ref(false);

async function openDetail(row: any) {
  detailVisible.value = true;
  detail.value = {};
  members.value = [];
  detail.value = await getBuyerOrgDetail(row.id);
  membersLoading.value = true;
  try {
    members.value = await getBuyerOrgMembers(row.id);
  } finally {
    membersLoading.value = false;
  }
}

const inviteVisible = ref(false);
const inviteCode = ref('');
async function generateInvite(row: any) {
  const data = await createBuyerOrgInviteCode(row.id, { expireHours: 168 });
  inviteCode.value = data.code;
  inviteVisible.value = true;
}

async function removeMember(member: any) {
  try {
    await ElMessageBox.confirm(
      `确认移除成员「${member.nickname || member.userId}」？`,
      '移除成员',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await removeBuyerOrgMember(detail.value.id, member.userId);
  members.value = members.value.filter((item) => item.userId !== member.userId);
  ElMessage.success('已移除');
}

const memberDialogVisible = ref(false);
const memberForm = reactive({
  userId: '',
  memberRole: '2',
  canEdit: '1',
  canConfirm: '0',
});
async function addMember() {
  if (!memberForm.userId) {
    ElMessage.warning('请填写商城用户ID');
    return;
  }
  await addBuyerOrgMember(detail.value.id, { ...memberForm });
  ElMessage.success('已添加/调整成员');
  memberDialogVisible.value = false;
  members.value = await getBuyerOrgMembers(detail.value.id);
}

// 新建/编辑
const editVisible = ref(false);
const editId = ref('');
const editForm = reactive<any>({
  orgName: '',
  orgType: '2',
  province: '',
  city: '',
  district: '',
  address: '',
  contactName: '',
  contactPhone: '',
  ownerUserId: '',
  remark: '',
});
const editTitle = computed(() =>
  editId.value ? '编辑采购主体' : '新建采购主体',
);

function openCreate() {
  editId.value = '';
  Object.keys(editForm).forEach((key) => (editForm[key] = ''));
  editForm.orgType = '2';
  editVisible.value = true;
}

function openEdit(row: any) {
  editId.value = row.id;
  Object.assign(editForm, {
    orgName: row.orgName ?? '',
    orgType: row.orgType ?? '2',
    province: row.province ?? '',
    city: row.city ?? '',
    district: row.district ?? '',
    address: row.address ?? '',
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    ownerUserId: row.ownerUserId ?? '',
    remark: row.remark ?? '',
  });
  editVisible.value = true;
}

async function submitEdit() {
  if (!editForm.orgName?.trim()) {
    ElMessage.warning('主体名称不能为空');
    return;
  }
  await (editId.value
    ? updateBuyerOrg(editId.value, editForm)
    : createBuyerOrg(editForm));
  ElMessage.success(editId.value ? '已保存' : '已创建');
  editVisible.value = false;
  loadOrgs();
}

// 申请单审核
async function auditApply(row: any, approved: boolean) {
  const remark = await ElMessageBox.prompt(
    `${approved ? '通过' : '驳回'}申请「${row.orgName || row.applyNo}」`,
    '申请单审核',
    {
      inputPlaceholder: approved ? '审核意见（可选）' : '驳回原因（建议填写）',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    },
  )
    .then((res) => res.value)
    .catch(() => null);
  if (remark === null) return;
  await auditBuyerOrgApply(row.id, { approved, auditRemark: remark });
  ElMessage.success('申请单已处理');
  loadApplies();
  loadOrgs();
}

useKeepAliveRefresh(() => {
  if (activeTab.value === 'applies') {
    loadApplies();
  } else {
    loadOrgs();
  }
});

onMounted(() => {
  loadOrgs();
});
</script>

<template>
  <div class="buyer-org-page">
    <ElTabs v-model="activeTab" @tab-change="onTabChange">
      <ElTabPane label="采购主体" name="orgs">
        <div class="filter-bar">
          <ElInput
            v-model="orgState.query.orgName"
            clearable
            placeholder="主体名称"
            style="width: 180px"
            @keyup.enter="((orgState.page.currentPage = 1), loadOrgs())"
          />
          <ElSelect
            v-model="orgState.query.orgType"
            clearable
            placeholder="主体类型"
            style="width: 130px"
          >
            <ElOption
              v-for="item in orgTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
          <ElSelect
            v-model="orgState.query.auditStatus"
            clearable
            placeholder="审核状态"
            style="width: 130px"
          >
            <ElOption
              v-for="item in auditOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
          <ElSelect
            v-model="orgState.query.status"
            clearable
            placeholder="启用状态"
            style="width: 120px"
          >
            <ElOption
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
          <ElButton
            type="primary"
            @click="((orgState.page.currentPage = 1), loadOrgs())"
          >
            查询
          </ElButton>
          <ElButton @click="resetOrgQuery">重置</ElButton>
          <ElButton
            v-access:code="'order:buyerorg:create'"
            type="primary"
            plain
            @click="openCreate"
          >
            新建主体
          </ElButton>
          <ElButton
            v-access:code="'order:buyerorg:audit'"
            type="success"
            plain
            :disabled="orgState.selected.length === 0"
            @click="batchAudit('1')"
          >
            批量通过
          </ElButton>
          <ElButton
            v-access:code="'order:buyerorg:audit'"
            type="warning"
            plain
            :disabled="orgState.selected.length === 0"
            @click="batchAudit('2')"
          >
            批量驳回
          </ElButton>
          <ElButton
            v-access:code="'order:buyerorg:audit'"
            type="danger"
            plain
            :disabled="orgState.selected.length === 0"
            @click="batchAudit('3')"
          >
            批量停用
          </ElButton>
        </div>

        <ElTable
          v-loading="loading"
          :data="orgState.rows"
          border
          row-key="id"
          @selection-change="onOrgSelection"
        >
          <ElTableColumn type="selection" width="46" />
          <ElTableColumn label="主体名称" min-width="180">
            <template #default="{ row }">
              <div class="org-name">{{ row.orgName }}</div>
              <div class="org-sub">{{ row.orgCode || row.id }}</div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="类型" width="100">
            <template #default="{ row }">
              {{ orgTypeText(row.orgType) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="地区" min-width="140">
            <template #default="{ row }">
              {{
                [row.province, row.city, row.district]
                  .filter(Boolean)
                  .join(' / ') || '-'
              }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="联系人" min-width="120">
            <template #default="{ row }">
              {{ row.contactName || '-' }}
              <span v-if="row.contactPhoneMasked" class="org-sub">
                {{ row.contactPhoneMasked }}
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="成员" width="80">
            <template #default="{ row }">{{ row.memberCount ?? 0 }}</template>
          </ElTableColumn>
          <ElTableColumn label="审核状态" width="100">
            <template #default="{ row }">
              <ElTag :type="auditMeta(row.auditStatus).type" effect="light">
                {{ auditMeta(row.auditStatus).label }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="启用" width="80">
            <template #default="{ row }">
              <ElTag
                :type="row.status === '0' ? 'info' : 'success'"
                effect="plain"
              >
                {{ row.status === '0' ? '停用' : '正常' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="创建时间" width="170">
            <template #default="{ row }">{{ row.createTime || '-' }}</template>
          </ElTableColumn>
          <ElTableColumn label="操作" fixed="right" width="230">
            <template #default="{ row }">
              <ElButton link type="primary" @click="openDetail(row)">
                详情
              </ElButton>
              <ElButton
                v-access:code="'order:buyerorg:edit'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑
              </ElButton>
              <ElButton
                v-access:code="'order:buyerorg:audit'"
                link
                type="success"
                @click="auditOne(row, '1')"
              >
                通过
              </ElButton>
              <ElButton
                v-access:code="'order:buyerorg:audit'"
                link
                type="warning"
                @click="auditOne(row, '2')"
              >
                驳回
              </ElButton>
              <ElButton
                v-access:code="'order:buyerorg:audit'"
                link
                type="danger"
                @click="auditOne(row, '3')"
              >
                停用
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <Pagination
          v-model:current="orgState.page.currentPage"
          v-model:size="orgState.page.pageSize"
          :total="orgState.page.total"
          @change="loadOrgs"
        />
      </ElTabPane>

      <ElTabPane label="申请单" name="applies">
        <div class="filter-bar">
          <ElInput
            v-model="applyState.query.orgName"
            clearable
            placeholder="主体名称"
            style="width: 180px"
            @keyup.enter="((applyState.page.currentPage = 1), loadApplies())"
          />
          <ElSelect
            v-model="applyState.query.status"
            clearable
            placeholder="申请状态"
            style="width: 130px"
          >
            <ElOption label="待审核" value="0" />
            <ElOption label="已通过" value="1" />
            <ElOption label="已驳回" value="2" />
          </ElSelect>
          <ElSelect
            v-model="applyState.query.applyType"
            clearable
            placeholder="申请类型"
            style="width: 140px"
          >
            <ElOption label="创建主体" value="1" />
            <ElOption label="加入主体" value="2" />
          </ElSelect>
          <ElButton
            type="primary"
            @click="((applyState.page.currentPage = 1), loadApplies())"
          >
            查询
          </ElButton>
          <ElButton @click="resetApplyQuery">重置</ElButton>
        </div>

        <ElTable
          v-loading="applyLoading"
          :data="applyState.rows"
          border
          row-key="id"
        >
          <ElTableColumn label="申请单号" width="180">
            <template #default="{ row }">{{ row.applyNo || row.id }}</template>
          </ElTableColumn>
          <ElTableColumn label="类型" width="110">
            <template #default="{ row }">
              {{ row.applyType === '1' ? '创建主体' : '加入主体' }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="orgName" label="主体名称" min-width="180" />
          <ElTableColumn label="主体类型" width="110">
            <template #default="{ row }">
              {{ orgTypeText(row.orgType) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="关联主体" min-width="160">
            <template #default="{ row }">
              {{ row.matchedOrgName || row.matchedOrgId || '-' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="100">
            <template #default="{ row }">
              <ElTag :type="applyStatusType(row.status)" effect="light">
                {{ applyStatusText(row.status) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="申请时间" width="170">
            <template #default="{ row }">{{ row.createTime || '-' }}</template>
          </ElTableColumn>
          <ElTableColumn label="操作" fixed="right" width="170">
            <template #default="{ row }">
              <ElButton
                v-access:code="'order:buyerorg:audit'"
                link
                type="success"
                :disabled="row.status !== '0'"
                @click="auditApply(row, true)"
              >
                通过
              </ElButton>
              <ElButton
                v-access:code="'order:buyerorg:audit'"
                link
                type="danger"
                :disabled="row.status !== '0'"
                @click="auditApply(row, false)"
              >
                驳回
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <Pagination
          v-model:current="applyState.page.currentPage"
          v-model:size="applyState.page.pageSize"
          :total="applyState.page.total"
          @change="loadApplies"
        />
      </ElTabPane>
    </ElTabs>

    <ElDialog v-model="detailVisible" title="采购主体详情" width="860px">
      <div v-if="detail.id" class="detail-grid">
        <div><span class="label">主体名称：</span>{{ detail.orgName }}</div>
        <div>
          <span class="label">主体编码：</span>{{ detail.orgCode || '-' }}
        </div>
        <div>
          <span class="label">主体类型：</span>{{ orgTypeText(detail.orgType) }}
        </div>
        <div>
          <span class="label">地区：</span
          >{{
            [detail.province, detail.city, detail.district]
              .filter(Boolean)
              .join(' / ') || '-'
          }}
        </div>
        <div>
          <span class="label">详细地址：</span>{{ detail.address || '-' }}
        </div>
        <div>
          <span class="label">联系人：</span>{{ detail.contactName || '-' }}
        </div>
        <div>
          <span class="label">联系电话：</span
          >{{ detail.contactPhoneMasked || detail.contactPhone || '-' }}
        </div>
        <div>
          <span class="label">审核状态：</span
          >{{ auditMeta(detail.auditStatus).label }}
        </div>
        <div>
          <span class="label">审核意见：</span>{{ detail.auditRemark || '-' }}
        </div>
      </div>

      <div class="member-head">
        <span>成员</span>
        <div>
          <ElButton
            v-access:code="'order:buyerorg:invite'"
            size="small"
            type="primary"
            plain
            @click="generateInvite(detail)"
          >
            生成邀请码
          </ElButton>
          <ElButton
            v-access:code="'order:buyerorg:member'"
            size="small"
            @click="memberDialogVisible = true"
          >
            添加成员
          </ElButton>
        </div>
      </div>
      <ElTable v-loading="membersLoading" :data="members" border size="small">
        <ElTableColumn label="成员" min-width="140">
          <template #default="{ row }">
            {{ row.nickname || row.userId }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="手机号" width="130">
          <template #default="{ row }">{{ row.phoneMasked || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="角色" width="110">
          <template #default="{ row }">
            {{ memberRoleText(row.memberRole) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="可编辑" width="90">
          <template #default="{ row }">
            {{ row.canEdit === '1' ? '是' : '否' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="可确认" width="90">
          <template #default="{ row }">
            {{ row.canConfirm === '1' ? '是' : '否' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="加入时间" width="170">
          <template #default="{ row }">{{ row.joinTime || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="90">
          <template #default="{ row }">
            <ElButton
              v-access:code="'order:buyerorg:member'"
              link
              type="danger"
              @click="removeMember(row)"
            >
              移除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElDialog>

    <ElDialog v-model="inviteVisible" title="邀请码" width="420px">
      <div class="invite-code">{{ inviteCode }}</div>
      <div class="org-sub">有效期 7 天，同主体只保留一个有效码。</div>
    </ElDialog>

    <ElDialog v-model="memberDialogVisible" title="添加成员" width="460px">
      <ElForm label-width="100px">
        <ElFormItem label="商城用户ID" required>
          <ElInput v-model="memberForm.userId" placeholder="用户ID" />
        </ElFormItem>
        <ElFormItem label="成员角色">
          <ElSelect v-model="memberForm.memberRole" style="width: 100%">
            <ElOption label="厂长/管理员" value="1" />
            <ElOption label="采购员" value="2" />
            <ElOption label="业务员" value="3" />
            <ElOption label="查看者" value="4" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="可编辑">
          <ElSelect v-model="memberForm.canEdit" style="width: 100%">
            <ElOption label="是" value="1" />
            <ElOption label="否" value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="可确认">
          <ElSelect v-model="memberForm.canConfirm" style="width: 100%">
            <ElOption label="是" value="1" />
            <ElOption label="否" value="0" />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <div class="dialog-actions">
        <ElButton @click="memberDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="addMember">确定</ElButton>
      </div>
    </ElDialog>

    <ElDialog v-model="editVisible" :title="editTitle" width="640px">
      <ElForm label-width="120px">
        <ElFormItem label="主体名称" required>
          <ElInput
            v-model="editForm.orgName"
            placeholder="工厂/企业/园区餐厅名称"
          />
        </ElFormItem>
        <ElFormItem label="主体类型">
          <ElSelect v-model="editForm.orgType" style="width: 100%">
            <ElOption
              v-for="item in orgTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="省/市/区">
          <div class="inline-fields">
            <ElInput v-model="editForm.province" placeholder="省" />
            <ElInput v-model="editForm.city" placeholder="市" />
            <ElInput v-model="editForm.district" placeholder="区/县" />
          </div>
        </ElFormItem>
        <ElFormItem label="详细地址">
          <ElInput v-model="editForm.address" placeholder="详细地址" />
        </ElFormItem>
        <ElFormItem label="联系人">
          <ElInput v-model="editForm.contactName" placeholder="联系人" />
        </ElFormItem>
        <ElFormItem label="联系电话">
          <ElInput v-model="editForm.contactPhone" placeholder="联系电话" />
        </ElFormItem>
        <ElFormItem label="厂长用户ID">
          <ElInput
            v-model="editForm.ownerUserId"
            placeholder="填写后自动建成员行"
          />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="editForm.remark" :rows="2" type="textarea" />
        </ElFormItem>
      </ElForm>
      <div class="dialog-actions">
        <ElButton @click="editVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitEdit">保存</ElButton>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
.buyer-org-page {
  padding: 16px;
  background: var(--el-bg-color);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.org-name {
  font-weight: 600;
}

.org-sub {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
  padding: 4px 0 16px;
}

.detail-grid .label {
  color: var(--el-text-color-secondary);
}

.member-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0;
  font-weight: 600;
}

.invite-code {
  padding: 16px;
  font-family: monospace;
  font-size: 24px;
  font-weight: 700;
  text-align: center;
  letter-spacing: 4px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.inline-fields {
  display: flex;
  gap: 8px;
  width: 100%;
}

.dialog-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
