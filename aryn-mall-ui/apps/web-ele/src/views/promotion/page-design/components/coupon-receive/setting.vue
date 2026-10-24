<script lang="ts" setup>
import { ref, watch } from 'vue';

import {
  ElForm,
  ElFormItem,
  ElRadio,
  ElRadioGroup,
  ElSlider,
  ElSwitch,
} from 'element-plus';

import IdPickerField from '../common/id-picker-field/index.vue';

interface CouponReceiveDataSource {
  mode: string;
  targetIds: string[];
}

interface CouponReceiveForm {
  commonStyle: Record<string, unknown>;
  dataSource: CouponReceiveDataSource;
  showNum: number;
  showReceiveBtn: boolean;
  showStyle: string;
}

const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);

const defaultConfig = {
  showStyle: '2',
  showNum: 3,
  showReceiveBtn: true,
  dataSource: {
    mode: 'automatic',
    targetIds: [] as string[],
  },
  commonStyle: {
    styleTopMargin: 0,
    styleBottomMargin: 0,
    styleLeftMargin: 10,
    styleRightMargin: 10,
    styleTopPadding: 0,
    styleBottomPadding: 0,
    styleLeftPadding: 0,
    styleRightPadding: 0,
    styleLtRadius: 0,
    styleRtRadius: 0,
    styleLbRadius: 0,
    styleRbRadius: 0,
    bgColorDirection: 'to right',
    bgStartColor: '',
    bgEndColor: '',
    bgPicUrl: '',
  },
};

const form = ref<CouponReceiveForm>({
  ...defaultConfig,
  ...props.modelValue,
  dataSource: {
    ...defaultConfig.dataSource,
    ...props.modelValue?.dataSource,
  },
});

watch(
  form,
  (val) => {
    emit('update:modelValue', val);
  },
  { deep: true, immediate: true },
);

// 手选超过「最多显示」时按选择顺序截断，避免面板保留看不见的配置
watch(
  () => form.value.showNum,
  (value) => {
    const targetIds = form.value.dataSource.targetIds;
    if (targetIds.length > value) {
      form.value.dataSource.targetIds = targetIds.slice(0, value);
    }
  },
);
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>优惠券领取</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElFormItem label="券来源">
          <ElRadioGroup v-model="form.dataSource.mode">
            <ElRadio value="automatic">自动领取列表</ElRadio>
            <ElRadio value="manual">手选优惠券</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem v-if="form.dataSource.mode === 'manual'" label="手选券">
          <IdPickerField
            kind="coupon"
            :limit="form.showNum"
            :model-value="form.dataSource.targetIds"
            @update:model-value="form.dataSource.targetIds = $event"
          />
        </ElFormItem>
        <ElFormItem label="展示样式">
          <ElRadioGroup v-model="form.showStyle">
            <ElRadio value="1">列表</ElRadio>
            <ElRadio value="2">卡片</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="最多显示">
          <div class="show-num-field">
            <ElSlider
              v-model="form.showNum"
              :min="1"
              :max="10"
              show-input
              :show-input-controls="false"
            />
            <p class="form-tip">
              {{
                form.dataSource.mode === 'manual'
                  ? `最多展示 ${form.showNum} 张，手选超出时按选择顺序截断`
                  : `按可领券列表取前 ${form.showNum} 张，不足时按实际数量展示；已抢完的券自动隐藏`
              }}
            </p>
          </div>
        </ElFormItem>
        <ElFormItem label="领取按钮">
          <ElSwitch v-model="form.showReceiveBtn" />
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;

.show-num-field {
  width: 100%;
}

.form-tip {
  margin: 4px 0 0;
  line-height: 1.5;
}
</style>
