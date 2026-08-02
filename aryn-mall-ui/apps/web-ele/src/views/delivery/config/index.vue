<script lang="ts" setup name="deliveryConfig">
import type { FormInstance } from 'element-plus';

import type { WarehouseConfig } from '#/api/delivery/config';

import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElCard,
  ElCascader,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import {
  getRegionTree,
  getWarehouseConfig,
  updateWarehouseConfig,
} from '#/api/delivery/config';

/** 省市区级联数据节点 */
interface RegionNode {
  name: string;
  code?: string;
  children?: RegionNode[];
}

const formRef = ref<FormInstance>();
const loading = ref(false);
const saving = ref(false);
const regionOptions = ref<RegionNode[]>([]);
/** 省市区级联选中值（名称数组） */
const regionValue = ref<string[]>([]);

const state = reactive<{
  form: WarehouseConfig;
  rules: Record<string, any>;
}>({
  form: {
    id: undefined,
    warehouseName: '',
    contactName: '',
    contactPhone: '',
    province: '',
    city: '',
    area: '',
    address: '',
  },
  rules: {
    warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
    contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
    contactPhone: [
      { required: true, message: '请输入联系电话', trigger: 'blur' },
      {
        pattern: /^1[3-9]\d{9}$/,
        message: '请输入正确的手机号',
        trigger: 'blur',
      },
    ],
    address: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
  },
});

/** ElCascader 配置：按 name 字段展示和取值 */
const cascaderProps = {
  label: 'name',
  value: 'name',
  children: 'children',
  expandTrigger: 'hover' as const,
};

/**
 * 加载省市区级联数据
 */
const loadRegionTree = () => {
  getRegionTree()
    .then((response: any) => {
      regionOptions.value = response || [];
    })
    .catch(() => {
      regionOptions.value = [];
    });
};

/**
 * 加载仓库配置
 */
const loadConfig = () => {
  loading.value = true;
  getWarehouseConfig()
    .then((response: any) => {
      loading.value = false;
      if (response) {
        state.form = {
          id: response.id,
          warehouseName: response.warehouseName ?? '',
          contactName: response.contactName ?? '',
          contactPhone: response.contactPhone ?? '',
          province: response.province ?? '',
          city: response.city ?? '',
          area: response.area ?? '',
          address: response.address ?? '',
        };
        // 回填级联选择器
        if (state.form.province && state.form.city && state.form.area) {
          regionValue.value = [
            state.form.province,
            state.form.city,
            state.form.area,
          ];
        }
      }
    })
    .catch(() => {
      loading.value = false;
    });
};

/**
 * 省市区级联变化
 */
const handleRegionChange = (value: any) => {
  if (Array.isArray(value) && value.length === 3) {
    state.form.province = value[0] as string;
    state.form.city = value[1] as string;
    state.form.area = value[2] as string;
  } else {
    state.form.province = '';
    state.form.city = '';
    state.form.area = '';
  }
};

/**
 * 保存配置
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      if (!state.form.province || !state.form.city || !state.form.area) {
        ElMessage.warning('请选择所在地区');
        return;
      }
      saving.value = true;
      updateWarehouseConfig(state.form)
        .then(() => {
          ElMessage.success('保存成功');
          saving.value = false;
          loadConfig();
        })
        .catch(() => {
          saving.value = false;
        });
    }
  });
};

/**
 * 重置表单
 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
  regionValue.value = [];
  loadConfig();
};

onMounted(() => {
  loadRegionTree();
  loadConfig();
});
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElCard v-loading="loading">
        <template #header>
          <div class="card-header">
            <span>仓库配置</span>
          </div>
        </template>
        <ElForm
          ref="formRef"
          :model="state.form"
          :rules="state.rules"
          label-width="120px"
          style="max-width: 640px"
        >
          <ElFormItem label="仓库名称" prop="warehouseName">
            <ElInput
              v-model="state.form.warehouseName"
              placeholder="请输入仓库名称"
              maxlength="50"
              show-word-limit
            />
          </ElFormItem>
          <ElFormItem label="联系人" prop="contactName">
            <ElInput
              v-model="state.form.contactName"
              placeholder="请输入联系人"
              maxlength="20"
            />
          </ElFormItem>
          <ElFormItem label="联系电话" prop="contactPhone">
            <ElInput
              v-model="state.form.contactPhone"
              placeholder="请输入联系电话"
              maxlength="20"
            />
          </ElFormItem>
          <ElFormItem label="所在地区" required>
            <ElCascader
              v-model="regionValue"
              :options="regionOptions"
              :props="cascaderProps"
              placeholder="请选择省/市/区"
              style="width: 100%"
              @change="handleRegionChange"
            />
          </ElFormItem>
          <ElFormItem label="详细地址" prop="address">
            <ElInput
              v-model="state.form.address"
              type="textarea"
              :rows="3"
              placeholder="请输入详细地址"
              maxlength="200"
              show-word-limit
            />
          </ElFormItem>
          <ElFormItem>
            <ElButton
              type="primary"
              :loading="saving"
              @click="submitForm(formRef)"
            >
              保 存
            </ElButton>
            <ElButton @click="resetForm(formRef)"> 重 置 </ElButton>
          </ElFormItem>
        </ElForm>
      </ElCard>
    </div>
  </div>
</template>
<style lang="scss" scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
