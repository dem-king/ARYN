<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/user/points-config';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useDict } from '#/utils/dict';

import { getPointsSceneOption } from '../scene-options';

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

// 全局规则/排除清单 Tab 懒加载：切到对应 Tab 才发起请求
const GlobalConfig = defineAsyncComponent(() => import('./global-config.vue'));
const ExcludeList = defineAsyncComponent(() => import('./exclude-list.vue'));

const { status } = useDict('status');
const activeTab = ref('rules');
const state = reactive({
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const loading = ref(false);
const formRef = ref();

const sceneLabel = (value?: string) =>
  getPointsSceneOption(value)?.label || value || '';

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(params)
    .then((res) => {
      state.tableData = res.records;
      state.page.total = res.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const handleAdd = () => {
  formRef.value.initForm();
};
const handleEdit = (row: any) => {
  formRef.value.initForm(row);
};
const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `删除后「${row.ruleName}」不再参与积分发放/抵扣，是否继续?`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  )
    .then(() => {
      delObj(row.id)
        .then(() => {
          ElMessage.success('删除成功');
          initPage();
        })
        .catch(() => {});
    })
    .catch(() => {});
};

initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElTabs v-model="activeTab" class="points-config-tabs">
        <!-- Tab A：抵扣与有效期（全局风控参数） -->
        <ElTabPane label="抵扣与有效期" name="global" lazy>
          <GlobalConfig />
        </ElTabPane>
        <!-- Tab B：排除清单（类目/商品黑名单） -->
        <ElTabPane label="排除清单" name="exclude" lazy>
          <ExcludeList />
        </ElTabPane>
        <!-- Tab C：按场景的积分规则（原有功能） -->
        <ElTabPane label="积分规则" name="rules">
          <!-- 工具栏 -->
          <PageToolbar :total="state.page.total" total-label="条规则">
            <template #left>
              <ElButton
                type="primary"
                v-access:code="'user:pointsconfig:add'"
                @click="handleAdd"
                :icon="Plus"
              >
                新增
              </ElButton>
            </template>
            <template #right>
              <RightToolbar
                :search-btn="false"
                :refresh-btn="true"
                @refresh="initPage"
              />
            </template>
          </PageToolbar>
          <Form ref="formRef" @init-page="initPage" />
          <!-- 列表 -->
          <ElTable
            v-loading="loading"
            class="hx-data-table"
            :data="state.tableData"
            border
            empty-text="暂无积分配置"
          >
            <ElTableColumn prop="ruleName" label="规则名称" />
            <ElTableColumn prop="ruleType" label="规则类型" width="100">
              <template #default="scope">
                <ElTag v-if="scope.row.ruleType === '1'" type="success">
                  获取
                </ElTag>
                <ElTag v-else-if="scope.row.ruleType === '2'" type="danger">
                  消耗
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="triggerScene" label="触发场景" width="120">
              <template #default="scope">
                {{ sceneLabel(scope.row.triggerScene) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="pointValue" label="积分值" width="100" />
            <ElTableColumn prop="status" label="状态" width="80">
              <template #default="scope">
                <DictTag :options="status" :value="scope.row.status" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="150" align="center">
              <template #default="scope">
                <ElButton
                  link
                  type="primary"
                  v-access:code="'user:pointsconfig:edit'"
                  @click="handleEdit(scope.row)"
                  :icon="Edit"
                >
                  编辑
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  v-access:code="'user:pointsconfig:del'"
                  @click="handleDelete(scope.row)"
                  :icon="Delete"
                >
                  删除
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
    </div>
  </div>
</template>

<style scoped>
.points-config-tabs :deep(.el-tabs__content) {
  padding-top: 8px;
}
</style>
