<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type {
  SalesStaffRecord,
  SalesStaffSavePayload,
  SalesStaffStatus,
} from '#/api/promotion/sales-staff';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { formatDateTime } from '@vben/utils';

import {
  CircleClose,
  Edit,
  Plus,
  RefreshLeft,
  SwitchButton,
} from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
  ElDatePicker,
  ElDialog,
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
  ElTreeSelect,
} from 'element-plus';

import {
  addObj,
  editObj,
  getById,
  getOptions,
  getPage,
  resign,
  updateStatus,
} from '#/api/promotion/sales-staff';
import { getTreeList } from '#/api/upms/dept';
import { getPage as getSysUserPage } from '#/api/upms/user';
import { getPage as getMemberPage } from '#/api/user/user-info';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

interface DeptNode {
  children?: DeptNode[];
  id: string;
  name: string;
}

/** 后台员工账号（upms/upms/user/page 精简字段） */
interface SysUserOption {
  deptId?: string;
  deptName?: string;
  id: string;
  nickname?: string;
  phone?: string;
  username?: string;
}

/** 小程序会员（mall-user/userinfo/page 精简字段） */
interface MemberOption {
  id: string;
  nickname?: string;
  phone?: string;
}

const { sales_staff_status } = useDict('sales_staff_status');

