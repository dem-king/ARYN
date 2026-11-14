<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  addEnterpriseLevel,
  deleteEnterpriseLevel,
  getEnterpriseLevels,
  updateEnterpriseLevel,
} from '#/api/user/enterprise-member';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

/**
 * 企业会员等级（双轨会员企业轨）：按近 12 个月主体采购额分档。
 * 主体维度：岸上企业（buyer_org）/ 船舶（vessel）；与个人会员等级互不干扰。
 */

const state = reactive({
  tableData: [] as any[],
});
const loading = ref(false);
const drawer = ref(false);
const saving = ref(false);
const editingId = ref<null | string>(null);

const form = reactive({
  conditionValue: 0,
  levelIcon: '',
  levelName: '',
  sortOrder: 0,
});

const load = async () => {
  loading.value = true;
  try {
    state.tableData = (await getEnterpriseLevels()) as any[];
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  editingId.value = null;
  Object.assign(form, {
    conditionValue: 0,
    levelIcon: '',
    levelName: '',
    sortOrder: 0,
  });
  drawer.value = true;
};

const openEdit = (row: any) => {
  editingId.value = row.id;
  Object.assign(form, {
    conditionValue: row.conditionValue ?? 0,
    levelIcon: row.levelIcon ?? '',
    levelName: row.levelName,
    sortOrder: row.sortOrder ?? 0,
  });
  drawer.value = true;
};

const save = async () => {
  if (!form.levelName) {
    ElMessage.warning('请填写等级名称');
    return;
  }
  saving.value = true;
  try {
    if (editingId.value) {
      await updateEnterpriseLevel(editingId.value, { ...form });
      ElMessage.success('已保存（等级重算在订单完成/月度对账时生效）');
    } else {
      await addEnterpriseLevel({ ...form });
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
    `删除企业等级「${row.levelName}」？挂载主体将回到未定级（下次重算按新配置定级）。`,
    '提示',
    { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
  ).then(async () => {
    await deleteEnterpriseLevel(row.id);
    ElMessage.success('删除成功');
    load();
  });
};

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
            v-access:code="'user:enterpriselevel:save'"
            :icon="Plus"
            @click="openCreate"
          >
            新增等级
          </ElButton>
        </template>
      </PageToolbar>

      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无企业等级"
      >
        <ElTableColumn prop="levelName" label="等级名称" />
        <ElTableColumn prop="sortOrder" label="排序（越大越高）" width="140" />
        <ElTableColumn label="升级门槛（近12月采购额）" min-width="180">
          <template #default="scope">
            ¥{{ scope.row.conditionValue }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="150" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'user:enterpriselevel:save'"
              :icon="Edit"
              @click="openEdit(scope.row)"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'user:enterpriselevel:del'"
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
        :title="editingId ? '编辑企业等级' : '新增企业等级'"
        width="480px"
        destroy-on-close
      >
        <ElForm label-width="140px">
          <ElFormItem label="等级名称">
            <ElInput v-model="form.levelName" placeholder="如：核心客户" />
          </ElFormItem>
          <ElFormItem label="排序（越大越高）">
            <ElInputNumber v-model="form.sortOrder" :min="0" :precision="0" />
          </ElFormItem>
          <ElFormItem label="门槛（近12月采购额）">
            <ElInputNumber
              v-model="form.conditionValue"
              :min="0"
              :precision="2"
              :step="10000"
            />
          </ElFormItem>
          <ElFormItem label="徽章图 URL">
            <ElInput v-model="form.levelIcon" placeholder="选填" />
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
