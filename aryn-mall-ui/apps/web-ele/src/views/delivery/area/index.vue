<script lang="ts" setup name="deliveryArea">
import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElCard,
  ElCascader,
  ElDialog,
  ElForm,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  createDeliveryArea,
  deleteDeliveryArea,
  getDeliveryAreaPage,
  updateDeliveryArea,
} from '#/api/delivery/area';
import { getRegionTree } from '#/api/delivery/config';

/** 省市区级联数据节点 */
interface RegionNode {
  name: string;
  code?: string;
  children?: RegionNode[];
}

const loading = ref(false);
const tableData = ref<any[]>([]);
const total = ref(0);
const dialogVisible = ref(false);
const dialogTitle = ref('新增配送范围');
const submitting = ref(false);

const queryParams = reactive({
  current: 1,
  size: 10,
});

const form = reactive({
  id: undefined as string | undefined,
  provinceCode: '',
  provinceName: '',
  cityCode: '',
  cityName: '',
  areaCode: '',
  areaName: '',
  enabled: '1',
});

const regionOptions = ref<RegionNode[]>([]);
/** 级联选中值（省市区编码数组） */
const regionValue = ref<string[]>([]);

/** ElCascader 配置：按 code 取值、按 name 展示 */
const cascaderProps = {
  label: 'name',
  value: 'code',
  children: 'children',
  expandTrigger: 'hover' as const,
};

/**
 * 加载省市区级联数据
 */
const loadRegionTree = () => {
  getRegionTree()
    .then((response: any) => {
      regionOptions.value = response || [];
    })
    .catch(() => {
      regionOptions.value = [];
    });
};

/**
 * 按编码路径在省市区树中定位节点
 */
const findRegionPath = (codes: string[]): RegionNode[] => {
  const path: RegionNode[] = [];
  let level = regionOptions.value;
  for (const code of codes) {
    const node = level.find((item) => item.code === code);
    if (!node) {
      return [];
    }
    path.push(node);
    level = node.children ?? [];
  }
  return path;
};

/**
 * 省市区级联变化，回填编码与名称
 */
const handleRegionChange = (value: any) => {
  if (Array.isArray(value) && value.length === 3) {
    const [province, city, area] = findRegionPath(value as string[]);
    if (province && city && area) {
      form.provinceCode = province.code ?? '';
      form.provinceName = province.name;
      form.cityCode = city.code ?? '';
      form.cityName = city.name;
      form.areaCode = area.code ?? '';
      form.areaName = area.name;
    }
  }
};

const statusMap: Record<string, { label: string; type: any }> = {
  '1': { label: '启用', type: 'success' },
  '0': { label: '禁用', type: 'info' },
};

const loadData = () => {
  loading.value = true;
  getDeliveryAreaPage(queryParams)
    .then((res: any) => {
      tableData.value = res?.records ?? [];
      total.value = res?.total ?? 0;
    })
    .finally(() => {
      loading.value = false;
    });
};

const handleAdd = () => {
  dialogTitle.value = '新增配送范围';
  Object.assign(form, {
    id: undefined,
    provinceCode: '',
    provinceName: '',
    cityCode: '',
    cityName: '',
    areaCode: '',
    areaName: '',
    enabled: '1',
  });
  regionValue.value = [];
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑配送范围';
  Object.assign(form, row);
  regionValue.value =
    row.provinceCode && row.cityCode && row.areaCode
      ? [row.provinceCode, row.cityCode, row.areaCode]
      : [];
  dialogVisible.value = true;
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm('确认删除该配送范围？', '提示', {
    type: 'warning',
  }).then(() => {
    deleteDeliveryArea(row.id).then(() => {
      ElMessage.success('删除成功');
      loadData();
    });
  });
};

const handleSubmit = () => {
  if (!form.provinceCode || !form.cityCode || !form.areaCode) {
    ElMessage.warning('请选择省/市/区县');
    return;
  }
  submitting.value = true;
  const action = form.id ? updateDeliveryArea(form) : createDeliveryArea(form);
  action
    .then(() => {
      ElMessage.success('保存成功');
      dialogVisible.value = false;
      loadData();
    })
    .finally(() => {
      submitting.value = false;
    });
};

const handlePageChange = (page: number) => {
  queryParams.current = page;
  loadData();
};

onMounted(() => {
  loadData();
  loadRegionTree();
});
</script>
<template>
  <div class="delivery-area-root">
    <div class="hx-layout-container">
      <div class="hx-layout-container-auto hx-layout-container-view">
        <ElCard v-loading="loading">
          <template #header>
            <div class="card-header">
              <span>配送范围管理</span>
              <ElButton type="primary" @click="handleAdd">新增</ElButton>
            </div>
          </template>
          <ElTable :data="tableData" border style="width: 100%">
            <ElTableColumn label="省" prop="provinceName" />
            <ElTableColumn label="市" prop="cityName" />
            <ElTableColumn label="区县" prop="areaName" />
            <ElTableColumn label="状态" width="100">
              <template #default="{ row }">
                <ElTag :type="statusMap[row.enabled]?.type || 'info'">
                  {{ statusMap[row.enabled]?.label || '未知' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="创建时间" prop="createTime" width="180" />
            <ElTableColumn label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <ElButton type="primary" link @click="handleEdit(row)">
                  编辑
                </ElButton>
                <ElButton type="danger" link @click="handleDelete(row)">
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <ElPagination
            :current-page="queryParams.current"
            :page-size="queryParams.size"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="handlePageChange"
          />
        </ElCard>
      </div>
    </div>

    <ElDialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <ElForm :model="form" label-width="100px">
        <ElFormItem label="所在地区">
          <ElCascader
            v-model="regionValue"
            :options="regionOptions"
            :props="cascaderProps"
            placeholder="请选择省/市/区县"
            style="width: 100%"
            @change="handleRegionChange"
          />
        </ElFormItem>
        <ElFormItem label="启用状态">
          <ElSwitch
            v-model="form.enabled"
            active-value="1"
            inactive-value="0"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>
<style lang="scss" scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
