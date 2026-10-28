<script setup lang="ts">
import { defineAsyncComponent, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  ArrowDown,
  Delete,
  Edit,
  InfoFilled,
  Key,
  Plus,
  Search,
} from '@element-plus/icons-vue';
import {
  ElAlert,
  ElAvatar,
  ElButton,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTree,
} from 'element-plus';

import { getTreeList } from '#/api/upms/dept';
import { delObj, getPage } from '#/api/upms/user';
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
const ResetPwd = defineAsyncComponent(() => import('./info/resetpwd.vue'));
const LoginLog = defineAsyncComponent(() => import('./info/loginlog.vue'));

// 字典
const { status } = useDict('status');
const state = reactive({
  queryParams: {
    deptId: '',
    username: '',
    phone: '',
  },
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 20, // 每页显示多少条
  },
  tableData: [],
  deptData: [] as any[],
  userInfo: {
    username: '',
    id: '',
  },
});

const showSearch = ref(true);
const loading = ref(false);
const filterText = ref('');
const treeRef = ref<InstanceType<typeof ElTree>>();
const dialog = ref(false);
const dialogLogin = ref(false);
const formRef = ref();
const defaultProps = ref({
  children: 'children',
  label: 'name',
});

const route = useRoute();
const router = useRouter();
/**
 * 从配送员向导“去创建员工账号”跳转过来时，展示返回配送员管理的入口
 */
const fromDeliveryStaff = route.query.from === 'delivery-staff';
const backToDeliveryStaff = () => {
  router.push('/delivery/staff');
};
/**
 * 树节点进行筛选时执行的方法
 */
const filterNode = (value: string, data: any) => {
  if (!value) return true;
  return data.name.includes(value);
};
/**
 * 输入部门名称时过滤左侧部门树（只影响树展示，不触发列表查询）
 */
watch(filterText, (value) => {
  treeRef.value?.filter(value.trim());
});

const initPage = async () => {
  loading.value = true;
  await getPage(
    Object.assign(
      {
        current: state.page.currentPage,
        size: state.page.pageSize,
        type: '0',
      },
      state.queryParams,
    ),
  )
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 新增按钮
 */
const add = () => {
  formRef.value.initForm();
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  formRef.value.initForm(row);
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该员工账号，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id).then(() => {
      ElMessage.success('删除成功');
      initPage();
    });
  });
};
/**
 * 查询全部部门
 */
const getDeptData = () => {
  getTreeList().then((response) => {
    state.deptData = response;
  });
};
/**
 * 点击部门节点筛选列表；再点一次已选中的节点视为取消该部门筛选
 */
const handleNodeClick = (row: any) => {
  if (state.queryParams.deptId === row.id) {
    clearDeptFilter();
    return;
  }
  state.queryParams.deptId = row.id;
  state.page.currentPage = 1;
  initPage();
};
/**
 * 清除部门筛选（树取消高亮、回到全部员工）
 */
const clearDeptFilter = () => {
  state.queryParams.deptId = '';
  treeRef.value?.setCurrentKey(null);
  state.page.currentPage = 1;
  initPage();
};
/**
 * 搜索：筛选条件变化后回到第一页，避免停留在筛选后不存在的页码
 */
const searchQuery = () => {
  state.page.currentPage = 1;
  initPage();
};
/**
 * 重置：PageFilterPanel 只重置带 prop 的表单字段，部门筛选（树选中）需要在此清空
 */
const resetQuery = () => {
  state.queryParams.deptId = '';
  filterText.value = '';
  treeRef.value?.setCurrentKey(null);
  state.page.currentPage = 1;
  initPage();
};
const editPassword = (row: any) => {
  state.userInfo = row;
  dialog.value = true;
};
const showLoginLog = (row: any) => {
  state.userInfo = row;
  dialogLogin.value = true;
};
/**
 * 操作列「更多」下拉分发
 */
