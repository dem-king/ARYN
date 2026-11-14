<script lang="ts" setup name="deliveryFreightRule">
import { onMounted, reactive, ref } from 'vue';

import { Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getRegionTree } from '#/api/delivery/config';
import {
  createFreightRule,
  deleteFreightRule,
  getFreightRulePage,
  updateFreightRule,
} from '#/api/delivery/freight-rule';
import PageToolbar from '#/components/page/page-toolbar.vue';

/** 省市区级联数据节点（本页只取省一级，用于偏远地区加价规则） */
interface RegionNode {
  name: string;
  code?: string;
  children?: RegionNode[];
}

const loading = ref(false);
const tableData = ref<any[]>([]);
const total = ref(0);
const dialogVisible = ref(false);
const dialogTitle = ref('新增运费规则');
const submitting = ref(false);

const queryParams = reactive({
  current: 1,
  size: 10,
  ruleName: '',
  bizScene: '',
  enabled: '',
});

const form = reactive({
  id: undefined as string | undefined,
  ruleName: '',
  bizScene: '0',
  deliveryWay: '1',
  provinceCodes: [] as string[],
  thresholdAmount: 0,
  baseFreight: 0,
  maxFreight: undefined as number | undefined,
  priority: 0,
  enabled: '1',
  remark: '',
});

const sceneOptions = [
  { value: '0', label: '全部场景' },
  { value: '1', label: '海员个人' },
  { value: '2', label: '船供采购' },
  { value: '3', label: '企业采购' },
];

const deliveryWayOptions = [
  { value: '1', label: '普通快递' },
  { value: '3', label: '公司配送（岸上）' },
  { value: '0', label: '全部（快递 + 岸上配送）' },
];

const sceneMap: Record<string, string> = Object.fromEntries(
  sceneOptions.map((item) => [item.value, item.label]),
);
const deliveryWayMap: Record<string, string> = Object.fromEntries(
  deliveryWayOptions.map((item) => [item.value, item.label]),
);

/** 省一级选项：规则留空=全国，选中省份=该省专属（偏远加价用） */
interface ProvinceOption {
  name: string;
  code: string;
}
const provinceOptions = ref<ProvinceOption[]>([]);

const loadProvinceOptions = () => {
  getRegionTree()
    .then((response: any) => {
      // 后端地区树可能存在无 code 的节点，这里过滤掉，避免选项值为 undefined 导致提交脏数据
      provinceOptions.value = (response || [])
        .filter((item: RegionNode) => Boolean(item.code))
        .map((item: RegionNode) => ({
          name: item.name,
          code: item.code as string,
        }));
    })
    .catch(() => {
      provinceOptions.value = [];
    });
};

const loadData = () => {
  loading.value = true;
  getFreightRulePage({
    current: queryParams.current,
    size: queryParams.size,
    ruleName: queryParams.ruleName || undefined,
    bizScene: queryParams.bizScene || undefined,
    enabled: queryParams.enabled || undefined,
  })
    .then((res: any) => {
      tableData.value = res?.records ?? [];
      total.value = res?.total ?? 0;
    })
    .finally(() => {
      loading.value = false;
    });
};

const handleSearch = () => {
  queryParams.current = 1;
  loadData();
};

