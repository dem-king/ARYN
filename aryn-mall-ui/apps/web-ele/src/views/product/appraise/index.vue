<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
  ElCol,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElRate,
  ElRow,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage, replyObj } from '#/api/product/appraise';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: {
    nickname: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const queryRef = ref();
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
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
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
};
// 商家回复
const handleReply = (id: string) => {
  ElMessageBox.prompt('请输入回复的内容', '回复', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputPattern: /^\s*\S[\s\S]*$/,
    inputErrorMessage: '回复的内容不能为空',
  }).then(({ value }) => {
    loading.value = true;
    replyObj({ id, businessReply: value })
      .then(() => {
        ElMessage({
          type: 'success',
          message: '回复成功',
        });
        initPage();
      })
      .catch(() => {
        loading.value = false;
      });
  });
};
initPage();
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
        <ElFormItem label="用户昵称" prop="nickname">
          <ElInput
            v-model="state.queryParams.nickname"
            clearable
            placeholder="请输入用户昵称"
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
        <div></div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="name" label="评价用户" width="200">
          <template #default="scope">
            <div style="display: flex; align-items: center">
              <ElAvatar :src="scope.row.avatarUrl" />
              <span>{{ scope.row.nickname }}</span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="name" label="商品信息" width="340">
          <template #default="scope">
            <ElRow :gutter="10">
              <ElCol :span="6">
                <ElImage
                  style="width: 60px; height: 60px"
                  :src="scope.row.spuUrls[0]"
                  fit="cover"
                  :preview-teleported="true"
                />
              </ElCol>
              <ElCol :span="18">
                {{ scope.row.spuName }}
              </ElCol>
            </ElRow>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="goodsScore" label="商品评分" width="200">
          <template #default="scope">
            <ElRate
              v-model="scope.row.goodsScore"
              disabled
              show-score
              text-color="#ff9900"
              score-template="{value} 评分"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="content" label="评价内容" min-width="250" />
        <ElTableColumn prop="picUrls" label="评价图" width="140">
          <template #default="scope">
            <ElImage
              v-if="
                scope.row.picUrls &&
                scope.row.picUrls.length > 0 &&
                scope.row.picUrls[0]
              "
              style="width: 60px; height: 60px"
              :src="scope.row.picUrls[0]"
              fit="cover"
              :preview-teleported="true"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="评价时间" width="200" />
        <ElTableColumn
          prop="businessReply"
          label="商家回复"
          min-width="200"
          fixed="right"
        >
          <template #default="scope">
            <div v-if="scope.row.businessReply">
              {{ scope.row.businessReply }}
            </div>
            <div v-else>
              <ElButton type="primary" @click="handleReply(scope.row.id)">
                回复
              </ElButton>
            </div>
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