const handleRowCommand = (command: string, row: any) => {
  if (command === 'password') {
    editPassword(row);
  } else if (command === 'loginlog') {
    showLoginLog(row);
  }
};
getDeptData();
initPage();
useKeepAliveRefresh(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view user-page">
      <!-- 部门树 -->
      <aside class="user-page__dept">
        <div class="user-page__dept-head">
          <span class="user-page__dept-title">部门</span>
          <ElButton
            v-if="state.queryParams.deptId"
            link
            type="primary"
            @click="clearDeptFilter"
          >
            查看全部
          </ElButton>
        </div>
        <ElInput
          v-model="filterText"
          clearable
          placeholder="搜索部门名称"
          :prefix-icon="Search"
        />
        <div class="user-page__dept-tree">
          <ElEmpty
            v-if="state.deptData.length === 0"
            :image-size="80"
            description="暂无部门"
          />
          <ElTree
            v-else
            ref="treeRef"
            :data="state.deptData"
            :props="defaultProps"
            node-key="id"
            highlight-current
            :default-expand-all="true"
            :expand-on-click-node="false"
            :filter-node-method="filterNode"
            @node-click="handleNodeClick"
          />
        </div>
      </aside>

      <!-- 列表区 -->
      <section class="user-page__main">
        <!-- 配送员向导跳转来源提示 -->
        <ElAlert
          v-if="fromDeliveryStaff"
          type="info"
          :closable="false"
          show-icon
          class="from-delivery-alert"
        >
          <template #title>
            <span>
              在此创建员工账号（不要勾选配送员角色），创建完成后返回配送员管理继续向导。
            </span>
            <ElButton link type="primary" @click="backToDeliveryStaff">
              返回配送员管理
            </ElButton>
          </template>
        </ElAlert>
        <!-- 搜索 -->
        <PageFilterPanel
          :model="state.queryParams"
          :visible="showSearch"
          @reset="resetQuery"
          @search="searchQuery"
        >
          <ElFormItem label="登录用户名" prop="username">
            <ElInput
              v-model="state.queryParams.username"
              placeholder="请输入登录用户名"
              @keyup.enter="searchQuery"
            />
          </ElFormItem>
          <ElFormItem label="手机号" prop="phone">
            <ElInput
              v-model="state.queryParams.phone"
              placeholder="请输入手机号"
              @keyup.enter="searchQuery"
            />
          </ElFormItem>
        </PageFilterPanel>
        <!-- 工具栏 -->
        <PageToolbar :total="state.page.total" total-label="个员工账号">
          <template #left>
            <ElButton
              type="primary"
              v-access:code="'upms:sysuser:add'"
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
        <!-- 新增修改弹窗 -->
        <Form ref="formRef" @init-page="initPage" />
        <!-- 修改密码 -->
        <ElDialog
          v-model="dialog"
          :title="`修改【${state.userInfo.username}】密码`"
          width="30%"
          center
        >
          <ResetPwd
            v-if="dialog"
            :user-id="state.userInfo.id"
            @up-success="dialog = false"
          />
        </ElDialog>
        <!-- 登录日志 -->
        <ElDialog
          v-model="dialogLogin"
          :title="`【${state.userInfo.username}】登录日志`"
          width="70%"
          center
        >
          <LoginLog v-if="dialogLogin" :user-name="state.userInfo.username" />
        </ElDialog>
        <!-- table列表 -->
        <ElTable
          v-loading="loading"
          class="hx-data-table"
          :data="state.tableData"
          row-key="id"
          border
          empty-text="暂无员工账号"
        >
          <ElTableColumn label="员工" min-width="200">
            <template #default="scope">
              <div class="user-cell">
                <ElAvatar
                  shape="square"
                  :size="36"
                  :src="scope.row.avatar"
                  class="user-cell__avatar"
                />
                <div class="user-cell__meta">
                  <span class="user-cell__name">
                    {{ scope.row.nickname || scope.row.username }}
                  </span>
                  <span class="user-cell__username">
                    {{ scope.row.username }}
                  </span>
                </div>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="phone" label="手机号" width="125" />
          <ElTableColumn
            prop="deptName"
            label="部门"
            min-width="140"
            show-overflow-tooltip
          />
          <ElTableColumn prop="status" label="状态" width="88">
            <template #default="scope">
              <DictTag :options="status" :value="scope.row.status" />
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="deliveryQualification"
            label="配送资格"
            width="96"
          >
            <template #default="scope">
              <ElTag
                :type="scope.row.deliveryQualification ? 'success' : 'info'"
                size="small"
              >
                {{ scope.row.deliveryQualification ? '已开通' : '未开通' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="createTime" label="创建时间" width="165" />
          <ElTableColumn label="操作" align="center" fixed="right" width="212">
            <template #default="scope">
              <ElButton
                link
                type="primary"
                v-access:code="'upms:sysuser:edit'"
                @click="edit(scope.row)"
                :icon="Edit"
              >
                修改
              </ElButton>
              <ElButton
                link
                type="danger"
                v-access:code="'upms:sysuser:del'"
                @click="del(scope.row.id)"
                :icon="Delete"
              >
                删除
              </ElButton>
              <ElDropdown
                trigger="click"
                v-access:code="['upms:sysuser:password', 'upms:sysuser:get']"
                @command="
                  (command: string) => handleRowCommand(command, scope.row)
                "
              >
                <ElButton link type="primary" class="user-page__more">
                  更多
                  <ElIcon class="el-icon--right">
                    <ArrowDown />
                  </ElIcon>
                </ElButton>
                <template #dropdown>
                  <ElDropdownMenu>
                    <ElDropdownItem
                      command="password"
                      :icon="Key"
                      v-access:code="'upms:sysuser:password'"
                    >
                      修改密码
                    </ElDropdownItem>
                    <ElDropdownItem
                      command="loginlog"
                      :icon="InfoFilled"
                      v-access:code="'upms:sysuser:get'"
                    >
                      登录日志
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
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 左右双栏复刻全局内滚模型（ele/index.css 的 :has 规则只认「卡片直接子级」，
   本页多套了一层双栏，因此在此页内等价实现）：
   卡片 height:auto + min-height:100%，视口充裕时表格吃满剩余高度、纵向滚动
   收进表格内部；筛选顶破视口时回落外层滚动，sticky 筛选/分页兜底 */
.user-page {
  display: flex;

  /* hx-layout-container-auto 自带 flex-direction:column，必须显式翻回行向 */
  flex-direction: row;
  gap: 14px;
  height: auto;
  min-height: 100%;
}

/* ---- 部门树面板 ---- */
.user-page__dept {
  display: flex;
  flex: 0 0 224px;
  flex-direction: column;
  min-width: 0;
  padding: 12px;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.user-page__dept-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 8px;
}

.user-page__dept-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.user-page__dept-tree {
  flex: 1;
  margin-top: 8px;
  overflow-y: auto;
}

/* 树整体占满左栏宽度，节点不再截断在半宽 */
.user-page__dept-tree :deep(.el-tree) {
  display: inline-flex;
  flex-direction: column;
  min-width: 100%;
  background: transparent;
}

.user-page__dept-tree :deep(.el-tree-node__content) {
  height: 30px;
  border-radius: 6px;
}

/* ---- 列表区 ---- */
.user-page__main {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-width: 0;
}

/* 表格吃满列表区剩余高度（等价全局 `&-view:has(> .hx-filter-panel) > .el-table`），
   min-height 是收缩地板：内容顶破视口时回落外层滚动 */
.user-page__main > .el-table {
  flex: 1 1 0;
  min-height: 240px;
}

.from-delivery-alert {
  margin-bottom: 12px;
}

/* ---- 员工列：头像 + 昵称/用户名 ---- */
.user-cell {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.user-cell__avatar {
  flex-shrink: 0;
  background: var(--el-fill-color-light);
}

.user-cell__meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.user-cell__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.user-cell__username {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

/* 「更多」触发布局：图标靠右、与相邻 link 按钮对齐 */
.user-page__more {
  margin-left: 8px;
}

/* ElDropdown 包装层是 inline-block，基线比相邻 link 按钮高 2px，统一垂直对齐 */
.user-page__main :deep(td .el-dropdown) {
  vertical-align: middle;
}
</style>
