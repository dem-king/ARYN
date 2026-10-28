<script lang="ts" setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { backfillCoupon, delObj, getPage } from '#/api/user/member-benefit';
import { getList as getLevelList } from '#/api/user/member-level';
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

const { status } = useDict('status');
const state = reactive({
  queryParams: {
    benefitName: '',
    levelId: '',
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
const loading = ref(false);
const formRef = ref();
const levelList = ref<any[]>([]);
const levelMap = computed(
  () =>
    new Map<string, string>(
      levelList.value.map(
        (item) => [item.id, item.levelName] as [string, string],
      ),
    ),
);

const loadLevelList = async () => {
  try {
    levelList.value = (await getLevelList()) as any[];
  } catch {
    levelList.value = [];
  }
};

const levelNameText = (row: any) =>
  (row.levelIds || [])
    .map((id: string) => levelMap.value.get(id) || id)
    .join('、');

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
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

const handleAdd = () => {
  formRef.value.initForm();
};

const handleEdit = (row: any) => {
  formRef.value.initForm(row);
};

/** 权益值按类型格式化：折扣显示折、专属券显示模板 ID，避免列表直出原始值 */
const formatBenefitValue = (row: any) => {
  switch (row.benefitType) {
    case '1': {
      const rate = Number(row.benefitValue);
      if (!Number.isFinite(rate)) {
        return row.benefitValue;
      }
      return rate >= 1 ? '无折扣' : `${(rate * 10).toFixed(1)}折`;
    }
    case '2': {
      return '包邮';
    }
    case '3': {
      return `模板 ${row.benefitValue}`;
    }
    case '4': {
      return `${row.benefitValue}倍积分`;
    }
    default: {
      return row.benefitValue;
    }
  }
};

const handleBackfill = (row: any) => {
  ElMessageBox.confirm(
    `将给「${row.benefitName}」关联等级下的现有会员补发优惠券，是否继续?`,
    '提示',
    {
      confirmButtonText: '确认补发',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(async () => {
    const result: any = await backfillCoupon(row.id);
    ElMessage.success(
      `补发完成：成功 ${result.successCount} 人，失败 ${result.failedCount} 人`,
    );
  });
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `此操作将删除会员权益「${row.benefitName}」并解除等级关联，是否继续?`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    delObj(row.id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

onMounted(() => {
  loadLevelList();
  initPage();
});

useKeepAliveRefresh(initPage);
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="项权益">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'user:memberbenefit:add'"
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

      <!-- 筛选 -->
      <div class="mb-3 flex items-center gap-2">
        <ElInput
          v-model="state.queryParams.benefitName"
          placeholder="权益名称"
          clearable
          style="width: 220px"
          @keyup.enter="initPage"
        />
        <ElSelect
          v-model="state.queryParams.levelId"
          placeholder="关联等级"
          clearable
          style="width: 180px"
          @change="initPage"
        >
          <ElOption
            v-for="level in levelList"
            :key="level.id"
            :label="level.levelName"
            :value="level.id"
          />
        </ElSelect>
        <ElButton type="primary" @click="initPage"> 搜索 </ElButton>
      </div>

      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无会员权益"
      >
        <ElTableColumn prop="benefitName" label="权益名称" />
        <ElTableColumn prop="benefitType" label="权益类型" width="120">
          <template #default="scope">
            <ElTag v-if="scope.row.benefitType === '1'" type="success">
              折扣
            </ElTag>
            <ElTag v-else-if="scope.row.benefitType === '2'" type="warning">
              免运费
            </ElTag>
            <ElTag v-else-if="scope.row.benefitType === '3'" type="info">
              专属优惠券
            </ElTag>
            <ElTag v-else-if="scope.row.benefitType === '4'" type="danger">
              积分倍率
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="benefitValue" label="权益值" width="110">
          <template #default="scope">
            {{ formatBenefitValue(scope.row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="关联等级" min-width="160">
          <template #default="scope">
            <span v-if="levelNameText(scope.row)">
              {{ levelNameText(scope.row) }}
            </span>
            <span v-else class="text-[#f56c6c]">未关联</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="description" label="描述" />
        <ElTableColumn prop="status" label="状态" width="80">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="220" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'user:memberbenefit:edit'"
              @click="handleEdit(scope.row)"
              :icon="Edit"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="scope.row.benefitType === '3'"
              link
              type="warning"
              v-access:code="'user:memberbenefit:edit'"
              @click="handleBackfill(scope.row)"
            >
              补发
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'user:memberbenefit:del'"
              @click="handleDelete(scope.row)"
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