const loading = ref(false);
const showSearch = ref(true);
const deptData = ref<DeptNode[]>([]);
const state = reactive({
  queryParams: {
    staffName: '',
    phone: '',
    deptId: '',
    status: '' as '' | SalesStaffStatus,
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [] as SalesStaffRecord[],
});

const formatTime = (value?: string) => (value ? formatDateTime(value) : '—');
/** 比例 0-1 展示为百分比；为空表示跟随全局默认比例 */
const formatRate = (value?: null | number) =>
  value === null || value === undefined
    ? '默认比例'
    : `${(Number(value) * 100).toFixed(2)}%`;

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const loadDeptTree = () => {
  getTreeList()
    .then((response) => {
      deptData.value = response as DeptNode[];
    })
    .catch(() => {
      deptData.value = [];
    });
};

// ---------------------------------------------------------------------------
// 新增/编辑弹窗
// ---------------------------------------------------------------------------
interface StaffFormModel {
  commissionRate?: null | number;
  id?: string;
  memberUserId: string;
  remark: string;
  sysUserId: string;
}

const formRef = ref<FormInstance>();
const formState = reactive({
  visible: false,
  submitting: false,
  /** 编辑时回填的是员工快照，远程搜索前先塞一条同 ID 的占位选项保证回显 */
  employeeOptions: [] as SysUserOption[],
  employeeLoading: false,
  memberKeyword: '',
  memberOptions: [] as MemberOption[],
  memberLoading: false,
  form: {
    id: undefined,
    sysUserId: '',
    memberUserId: '',
    commissionRate: null,
    remark: '',
  } as StaffFormModel,
});

const employeeLabel = (option: SysUserOption) =>
  [option.nickname, option.username, option.phone].filter(Boolean).join(' / ');

const memberLabel = (option: MemberOption) =>
  [option.nickname, option.phone].filter(Boolean).join(' / ') || option.id;

/** 远程搜索后台员工账号（用户名/昵称/手机号） */
const remoteEmployee = async (keyword: string) => {
  formState.employeeLoading = true;
  try {
    const response = (await getSysUserPage({
      current: 1,
      size: 20,
      username: keyword || undefined,
    })) as { records?: SysUserOption[] };
    formState.employeeOptions = response?.records ?? [];
  } catch {
    formState.employeeOptions = [];
  } finally {
    formState.employeeLoading = false;
  }
};

/** 按手机号查询小程序会员（销售本人的会员账号） */
const searchMembers = async () => {
  const phone = formState.memberKeyword.trim();
  if (!phone) {
    ElMessage.warning('请输入手机号');
    return;
  }
  formState.memberLoading = true;
  try {
    const response = (await getMemberPage({
      current: 1,
      size: 20,
      phone,
    })) as { records?: MemberOption[] };
    formState.memberOptions = response?.records ?? [];
    if (formState.memberOptions.length === 0) {
      ElMessage.warning('未查询到该手机号对应的会员');
    }
  } catch {
    formState.memberOptions = [];
  } finally {
    formState.memberLoading = false;
  }
};

const openAdd = () => {
  formState.form = {
    id: undefined,
    sysUserId: '',
    memberUserId: '',
    commissionRate: null,
    remark: '',
  };
  formState.employeeOptions = [];
  formState.memberOptions = [];
  formState.memberKeyword = '';
  formState.visible = true;
  formRef.value?.clearValidate();
};

const openEdit = async (row: SalesStaffRecord) => {
  formState.visible = true;
  formRef.value?.clearValidate();
  formState.memberKeyword = '';
  formState.memberOptions = [
    {
      id: row.memberUserId,
      nickname: row.memberNickname || `${row.staffName ?? ''}的会员账号`,
    },
  ];
  formState.employeeOptions = [
    {
      id: row.sysUserId,
      nickname: row.staffName,
      phone: row.phone,
    },
  ];
  formState.form = {
    id: row.id,
    sysUserId: row.sysUserId,
    memberUserId: row.memberUserId,
    commissionRate: row.commissionRate ?? null,
    remark: row.remark ?? '',
  };
  // 用详情接口补齐比例/备注等分页列表可能裁剪的字段
  try {
    const detail = await getById(row.id);
    if (detail) {
      formState.form.commissionRate = detail.commissionRate ?? null;
      formState.form.memberUserId = detail.memberUserId;
      formState.form.remark = detail.remark ?? '';
      if (detail.memberUserId !== row.memberUserId) {
        formState.memberOptions = [
          {
            id: detail.memberUserId,
            nickname:
              detail.memberNickname || `${detail.staffName ?? ''}的会员账号`,
          },
        ];
      }
    }
  } catch {
    // 详情失败时保留列表行数据，不阻断编辑入口
  }
};

const submitForm = async () => {
  await formRef.value?.validate();
  formState.submitting = true;
  const payload: SalesStaffSavePayload = {
    id: formState.form.id,
    sysUserId: formState.form.sysUserId,
    memberUserId: formState.form.memberUserId,
    commissionRate: formState.form.commissionRate,
    remark: formState.form.remark,
  };
  try {
    if (formState.form.id) {
      await editObj(payload);
      ElMessage.success('修改成功');
    } else {
      await addObj(payload);
      ElMessage.success('新增成功');
    }
    formState.visible = false;
    initPage();
  } finally {
    formState.submitting = false;
  }
};

// ---------------------------------------------------------------------------
// 离职交接弹窗
// ---------------------------------------------------------------------------
const resignState = reactive({
  visible: false,
  submitting: false,
  staffId: '',
  staffName: '',
  receiveStaffId: '',
  leaveTime: '',
  reason: '',
  options: [] as SalesStaffRecord[],
});

const openResign = async (row: SalesStaffRecord) => {
  resignState.staffId = row.id;
  resignState.staffName = row.staffName ?? '';
  resignState.receiveStaffId = '';
  resignState.leaveTime = '';
  resignState.reason = '';
  resignState.options = [];
  resignState.visible = true;
  try {
    const options = await getOptions();
    resignState.options = (options ?? []).filter((item) => item.id !== row.id);
  } catch {
    resignState.options = [];
  }
};

const submitResign = async () => {
  if (!resignState.receiveStaffId) {
    ElMessage.error('请选择客户接收销售');
    return;
  }
  resignState.submitting = true;
  try {
    await resign({
      staffId: resignState.staffId,
      receiveStaffId: resignState.receiveStaffId,
      leaveTime: resignState.leaveTime || undefined,
    });
    ElMessage.success('离职交接完成，名下客户已转交');
    resignState.visible = false;
    initPage();
  } finally {
    resignState.submitting = false;
  }
};

/** 停用（仅置离职状态，不转交客户）：需要转交名下客户时走「离职交接」 */
const disableStatus = (row: SalesStaffRecord) => {
  ElMessageBox.confirm(
    `确认停用销售「${row.staffName ?? row.id}」？停用后不再产生新业绩；名下客户不会自动转交，如需转交请使用「离职交接」。`,
    '提示',
    {
      confirmButtonText: '确认停用',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    updateStatus({ id: row.id, status: '1' })
      .then(() => {
        ElMessage.success('已停用');
        initPage();
      })
      .catch(() => {});
  });
};

/** 已离职销售恢复在职（启用） */
const restoreStatus = (row: SalesStaffRecord) => {
  ElMessageBox.confirm(
    `确认将销售「${row.staffName ?? row.id}」恢复为在职状态？`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    updateStatus({ id: row.id, status: '0' })
      .then(() => {
        ElMessage.success('已恢复在职');
        initPage();
      })
      .catch(() => {});
  });
};

