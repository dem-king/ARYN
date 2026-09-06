<script lang="ts" setup name="deliveryStaff">
import type { FormInstance } from 'element-plus';

import type {
  DeliveryBindingStatus,
  DeliveryStaffManager,
  DeliveryStaffStatus,
} from '#/api/delivery/staff';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
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
  deleteDeliveryStaff,
  getDeliveryStaffManagerPage,
  unbindDeliveryStaffMallUser,
  updateDeliveryStaffAvailability,
  updateDeliveryStaffQualification,
} from '#/api/delivery/staff';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const StaffForm = defineAsyncComponent(() => import('./staff-form.vue'));
const StaffOnboard = defineAsyncComponent(() => import('./staff-onboard.vue'));
const BindMallUser = defineAsyncComponent(() => import('./bind-mall-user.vue'));

/** 接单状态选项：离线配送员仍可登录查看任务，但不出现在默认派单候选中 */
const statusOptions: {
  label: string;
  type: 'danger' | 'info' | 'success' | 'warning';
  value: DeliveryStaffStatus;
}[] = [
  { value: '1', label: '在线', type: 'success' },
  { value: '2', label: '忙碌', type: 'warning' },
  { value: '3', label: '离线', type: 'info' },
];

const bindingStatusOptions: {
  label: string;
  type: 'danger' | 'info' | 'success' | 'warning';
  value: DeliveryBindingStatus;
}[] = [
  { value: 'bound', label: '已绑定', type: 'success' },
  { value: 'unbound', label: '未绑定', type: 'info' },
];

const queryRef = ref<FormInstance>();
const staffFormRef = ref();
const staffOnboardRef = ref();
const bindMallUserRef = ref();
const loading = ref(false);
const showSearch = ref(true);

