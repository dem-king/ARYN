<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  Delete,
  Edit,
  InfoFilled,
  Key,
  Plus,
  Refresh,
  Search,
} from '@element-plus/icons-vue';
import {
  ElAlert,
  ElAvatar,
  ElButton,
  ElCol,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElRow,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTree,
} from 'element-plus';

import { getTreeList } from '#/api/upms/dept';
import { delObj, getPage } from '#/api/upms/user';
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
  deptData: [],
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
const queryRef = ref();
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
 * 树节点进行筛选时执行的方法
 */

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
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
  initPage();
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
const handleNodeClick = (row: any) => {
  state.queryParams.deptId = row.id;
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
getDeptData();
initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElRow :gutter="35" class="user-page-row">
        <!-- 部门 -->
        <ElCol :span="4" class="user-page-dept min-w-0">
          <div>
            <ElInput
              v-model="filterText"
              clearable
              placeholder="请输入部门名称"
            />
          </div>
          <ElTree
            ref="treeRef"
            :data="state.deptData"
            :props="defaultProps"
            :default-expand-all="true"
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
            :filter-node-method="filterNode"
          />
        </ElCol>
        <ElCol :span="20" class="user-page-main min-w-0">
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
          <ElForm
            :model="state.queryParams"
            ref="queryRef"
            :inline="true"
            v-show="showSearch"
          >
            <ElFormItem label="登录用户名" prop="username">
              <ElInput
                v-model="state.queryParams.username"
                placeholder="请输入登录用户名"
              />
            </ElFormItem>
            <ElFormItem label="手机号" prop="phone">
              <ElInput
                v-model="state.queryParams.phone"
                placeholder="请输入手机号"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" @click="initPage" :icon="Search">
                搜索
              </ElButton>
              <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
            </ElFormItem>
          </ElForm>
          <!-- 工具栏 -->
          <div class="hx-table-toolbar">
            <div>
              <ElButton
                type="primary"
                v-access:code="'upms:sysuser:add'"
                @click="add"
                :icon="Plus"
              >
                新增
              </ElButton>
            </div>
            <RightToolbar
              :search-btn="true"
              :refresh-btn="true"
              @search="showSearch = !showSearch"
              @refresh="initPage"
            />
          </div>
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
            <LoginLog v-if="dialogLogin" :user-id="state.userInfo.id" />
          </ElDialog>
          <!-- table列表 -->
          <ElTable v-loading="loading" :data="state.tableData" border>
            <ElTableColumn prop="username" label="登录用户名" />
            <ElTableColumn prop="avatar" label="头像">
              <template #default="scope">
                <ElAvatar shape="square" :size="50" :src="scope.row.avatar" />
              </template>
            </ElTableColumn>
            <ElTableColumn prop="nickname" label="员工昵称" />
            <ElTableColumn prop="phone" label="手机号" />
            <ElTableColumn prop="deptName" label="部门" />
            <ElTableColumn prop="status" label="状态">
              <template #default="scope">
                <DictTag :options="status" :value="scope.row.status" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="配送资格" align="center" width="100">
              <template #default="scope">
                <ElTag
                  :type="scope.row.deliveryQualification ? 'success' : 'info'"
                  size="small"
                >
                  {{ scope.row.deliveryQualification ? '已开通' : '未开通' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="createTime" label="创建时间" width="170" />
            <ElTableColumn
              label="操作"
              align="center"
              fixed="right"
              width="220"
            >
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
                <ElButton
                  link
                  type="primary"
                  v-access:code="'upms:sysuser:password'"
                  @click="editPassword(scope.row)"
                  :icon="Key"
                >
                  修改密码
                </ElButton>
                <ElButton
                  link
                  type="primary"
                  v-access:code="'upms:sysuser:get'"
                  @click="showLoginLog(scope.row)"
                  :icon="InfoFilled"
                >
                  登录日志
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
        </ElCol>
      </ElRow>
    </div>
  </div>
</template>

<style scoped>
.user-page-row {
  width: 100%;
  max-width: 100%;
}

.from-delivery-alert {
  margin-bottom: 12px;
}

.user-page-dept {
  flex-shrink: 0;
}

.user-page-main {
  min-width: 0;
}

.user-table-wrap {
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
}
</style>