loadDeptTree();
initPage();

useKeepAliveRefresh(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.queryParams"
        :visible="showSearch"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="销售姓名" prop="staffName">
          <ElInput
            v-model="state.queryParams.staffName"
            clearable
            placeholder="请输入销售姓名"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="手机号" prop="phone">
          <ElInput
            v-model="state.queryParams.phone"
            clearable
            placeholder="请输入手机号"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="所属部门" prop="deptId">
          <ElTreeSelect
            v-model="state.queryParams.deptId"
            :data="deptData"
            :props="{ children: 'children', label: 'name' }"
            node-key="id"
            check-strictly
            clearable
            filterable
            placeholder="请选择部门"
          />
        </ElFormItem>
        <ElFormItem label="销售状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in sales_staff_status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="名销售">
        <template #left>
          <ElButton
            v-access:code="'promotion:salesstaff:add'"
            :icon="Plus"
            type="primary"
            @click="openAdd"
          >
            登记销售
          </ElButton>
        </template>
        <template #right>
          <RightToolbar
            :search-btn="true"
            :refresh-btn="true"
            @search="showSearch = !showSearch"
            @refresh="initPage"
          />
        </template>
      </PageToolbar>

      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无销售员"
      >
        <ElTableColumn
          prop="staffName"
          label="销售姓名"
          min-width="110"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="phone" label="手机号" width="130" align="center" />
        <ElTableColumn
          prop="deptName"
          label="所属部门"
          min-width="120"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn label="会员账号" min-width="200">
          <template #default="scope">
            <div class="member-cell">
              <ElAvatar :size="28" :src="scope.row.memberAvatar">
                {{ (scope.row.memberNickname ?? '会').slice(0, 1) }}
              </ElAvatar>
              <div class="member-cell__main">
                <span class="member-cell__name">
                  {{ scope.row.memberNickname || '—' }}
                </span>
                <span class="member-cell__id">
                  {{ scope.row.memberUserId }}
                </span>
              </div>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="个人提成比例" width="130" align="center">
          <template #default="scope">
            <span :class="{ 'is-muted': scope.row.commissionRate == null }">
              {{ formatRate(scope.row.commissionRate) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="customerCount"
          label="名下客户"
          width="96"
          align="center"
        >
          <template #default="scope">
            {{ scope.row.customerCount ?? '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="90" align="center">
          <template #default="scope">
            <DictTag :options="sales_staff_status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="离职时间" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.leaveTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="remark"
          label="备注"
          min-width="140"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn label="创建时间" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="180" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-access:code="'promotion:salesstaff:edit'"
              :icon="Edit"
              link
              type="primary"
              @click="openEdit(scope.row as SalesStaffRecord)"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="scope.row.status === '0'"
              v-access:code="'promotion:salesstaff:edit'"
              :icon="SwitchButton"
              link
              type="danger"
              @click="openResign(scope.row as SalesStaffRecord)"
            >
              离职交接
            </ElButton>
            <ElButton
              v-if="scope.row.status === '0'"
              v-access:code="'promotion:salesstaff:edit'"
              :icon="CircleClose"
              link
              type="warning"
              @click="disableStatus(scope.row as SalesStaffRecord)"
            >
              停用
            </ElButton>
            <ElButton
              v-else
              v-access:code="'promotion:salesstaff:edit'"
              :icon="RefreshLeft"
              link
              type="primary"
              @click="restoreStatus(scope.row as SalesStaffRecord)"
            >
              恢复在职
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />

      <!-- 登记/编辑销售 -->
      <ElDialog
        v-model="formState.visible"
        :title="formState.form.id ? '编辑销售' : '登记销售'"
        width="600px"
      >
        <ElForm
          ref="formRef"
          :model="formState.form"
          :rules="{
            sysUserId: [
              { required: true, message: '请选择员工账号', trigger: 'change' },
            ],
            memberUserId: [
              {
                required: true,
                message: '请选择销售的小程序会员账号',
                trigger: 'change',
              },
            ],
          }"
          label-width="130px"
        >
          <ElFormItem label="员工账号" prop="sysUserId">
            <ElSelect
              v-model="formState.form.sysUserId"
              :loading="formState.employeeLoading"
              filterable
              remote
              clearable
              :remote-method="remoteEmployee"
              placeholder="输入姓名/用户名/手机号搜索"
              style="width: 100%"
            >
              <ElOption
                v-for="item in formState.employeeOptions"
                :key="item.id"
                :label="employeeLabel(item)"
                :value="item.id"
              />
            </ElSelect>
            <div class="form-tip">
              关联后台员工账号，姓名/手机号/部门由后端按员工信息快照
            </div>
          </ElFormItem>
          <ElFormItem label="小程序会员" prop="memberUserId">
            <div class="member-picker">
              <ElInput
                v-model="formState.memberKeyword"
                clearable
                placeholder="输入销售本人手机号查询会员"
                @keyup.enter="searchMembers"
              />
              <ElButton
                :loading="formState.memberLoading"
                @click="searchMembers"
              >
                查询
              </ElButton>
              <ElSelect
                v-model="formState.form.memberUserId"
                filterable
                placeholder="请选择销售的小程序会员账号"
                style="width: 100%"
              >
                <ElOption
                  v-for="item in formState.memberOptions"
                  :key="item.id"
                  :label="memberLabel(item)"
                  :value="item.id"
                />
              </ElSelect>
            </div>
            <div class="form-tip">用于生成销售分享参数与推广码，租户内唯一</div>
          </ElFormItem>
          <ElFormItem label="个人提成比例">
            <ElInputNumber
              v-model="formState.form.commissionRate"
              :max="1"
              :min="0"
              :precision="4"
              :step="0.01"
            />
            <span class="form-tip is-inline">
              0.05 表示 5%，留空取全局默认比例
            </span>
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput
              v-model="formState.form.remark"
              :maxlength="255"
              placeholder="请输入备注"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="formState.visible = false">取消</ElButton>
            <ElButton
              :loading="formState.submitting"
              type="primary"
              @click="submitForm"
            >
              确定
            </ElButton>
          </div>
        </template>
      </ElDialog>

      <!-- 离职交接 -->
      <ElDialog v-model="resignState.visible" title="离职交接" width="520px">
        <ElForm label-width="110px">
          <ElFormItem label="离职销售">
            <ElInput :model-value="resignState.staffName" disabled />
          </ElFormItem>
          <ElFormItem label="客户接收人" required>
            <ElSelect
              v-model="resignState.receiveStaffId"
              filterable
              placeholder="请选择接收客户的在职销售（必选）"
              style="width: 100%"
            >
              <ElOption
                v-for="item in resignState.options"
                :key="item.id"
                :label="`${item.staffName ?? item.id}${item.deptName ? `（${item.deptName}）` : ''}`"
                :value="item.id"
              />
            </ElSelect>
            <div class="form-tip">
              离职销售名下全部客户将转交给接收人，历史订单归属不变
            </div>
          </ElFormItem>
          <ElFormItem label="离职时间">
            <ElDatePicker
              v-model="resignState.leaveTime"
              clearable
              placeholder="默认当前时间"
              style="width: 100%"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </ElFormItem>
          <ElFormItem label="交接原因">
            <ElInput
              v-model="resignState.reason"
              :maxlength="255"
              placeholder="请输入交接原因"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="resignState.visible = false">取消</ElButton>
            <ElButton
              :loading="resignState.submitting"
              type="primary"
              @click="submitResign"
            >
              确认交接
            </ElButton>
          </div>
        </template>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
.form-tip {
  width: 100%;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.form-tip.is-inline {
  width: auto;
  margin: 0 0 0 10px;
  line-height: 32px;
}

.member-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}

.member-picker :deep(.el-input) {
  flex: 1 1 200px;
  width: auto;
}

.is-muted {
  color: var(--el-text-color-secondary);
}

.member-cell {
  display: flex;
  gap: 8px;
  align-items: center;
}

.member-cell__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.member-cell__name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.member-cell__id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
