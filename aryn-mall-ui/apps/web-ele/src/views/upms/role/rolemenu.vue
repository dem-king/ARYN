<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue';

import { ElButton, ElDialog, ElMessage, ElTree } from 'element-plus';

import { getMenusByRole, getTenantMenu } from '#/api/upms/menu';
import { saveRoleMenu } from '#/api/upms/sys-role';

interface State {
  menuList: any[];
  menuIds: string[];
}
const emit = defineEmits(['initPage']);
// 字典
const menuRef = ref();
const dialog = ref(false);
const state = reactive<State>({ menuList: [], menuIds: [] });
const roleId = ref();
const loading = ref(false);
const defaultProps = {
  children: 'children',
  label: 'name',
  disabled: 'disabled',
};
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
const getRoleMenus = async (id: string) => {
  roleId.value = id;
  /**
   * 查询菜单树数据
   */
  await getTenantMenu().then((response: any) => {
    state.menuList = response;
  });
  await getMenusByRole(id).then((response: any) => {
    if (response) {
      state.menuIds = response.map((v: any) => v.id);
      state.menuIds.forEach((id: string) => {
        nextTick(() => {
          menuRef.value.setChecked(id, true, false);
        });
      });
    }
  });
};
const onsubmit = () => {
  loading.value = true;
  saveRoleMenu({ roleId: roleId.value, menuIds: state.menuIds }).then(() => {
    loading.value = false;
    ElMessage.success('更新成功');
    dialog.value = false;
    emit('initPage');
  });
};
const initRoleMenu = async (id: string) => {
  dialog.value = true;
  getRoleMenus(id);
};
defineExpose({
  initRoleMenu,
});
</script>
<template>
  <ElDialog v-model="dialog" title="分配菜单" width="50%" destroy-on-close>
    <div class="hx-menus">
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
