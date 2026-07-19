<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { onMounted } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon, Plus } from '@vben/icons';
import { $t } from '@vben/locales';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj as deleteMenu, getList as getMenuList } from '#/api/upms/menu';

import { useColumns } from './data';
import Form from './modules/form.vue';

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      autoLoad: false,
      ajax: {
        query: async (_params) => {
          return { list: await getMenuList() };
        },
      },
      response: {
        list: 'list',
      },
    },
    rowConfig: {
      keyField: 'id',
    },
    toolbarConfig: {
      refresh: { code: 'query' },
    },
    treeConfig: {
      parentField: 'parentId',
      rowField: 'id',
      transform: false,
      reserve: true,
    },
  } as VxeTableGridOptions,
});

onMounted(() => {
  gridApi.query();
});

function onRefresh() {
  gridApi.query();
}
function onEdit(row: any) {
  formDrawerApi.setData(row).open();
}
function onCreate() {
  formDrawerApi.setData({}).open();
}
function onAppend(row: any) {
  formDrawerApi.setData({ parentId: row.id }).open();
}

function onDelete(row: any) {
  ElMessageBox.confirm('此操作将删除该菜单，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    deleteMenu(row.id)
      .then(() => {
        ElMessage.success($t('ui.actionMessage.deleteSuccess', [row.name]));
        onRefresh();
      })
      .catch(() => {
        // hideLoading();
      });
  });
}
</script>
<template>
  <Page auto-content-height>
    <FormDrawer @success="onRefresh" />
    <Grid>
      <template #toolbar-tools>
        <ElButton
          type="primary"
          @click="onCreate"
          v-access:code="'upms:sysmenu:add'"
        >
          <Plus class="size-5" />
          {{ $t('ui.actionTitle.create', ['菜单']) }}
        </ElButton>
      </template>
      <template #title="{ row }">
        <div class="flex w-full items-center gap-1">
          <div class="size-5 flex-shrink-0">
            <IconifyIcon
              v-if="row.type === '1'"
              icon="carbon:security"
              class="size-full"
            />
            <IconifyIcon
              v-else-if="row?.icon"
              :icon="row.icon || 'carbon:circle-dash'"
              class="size-full"
            />
          </div>
          <span class="flex-auto">{{ row?.name }}</span>
        </div>
      </template>
      <template #operation="{ row }">
        <ElButton
          @click="onEdit(row)"
          link
          v-access:code="'upms:sysmenu:edit'"
          type="primary"
        >
          修改菜单
        </ElButton>
        <ElButton
          link
          @click="onDelete(row)"
          v-access:code="'upms:sysmenu:del'"
          type="primary"
        >
          删除菜单
        </ElButton>
        <ElButton
          link
          @click="onAppend(row)"
          v-access:code="'upms:sysmenu:add'"
          type="primary"
        >
          新增下级
        </ElButton>
      </template>
    </Grid>
  </Page>
</template>
<style lang="scss" scoped></style>
