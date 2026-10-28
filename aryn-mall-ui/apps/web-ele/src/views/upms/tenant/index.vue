<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { ARYN_PLATFORM_TENANT_ID } from '@vben/constants';

import { ArrowDown, Edit, Plus } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElFormItem,
  ElIcon,
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
  changeStatus,
  delObj,
  getPage,
  kickoutSessions,
  renew,
} from '#/api/upms/tenant';
import { getList as getTenantPackageList } from '#/api/upms/tenant-package';
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

const Form = defineAsyncComponent(() => import('./form.vue'));
const Detail = defineAsyncComponent(() => import('./detail.vue'));
const TenantMenu = defineAsyncComponent(() => import('./tenantmenu.vue'));
// 字典
const { status } = useDict('status');
// 业务模式：1 综合（个人+船供并存）；2 纯零售
const { business_mode: businessModeOptions } = useDict('business_mode');
const state = reactive({
  queryParams: {
    name: '',
    status: '',
    packageId: '',
    expiringDays: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
  tenantPackageList: [] as Array<{ id: string; name: string }>,
});
const showSearch = ref(true);
const loading = ref(false);
const refForm = ref();
const detailRef = ref();
const tenantMenuRef = ref();

const initPage = async () => {
  loading.value = true;
  const params: Record<string, any> = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  Object.entries(state.queryParams).forEach(([key, value]) => {
    if (value !== '' && value !== null && value !== undefined) {
      params[key] = value;
    }
  });
  await getPage(params)
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const loadPackages = () => {
  getTenantPackageList({})
    .then((response) => {
      state.tenantPackageList = response ?? [];
    })
    .catch(() => {
      state.tenantPackageList = [];
    });
};

const packageName = (id?: string) => {
  return (
    state.tenantPackageList.find((item: any) => item.id === id)?.name ?? '-'
  );
};

const remainingDays = (row: any) => {
  if (!row.authEndTime) return null;
  return dayjs(row.authEndTime)
    .startOf('day')
    .diff(dayjs().startOf('day'), 'day');
};

const isPlatformTenant = (row: any) => row.id === ARYN_PLATFORM_TENANT_ID;

const remainingText = (row: any) => {
  if (isPlatformTenant(row)) return '长期有效';
  const days = remainingDays(row);
  if (days === null) return '-';
  if (days < 0) return `已过期 ${Math.abs(days)} 天`;
  if (days === 0) return '今天到期';
  return `${days} 天`;
};

const remainingTagType = (
  row: any,
): 'danger' | 'info' | 'success' | 'warning' => {
  if (isPlatformTenant(row)) return 'success';
  const days = remainingDays(row);
  if (days === null) return 'info';
  if (days <= 7) return 'danger';
  if (days <= 30) return 'warning';
  return 'success';
};

const confirmAction = async (
  message: string,
  title: string,
  confirmText = '确定',
) => {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: confirmText,
      cancelButtonText: '取消',
    });
    return true;
  } catch {
    return false;
  }
};

/**
 * 新增按钮
 */
const add = () => {
  refForm.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  refForm.value.initForm(row);
};
const showDetail = (row: any) => {
  detailRef.value.open(row.id);
};
const upMenu = (id: string) => {
  tenantMenuRef.value.initMenu(id);
};

const handleRenew = async (row: any) => {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入续期天数（1-3650）',
      `续期：${row.name}`,
      {
        inputPattern: /^[1-9]\d{0,3}$/,
        inputErrorMessage: '请输入 1-3650 的整数',
        inputValue: '365',
        confirmButtonText: '续期',
        cancelButtonText: '取消',
      },
    );
    const days = Number(value);
    if (days < 1 || days > 3650) {
      ElMessage.error('续期天数需在 1-3650 天之间');
      return;
    }
    await renew(row.id, days);
    ElMessage.success('续期成功');
    initPage();
  } catch {
    // 用户取消
  }
};

const toggleStatus = async (row: any) => {
  const disabling = row.status === '0';
  const confirmed = await confirmAction(
    disabling
      ? `确认停用租户「${row.name}」？停用后该租户全部会话将被强制下线。`
      : `确认启用租户「${row.name}」？`,
    disabling ? '停用租户' : '启用租户',
    disabling ? '停用' : '启用',
  );
  if (!confirmed) return;
  await changeStatus(row.id, disabling ? '1' : '0');
  ElMessage.success(disabling ? '已停用' : '已启用');
  initPage();
};

const kickout = async (row: any) => {
  const confirmed = await confirmAction(
    `确认强制下线租户「${row.name}」的全部会话？`,
    '强制下线',
    '强制下线',
  );
  if (!confirmed) return;
  const count = await kickoutSessions(row.id);
  ElMessage.success(`已下线 ${Number(count ?? 0)} 个会话`);
};

