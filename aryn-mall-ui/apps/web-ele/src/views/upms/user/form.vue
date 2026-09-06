<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { computed, defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElTag,
  ElTreeSelect,
} from 'element-plus';

import { getTreeList } from '#/api/upms/dept';
import { getList as getRoleList } from '#/api/upms/sys-role';
import { addObj, editObj, getById } from '#/api/upms/user';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
/**
 * 受保护配送资格角色编码：只能由配送员管理开通/停用，
 * 员工账号表单不展示复选框，编辑提交时由后端保留原有关联
 */
const PROTECTED_DELIVERY_ROLE_CODE = 'delivery_staff';
interface UserSate {
  rules: object;
  form: {
    avatar: string;
    deptId: string;
    id: string;
    nickname: string;
    password: string;
    phone: string;
    roles: string[];
    shopName: string;
    status: string;
    type: string;
    username: string;
  };
  roleList: any;
  deptList: any;
}
// 字典
const { status } = useDict('status');
const state = reactive<UserSate>({
  form: {
    id: '',
    password: '',
    deptId: '',
    username: '',
    phone: '',
    nickname: '',
    avatar: '',
    roles: [],
    shopName: '',
    type: '0',
    status: '0',
  },
  rules: {
    nickname: [
      {
        required: true,
        message: '请输入用户昵称',
        trigger: 'change',
      },
    ],
    phone: [
      {
        required: true,
        message: '请输入手机号码',
        trigger: 'change',
      },
    ],
    username: [
      {
        required: true,
        message: '请输入用户名',
        trigger: 'change',
      },
    ],
    password: [
      {
        required: true,
        message: '请输入密码',
        trigger: 'change',
      },
    ],
    status: [
      {
        required: true,
        message: '请选择状态',
        trigger: 'change',
      },
    ],
    roles: [
      {
        required: true,
        message: '请选择角色',
        trigger: 'change',
      },
    ],
    deptId: [
      {
        required: true,
        message: '请选择部门',
        trigger: 'change',
      },
    ],
    avatar: [
      {
        required: true,
        message: '请上传头像',
        trigger: 'change',
      },
    ],
  },
  roleList: [],
  deptList: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

/**
 * 受保护角色ID集合（存在重复编码角色时全部纳入保护）
 */
const protectedRoleIds = computed(() =>
  (state.roleList || [])
    .filter((item: any) => item.roleCode === PROTECTED_DELIVERY_ROLE_CODE)
    .map((item: any) => item.id),
);

/**
 * 是否已具备配送资格（后端返回受保护角色时只读展示）
 */
const hasDeliveryQualification = computed(() =>
  (state.form.roles || []).some((roleId) =>
    protectedRoleIds.value.includes(roleId),
  ),
);

/**
 * 后台角色复选框列表：过滤受保护的配送资格角色
 */
const editableRoleList = computed(() =>
  (state.roleList || []).filter(
    (item: any) => item.roleCode !== PROTECTED_DELIVERY_ROLE_CODE,
  ),
);

const defaultProps = {
  children: 'children',
  label: 'name',
};
const initForm = (row: any | undefined) => {
  dialog.value = true;
  if (row && row.id) {
    getDetail(row.id);
  }

  handleRoleList();
  handleDeptList();
};
const getDetail = (id: string) => {
  loading.value = true;
  getById(id).then((response) => {
    state.form = response;
    loading.value = false;
  });
};
/**
 * 关闭事件
 */
const handleClose = () => {
  resetForm(formRef.value);
};
/**
 * 重置表单
 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = '';
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};
/**
 * 提交按钮
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      // 受保护角色不在提交列表中：授予/回收只由配送员管理负责，后端编辑时会保留原有关联
      state.form.roles = (state.form.roles || []).filter(
        (roleId) => !protectedRoleIds.value.includes(roleId),
      );
      if (state.form.id) {
        // 修改
        edit();
      } else {
        // 新增
        add();
      }
    }
  });
};
/**
 * 新增
 */
const add = () => {
  state.form.type = '0';
  addObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('新增成功');

      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 修改
 */
const edit = () => {
  editObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 查询全部角色
 */
const handleRoleList = () => {
  getRoleList({}).then((response) => {
    state.roleList = response;
  });
};

/**
 * 查询全部部门
 */
const handleDeptList = () => {
  getTreeList()
    .then((response) => {
      state.deptList = response;
    })
    .catch(() => {});
};
defineExpose({
  initForm,
});
</script>
<template>
  <div class="system-add-user-container">
    <ElDialog
      v-model="dialog"
      :title="state.form.id ? '修改员工账号' : '新增员工账号'"
      width="60%"
      :before-close="handleClose"
    >
      <ElForm
        ref="formRef"
        :model="state.form"
        label-width="120px"
        :rules="state.rules"
      >
        <ElRow>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="员工昵称" prop="nickname">
              <ElInput v-model="state.form.nickname" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="手机号码" prop="phone">
              <ElInput
                v-model="state.form.phone"
                maxlength="11"
                :disabled="!!state.form.id"
                show-word-limit
              />
            </ElFormItem>
          </ElCol>
        </ElRow>
        <ElRow>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="登录用户名" prop="username">
              <ElInput
                v-model="state.form.username"
                :disabled="!!state.form.id"
              />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="密码" prop="password">
              <ElInput
                v-model="state.form.password"
                type="password"
                :disabled="!!state.form.id"
              />
            </ElFormItem>
          </ElCol>
        </ElRow>
        <ElRow>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="部门" prop="deptId">
              <ElTreeSelect
                style="width: 100%"
                v-model="state.form.deptId"
                :data="state.deptList"
                check-strictly
                :props="defaultProps"
                :render-after-expand="false"
                node-key="id"
              />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12" class="mb20">
            <ElFormItem label="状态" prop="status">
              <ElRadioGroup v-model="state.form.status">
                <ElRadio
                  v-for="item in status"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </ElRadio>
              </ElRadioGroup>
            </ElFormItem>
          </ElCol>
        </ElRow>
        <ElFormItem label="后台角色" prop="roles">
          <div class="role-field">
            <ElCheckboxGroup v-model="state.form.roles">
              <ElCheckbox
                v-for="item in editableRoleList"
                :key="item.id"
                :value="item.id"
              >
                {{ item.roleName }}
              </ElCheckbox>
            </ElCheckboxGroup>
            <ElTag v-if="hasDeliveryQualification" type="success" size="small">
              已具备配送资格（配送员角色，仅配送员管理可变更）
            </ElTag>
            <div class="role-tip">
              配送资格请在 配送管理 &gt; 配送员管理 中开通，此处只管理后台角色
            </div>
          </div>
        </ElFormItem>
        <ElFormItem label="头像" prop="avatar">
          <SelectMaterial
            v-model="state.form.avatar"
            :can-choose-images-num="1"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="handleClose">关 闭</ElButton>
          <ElButton
            type="primary"
            :loading="loading"
            @click="submitForm(formRef)"
          >
            确 认
          </ElButton>
        </span>
      </template>
    </ElDialog>
  </div>
</template>
<style scoped>
.avatar-uploader .avatar {
  display: block;
  width: 140px;
  height: 140px;
}

.role-field {
  width: 100%;
}

.role-tip {
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}
</style>
