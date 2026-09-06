<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type {
  DeliveryOnboardResult,
  DeliveryStaffStatus,
  MallUserForBinding,
  SysUserForOnboard,
} from '#/api/delivery/staff';

import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElStep,
  ElSteps,
  ElTag,
} from 'element-plus';

import {
  onboardDeliveryStaff,
  searchMallUserForBinding,
  searchSysUserForOnboard,
} from '#/api/delivery/staff';

const emit = defineEmits(['initPage']);

const router = useRouter();

/**
 * 跳转员工账号管理创建新账号；返回配送员管理后重新打开向导即可加载最新员工列表
 */
const goToCreateSysUser = () => {
  router.push('/system/user?from=delivery-staff');
};

const statusOptions: { label: string; value: DeliveryStaffStatus }[] = [
  { value: '3', label: '离线（默认，确认后可切换在线）' },
  { value: '1', label: '在线' },
  { value: '2', label: '忙碌' },
];

const state = reactive({
  active: 0,
  sysUserKeyword: '',
  sysUserOptions: [] as SysUserForOnboard[],
  sysUserLoading: false,
  selectedSysUser: undefined as SysUserForOnboard | undefined,
  mallUserKeyword: '',
  mallUserOptions: [] as MallUserForBinding[],
  mallUserLoading: false,
  selectedMallUser: undefined as MallUserForBinding | undefined,
  skipBindMallUser: false,
  grantQualification: true,
  form: {
    staffName: '',
    staffPhone: '',
    vehicleInfo: '',
    status: '3' as DeliveryStaffStatus,
  },
});

const drawer = ref(false);
const loading = ref(false);
const formRef = ref();

const open = () => {
  state.active = 0;
  state.sysUserKeyword = '';
  state.sysUserOptions = [];
  state.selectedSysUser = undefined;
  state.mallUserKeyword = '';
  state.mallUserOptions = [];
  state.selectedMallUser = undefined;
  state.skipBindMallUser = false;
  state.grantQualification = true;
  state.form = {
    staffName: '',
    staffPhone: '',
    vehicleInfo: '',
    status: '3',
  };
  drawer.value = true;
  loadSysUsers('');
};

defineExpose({ open });

/**
 * 第一步：搜索可选员工账号
 */
const loadSysUsers = (keyword: string) => {
  state.sysUserLoading = true;
  searchSysUserForOnboard(keyword || undefined)
    .then((response: any) => {
      state.sysUserOptions = (response || []) as SysUserForOnboard[];
    })
    .catch(() => {})
    .finally(() => {
      state.sysUserLoading = false;
    });
};

/**
 * 第二步：搜索可绑定商城用户
 */
const loadMallUsers = (keyword: string) => {
  state.mallUserLoading = true;
  searchMallUserForBinding(keyword || undefined)
    .then((response: any) => {
      state.mallUserOptions = (response || []) as MallUserForBinding[];
    })
    .catch(() => {})
    .finally(() => {
      state.mallUserLoading = false;
    });
};

/**
 * 选择员工账号并回填资料默认值
 */
const selectSysUser = (row: SysUserForOnboard) => {
  if (row.deliveryExists) return;
  state.selectedSysUser = row;
  if (!state.form.staffName) {
    state.form.staffName = row.nickname || row.username || '';
  }
  if (!state.form.staffPhone && row.phone) {
    state.form.staffPhone = row.phone;
  }
};

/**
 * 选择商城账号（已绑定的不可选）
 */
const selectMallUser = (row: MallUserForBinding) => {
  if (row.bindingStatus === 'bound') return;
  state.selectedMallUser = row;
};

const goStep1 = () => {
  if (!state.selectedSysUser) {
    ElMessage.warning('请先选择员工账号');
    return;
  }
  state.active = 1;
  loadMallUsers('');
};

const goStep2 = () => {
  if (!state.skipBindMallUser && !state.selectedMallUser) {
    ElMessage.warning('请选择商城账号或勾选"暂不绑定"');
    return;
  }
  state.active = 2;
};

/**
 * 校验第三步资料并展示确认信息
 */
const validateProfile = (formEl: FormInstance | undefined) => {
  if (!formEl) return false;
  let valid = true;
  formEl.validate((ok) => {
    valid = ok;
  });
  return valid;
};

/**
 * 提交按钮文案：勾选开通资格时为"创建并开通配送资格"，否则为"仅创建配送资料"
 */
const submitLabel = computed(() =>
  state.grantQualification ? '创建并开通配送资格' : '仅创建配送资料',
);

/**
 * 根据实际结果生成成功提示，禁止固定显示"创建并开通成功"
 */
