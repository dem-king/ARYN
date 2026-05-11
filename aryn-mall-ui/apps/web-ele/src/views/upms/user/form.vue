<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

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
      :title="state.form.id ? '修改用户' : '新增用户'"
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
            <ElFormItem label="用户昵称" prop="nickname">
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
            <ElFormItem label="用户名" prop="username">
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
        <ElFormItem label="角色权限" prop="roles">
          <ElCheckboxGroup v-model="state.form.roles">
            <ElCheckbox
              v-for="(item, index) in state.roleList"
              :key="index"
              :value="item.id"
            >
              {{ item.roleName }}
            </ElCheckbox>
          </ElCheckboxGroup>
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
</style>
