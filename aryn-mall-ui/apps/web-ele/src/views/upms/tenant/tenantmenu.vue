<script lang="ts" setup>
import { nextTick, reactive, ref } from 'vue';

import { ElButton, ElDialog, ElMessage, ElTree } from 'element-plus';

import { getList } from '#/api/upms/menu';
import { getTenantMenuList, saveTenantMenu } from '#/api/upms/tenant';

interface State {
  menuList: Array<any>;
  menuIds: Array<string>;
  sysRole: any;
}
const emit = defineEmits(['initPage']);
const menuRef = ref();
const dialog = ref(false);
const tenantId = ref();
const state = reactive<State>({ menuList: [], menuIds: [], sysRole: {} });
const loading = ref(false);
const defaultProps = ref({
  children: 'children',
  label: 'name',
  disabled: 'disabled',
});
/** 不可分配给租户的菜单（系统内置 applicationKey） */
const SYS_APP_KEY = 'sys_key';
const sysKeyMenuIdSet = ref<Set<string>>(new Set());

function markSysKeyMenusDisabled(nodes: any[]) {
  for (const node of nodes) {
    if (node.applicationKey === SYS_APP_KEY) {
      node.disabled = true;
    }
    if (node.children?.length) {
      markSysKeyMenusDisabled(node.children);
    }
  }
}

function collectSysKeyMenuIds(nodes: any[], out: Set<string>) {
  for (const node of nodes) {
    if (node.applicationKey === SYS_APP_KEY) {
      out.add(node.id);
    }
    if (node.children?.length) {
      collectSysKeyMenuIds(node.children, out);
    }
  }
}

/**
 * 选择菜单权限事件
 */
const handleChange = () => {
  const checkedKeys = menuRef.value.getCheckedKeys(false);
  const halfCheckedKeys = menuRef.value.getHalfCheckedKeys(false);
  state.menuIds = [...checkedKeys, ...halfCheckedKeys];
};

/**
 * 获取指定角色的菜单
 */
const getTenantMenus = async (tenantIdParam: string) => {
  /**
   * 查询菜单树数据
   */
  await getList().then((response) => {
    state.menuList = response;
    markSysKeyMenusDisabled(state.menuList);
    const set = new Set<string>();
    collectSysKeyMenuIds(state.menuList, set);
    sysKeyMenuIdSet.value = set;
  });
  await getTenantMenuList(tenantIdParam).then((response) => {
    if (response) {
      state.menuIds = response
        .map((v: { menuId: string }) => v.menuId)
        .filter((menuId: string) => !sysKeyMenuIdSet.value.has(menuId));
      if (state.menuIds.length > 0) {
        state.menuIds.forEach((menuId: string) => {
          nextTick(() => {
            menuRef.value?.setChecked(menuId, true, false);
          });
        });
      }
    }
  });
};
const onsubmit = () => {
  loading.value = true;
  saveTenantMenu({ menuIds: state.menuIds, tenantId: tenantId.value }).then(
    () => {
      loading.value = false;
      ElMessage.success('更新成功');
      dialog.value = false;
      emit('initPage');
    },
  );
};
const initMenu = (id: string) => {
  dialog.value = true;
  tenantId.value = id;
  getTenantMenus(id);
};
defineExpose({
  initMenu,
});
</script>
<template>
  <ElDialog v-model="dialog" title="分配菜单" width="50%" destroy-on-close>
    <div v-loading="loading">
      <ElTree
        ref="menuRef"
        :data="state.menuList"
        :props="defaultProps"
        node-key="id"
        show-checkbox
        @check="handleChange"
      >
        <template #default="{ node }">
          <span class="custom-tree-node">
            <span>{{ $t(node.label) }}</span>
          </span>
        </template>
      </ElTree>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="dialog = false">取 消</ElButton>
        <ElButton type="primary" @click="onsubmit" :loading="loading">
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
<style>
.hx-menus {
  width: 100%;
  height: 500px;
  padding: 4px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}
</style>
