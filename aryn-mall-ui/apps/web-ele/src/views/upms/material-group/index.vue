<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, editObj, getPage } from '#/api/upms/material-group';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

const emit = defineEmits(['initGroup']);

// 分组名长度与后端 sys_material_group.name varchar(10) 对齐
const GROUP_NAME_MAX_LENGTH = 10;
// 默认分组：后端 is_default='1'，每个租户有且仅有一个，由初始化/兜底逻辑落库
const isDefaultGroup = (row: any) => String(row?.isDefault) === '1';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const state = reactive<any>({
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [],
});

const loading = ref(false);
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
  };
  await getPage(params)
    .then((response) => {
      // 默认分组由后端返回并排在第一页第一条，前端不再插入虚拟行
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 校验分组名：空名与超长都会在落库时失败，提前拦截给出可读提示
 */
const validateGroupName = (value: unknown) => {
  const name = String(value ?? '').trim();
  if (name === '') {
    return '分组名不能为空';
  }
  if (name.length > GROUP_NAME_MAX_LENGTH) {
    return `分组名不能超过 ${GROUP_NAME_MAX_LENGTH} 个字符`;
  }
  return null;
};
/**
 * 修改素材分组
 *
 * 默认分组同样允许改名（用户希望「未分组」这个叫法可自定义），
 * 但不允许删除，删除入口在模板里按 isDefault 隐藏。
 */
const edit = (row: any) => {
  ElMessageBox.prompt('请输入分组名', '修改分组名', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputValue: row.name,
    inputValidator: (value: string) => {
      const message = validateGroupName(value);
      return message === null ? true : message;
    },
  })
    .then(({ value }) => {
      editObj({ id: row.id, name: String(value ?? '').trim() }).then(() => {
        ElMessage.success('修改成功');
        initPage();
        // 左侧分组树与素材区标题都取自分组名，改名后必须同步刷新
        emit('initGroup', true);
      });
    })
    .catch(() => {
      // 用户取消输入，无需提示
    });
};
/**
 * 删除分组
 */
const del = (row: any) => {
  if (isDefaultGroup(row)) {
    ElMessage.warning('默认分组不可删除');
    return;
  }
  ElMessageBox.confirm(
    '此操作将删除该分组，分组下的素材会移动到默认分组，是否继续?',
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
        emit('initGroup', true);
      })
      .catch(() => {
        loading.value = false;
      });
  });
};

defineExpose({
  initPage,
});

useKeepAliveRefresh(initPage);
</script>
<template>
  <div class="hx-layout-container-auto hx-layout-container-view">
    <ElTable
      v-loading="loading"
      class="hx-data-table"
      border
      :data="state.tableData"
      empty-text="暂无分组"
    >
      <ElTableColumn prop="name" label="分组名">
        <template #default="scope">
          <span>{{ scope.row.name }}</span>
          <ElTag
            v-if="isDefaultGroup(scope.row)"
            class="ml-2"
            size="small"
            type="info"
          >
            默认
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="200" align="center">
        <template #default="scope">
          <ElButton
            link
            type="primary"
            @click="edit(scope.row)"
            v-access:code="'upms:materialgroup:edit'"
            :icon="Edit"
          >
            修改
          </ElButton>
          <ElButton
            v-if="!isDefaultGroup(scope.row)"
            link
            type="danger"
            @click="del(scope.row)"
            v-access:code="'upms:materialgroup:del'"
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
</template>
