<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTreeSelect,
} from 'element-plus';

import { addObj, editObj, getById, getTreeList } from '#/api/upms/dept';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
// 字典
const { status } = useDict('status');
const state = reactive<any>({
  form: {
    id: '',
    deptName: '',
    parentId: '',
    leader: '',
    leaderPhone: '',
    sort: 0,
    status: '0',
  },
  rules: {
    deptName: [
      {
        required: true,
        message: '请输入部门名称',
        trigger: 'change',
      },
    ],
    parentId: [
      {
        required: true,
        message: '请选择上级部门',
        trigger: 'change',
      },
    ],
    leader: [
      {
        required: true,
        message: '请输入负责人',
        trigger: 'change',
      },
    ],
    sort: [
      {
        required: true,
        message: '请输入排序序号',
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
  },
  deptTree: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const defaultProps = {
  children: 'children',
  label: 'name',
};
const initForm = (row: any) => {
  dialog.value = true;
  if (row && row.id) {
    getDetail(row.id);
  }
  getDeptData();
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response: any) => {
      loading.value = false;
      state.form = response;
    })
    .catch(() => {
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
const getDeptData = async () => {
  await getTreeList().then((response) => {
    const category = {
      id: '0',
      name: '顶级部门',
      children: [],
    };
    const deptList = response;
    if (deptList) {
      deptList.unshift(category);
      state.deptTree = deptList;
    } else {
      state.deptTree = [category];
    }
  });
};
defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改部门' : '添加部门'"
    width="60%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
    >
      <ElFormItem label="部门名称" prop="deptName">
        <ElInput v-model="state.form.deptName" />
      </ElFormItem>
      <ElFormItem label="上级部门" prop="parentId">
        <ElTreeSelect
          :disabled="state.form.parentId === '0' && state.form.id !== ''"
          style="width: 100%"
          v-model="state.form.parentId"
          :data="state.deptTree"
          check-strictly
          :props="defaultProps"
          :render-after-expand="false"
          node-key="id"
        />
      </ElFormItem>
      <ElFormItem label="负责人" prop="leader">
        <ElInput v-model="state.form.leader" />
      </ElFormItem>
      <ElFormItem label="手机号" prop="leaderPhone">
        <ElInput v-model="state.form.leaderPhone" />
      </ElFormItem>
      <ElFormItem label="排序序号" prop="sort">
        <ElInputNumber v-model="state.form.sort" :min="0" />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio v-for="item in status" :key="item.value" :value="item.value">
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
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
</template>
