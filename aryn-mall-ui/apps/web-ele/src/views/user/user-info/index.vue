<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSegmented,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getList as getLevelList } from '#/api/user/member-level';
import { getPage } from '#/api/user/user-info';
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
// 字典
const { user_source, user_sex } = useDict('user_source', 'user_sex');
const state = reactive({
  queryParams: { phone: '', sex: '', memberLevelId: '' },
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 10, // 每页显示多少条
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
  activeName: '',
});
const showSearch = ref(true);
const loading = ref(false);
const detailRef = ref();
const queryRef = ref();
const formRef = ref();
const levelOptions = ref<any[]>([]);

const userSourceOptions = reactive([
  {
    label: '全部',
    value: '',
  },
  {
    label: '微信小程序',
    value: 'WX_MA',
  },
  {
    label: 'APP',
    value: 'APP',
  },
  {
    label: '普通H5',
    value: 'H5',
  },
]);

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
    userSource: state.activeName,
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
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
};
/**
 * 用户来源切换
 */
const tabHandle = (event: string) => {
  state.activeName = event;
  initPage();
};
const doDetail = (row: any) => {
  detailRef.value.initPage(row);
};

// 创建用户
const add = () => {
  formRef.value.initForm();
};
const doEdit = (row: any) => {
  formRef.value.initForm(row);
};

initPage();

// 加载会员等级选项
const loadLevelOptions = async () => {
  try {
    const res = await getLevelList();
    levelOptions.value = res || [];
  } catch {
    levelOptions.value = [];
  }
};
loadLevelOptions();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <Detail ref="detailRef" />
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="手机号" prop="phone">
          <ElInput
            v-model="state.queryParams.phone"
            placeholder="请输入手机号"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="性别" prop="sex">
          <ElSelect
            v-model="state.queryParams.sex"
            style="width: 200px"
            placeholder="请选择性别"
            clearable
          >
            <ElOption
              v-for="item in user_sex"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="会员等级" prop="memberLevelId">
          <ElSelect
            v-model="state.queryParams.memberLevelId"
            style="width: 200px"
            placeholder="请选择等级"
            clearable
          >
            <ElOption
              v-for="item in levelOptions"
              :key="item.id"
              :label="item.levelName"
              :value="item.id"
            />
          </ElSelect>
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
            v-access:code="'user:userinfo:add'"
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
      <Form ref="formRef" @init-page="initPage" />

      <ElSegmented
        v-model="state.activeName"
        :options="userSourceOptions"
        style="width: 300px; margin-bottom: 5px"
        @change="tabHandle"
      />
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="avatarUrl" label="头像">
          <template #default="scope">
            <ElAvatar :src="scope.row.avatarUrl" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="nickname" label="昵称" />
        <ElTableColumn prop="phone" label="手机号" />
        <ElTableColumn prop="sex" label="性别">
          <template #default="scope">
            <DictTag :options="user_sex" :value="scope.row.sex" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="userSource" label="来源">
          <template #default="scope">
            <DictTag :options="user_source" :value="scope.row.userSource" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="levelName" label="会员等级" />
        <ElTableColumn prop="point" label="积分余额" />
        <ElTableColumn prop="balance" label="储值余额" />
        <ElTableColumn prop="province" label="所在省份" />
        <ElTableColumn prop="city" label="所在城市" />
        <ElTableColumn prop="createTime" label="创建时间" />
        <ElTableColumn label="操作" width="200" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'user:userinfo:get'"
              @click="doDetail(scope.row)"
              :icon="View"
            >
              详情
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'user:userinfo:edit'"
              @click="doEdit(scope.row)"
              :icon="Edit"
            >
              编辑
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