const del = async (row: any) => {
  const confirmed = await confirmAction(
    `确认删除租户「${row.name}」？删除后该租户全部会话将被强制下线。`,
    '删除租户',
    '删除',
  );
  if (!confirmed) return;
  await delObj(row.id);
  ElMessage.success('删除成功');
  initPage();
};

const handleRowCommand = (command: string, row: any) => {
  switch (command) {
    case 'delete': {
      del(row);
      break;
    }
    case 'kickout': {
      kickout(row);
      break;
    }
    case 'renew': {
      handleRenew(row);
      break;
    }
    case 'toggle': {
      toggleStatus(row);
      break;
    }
  }
};

initPage();
loadPackages();

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
        <ElFormItem label="租户名称" prop="name">
          <ElInput
            v-model="state.queryParams.name"
            clearable
            placeholder="请输入租户名称"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="全部状态"
          >
            <ElOption
              v-for="item in status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="套餐" prop="packageId">
          <ElSelect
            v-model="state.queryParams.packageId"
            clearable
            placeholder="全部套餐"
          >
            <ElOption
              v-for="item in state.tenantPackageList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="到期范围" prop="expiringDays">
          <ElSelect
            v-model="state.queryParams.expiringDays"
            clearable
            placeholder="不限"
          >
            <ElOption label="30 天内到期" :value="30" />
            <ElOption label="7 天内到期" :value="7" />
            <ElOption label="1 天内到期" :value="1" />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个租户">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'upms:systenant:add'"
            @click="add"
            :icon="Plus"
          >
            新增
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
      <Form ref="refForm" @init-page="initPage" />
      <Detail ref="detailRef" />
      <TenantMenu ref="tenantMenuRef" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        :data="state.tableData"
        class="hx-data-table"
        border
        empty-text="暂无租户"
      >
        <ElTableColumn
          prop="name"
          label="租户名"
          min-width="140"
          show-overflow-tooltip
        />
        <ElTableColumn label="套餐" width="120">
          <template #default="scope">
            {{ packageName(scope.row.packageId) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="phone" label="手机号" width="120" />
        <ElTableColumn prop="authBeginTime" label="授权开始时间" width="165" />
        <ElTableColumn label="授权结束时间" width="165">
          <template #default="scope">
            {{
              isPlatformTenant(scope.row) ? '长期有效' : scope.row.authEndTime
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="剩余天数" width="120">
          <template #default="scope">
            <ElTag :type="remainingTagType(scope.row)">
              {{ remainingText(scope.row) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="businessMode" label="业务模式" width="110">
          <template #default="scope">
            <DictTag
              :options="businessModeOptions"
              :value="scope.row.businessMode || '1'"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="90">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="260" align="center" fixed="right">
          <template #default="scope">
            <ElButton link type="primary" @click="showDetail(scope.row)">
              详情
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'upms:systenant:edit'"
              :icon="Edit"
              @click="edit(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="primary"
              v-if="scope.row.id !== ARYN_PLATFORM_TENANT_ID"
              v-access:code="'upms:systenant:add'"
              @click="upMenu(scope.row.id)"
            >
              配置菜单
            </ElButton>
            <ElDropdown
              v-if="scope.row.id !== ARYN_PLATFORM_TENANT_ID"
              trigger="click"
              @command="
                (command: string) => handleRowCommand(command, scope.row)
              "
            >
              <ElButton link type="primary">
                更多
                <ElIcon class="el-icon--right">
                  <ArrowDown />
                </ElIcon>
              </ElButton>
              <template #dropdown>
                <ElDropdownMenu>
                  <ElDropdownItem
                    v-if="scope.row.id !== ARYN_PLATFORM_TENANT_ID"
                    command="renew"
                    v-access:code="'upms:systenant:edit'"
                  >
                    续期
                  </ElDropdownItem>
                  <ElDropdownItem
                    v-if="scope.row.id !== ARYN_PLATFORM_TENANT_ID"
                    command="toggle"
                    v-access:code="'upms:systenant:edit'"
                  >
                    {{ scope.row.status === '0' ? '停用' : '启用' }}
                  </ElDropdownItem>
                  <ElDropdownItem
                    v-if="scope.row.id !== ARYN_PLATFORM_TENANT_ID"
                    command="kickout"
                    v-access:code="'upms:systenant:edit'"
                  >
                    强制下线
                  </ElDropdownItem>
                  <ElDropdownItem
                    v-if="scope.row.id !== ARYN_PLATFORM_TENANT_ID"
                    command="delete"
                    divided
                    v-access:code="'upms:systenant:del'"
                  >
                    删除
                  </ElDropdownItem>
                </ElDropdownMenu>
              </template>
            </ElDropdown>
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
