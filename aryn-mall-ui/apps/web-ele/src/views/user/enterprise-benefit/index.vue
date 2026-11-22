<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  addEnterpriseBenefit,
  deleteEnterpriseBenefit,
  getBenefitLevelRels,
  getEnterpriseBenefits,
  getEnterpriseLevels,
  updateEnterpriseBenefit,
} from '#/api/user/enterprise-member';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

/**
 * 企业会员权益（双轨会员企业轨）。
 * 权益类型：1企业折扣率 2免邮 3协议价政策池 4开票资质 5专属客服 6返利率(预留) 7账期天数(预留)。
 * 返利率/账期天数本期仅配置不生效（二期资金域启用）。
 */

const BENEFIT_TYPE: Record<string, string> = {
  1: '企业折扣率',
  2: '免邮',
  3: '协议价政策池',
  4: '开票资质',
  5: '专属客服',
  6: '返利率（预留）',
  7: '账期天数（预留）',
};

const state = reactive({
  levels: [] as any[],
  tableData: [] as any[],
});
const loading = ref(false);
const drawer = ref(false);
const saving = ref(false);
const editingId = ref<null | string>(null);
const levelIds = ref<string[]>([]);

const form = reactive({
  benefitName: '',
  benefitType: '1',
  benefitValue: '0.95',
  description: '',
  status: '0',
});

// 值域提示：按权益类型变化（校验在后端，前端给引导）
const valuePlaceholder = computed(() => {
  switch (form.benefitType) {
    case '1': {
      return '折扣率，如 0.95 = 95折';
    }
    case '2':
    case '4':
    case '5': {
      return '固定填 1';
    }
    case '3': {
      return '价格政策ID，多个英文逗号分隔';
    }
    case '6': {
      return '返利率百分数，如 1.5';
    }
    case '7': {
      return '账期天数，如 30';
    }
    default: {
      return '';
    }
  }
});

/** 返利率(6)/账期天数(7) 为二期资金域预留，本期仅保存配置、不参与成交计算 */
const isReservedBenefit = computed(
  () => form.benefitType === '6' || form.benefitType === '7',
);

const load = async () => {
  loading.value = true;
  try {
    state.tableData = (await getEnterpriseBenefits()) as any[];
    state.levels = (await getEnterpriseLevels()) as any[];
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  editingId.value = null;
  levelIds.value = [];
  Object.assign(form, {
    benefitName: '',
    benefitType: '1',
    benefitValue: '0.95',
    description: '',
    status: '0',
  });
  drawer.value = true;
};

const openEdit = async (row: any) => {
  editingId.value = row.id;
  Object.assign(form, {
    benefitName: row.benefitName,
    benefitType: row.benefitType,
    benefitValue: row.benefitValue,
    description: row.description ?? '',
    status: row.status ?? '0',
  });
  levelIds.value = ((await getBenefitLevelRels(row.id)) as any[]) || [];
  drawer.value = true;
};

const save = async () => {
  if (!form.benefitName) {
    ElMessage.warning('请填写权益名称');
    return;
  }
  if (!form.benefitValue) {
    ElMessage.warning('请填写权益值');
    return;
  }
  saving.value = true;
  try {
    const payload = { benefit: { ...form }, levelIds: levelIds.value };
    if (editingId.value) {
      await updateEnterpriseBenefit(editingId.value, payload);
      ElMessage.success('已保存');
    } else {
      await addEnterpriseBenefit(payload);
      ElMessage.success('已新增');
    }
    drawer.value = false;
    load();
  } finally {
    saving.value = false;
  }
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `删除企业权益「${row.benefitName}」并解除挂载？`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(async () => {
    await deleteEnterpriseBenefit(row.id);
    ElMessage.success('删除成功');
    load();
  });
};

const levelName = (id: string) =>
  state.levels.find((level) => level.id === id)?.levelName || id;

load();
useKeepAliveRefresh(load);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <PageToolbar total-label="">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'user:enterprisebenefit:save'"
            :icon="Plus"
            @click="openCreate"
          >
            新增权益
          </ElButton>
        </template>
      </PageToolbar>

      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无企业权益"
      >
        <ElTableColumn prop="benefitName" label="权益名称" min-width="140" />
        <ElTableColumn label="权益类型" width="140">
          <template #default="scope">
            {{ BENEFIT_TYPE[scope.row.benefitType] || '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="benefitValue" label="权益值" min-width="140" />
        <ElTableColumn prop="description" label="说明" min-width="140" />
        <ElTableColumn label="状态" width="80">
          <template #default="scope">
            {{ scope.row.status === '0' ? '启用' : '禁用' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="150" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'user:enterprisebenefit:save'"
              :icon="Edit"
              @click="openEdit(scope.row)"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'user:enterprisebenefit:del'"
              :icon="Delete"
              @click="handleDelete(scope.row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <ElDialog
        v-model="drawer"
        :title="editingId ? '编辑企业权益' : '新增企业权益'"
        width="520px"
        destroy-on-close
      >
        <ElForm label-width="100px">
          <ElFormItem label="权益名称">
            <ElInput v-model="form.benefitName" />
          </ElFormItem>
          <ElFormItem label="权益类型">
            <ElSelect v-model="form.benefitType">
              <ElOption
                v-for="(text, type) in BENEFIT_TYPE"
                :key="type"
                :label="text"
                :value="type"
              />
            </ElSelect>
            <div v-if="isReservedBenefit" class="text-xs text-gray-400">
              预留，暂未生效：本期仅保存配置，二期资金域启用后才参与成交计算。
            </div>
          </ElFormItem>
          <ElFormItem label="权益值">
            <ElInput
              v-model="form.benefitValue"
              :placeholder="valuePlaceholder"
            />
          </ElFormItem>
          <ElFormItem label="说明">
            <ElInput v-model="form.description" type="textarea" :rows="2" />
          </ElFormItem>
          <ElFormItem label="挂载等级">
            <ElSelect v-model="levelIds" multiple style="width: 100%">
              <ElOption
                v-for="level in state.levels"
                :key="level.id"
                :label="level.levelName"
                :value="level.id"
              />
            </ElSelect>
            <div class="text-xs text-gray-400">
              已挂载等级{{
                levelIds.length > 0
                  ? `：${levelIds.map(levelName).join('、')}`
                  : '（空）'
              }}
            </div>
          </ElFormItem>
          <ElFormItem label="状态">
            <ElSelect v-model="form.status">
              <ElOption label="启用" value="0" />
              <ElOption label="禁用" value="1" />
            </ElSelect>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="drawer = false"> 取消 </ElButton>
          <ElButton type="primary" :loading="saving" @click="save">
            保存
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