const buildSuccessMessage = (result: DeliveryOnboardResult) => {
  const parts = ['配送员已创建'];
  if (result.bindingStatus === 'BOUND') {
    parts.push('商城账号已绑定，该账号下次进入个人中心即可看到配送工作台');
  } else {
    parts.push('商城账号未绑定，绑定前移动端不会显示入口');
  }
  if (result.qualificationStatus === 'GRANTED') {
    parts.push('配送资格已开通');
  } else if (result.qualificationStatus === 'PROCESSING') {
    parts.push(
      `配送资格开通处理中${
        result.qualificationMessage ? `（${result.qualificationMessage}）` : ''
      }`,
    );
  } else {
    parts.push('配送资格未开通，可在列表中一键开通');
  }
  return parts.join('；');
};

/**
 * 提交：员工账号 + 可选商城绑定 + 配送资料 + 可选资格开通一次完成
 */
const submit = async (formEl: FormInstance | undefined) => {
  if (!validateProfile(formEl)) return;
  loading.value = true;
  await onboardDeliveryStaff({
    userId: state.selectedSysUser?.userId ?? '',
    staffName: state.form.staffName || undefined,
    staffPhone: state.form.staffPhone || undefined,
    vehicleInfo: state.form.vehicleInfo || undefined,
    mallUserId:
      !state.skipBindMallUser && state.selectedMallUser
        ? state.selectedMallUser.mallUserId
        : undefined,
    status: state.form.status,
    grantQualification: state.grantQualification,
  })
    .then((result) => {
      ElMessage.success(buildSuccessMessage(result));
      drawer.value = false;
      emit('initPage');
    })
    .catch(() => {})
    .finally(() => {
      loading.value = false;
    });
};
</script>
<template>
  <ElDrawer v-model="drawer" title="新增配送员" size="620px">
    <ElSteps :active="state.active" finish-status="success" simple>
      <ElStep title="选择员工账号" />
      <ElStep title="绑定商城账号（可选）" />
      <ElStep title="配送资料" />
    </ElSteps>

    <!-- 第一步：选择员工账号 -->
    <div v-show="state.active === 0" class="step-body">
      <ElAlert
        type="info"
        :closable="false"
        show-icon
        title="请选择一个已有的后台员工账号作为配送端登录账号。新员工请先在【系统设置 > 员工账号】创建账号。"
      />
      <div class="step-toolbar">
        <ElButton type="primary" plain @click="goToCreateSysUser">
          去创建员工账号
        </ElButton>
        <span class="step-toolbar-tip">
          创建后返回本页重新搜索即可选择新账号
        </span>
      </div>
      <ElInput
        v-model="state.sysUserKeyword"
        clearable
        placeholder="按用户名/昵称/手机号搜索员工"
        class="step-search"
        @change="loadSysUsers(state.sysUserKeyword)"
      >
        <template #append>
          <ElButton @click="loadSysUsers(state.sysUserKeyword)">搜索</ElButton>
        </template>
      </ElInput>
      <div v-loading="state.sysUserLoading" class="option-list">
        <div
          v-for="item in state.sysUserOptions"
          :key="item.userId"
          class="option-row"
          :class="{
            'option-row-selected':
              state.selectedSysUser?.userId === item.userId,
            'option-row-disabled': item.deliveryExists,
          }"
          @click="selectSysUser(item)"
        >
          <div class="option-main">
            {{ item.nickname || item.username }}
            <span class="option-sub">
              {{ item.username }} {{ item.phone ? `· ${item.phone}` : '' }}
            </span>
          </div>
          <ElTag
            :type="item.deliveryPermission ? 'success' : 'warning'"
            size="small"
          >
            {{ item.deliveryPermission ? '已开通配送权限' : '未开通配送权限' }}
          </ElTag>
          <ElTag v-if="item.deliveryExists" type="info" size="small">
            已是配送员
          </ElTag>
        </div>
        <div
          v-if="!state.sysUserLoading && state.sysUserOptions.length === 0"
          class="option-empty"
        >
          未找到员工账号
        </div>
      </div>
      <div class="step-footer">
        <ElButton @click="drawer = false">取消</ElButton>
        <ElButton type="primary" @click="goStep1">下一步</ElButton>
      </div>
    </div>

    <!-- 第二步：绑定商城账号 -->
    <div v-show="state.active === 1" class="step-body">
      <ElAlert
        type="info"
        :closable="false"
        show-icon
        title="绑定后该商城账号登录个人中心时会显示配送工作台；手机号不一致时可先解绑后再绑定。"
      />
      <ElCheckbox v-model="state.skipBindMallUser" class="step-checkbox">
        暂不绑定，稍后处理
      </ElCheckbox>
      <ElInput
        v-model="state.mallUserKeyword"
        clearable
        :disabled="state.skipBindMallUser"
        placeholder="按手机号/昵称/用户ID搜索商城用户"
        class="step-search"
        @change="loadMallUsers(state.mallUserKeyword)"
      >
        <template #append>
          <ElButton
            :disabled="state.skipBindMallUser"
            @click="loadMallUsers(state.mallUserKeyword)"
          >
            搜索
          </ElButton>
        </template>
      </ElInput>
      <div v-loading="state.mallUserLoading" class="option-list">
        <div
          v-for="item in state.mallUserOptions"
          :key="item.mallUserId"
          class="option-row"
          :class="{
            'option-row-selected':
              state.selectedMallUser?.mallUserId === item.mallUserId,
            'option-row-disabled': item.bindingStatus === 'bound',
          }"
          @click="selectMallUser(item)"
        >
          <div class="option-main">
            {{ item.nickname || '-' }}
            <span class="option-sub">{{ item.phone || '-' }}</span>
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
          v-if="!state.mallUserLoading && state.mallUserOptions.length === 0"
          class="option-empty"
        >
          未找到商城用户
        </div>
      </div>
      <div class="step-footer">
        <ElButton @click="state.active = 0">上一步</ElButton>
        <ElButton type="primary" @click="goStep2">下一步</ElButton>
      </div>
    </div>

    <!-- 第三步：配送资料与摘要 -->
    <div v-show="state.active === 2" class="step-body">
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="110px"
        class="step-form"
      >
        <ElFormItem label="配送员姓名" required prop="staffName">
          <ElInput
            v-model="state.form.staffName"
            placeholder="默认取员工账号昵称"
          />
        </ElFormItem>
        <ElFormItem label="联系手机号" required prop="staffPhone">
          <ElInput
            v-model="state.form.staffPhone"
            placeholder="默认取员工账号手机号"
          />
        </ElFormItem>
        <ElFormItem label="车辆信息">
          <ElInput
            v-model="state.form.vehicleInfo"
            placeholder="选填，如：电动车-京A12345"
          />
        </ElFormItem>
        <ElFormItem label="接单状态">
          <ElSelect v-model="state.form.status" style="width: 100%">
            <ElOption
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="配送资格">
          <ElCheckbox v-model="state.grantQualification">
            立即开通配送资格（授予"配送员"角色，包含 delivery:execute）
          </ElCheckbox>
        </ElFormItem>
      </ElForm>
      <div class="summary">
        <div class="summary-title">创建摘要</div>
        <div>
          本次将执行：创建配送资料、{{
            state.skipBindMallUser ? '不绑定商城账号' : '绑定商城账号'
          }}、{{ state.grantQualification ? '开通配送资格' : '不开通配送资格' }}
        </div>
        <div>
          员工账号：{{
            state.selectedSysUser?.nickname || state.selectedSysUser?.username
          }}
        </div>
        <div>
          商城账号：
          {{
            state.skipBindMallUser
              ? '暂不绑定'
              : state.selectedMallUser?.nickname || '-'
          }}
          {{
            state.selectedMallUser?.phone
              ? `（${state.selectedMallUser.phone}）`
              : ''
          }}
        </div>
        <div>
          当前权限状态：
          <ElTag
            :type="
              state.selectedSysUser?.deliveryPermission ? 'success' : 'warning'
            "
            size="small"
          >
            {{
              state.selectedSysUser?.deliveryPermission
                ? '已开通'
                : state.grantQualification
                  ? '未开通（本次将自动开通）'
                  : '未开通'
            }}
          </ElTag>
        </div>
      </div>
      <div class="step-footer">
        <ElButton @click="state.active = 1">上一步</ElButton>
        <ElButton type="primary" :loading="loading" @click="submit(formRef)">
          {{ submitLabel }}
        </ElButton>
      </div>
    </div>
  </ElDrawer>
</template>
<style scoped>
.step-body {
  padding-top: 16px;
}

.step-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 12px;
}

.step-toolbar-tip {
  font-size: 12px;
  color: #909399;
}

.step-search {
  margin: 12px 0;
}

.step-checkbox {
  margin-bottom: 4px;
}

.option-list {
  min-height: 120px;
  max-height: 300px;
  overflow: auto;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.option-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}

.option-row:last-child {
  border-bottom: none;
}

.option-row-selected {
  background-color: var(--el-color-primary-light-9);
}

.option-row-disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  opacity: 0.7;
}

.option-main {
  flex: 1;
  font-size: 14px;
}

.option-sub {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.option-empty {
  padding: 32px 0;
  color: #909399;
  text-align: center;
}

.step-form {
  margin-top: 8px;
}

.summary {
  padding: 12px 16px;
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.9;
  background: #f8f8f9;
  border-radius: 6px;
}

.summary-title {
  margin-bottom: 4px;
  font-weight: 600;
}

.step-footer {
  margin-top: 20px;
  text-align: right;
}
</style>
