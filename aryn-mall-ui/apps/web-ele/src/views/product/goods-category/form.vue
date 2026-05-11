<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

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

import {
  addObj,
  editObj,
  getById,
  getPage as getList,
} from '#/api/product/goods-category';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
interface DataState {
  form: {
    categoryPic: string;
    description: string;
    id: string;
    name: string;
    parentId: string;
    sort: number;
    status: string;
  };
  rules: any;
  categoryTree: Array<any>;
}
// 字典
const { status } = useDict('status');
const state = reactive<DataState>({
  form: {
    id: '',
    name: '',
    parentId: '',
    categoryPic: '',
    status: '0',
    description: '',
    sort: 0,
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入类目名称',
        trigger: 'change',
      },
    ],
    parentId: [
      {
        required: true,
        message: '请输入上级类目',
        trigger: 'change',
      },
    ],
    categoryPic: [
      {
        required: true,
        message: '请上传类目图片',
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
    sort: [
      {
        required: true,
        message: '请输入排序序号',
        trigger: 'change',
      },
    ],
  },
  categoryTree: [],
});
const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const defaultProps = {
  children: 'children',
  label: 'name',
};
const initForm = (row: any) => {
  getCategoryTree();
  if (row && row.id) {
    getDetail(row.id);
  }
  dialog.value = true;
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response) => {
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
// 获取类目
const getCategoryTree = () => {
  getList()
    .then((response) => {
      const category = {
        id: '0',
        name: '顶级菜单',
        children: [],
      };
      const categoryList = response;
      if (categoryList) {
        categoryList.unshift(category);
        state.categoryTree = categoryList;
      } else {
        state.categoryTree = [category];
      }
    })
    .catch(() => {});
};
defineExpose({
  initForm,
});
</script>
<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改商品类目' : '新增商品类目'"
    width="60%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
    >
      <ElFormItem label="类目名称" prop="name">
        <ElInput v-model="state.form.name" maxlength="20" show-word-limit />
      </ElFormItem>
      <ElFormItem label="上级类目" prop="parentId">
        <ElTreeSelect
          :disabled="state.form.parentId === '0' && state.form.id !== ''"
          style="width: 100%"
          v-model="state.form.parentId"
          :data="state.categoryTree"
          check-strictly
          :props="defaultProps"
          :render-after-expand="false"
          node-key="id"
        />
      </ElFormItem>
      <ElFormItem label="类目描述" prop="description">
        <ElInput v-model="state.form.description" />
      </ElFormItem>
      <ElFormItem label="类目图片" prop="categoryPic">
        <SelectMaterial
          v-model="state.form.categoryPic"
          :can-choose-images-num="1"
        />
      </ElFormItem>
      <ElFormItem label="排序序号" prop="sort">
        <ElInputNumber
          style="width: 100%"
          v-model="state.form.sort"
          :min="0"
          :max="99999"
          controls-position="right"
        />
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
