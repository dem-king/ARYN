<script setup lang="ts">
import type {
  CampaignActivity,
  MainType,
} from '#/api/promotion/campaign-draft';

import { ref, watch } from 'vue';

import {
  ElButton,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

const props = defineProps<{
  activity?: CampaignActivity;
  mainType: MainType;
  skus: { id: string; label: string }[];
}>();
const emit = defineEmits<{ change: [value: CampaignActivity] }>();
interface RuleRow {
  skuId: string;
  minQty: number;
  unitPrice: number;
  minAmount: number;
  discountAmount: number;
  quantity: number;
  price: number;
}
const rows = ref<RuleRow[]>([]);
const name = ref('');
const giftStock = ref(1);
const minAmount = ref(0);
let original: Record<string, unknown> = {};
const blank = (): RuleRow => ({
  skuId: '',
  minQty: 1,
  unitPrice: 0,
  minAmount: 0,
  discountAmount: 0,
  quantity: 1,
  price: 0,
});
watch(
  () => props.activity,
  (value) => {
    name.value = value?.activityName ?? '';
    giftStock.value = value?.giftStock ?? 1;
    try {
      const rules = JSON.parse(value?.rules ?? '{}');
      original = rules;
      minAmount.value = rules.minAmount ?? 0;
      rows.value =
        props.mainType === '4'
          ? (rules.skuLadders ?? []).flatMap(
              (sku: {
                ladders: { minQty: number; unitPrice: number }[];
                skuId: string;
              }) =>
                sku.ladders.map((tier) => ({
                  ...blank(),
                  ...tier,
                  skuId: sku.skuId,
                })),
            )
          : (
              rules[
                ({ '7': 'tiers', '5': 'gifts' } as Record<string, string>)[
                  props.mainType
                ] ?? 'skus'
              ] ?? []
            ).map((row: Partial<RuleRow>) => ({ ...blank(), ...row }));
    } catch {
      rows.value = [];
      original = {};
    }
  },
  { immediate: true },
);
function changed() {
  if (props.mainType === 'POLICY') return;
  const rules = { ...original };
  switch (props.mainType) {
    case '4': {
      rules.skuLadders = [...new Set(rows.value.map((row) => row.skuId))].map(
        (skuId) => ({
          skuId,
          ladders: rows.value
            .filter((row) => row.skuId === skuId)
            .map(({ minQty, unitPrice }) => ({ minQty, unitPrice })),
        }),
      );

      break;
    }
    case '7': {
      rules.tiers = rows.value.map(({ minAmount, minQty, discountAmount }) =>
        minAmount > 0
          ? { minAmount, discountAmount }
          : { minQty, discountAmount },
      );
      break;
    }
    case '12': {
      rules.skus = rows.value.map(({ skuId, price }) => ({ skuId, price }));
      break;
    }
    default: {
      rules.gifts = rows.value.map(({ skuId, quantity }) => ({
        skuId,
        quantity,
      }));
      rules.minAmount = minAmount.value;
    }
  }
  emit('change', {
    ...props.activity,
    activityName: name.value,
    activityType: props.mainType,
    rules: JSON.stringify(rules),
    ...(props.mainType === '5' ? { giftStock: giftStock.value } : {}),
  });
}
</script>
<template>
  <div class="rule-editor">
    <ElFormItem label="方案名称">
      <ElInput v-model="name" maxlength="100" @change="changed" />
    </ElFormItem>
    <template v-if="mainType === '5'">
      <ElFormItem label="赠送门槛（元）">
        <ElInputNumber
          v-model="minAmount"
          :min="0"
          :precision="2"
          @change="changed"
        />
      </ElFormItem>
      <ElFormItem label="赠品总件数上限">
        <ElInputNumber
          v-model="giftStock"
          :min="1"
          :precision="0"
          @change="changed"
        />
      </ElFormItem>
    </template>
    <div v-for="(row, index) in rows" :key="index" class="rule-row">
      <ElSelect
        v-if="mainType !== '7'"
        v-model="row.skuId"
        placeholder="选择已核定商品规格"
        @change="changed"
      >
        <ElOption
          v-for="sku in skus"
          :key="sku.id"
          :label="sku.label"
          :value="sku.id"
        />
      </ElSelect>
      <template v-if="mainType === '4'">
        <label
          >起购件数<ElInputNumber
            v-model="row.minQty"
            :min="1"
            :precision="0"
            @change="changed" /></label
        ><label
          >每件价<ElInputNumber
            v-model="row.unitPrice"
            :min="0.01"
            :precision="2"
            @change="changed"
        /></label>
      </template>
      <template v-if="mainType === '7'">
        <label
          >满额（0为满量）<ElInputNumber
            v-model="row.minAmount"
            :min="0"
            :precision="2"
            @change="changed" /></label
        ><label v-if="!row.minAmount"
          >满件数<ElInputNumber
            v-model="row.minQty"
            :min="1"
            :precision="0"
            @change="changed" /></label
        ><label
          >优惠金额<ElInputNumber
            v-model="row.discountAmount"
            :min="0.01"
            :precision="2"
            @change="changed"
        /></label>
      </template>
      <label v-if="mainType === '12'"
        >特供价<ElInputNumber
          v-model="row.price"
          :min="0.01"
          :precision="2"
          @change="changed"
      /></label>
      <label v-if="mainType === '5'"
        >赠品件数<ElInputNumber
          v-model="row.quantity"
          :min="1"
          :precision="0"
          @change="changed"
      /></label>
      <ElButton
        link
        type="danger"
        @click="
          rows.splice(index, 1);
          changed();
        "
      >
        移除
      </ElButton>
    </div>
    <ElButton
      @click="
        rows.push(blank());
        changed();
      "
    >
      增加一档规则
    </ElButton>
  </div>
</template>
<style scoped>
.rule-editor {
  width: 100%;
}

.rule-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: end;
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.rule-row label {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
