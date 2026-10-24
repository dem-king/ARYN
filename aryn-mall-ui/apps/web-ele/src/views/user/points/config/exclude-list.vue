<script lang="ts" setup>
import type { PointsExclude } from '#/api/user/points-config';

import { onMounted, reactive, ref } from 'vue';

import { Delete, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCascader,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage as getCategoryTree } from '#/api/product/goods-category';
import { getPage as getSpuPage } from '#/api/product/goods-spu';
import { addExclude, delExclude, listExclude } from '#/api/user/points-config';

/** 排除类型：1-类目；2-商品（SPU），与后端 PointsExclude 常量一致 */
const TYPE_CATEGORY = '1';
const TYPE_GOODS = '2';

const targetTypeOptions = [
  { value: TYPE_CATEGORY, label: '类目' },
  { value: TYPE_GOODS, label: '商品' },
];

const loading = ref(false);
const submitting = ref(false);
const tableData = ref<PointsExclude[]>([]);

// 类目树（ElCascader 数据源）
const categoryTree = ref<any[]>([]);
const cascaderProps = {
  label: 'name',
  value: 'id',
  children: 'children',
  emitPath: false,
  checkStrictly: true,
};

// 商品远程搜索
const spuLoading = ref(false);
const spuOptions = ref<Array<{ id: string; name: string }>>([]);

const addFormRef = ref();
const addForm = reactive({
  targetType: TYPE_CATEGORY,
  targetId: '',
  targetName: '',
});
const addRules = reactive({
  targetId: [{ required: true, message: '请选择排除目标', trigger: 'change' }],
  targetName: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
});

/** 从类目树按 id 反查名称（名称快照，避免类目改名后不可读） */
const findCategoryName = (nodes: any[], id: string): null | string => {
  for (const node of nodes) {
    if (String(node.id) === id) return node.name;
    const childName = findCategoryName(node.children ?? [], id);
    if (childName !== null) return childName;
  }
  return null;
};

const initList = async () => {
  loading.value = true;
  try {
    tableData.value = ((await listExclude()) ?? []) as PointsExclude[];
  } finally {
    loading.value = false;
  }
};

const loadCategoryTree = async () => {
  try {
    categoryTree.value = ((await getCategoryTree()) ?? []) as any[];
  } catch {
    categoryTree.value = [];
  }
};

const searchSpu = async (keyword: string) => {
  if (!keyword) {
    spuOptions.value = [];
    return;
  }
  spuLoading.value = true;
  try {
    const res: any = await getSpuPage({
      name: keyword,
      current: 1,
      size: 20,
    });
    spuOptions.value = (res?.records ?? []).map((item: any) => ({
      id: String(item.id),
      name: item.name,
    }));
  } finally {
    spuLoading.value = false;
  }
};

/** 类型切换后目标含义不同，清空已选目标避免错位提交 */
const handleTypeChange = () => {
  addForm.targetId = '';
  addForm.targetName = '';
  spuOptions.value = [];
};

const handleCategoryChange = (value: unknown) => {
  const id = value ? String(value) : '';
  addForm.targetId = id;
  addForm.targetName = id
    ? (findCategoryName(categoryTree.value, id) ?? '')
    : '';
};

const handleSpuChange = (value: unknown) => {
  const id = value ? String(value) : '';
  addForm.targetId = id;
  const matched = spuOptions.value.find((item) => item.id === id);
  addForm.targetName = matched?.name ?? '';
};

const handleSubmit = async () => {
  await addFormRef.value.validate();
  if (
    tableData.value.some(
      (item) =>
        item.targetType === addForm.targetType &&
        item.targetId === addForm.targetId,
    )
  ) {
    ElMessage.warning('该目标已在排除清单中，请勿重复添加');
    return;
  }
  submitting.value = true;
  try {
    await addExclude({
      targetType: addForm.targetType,
      targetId: addForm.targetId,
      targetName: addForm.targetName,
    });
    ElMessage.success('新增成功');
    addForm.targetId = '';
    addForm.targetName = '';
    await initList();
  } finally {
    submitting.value = false;
  }
};

const handleDelete = (row: any) => {
  const typeLabel = row.targetType === TYPE_CATEGORY ? '类目' : '商品';
  ElMessageBox.confirm(
    `移除后「${row.targetName}」（${typeLabel}）将恢复参与积分抵扣与返积分，是否继续?`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  )
    .then(() => {
      delExclude(row.id as string)
        .then(() => {
          ElMessage.success('移除成功');
          initList();
        })
        .catch(() => {});
    })
    .catch(() => {});
};

onMounted(() => {
  initList();
  loadCategoryTree();
});
</script>
<template>
  <div>
    <!-- 新增区：类目走类目树、商品走远程搜索，名称自动带出（可手动修正） -->
    <ElForm
      ref="addFormRef"
      :model="addForm"
      :rules="addRules"
      label-width="90px"
      inline
      class="exclude-add-form"
    >
      <ElFormItem label="排除类型">
        <ElRadioGroup v-model="addForm.targetType" @change="handleTypeChange">
          <ElRadio
            v-for="item in targetTypeOptions"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="排除目标" prop="targetId">
        <ElCascader
          v-if="addForm.targetType === TYPE_CATEGORY"
          :model-value="addForm.targetId"
          :options="categoryTree"
          :props="cascaderProps"
          clearable
          filterable
          placeholder="请选择类目（任意层级）"
          style="width: 240px"
          @update:model-value="handleCategoryChange"
        />
        <ElSelect
          v-else
          :model-value="addForm.targetId"
          filterable
          remote
          clearable
          :remote-method="searchSpu"
          :loading="spuLoading"
          placeholder="输入商品名称搜索"
          style="width: 240px"
          @change="handleSpuChange"
        >
          <ElOption
            v-for="item in spuOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="名称快照" prop="targetName">
        <ElInput
          v-model="addForm.targetName"
          placeholder="选择目标后自动带出，可修正"
          style="width: 240px"
        />
      </ElFormItem>
      <ElFormItem>
        <ElButton
          type="primary"
          v-access:code="'user:pointsconfig:add'"
          :loading="submitting"
          :icon="Plus"
          @click="handleSubmit"
        >
          新增
        </ElButton>
      </ElFormItem>
    </ElForm>
    <!-- 列表 -->
    <ElTable
      v-loading="loading"
      class="hx-data-table"
      :data="tableData"
      border
      empty-text="暂无排除规则，命中类目/商品将正常参与积分"
    >
      <ElTableColumn label="类型" width="100" align="center">
        <template #default="scope">
          <ElTag v-if="scope.row.targetType === TYPE_CATEGORY" type="warning">
            类目
          </ElTag>
          <ElTag v-else type="danger">商品</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="targetId" label="目标ID" width="220" />
      <ElTableColumn prop="targetName" label="名称" min-width="180" />
      <ElTableColumn label="操作" width="120" align="center">
        <template #default="scope">
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
  </div>
</template>

<style scoped>
.exclude-add-form {
  margin-bottom: 4px;
}
</style>