const handleAdd = () => {
  dialogTitle.value = '新增运费规则';
  Object.assign(form, {
    id: undefined,
    ruleName: '',
    bizScene: '0',
    deliveryWay: '1',
    provinceCodes: [],
    thresholdAmount: 0,
    baseFreight: 0,
    maxFreight: undefined,
    priority: 0,
    enabled: '1',
    remark: '',
  });
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑运费规则';
  Object.assign(form, {
    id: row.id,
    ruleName: row.ruleName ?? '',
    bizScene: row.bizScene ?? '0',
    deliveryWay: row.deliveryWay ?? '1',
    provinceCodes: row.provinceCodes
      ? String(row.provinceCodes).split(',').filter(Boolean)
      : [],
    thresholdAmount: Number(row.thresholdAmount ?? 0),
    baseFreight: Number(row.baseFreight ?? 0),
    maxFreight:
      row.maxFreight === null || row.maxFreight === undefined
        ? undefined
        : Number(row.maxFreight),
    priority: Number(row.priority ?? 0),
    enabled: row.enabled ?? '1',
    remark: row.remark ?? '',
  });
  dialogVisible.value = true;
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确认删除运费规则「${row.ruleName}」？`, '提示', {
    type: 'warning',
  }).then(() => {
    deleteFreightRule(row.id).then(() => {
      ElMessage.success('删除成功');
      loadData();
    });
  });
};

const handleSubmit = () => {
  if (!form.ruleName) {
    ElMessage.warning('请填写规则名称');
    return;
  }
  if (form.thresholdAmount > 0 && form.baseFreight <= 0) {
    ElMessage.warning('设置包邮门槛时，基础运费必须大于 0');
    return;
  }
  if (form.thresholdAmount === 0) {
    ElMessage.warning('门槛为 0 表示不设免邮门槛，将始终收取基础运费');
  }
  const payload = {
    ...form,
    provinceCodes: form.provinceCodes.join(','),
    maxFreight: form.maxFreight ?? null,
  };
  submitting.value = true;
  const action = form.id
    ? updateFreightRule(payload)
    : createFreightRule(payload);
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

const formatProvince = (row: any) => {
  if (!row.provinceCodes) {
    return '全国';
  }
  const names = String(row.provinceCodes)
    .split(',')
    .filter(Boolean)
    .map(
      (code: string) =>
        provinceOptions.value.find((item) => item.code === code)?.name ?? code,
    );
  return names.join('、');
};

const statusMap: Record<string, { label: string; type: any }> = {
  '1': { label: '启用', type: 'success' },
  '0': { label: '禁用', type: 'info' },
};

onMounted(() => {
  loadData();
  loadProvinceOptions();
});
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <PageToolbar :total="total" total-label="条运费规则">
        <template #left>
          <ElButton type="primary" :icon="Plus" @click="handleAdd">
            新增规则
          </ElButton>
        </template>
        <template #right>
          <div class="flex items-center gap-2">
            <ElInput
              v-model="queryParams.ruleName"
              placeholder="规则名称"
              clearable
              style="width: 160px"
              @keyup.enter="handleSearch"
            />
            <ElSelect
              v-model="queryParams.bizScene"
              placeholder="场景"
              clearable
              style="width: 130px"
            >
              <ElOption
                v-for="item in sceneOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
            <ElSelect
              v-model="queryParams.enabled"
              placeholder="状态"
              clearable
              style="width: 110px"
            >
              <ElOption label="启用" value="1" />
              <ElOption label="禁用" value="0" />
            </ElSelect>
            <ElButton @click="handleSearch">查询</ElButton>
          </div>
        </template>
      </PageToolbar>

      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="tableData"
        border
        empty-text="暂无运费规则"
        style="width: 100%"
      >
        <ElTableColumn prop="ruleName" label="规则名称" min-width="180" />
        <ElTableColumn label="购买场景" width="120">
          <template #default="{ row }">
            {{ sceneMap[row.bizScene] || '未知' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="配送方式" width="160">
          <template #default="{ row }">
            {{ deliveryWayMap[row.deliveryWay] || '未知' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="适用地区" min-width="140">
          <template #default="{ row }">
            {{ formatProvince(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="包邮门槛" width="120">
          <template #default="{ row }">
            {{
              row.thresholdAmount > 0
                ? `满 ¥${row.thresholdAmount}`
                : '不设门槛'
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="基础运费" width="120">
          <template #default="{ row }">¥{{ row.baseFreight }}</template>
        </ElTableColumn>
        <ElTableColumn label="封顶" width="100">
          <template #default="{ row }">
            {{ row.maxFreight ? `¥${row.maxFreight}` : '不封顶' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="priority"
          label="优先级"
          width="90"
          align="center"
        />
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="statusMap[row.enabled]?.type || 'info'">
              {{ statusMap[row.enabled]?.label || '未知' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="150" fixed="right">
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

      <ElDialog v-model="dialogVisible" :title="dialogTitle" width="580px">
        <ElForm :model="form" label-width="120px">
          <ElFormItem label="规则名称" required>
            <ElInput
              v-model="form.ruleName"
              placeholder="如：海员个人-普通快递"
            />
          </ElFormItem>
          <ElFormItem label="购买场景">
            <ElSelect v-model="form.bizScene" style="width: 100%">
              <ElOption
                v-for="item in sceneOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="配送方式">
            <ElSelect v-model="form.deliveryWay" style="width: 100%">
              <ElOption
                v-for="item in deliveryWayOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
            <div class="mt-1 text-xs text-gray-400">
              自提与到船配送恒定免运费，不在此配置
            </div>
          </ElFormItem>
          <ElFormItem label="适用省份">
            <ElSelect
              v-model="form.provinceCodes"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="留空=全国；选中省份=该省专属（偏远加价用）"
              style="width: 100%"
            >
              <ElOption
                v-for="item in provinceOptions"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="包邮门槛">
            <ElInputNumber
              v-model="form.thresholdAmount"
              :min="0"
              :precision="2"
              :step="1"
              style="width: 180px"
            />
            <span class="ml-2 text-xs text-gray-400">
              0 = 不设门槛，始终收基础运费
            </span>
          </ElFormItem>
          <ElFormItem label="基础运费">
            <ElInputNumber
              v-model="form.baseFreight"
              :min="0"
              :precision="2"
              :step="1"
              style="width: 180px"
            />
            <span class="ml-2 text-xs text-gray-400"> 未达门槛时收取 </span>
          </ElFormItem>
          <ElFormItem label="运费封顶">
            <ElInputNumber
              v-model="form.maxFreight"
              :min="0.01"
              :precision="2"
              :step="1"
              placeholder="留空不封顶"
              style="width: 180px"
            />
          </ElFormItem>
          <ElFormItem label="优先级">
            <ElInputNumber
              v-model="form.priority"
              :min="0"
              :step="1"
              style="width: 180px"
            />
            <span class="ml-2 text-xs text-gray-400">
              数值大者优先；指定省份的规则优先于全国规则
            </span>
          </ElFormItem>
          <ElFormItem label="运营备注">
            <ElInput
              v-model="form.remark"
              type="textarea"
              :rows="2"
              placeholder="记录定价依据，便于后续复盘"
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
          <div class="hx-form-footer">
            <ElButton @click="dialogVisible = false">取消</ElButton>
            <ElButton
              type="primary"
              :loading="submitting"
              @click="handleSubmit"
            >
              确定
            </ElButton>
          </div>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
