<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/promotion/group-buy-activity';

const emit = defineEmits(['initPage']);

const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
);

interface DataState {
  form: {
    activityName: string;
    activityStatus: string;
    datatimes: Array<any>;
    endedAt: string;
    groupExpireHours: number;
    groupNum: number;
    groupPrice: number;
    id: string | undefined;
    limitNum: number;
    originalPrice: number;
    skuId: string;
    spuId: string;
    spuName: string;
    spuUrls: Array<any>;
    startedAt: string;
    virtualNum: number;
  };
  rules: any;
}

const state = reactive<DataState>({
  form: {
    id: undefined,
    activityName: '',
    spuId: '',
    spuName: '',
    spuUrls: [],
    skuId: '',
    originalPrice: 0,
    groupPrice: 0,
    groupNum: 2,
    limitNum: 0,
    virtualNum: 0,
    activityStatus: '0',
    datatimes: [],
    startedAt: '',
    endedAt: '',
    groupExpireHours: 24,
  },
  rules: {
    activityName: [
      { required: true, message: '请输入活动名称', trigger: 'change' },
    ],
    spuId: [{ required: true, message: '请输入商品SPU ID', trigger: 'change' }],
    skuId: [{ required: true, message: '请输入商品SKU ID', trigger: 'change' }],
    originalPrice: [
      { required: true, message: '请输入商品原价', trigger: 'change' },
    ],
    groupPrice: [
      { required: true, message: '请输入拼团价', trigger: 'change' },
    ],
    groupNum: [
      { required: true, message: '请输入成团人数', trigger: 'change' },
    ],
    datatimes: [
      { required: true, message: '请选择活动时间', trigger: 'change' },
    ],
    activityStatus: [
      { required: true, message: '请选择活动状态', trigger: 'change' },
    ],
  },
});

const loading = ref(false);
const formRef = ref();
const dialog = ref(false);
const selectGoods = ref();

const selectSpu = () => {
  selectGoods.value.initPage();
};

const spuCurrent = (spuList: any) => {
  if (spuList && spuList.length > 0) {
    const item = spuList[0];
    state.form.spuId = item.id;
    state.form.spuName = item.name;
    state.form.spuUrls = item.spuUrls;
  }
};

const handleClose = () => {
  resetForm(formRef.value);
};

const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = undefined;
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      if (state.form.groupPrice >= state.form.originalPrice) {
        ElMessage.warning('拼团价必须小于商品原价');
        return;
      }
      if (state.form.groupNum < 2) {
        ElMessage.warning('成团人数至少为2人');
        return;
      }
      state.form.startedAt = state.form.datatimes[0];
      state.form.endedAt = state.form.datatimes[1];
      loading.value = true;
      if (state.form.id) {
        edit();
      } else {
        add();
      }
    }
  });
};

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

const initForm = (row: any) => {
  dialog.value = true;
  if (row && row.id) {
    getDetail(row.id);
  }
};

const getDetail = (id: string) => {
  loading.value = true;
  getById(id)
    .then((response) => {
      loading.value = false;
      state.form = response;
      state.form.datatimes = [state.form.startedAt, state.form.endedAt];
    })
    .catch(() => {
      loading.value = false;
    });
};

defineExpose({
  initForm,
});
</script>
<template>
  <ElDrawer
    v-model="dialog"
    :title="state.form.id ? '修改拼团活动' : '新增拼团活动'"
    :before-close="handleClose"
    size="50%"
  >
    <ElForm
      ref="formRef"
      class="form"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
      status-icon
      label-position="left"
      v-loading="loading"
    >
      <ElFormItem label="活动名称" prop="activityName">
        <ElInput
          v-model="state.form.activityName"
          maxlength="128"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="拼团商品" prop="spuId">
        <div style="display: flex; align-items: center; gap: 8px">
          <ElButton
            type="success"
            v-if="!state.form.id"
            link
            @click="selectSpu"
          >
            选择商品
          </ElButton>
          <span v-if="state.form.spuName" style="color: #409eff">{{
            state.form.spuName
          }}</span>
          <ElImage
            v-if="state.form.spuUrls && state.form.spuUrls.length > 0"
            style="width: 40px; height: 40px"
            :src="state.form.spuUrls[0]"
            :preview-src-list="state.form.spuUrls"
            fit="cover"
            :preview-teleported="true"
          />
        </div>
        <SelectGoods
          ref="selectGoods"
          :limit-num="1"
          @current-row="spuCurrent"
        />
      </ElFormItem>
      <ElFormItem label="商品原价" prop="originalPrice">
        <ElInputNumber
          v-model="state.form.originalPrice"
          :disabled="!!state.form.id"
          :min="0.01"
          :precision="2"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="拼团价" prop="groupPrice">
        <ElInputNumber
          v-model="state.form.groupPrice"
          :disabled="!!state.form.id"
          :min="0.01"
          :precision="2"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="成团人数" prop="groupNum">
        <ElInputNumber
          v-model="state.form.groupNum"
          :disabled="!!state.form.id"
          :min="2"
          :precision="0"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="限购数量" prop="limitNum">
        <div style="display: flex; align-items: center; gap: 8px">
          <ElInputNumber
            v-model="state.form.limitNum"
            :min="0"
            :precision="0"
            :controls="false"
          />
          <span style="color: #909399">0表示不限购</span>
        </div>
      </ElFormItem>
      <ElFormItem label="虚拟成团人数" prop="virtualNum">
        <div style="display: flex; align-items: center; gap: 8px">
          <ElInputNumber
            v-model="state.form.virtualNum"
            :min="0"
            :precision="0"
            :controls="false"
          />
          <span style="color: #909399">展示用，0表示不虚增</span>
        </div>
      </ElFormItem>
      <ElFormItem label="活动时间" prop="datatimes">
        <ElDatePicker
          v-model="state.form.datatimes"
          :disabled="!!state.form.id"
          type="datetimerange"
          range-separator="-"
          start-placeholder="活动开始时间"
          end-placeholder="活动结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </ElFormItem>
      <ElFormItem label="拼团时效(小时)" prop="groupExpireHours">
        <ElInputNumber
          v-model="state.form.groupExpireHours"
          :min="1"
          :max="720"
          :precision="0"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="活动状态" prop="activityStatus">
        <ElRadioGroup v-model="state.form.activityStatus">
          <ElRadio value="0">草稿</ElRadio>
          <ElRadio value="1">进行中</ElRadio>
          <ElRadio value="2">已结束</ElRadio>
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
  </ElDrawer>
</template>
<style lang="scss" scoped></style>
