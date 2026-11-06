<script lang="ts" setup>
import type { SalesConfigRecord } from '#/api/promotion/sales-config';

import { computed, reactive, ref } from 'vue';

import { Check, InfoFilled } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInputNumber,
  ElMessage,
  ElTooltip,
} from 'element-plus';

import { getConfig, saveConfig } from '#/api/promotion/sales-config';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

/**
 * 「销售全局配置」页：每个租户一行配置。
 * 数据落在 sales_config，业绩单/台账生成时按此读取；修改只影响此后生成的业绩单。
 */

/** 后端无配置时的默认值，与 sales_config 表默认值保持一致 */
const DEFAULT_CONFIG: SalesConfigRecord = {
  defaultCommissionRate: 0.05,
  referralRewardRate: 0,
  monthlyCutoffDay: 1,
  refundRiskDays: 7,
  claimExpireDays: 90,
};

const loading = ref(false);
const saving = ref(false);
const state = reactive({
  form: { ...DEFAULT_CONFIG } as SalesConfigRecord,
});

const defaultRateText = computed(
  () => `${(Number(state.form.defaultCommissionRate ?? 0) * 100).toFixed(2)}%`,
);
const referralRateText = computed(
  () => `${(Number(state.form.referralRewardRate ?? 0) * 100).toFixed(2)}%`,
);

const loadConfig = async () => {
  loading.value = true;
  try {
    const response = await getConfig();
    state.form = response
      ? { ...DEFAULT_CONFIG, ...response }
      : { ...DEFAULT_CONFIG };
  } catch {
    state.form = { ...DEFAULT_CONFIG };
  } finally {
    loading.value = false;
  }
};

const submitForm = async () => {
  const rate = Number(state.form.defaultCommissionRate ?? 0);
  if (rate < 0 || rate > 1) {
    ElMessage.error('默认提成比例需在 0-1 之间');
    return;
  }
  const referralRate = Number(state.form.referralRewardRate ?? 0);
  if (referralRate < 0 || referralRate > 1) {
    ElMessage.error('转交奖励比例需在 0-1 之间');
    return;
  }
  saving.value = true;
  try {
    await saveConfig({ ...state.form });
    ElMessage.success('配置已保存');
    loadConfig();
  } finally {
    saving.value = false;
  }
};

loadConfig();

useKeepAliveRefresh(loadConfig);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div v-loading="loading" class="config-card">
        <header class="config-card__header">
          <div class="config-card__title">销售提成全局配置</div>
          <div class="config-card__desc">
            影响此后生成的业绩单与月度发放台账，已生成的业绩单比例不回溯。
          </div>
        </header>

        <ElAlert
          class="config-card__alert"
          :closable="false"
          show-icon
          type="info"
          title="提成 = 实付金额（不含运费）× 销售个人比例，个人比例为空时使用下方默认比例"
        />

        <ElForm :model="state.form" label-width="160px" class="config-form">
          <ElFormItem label="默认提成比例">
            <ElInputNumber
              v-model="state.form.defaultCommissionRate"
              :max="1"
              :min="0"
              :precision="4"
              :step="0.01"
            />
            <span class="form-tip is-inline">
              {{ defaultRateText }}，销售未单独配置个人比例时生效
            </span>
            <ElTooltip content="0.05 表示 5%" placement="right">
              <ElIcon class="form-tip-icon"><InfoFilled /></ElIcon>
            </ElTooltip>
          </ElFormItem>

          <ElFormItem label="转交奖励比例">
            <ElInputNumber
              v-model="state.form.referralRewardRate"
              :max="1"
              :min="0"
              :precision="4"
              :step="0.01"
            />
            <span class="form-tip is-inline">
              {{ referralRateText }}，0 表示不启用；奖励 =
              客户首单实付（不含运费）× 比例
            </span>
          </ElFormItem>

          <ElFormItem label="月度台账生成日">
            <ElInputNumber
              v-model="state.form.monthlyCutoffDay"
              :max="28"
              :min="1"
              :precision="0"
            />
            <span class="form-tip is-inline">
              每月该日生成上月发放台账（1-28）
            </span>
          </ElFormItem>

          <ElFormItem label="退款风险观察期">
            <ElInputNumber
              v-model="state.form.refundRiskDays"
              :max="365"
              :min="0"
              :precision="0"
            />
            <span class="form-tip is-inline">
              支付后 N 天内标记为风险观察期，业绩单计划可发放时间 = 支付时间 + N
              天
            </span>
          </ElFormItem>

          <ElFormItem label="认领有效期">
            <ElInputNumber
              v-model="state.form.claimExpireDays"
              :max="365"
              :min="1"
              :precision="0"
            />
            <span class="form-tip is-inline">
              客户点击销售分享后生成的待转交认领有效天数（默认 90）
            </span>
          </ElFormItem>
        </ElForm>

        <footer class="config-card__footer">
          <ElButton @click="loadConfig">重置</ElButton>
          <ElButton
            v-access:code="'promotion:salesconfig:edit'"
            :icon="Check"
            :loading="saving"
            type="primary"
            @click="submitForm"
          >
            保存
          </ElButton>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.config-card {
  padding: 18px 20px 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.config-card__header {
  margin-bottom: 14px;
}

.config-card__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.config-card__desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.config-card__alert {
  margin-bottom: 18px;
}

.config-form {
  max-width: 720px;
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-tip.is-inline {
  margin-left: 10px;
}

.form-tip-icon {
  margin-left: 6px;
  color: var(--el-text-color-placeholder);
}

.config-card__footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 16px;
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