const state = reactive({
  queryParams: {
    keyword: '',
    bindingStatus: '' as '' | DeliveryBindingStatus,
    status: '' as '' | DeliveryStaffStatus,
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  stats: {
    bound: 0,
    unbound: 0,
  },
  tableData: [] as DeliveryStaffManager[],
});

/**
 * 查询列表
 */
const initPage = async () => {
  loading.value = true;
  await getDeliveryStaffManagerPage({
    current: state.page.currentPage,
    size: state.page.pageSize,
    keyword: state.queryParams.keyword || undefined,
    bindingStatus: state.queryParams.bindingStatus || undefined,
    status: state.queryParams.status || undefined,
  })
    .then((response: any) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

/**
 * 查询绑定概况统计（列表顶部）
 */
const initStats = async () => {
  const fetchTotal = async (bindingStatus: DeliveryBindingStatus) => {
    try {
      const response: any = await getDeliveryStaffManagerPage({
        current: 1,
        size: 1,
        bindingStatus,
      });
      return response.total ?? 0;
    } catch {
      return 0;
    }
  };
  const [bound, unbound] = await Promise.all([
    fetchTotal('bound'),
    fetchTotal('unbound'),
  ]);
  state.stats.bound = bound;
  state.stats.unbound = unbound;
};

/**
 * 刷新列表与统计
 */
const refreshAll = () => {
  initPage();
  initStats();
};

/**
 * 重置搜索表单
 */
const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
  refreshAll();
};

/**
 * 打开向导式创建配送员
 */
const onboard = () => {
  staffOnboardRef.value.open();
};

/**
 * 编辑配送员资料
 */
const edit = (row: any) => {
  staffFormRef.value.initForm(row);
};

/**
 * 打开绑定商城账号弹窗
 */
const bind = (row: any) => {
  bindMallUserRef.value.open(row);
};

/**
 * 解绑商城账号（不删除员工账号与配送员历史数据）
 */
const unbind = (row: any) => {
  ElMessageBox.confirm(
    '解绑后，该商城账号将不再显示配送工作台入口；配送员员工账号和历史配送任务不受影响。是否继续?',
    '解绑商城账号',
    {
      confirmButtonText: '确认解绑',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    unbindDeliveryStaffMallUser(row.id)
      .then(() => {
        ElMessage.success('解绑成功');
        refreshAll();
      })
      .catch(() => {});
  });
};

/**
 * 删除配送员（同时解绑商城账号、回收配送资格与配送端会话）
 */
const del = (id: string) => {
  ElMessageBox.confirm(
    '删除后将同时解绑商城账号、回收该员工的配送资格（delivery_staff 角色）并撤销配送端登录会话，历史任务保留归属。是否继续?',
    '删除配送员',
    {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    deleteDeliveryStaff(id)
      .then(() => {
        ElMessage.success('删除成功，配送资格已同步回收');
        refreshAll();
      })
      .catch(() => {});
  });
};

/**
 * 停用/恢复接单：停用接单仅影响派单候选，不影响登录与配送权限
 */
const toggleAvailability = (row: any) => {
  const next: DeliveryStaffStatus = row.status === '3' ? '1' : '3';
  const label = statusOptions.find((item) => item.value === next)?.label;
  ElMessageBox.confirm(`确认将接单状态切换为「${label}」?`, '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    updateDeliveryStaffAvailability(row.id, next)
      .then(() => {
        ElMessage.success('接单状态更新成功');
        refreshAll();
      })
      .catch(() => {});
  });
};

/**
 * 开通/停用配送资格：授予或回收配送员角色（delivery:execute）
 */
const toggleQualification = (row: any) => {
  if (!row.userId) {
    ElMessage.warning('该配送员未关联员工账号');
    return;
  }
  const enabled = row.deliveryPermission !== true;
  const message = enabled
    ? '开通后该员工将获得配送执行权限（delivery:execute），移动端可进入配送工作台。是否继续?'
    : '停用资格后该员工无法再进入配送工作台，已有配送任务仍保留归属。是否继续?';
  ElMessageBox.confirm(message, enabled ? '开通配送资格' : '停用配送资格', {
    confirmButtonText: enabled ? '确认开通' : '确认停用',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    updateDeliveryStaffQualification(row.id, enabled)
      .then(() => {
        ElMessage.success(enabled ? '配送资格已开通' : '配送资格已停用');
        refreshAll();
      })
      .catch(() => {});
  });
};

/**
 * 获取接单状态标签类型
 */
const getStatusType = (status: DeliveryStaffStatus) => {
  return statusOptions.find((item) => item.value === status)?.type ?? 'info';
};

/**
 * 获取接单状态标签文本
 */
const getStatusLabel = (status: DeliveryStaffStatus) => {
  return statusOptions.find((item) => item.value === status)?.label ?? '未知';
};

/**
 * 绑定状态标签
 */
const getBindingTag = (status: DeliveryBindingStatus) => {
  return (
    bindingStatusOptions.find((item) => item.value === status) ?? {
      label: status,
      type: 'info' as const,
    }
  );
};

/**
 * 权限状态展示
 */
const permissionText = (row: any) => {
  if (row.sysUserStatus && row.sysUserStatus !== '0') return '账号停用';
  return row.deliveryPermission ? '已开通' : '未开通';
};

/**
 * 权限状态标签类型
 */
const permissionTagType = (row: any) => {
  if (row.sysUserStatus && row.sysUserStatus !== '0') return 'danger';
  return row.deliveryPermission ? 'success' : 'warning';
};

refreshAll();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="姓名/手机号" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="请输入配送员姓名或手机号"
          />
        </ElFormItem>
        <ElFormItem label="绑定状态" prop="bindingStatus">
          <ElSelect
            v-model="state.queryParams.bindingStatus"
            clearable
            placeholder="请选择绑定状态"
            style="width: 200px"
          >
            <ElOption
              v-for="item in bindingStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="接单状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择接单状态"
            style="width: 200px"
          >
            <ElOption
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery(queryRef)" :icon="Refresh">
            重置
          </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'delivery:staff:add'"
            @click="onboard"
            :icon="Plus"
          >
            新增配送员
          </ElButton>
          <span class="hx-table-toolbar-stat">
            已绑定商城账号 {{ state.stats.bound }} 人 · 待绑定
            {{ state.stats.unbound }} 人
          </span>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="refreshAll"
        />
      </div>
      <StaffForm ref="staffFormRef" @init-page="refreshAll" />
      <StaffOnboard ref="staffOnboardRef" @init-page="refreshAll" />
      <BindMallUser ref="bindMallUserRef" @init-page="refreshAll" />

      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="staffName"
          label="配送员"
          align="center"
          width="120"
        >
          <template #default="scope">
            {{ scope.row.staffName }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="staffPhone"
          label="手机号"
          align="center"
          width="140"
        />
        <ElTableColumn
          prop="sysUserName"
          label="员工账号"
          align="center"
          min-width="120"
        >
          <template #default="scope">
            <span v-if="scope.row.sysUserName">{{
              scope.row.sysUserName
            }}</span>
            <ElTag v-else type="warning">未关联</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="商城账号" align="center" min-width="160">
          <template #default="scope">
            <template v-if="scope.row.mallUserId">
              <div>{{ scope.row.mallUserNickname || '-' }}</div>
              <div class="hx-cell-sub">
                {{ scope.row.mallUserPhone || '-' }}
              </div>
            </template>
            <span v-else>-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="bindingStatus"
          label="绑定状态"
          align="center"
          width="100"
        >
          <template #default="scope">
            <ElTag :type="getBindingTag(scope.row.bindingStatus).type">
              {{ getBindingTag(scope.row.bindingStatus).label }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="权限状态" align="center" width="100">
          <template #default="scope">
            <ElTag :type="permissionTagType(scope.row)">
              {{ permissionText(scope.row) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="接单状态" align="center" width="90">
          <template #default="scope">
            <ElTag :type="getStatusType(scope.row.status)">
              {{ getStatusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="进行中任务" align="center" width="100">
          <template #default="scope">
            {{ scope.row.pendingTaskCount ?? 0 }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="createTime"
          label="创建时间"
          align="center"
          width="180"
        />
        <ElTableColumn label="操作" align="center" width="330" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'delivery:staff:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="scope.row.bindingStatus === 'unbound'"
              link
              type="success"
              v-access:code="'delivery:staff:bind'"
              @click="bind(scope.row)"
            >
              绑定商城账号
            </ElButton>
            <ElButton
              v-else
              link
              type="warning"
              v-access:code="'delivery:staff:bind'"
              @click="unbind(scope.row)"
            >
              解绑
            </ElButton>
            <ElButton
              link
              v-access:code="'delivery:staff:availability'"
              @click="toggleAvailability(scope.row)"
            >
              {{ scope.row.status === '3' ? '恢复接单' : '停用接单' }}
            </ElButton>
            <ElButton
              v-if="!scope.row.sysUserStatus || scope.row.sysUserStatus === '0'"
              link
              :type="scope.row.deliveryPermission ? 'danger' : 'success'"
              v-access:code="'delivery:staff:qualification'"
              @click="toggleQualification(scope.row)"
            >
              {{ scope.row.deliveryPermission ? '停用资格' : '开通资格' }}
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'delivery:staff:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
<style scoped>
.hx-table-toolbar-stat {
  margin-left: 16px;
  font-size: 13px;
  color: #909399;
}

.hx-cell-sub {
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
}
</style>
