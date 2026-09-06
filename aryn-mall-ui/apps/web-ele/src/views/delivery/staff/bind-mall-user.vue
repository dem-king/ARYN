<script lang="ts" setup>
import type {
  DeliveryStaffManager,
  MallUserForBinding,
} from '#/api/delivery/staff';

import { reactive, ref } from 'vue';

import { ElButton, ElDialog, ElInput, ElMessage, ElTag } from 'element-plus';

import {
  bindDeliveryStaffMallUser,
  searchMallUserForBinding,
} from '#/api/delivery/staff';

const emit = defineEmits(['initPage']);

const state = reactive({
  keyword: '',
  options: [] as MallUserForBinding[],
  loading: false,
  selected: undefined as MallUserForBinding | undefined,
  staff: undefined as DeliveryStaffManager | undefined,
});

const dialog = ref(false);
const submitting = ref(false);

const open = (row: DeliveryStaffManager) => {
  state.staff = row;
  state.keyword = '';
  state.options = [];
  state.selected = undefined;
  dialog.value = true;
  load('');
};

defineExpose({ open });

/**
 * 搜索可绑定的商城用户
 */
const load = (keyword: string) => {
  state.loading = true;
  searchMallUserForBinding(keyword || undefined)
    .then((response: any) => {
      state.options = (response || []) as MallUserForBinding[];
    })
    .catch(() => {})
    .finally(() => {
      state.loading = false;
    });
};

const select = (row: MallUserForBinding) => {
  if (row.bindingStatus === 'bound') return;
  state.selected = row;
};

/**
 * 确认绑定：成功后该商城账号下次进入个人中心即可看到配送工作台
 */
const confirm = () => {
  if (!state.staff?.id) return;
  if (!state.selected) {
    ElMessage.warning('请选择商城用户');
    return;
  }
  submitting.value = true;
  bindDeliveryStaffMallUser(state.staff.id, state.selected.mallUserId)
    .then(() => {
      ElMessage.success(
        '绑定成功。该商城账号下次进入个人中心时会显示配送工作台',
      );
      dialog.value = false;
      emit('initPage');
    })
    .catch(() => {})
    .finally(() => {
      submitting.value = false;
    });
};
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="`绑定商城账号 - ${state.staff?.staffName ?? ''}`"
    width="560px"
  >
    <ElInput
      v-model="state.keyword"
      clearable
      placeholder="按手机号/昵称/用户ID搜索商城用户"
      @change="load(state.keyword)"
    >
      <template #append>
        <ElButton @click="load(state.keyword)">搜索</ElButton>
      </template>
    </ElInput>
    <div v-loading="state.loading" class="bind-list">
      <div
        v-for="item in state.options"
        :key="item.mallUserId"
        class="bind-row"
        :class="{
          'bind-row-selected': state.selected?.mallUserId === item.mallUserId,
          'bind-row-disabled': item.bindingStatus === 'bound',
        }"
        @click="select(item)"
      >
        <div class="bind-main">
          {{ item.nickname || '-' }}
          <span class="bind-sub">{{ item.phone || '-' }}</span>
        </div>
        <ElTag
          :type="item.bindingStatus === 'bound' ? 'danger' : 'success'"
          size="small"
        >
          {{
            item.bindingStatus === 'bound'
              ? `已绑定配送员${item.boundStaffName ? `：${item.boundStaffName}` : ''}`
              : '未绑定配送员'
          }}
        </ElTag>
      </div>
      <div
        v-if="!state.loading && state.options.length === 0"
        class="bind-empty"
      >
        未找到商城用户
      </div>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="dialog = false">取 消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="confirm">
          确认绑定
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
<style scoped>
.bind-list {
  min-height: 120px;
  max-height: 320px;
  margin-top: 12px;
  overflow: auto;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.bind-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}

.bind-row:last-child {
  border-bottom: none;
}

.bind-row-selected {
  background-color: var(--el-color-primary-light-9);
}

.bind-row-disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  opacity: 0.7;
}

.bind-main {
  flex: 1;
  font-size: 14px;
}

.bind-sub {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.bind-empty {
  padding: 32px 0;
  color: #909399;
  text-align: center;
}
</style>
