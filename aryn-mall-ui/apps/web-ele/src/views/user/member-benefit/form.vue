<script lang="ts" setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { getPage as getCouponPage } from '#/api/promotion/coupon-info';
import { addObj, editObj, getById } from '#/api/user/member-benefit';
import { getList as getLevelList } from '#/api/user/member-level';

const emit = defineEmits(['initPage']);
const visible = ref(false);
const loading = ref(false);
const title = ref('');
const formRef = ref();
const levelList = ref<any[]>([]);
const couponOptions = ref<any[]>([]);
// 编辑回填与新增初始化期间抑制 benefitType 监听，避免覆盖已回填的 benefitValue
let suppressTypeWatch = false;
const state = reactive({
  form: {
    id: '',
    benefitName: '',
    benefitType: '1',
    benefitValue: '1',
    description: '',
    status: '0',
    levelIds: [] as string[],
  },
});

const numericBenefitValue = computed({
  get: () => Number(state.form.benefitValue || 1),
  set: (value: number | undefined) => {
    state.form.benefitValue = value === undefined ? '' : String(value);
  },
});

const rules = reactive({
  benefitName: [{ required: true, message: '请输入权益名称', trigger: 'blur' }],
  benefitType: [
    { required: true, message: '请选择权益类型', trigger: 'change' },
  ],
  benefitValue: [{ required: true, message: '请输入权益值', trigger: 'blur' }],
  levelIds: [
    {
      type: 'array',
      required: true,
      min: 1,
      message: '请至少选择一个关联等级',
      trigger: 'change',
    },
  ],
});

const loadLevelList = async () => {
  try {
    const res = await getLevelList();
    levelList.value = res;
  } catch {
    levelList.value = [];
  }
};

const loadCouponOptions = async (selectedId?: string) => {
  try {
    const res: any = await getCouponPage({
      current: 1,
      size: 200,
      status: '0',
    });
    couponOptions.value = res.records || [];
  } catch {
    couponOptions.value = [];
  }
  if (
    selectedId &&
    !couponOptions.value.some((item) => item.id === selectedId)
  ) {
    couponOptions.value.unshift({
      id: selectedId,
      couponName: `已选模板 ${selectedId}`,
    });
  }
};

onMounted(() => {
  loadLevelList();
  loadCouponOptions();
});

watch(
  () => state.form.benefitType,
  (type) => {
    // 编辑回填/新增初始化期间不重置，见 initForm
    if (suppressTypeWatch) {
      return;
    }
    state.form.benefitValue = ['1', '2', '4'].includes(type) ? '1' : '';
    if (type === '3') {
      loadCouponOptions();
    }
  },
);

// 仅在用户主动切换权益类型时重置权益值；编辑回显时 Object.assign 也会改 benefitType，
// 但那是回填而不是切换，若一并重置会把后端回填的 benefitValue 冲掉
// （编辑折扣 0.85 会显示成 1，编辑专属优惠券会清空模板 ID）。
const initForm = async (row?: any) => {
  visible.value = true;
  if (row?.id) {
    title.value = '编辑会员权益';
    const res = await getById(row.id);
    // 先摘掉监听，避免回填过程把 benefitValue 覆盖成默认值
    suppressTypeWatch = true;
    Object.assign(state.form, res);
    await nextTick();
    suppressTypeWatch = false;
    if (!state.form.levelIds) {
      state.form.levelIds = [];
    }
    if (state.form.benefitType === '3') {
      await loadCouponOptions(state.form.benefitValue);
    }
  } else {
    title.value = '新增会员权益';
    suppressTypeWatch = true;
    state.form = {
      id: '',
      benefitName: '',
      benefitType: '1',
      benefitValue: '1',
      description: '',
      status: '0',
      levelIds: [],
    };
    await nextTick();
    suppressTypeWatch = false;
  }
};

const submitForm = async () => {
  await formRef.value.validate();
  loading.value = true;
  try {
    const payload = {
      ...state.form,
      benefitValue: String(state.form.benefitValue),
    };
    if (state.form.id) {
      await editObj(payload);
      ElMessage.success('修改成功');
    } else {
      await addObj(payload);
      ElMessage.success('新增成功');
    }
    visible.value = false;
    emit('initPage');
  } finally {
    loading.value = false;
  }
};

defineExpose({ initForm });
</script>
<template>
  <ElDialog v-model="visible" :title="title" width="500px" draggable>
    <ElForm
      ref="formRef"
      :model="state.form"
      :rules="rules"
      label-width="120px"
    >
      <ElFormItem label="权益名称" prop="benefitName">
        <ElInput
          v-model="state.form.benefitName"
          placeholder="请输入权益名称"
        />
      </ElFormItem>
      <ElFormItem label="权益类型" prop="benefitType">
        <ElRadioGroup v-model="state.form.benefitType">
          <ElRadio value="1">折扣</ElRadio>
          <ElRadio value="2">免运费</ElRadio>
          <ElRadio value="3">专属优惠券</ElRadio>
          <ElRadio value="4">积分倍率</ElRadio>
          <ElRadio value="5">积分抵扣上限</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="权益值" prop="benefitValue">
        <ElInputNumber
          v-if="state.form.benefitType === '1'"
          v-model="numericBenefitValue"
          :min="0.01"
          :max="1"
          :step="0.01"
          :precision="2"
        />
        <ElInput
          v-else-if="state.form.benefitType === '2'"
          model-value="1"
          disabled
        />
        <ElSelect
          v-else-if="state.form.benefitType === '3'"
          v-model="state.form.benefitValue"
          filterable
          placeholder="请选择优惠券模板"
          style="width: 100%"
        >
          <ElOption
            v-for="coupon in couponOptions"
            :key="coupon.id"
            :label="`${coupon.couponName}（剩余 ${coupon.remainNum ?? 0}）`"
            :value="coupon.id"
          />
        </ElSelect>
        <ElInputNumber
          v-else-if="state.form.benefitType === '5'"
          v-model="numericBenefitValue"
          :min="0.01"
          :max="100"
          :step="1"
          :precision="2"
        />
        <ElInputNumber
          v-else
          v-model="numericBenefitValue"
          :min="1"
          :step="0.1"
          :precision="2"
        />
        <div v-if="state.form.benefitType === '1'" class="form-tip">
          0.9 表示 9 折；1 表示不打折
        </div>
        <div v-else-if="state.form.benefitType === '3'" class="form-tip">
          仅可选择启用且有余量的优惠券模板
        </div>
        <div v-else-if="state.form.benefitType === '5'" class="form-tip">
          百分数：20 表示该等级单笔最多抵
          20%；等级配置优先于积分全局配置（等级越高抵得越多）
        </div>
      </ElFormItem>
      <ElFormItem label="描述" prop="description">
        <ElInput
          v-model="state.form.description"
          type="textarea"
          placeholder="请输入描述"
        />
      </ElFormItem>
      <ElFormItem label="关联等级" prop="levelIds">
        <ElSelect
          v-model="state.form.levelIds"
          multiple
          placeholder="请选择关联等级"
          style="width: 100%"
        >
          <ElOption
            v-for="item in levelList"
            :key="item.id"
            :label="item.levelName"
            :value="item.id"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio value="0">启用</ElRadio>
          <ElRadio value="1">禁用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="submitForm">
        确定
      </ElButton>
    </template>
  </ElDialog>
</template>
<style scoped>
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
}
</style>
