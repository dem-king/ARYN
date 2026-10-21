<script lang="ts" setup>
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';

import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  deleteNewbieActivity,
  getNewbieConflicts,
  getNewbiePage,
  pauseNewbieActivity,
  publishNewbieActivity,
} from '#/api/promotion/newbie-activity';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const router = useRouter();
const { hasAccessByCodes } = useAccess();

const state = reactive({
  page: { currentPage: 1, pageSize: 10, total: 0 },
  query: { status: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const statusLabel: Record<string, string> = {
  '1': '草稿',
  '2': '已发布',
  '3': '已暂停',
  '4': '已结束',
  '5': '待开始',
};
const statusTagType: Record<string, string> = {
  '1': 'info',
  '2': 'success',
  '3': 'warning',
  '4': 'info',
  '5': 'warning',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getNewbiePage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      status: state.query.status,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

/** 规则摘要：新人价商品数与注册时效（列表不展开逐 SKU 价格） */
const rulesSummary = (row: any) => {
  try {
    const rules = JSON.parse(row.rules ?? '{}');
    const skuCount = (rules.skus ?? []).length;
    const days = rules.registerWithinDays;
    const scope = days ? `注册 ${days} 天内` : '不限注册时效';
    return `${skuCount} 个商品 · ${scope} · 限购 1 件`;
  } catch {
    return '规则解析失败';
  }
};

const goEdit = (row?: any) => {
  router.push({
    path: '/promotion/newbie-activity/edit',
    query: row?.id ? { id: row.id } : {},
  });
};

const onPublish = async (row: any) => {
  try {
    await publishNewbieActivity(row.id, false);
    ElMessage.success('发布成功');
    await initPage();
  } catch (error: any) {
    // 同类活动时间重叠时给出名单并允许强制发布（与船供活动一致的交互）
    const message = error?.message ?? '';
    if (!message.includes('时间重叠')) {
      throw error;
    }
    const conflicts = await getNewbieConflicts(row.id).catch(() => []);
    const names = (conflicts as any[])
      .map((item) => item.activityName)
      .join('、');
    await ElMessageBox.confirm(
      `与已发布活动时间重叠：${names || message}。运行时同类取最优，确认强制发布?`,
      '提示',
      {
        cancelButtonText: '取消',
        confirmButtonText: '仍要发布',
        type: 'warning',
      },
    );
    await publishNewbieActivity(row.id, true);
    ElMessage.success('发布成功');
    await initPage();
  }
};

const onPause = async (row: any) => {
  await pauseNewbieActivity(row.id);
  ElMessage.success('已暂停');
  await initPage();
};

const onDelete = async (row: any) => {
  await ElMessageBox.confirm('确认删除该新人专享活动?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确认删除',
    type: 'warning',
  });
  await deleteNewbieActivity(row.id);
  ElMessage.success('删除成功');
  await initPage();
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            v-if="hasAccessByCodes(['promotion:newbieactivity:save'])"
            type="primary"
            @click="goEdit()"
          >
            新增
          </ElButton>
        </div>
        <div>
          <ElSelect
            v-model="state.query.status"
            clearable
            placeholder="状态"
            style="width: 120px"
            @change="initPage"
          >
            <ElOption label="草稿" value="1" />
            <ElOption label="已发布" value="2" />
            <ElOption label="已暂停" value="3" />
            <ElOption label="已结束" value="4" />
            <ElOption label="待开始" value="5" />
          </ElSelect>
        </div>
      </div>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn label="活动名称" min-width="160" prop="activityName" />
        <ElTableColumn label="规则" min-width="220">
          <template #default="{ row }">
            <span>{{ rulesSummary(row) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="开始时间" prop="startTime" width="170" />
        <ElTableColumn label="结束时间" prop="endTime" width="170" />
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="(statusTagType[row.status] as any) ?? 'info'">
              {{ statusLabel[row.status] ?? row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="操作" width="240">
          <template #default="{ row }">
            <ElButton
              v-if="
                hasAccessByCodes(['promotion:newbieactivity:save']) &&
                ['1', '3'].includes(row.status)
              "
              link
              type="primary"
              @click="goEdit(row)"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="
                hasAccessByCodes(['promotion:newbieactivity:publish']) &&
                row.status === '2'
              "
              link
              type="warning"
              @click="onPause(row)"
            >
              暂停
            </ElButton>
            <ElButton
              v-if="
                hasAccessByCodes(['promotion:newbieactivity:publish']) &&
                ['1', '3', '5'].includes(row.status)
              "
              link
              type="success"
              @click="onPublish(row)"
            >
              发布
            </ElButton>
            <ElButton
              v-if="
                hasAccessByCodes(['promotion:newbieactivity:save']) &&
                row.status !== '2'
              "
              link
              type="danger"
              @click="onDelete(row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <span>暂无新人专享活动，点击「新增」创建</span>
        </template>
      </ElTable>

      <Pagination
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />
    </div>
  </div>
</template>
