<script lang="ts" setup>
import { ref, watch } from 'vue';

import {
  ElAlert,
  ElForm,
  ElFormItem,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElSlider,
  ElSwitch,
} from 'element-plus';

import IdPickerField from '../../common/id-picker-field/index.vue';
import LinkUrl from '../../common/link-url/index.vue';
import {
  COUPON_LADDER_MAX_TIERS,
  COUPON_LADDER_MIN_TIERS,
  createCouponLadderDefaults,
} from '../types';

const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);

const defaults = createCouponLadderDefaults();

const form = ref({
  ...defaults,
  ...props.modelValue,
  commonStyle: {
    ...defaults.commonStyle,
    ...props.modelValue?.commonStyle,
  },
  dataSource: {
    ...defaults.dataSource,
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

// 展示档数下调后，手选里多出来的券按选择顺序截断，避免面板保留看不见的配置
watch(
  () => form.value.showNum,
  (value) => {
    const targetIds = form.value.dataSource.targetIds ?? [];
    if (targetIds.length > value) {
      form.value.dataSource.targetIds = targetIds.slice(0, value);
    }
  },
);
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>满减券阶梯</p>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-position="top">
        <ElAlert class="ladder-tip" :closable="false" show-icon type="info">
          档位不在这里填：取「优惠券」中满减券的使用门槛与面额，按门槛升序展示前
          {{ form.showNum }} 档。改券即改档位，页面无需重新编辑。
        </ElAlert>

        <ElFormItem label="券来源">
          <ElRadioGroup v-model="form.dataSource.mode">
            <ElRadio value="automatic">自动取满减券</ElRadio>
            <ElRadio value="manual">手选满减券</ElRadio>
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

        <ElFormItem label="展示档数">
          <ElSlider
            v-model="form.showNum"
            :min="COUPON_LADDER_MIN_TIERS"
            :max="COUPON_LADDER_MAX_TIERS"
            show-input
            :show-input-controls="false"
          />
        </ElFormItem>

        <ElFormItem label="领取按钮">
          <ElSwitch v-model="form.showClaimBtn" />
          <span class="ladder-inline-tip">
            关闭后只展示档位，不提供领取入口
          </span>
        </ElFormItem>

        <ElFormItem label="标题">
          <ElInput v-model="form.title" maxlength="12" show-word-limit />
        </ElFormItem>
        <ElFormItem label="副标题">
          <ElInput v-model="form.subtitle" maxlength="24" show-word-limit />
        </ElFormItem>
        <ElFormItem label="活动标签">
          <ElInput
            v-model="form.badgeText"
            maxlength="16"
            placeholder="如：预热期 11.01 - 11.10（留空不展示）"
          />
        </ElFormItem>
        <ElFormItem label="规则说明">
          <ElInput
            v-model="form.noteText"
            :autosize="{ minRows: 3, maxRows: 6 }"
            maxlength="120"
            placeholder="如：满减券与折扣活动不可同时使用，退款后已用优惠不予返还（留空不展示）"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem label="底部按钮文案">
          <ElInput
            v-model="form.ctaText"
            maxlength="12"
            placeholder="留空则不展示底部按钮"
          />
        </ElFormItem>
        <ElFormItem label="底部按钮链接">
          <LinkUrl v-model="form.ctaLink" />
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>

<style lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;

.ladder-tip {
  margin-bottom: 12px;
}

.ladder-inline-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
