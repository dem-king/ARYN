<script lang="ts" setup>
import type {
  ShipGoodsProfile,
  ShipSkuProfile,
} from '#/api/product/ship-profile';

import { computed, reactive, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCol,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  computePublishCompleteness,
  validateQtyRule,
  validateSaleScope,
  validateShipCodes,
  validateStorageType,
} from '../ship-product-validation';

const props = defineProps<{
  skuOptions: Array<{ id: string; label: string }>;
}>();

const emit = defineEmits<{
  save: [
    {
      profile: Partial<ShipGoodsProfile>;
      skuProfiles: ShipSkuProfile[];
    },
  ];
}>();

const profile = reactive<Partial<ShipGoodsProfile>>({
  barcode: '',
  impaCode: '',
  internalItemCode: '',
  issaCode: '',
  nameEn: '',
  saleScope: '3',
  searchAliases: '',
  shelfLifeDays: undefined,
  shipSupplyRemark: '',
  storageType: undefined,
  temperatureRequirement: '',
});

const skuProfiles = reactive<ShipSkuProfile[]>([]);

const ensureSkuRows = () => {
  for (const option of props.skuOptions) {
    if (!skuProfiles.some((item) => item.skuId === option.id)) {
      skuProfiles.push({ moq: 1, skuId: option.id, stepQty: 1 });
    }
  }
};

watch(
  () => props.skuOptions,
  () => ensureSkuRows(),
  { immediate: true, deep: true },
);

const completeness = computed(() =>
  computePublishCompleteness(profile as any, skuProfiles[0] as any),
);

const profileIssues = computed(() => {
  const issues: string[] = [];
  const scopeError = validateSaleScope(profile.saleScope);
  if (scopeError) issues.push(scopeError);
  const codeError = validateShipCodes(profile as any);
  if (codeError) issues.push(codeError);
  const storageError = validateStorageType(profile.storageType);
  if (storageError) issues.push(storageError);
  for (const sku of skuProfiles) {
    const qtyError = validateQtyRule(profile.saleScope, sku as any);
    if (qtyError) {
      const label = props.skuOptions.find(
        (option) => option.id === sku.skuId,
      )?.label;
      issues.push(`${label ?? sku.skuId}：${qtyError}`);
    }
  }
  return issues;
});

const handleSave = () => {
  if (profileIssues.value.length > 0) return;
  emit('save', { profile: { ...profile }, skuProfiles: [...skuProfiles] });
};

defineExpose({
  /**
   * 从服务端资料回填（首次为空时保持默认值）
   */
  load(input: {
    profile: null | ShipGoodsProfile;
    skuProfiles: ShipSkuProfile[];
  }) {
    if (input.profile) {
      Object.assign(profile, input.profile);
    }
    skuProfiles.splice(0, skuProfiles.length, ...(input.skuProfiles ?? []));
    ensureSkuRows();
  },
});
</script>

<template>
  <div>
    <ElForm label-width="110px">
      <ElRow>
        <ElCol :span="8">
          <ElFormItem label="销售范围" required>
            <ElSelect v-model="profile.saleScope" style="width: 100%">
              <ElOption label="仅个人购买" value="1" />
              <ElOption label="仅船供采购" value="2" />
              <ElOption label="个人购买和船供采购" value="3" />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="IMPA 编码">
            <ElInput v-model="profile.impaCode" placeholder="IMPA 编码" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="ISSA 编码">
            <ElInput v-model="profile.issaCode" placeholder="ISSA 编码" />
          </ElFormItem>
        </ElCol>
      </ElRow>
      <ElRow>
        <ElCol :span="8">
          <ElFormItem label="内部物料编码">
            <ElInput
              v-model="profile.internalItemCode"
              placeholder="内部物料编码"
            />
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="条形码">
            <ElInput v-model="profile.barcode" placeholder="条形码" />
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="英文品名">
            <ElInput v-model="profile.nameEn" placeholder="英文品名" />
          </ElFormItem>
        </ElCol>
      </ElRow>
      <ElRow>
        <ElCol :span="8">
          <ElFormItem label="储存条件">
            <ElSelect
              v-model="profile.storageType"
              clearable
              style="width: 100%"
            >
              <ElOption label="常温" value="1" />
              <ElOption label="冷藏" value="2" />
              <ElOption label="冷冻" value="3" />
              <ElOption label="危险品" value="4" />
              <ElOption label="其他" value="5" />
            </ElSelect>
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="保质期（天）">
            <ElInputNumber
              v-model="profile.shelfLifeDays"
              :min="0"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
        </ElCol>
        <ElCol :span="8">
          <ElFormItem label="温度要求">
            <ElInput
              v-model="profile.temperatureRequirement"
              placeholder="如 2~8℃"
            />
          </ElFormItem>
        </ElCol>
      </ElRow>
      <ElFormItem label="搜索别名">
        <ElInput
          v-model="profile.searchAliases"
          placeholder="逗号分隔，如 mooring,rope,缆绳"
        />
      </ElFormItem>
      <ElFormItem label="船供说明">
        <ElInput v-model="profile.shipSupplyRemark" type="textarea" :rows="2" />
      </ElFormItem>
    </ElForm>

    <ElTable :data="skuProfiles" border class="mb10">
      <ElTableColumn label="SKU" min-width="180">
        <template #default="scope">
          {{
            props.skuOptions.find((option) => option.id === scope.row.skuId)
              ?.label ?? scope.row.skuId
          }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="采购单位" width="120">
        <template #default="scope">
          <ElInput v-model="scope.row.purchaseUnit" placeholder="箱/卷" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="箱规" width="140">
        <template #default="scope">
          <ElInput v-model="scope.row.packageSpec" placeholder="1*24" />
        </template>
      </ElTableColumn>
      <ElTableColumn label="MOQ" width="130">
        <template #default="scope">
          <ElInputNumber
            v-model="scope.row.moq"
            :min="1"
            controls-position="right"
            style="width: 100%"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="步长" width="130">
        <template #default="scope">
          <ElInputNumber
            v-model="scope.row.stepQty"
            :min="1"
            controls-position="right"
            style="width: 100%"
          />
        </template>
      </ElTableColumn>
    </ElTable>

    <ElAlert
      v-if="profileIssues.length > 0"
      type="warning"
      :closable="false"
      class="mb10"
      :title="profileIssues.join('；')"
    />
    <div class="mb10" style="color: #909399">
      资料完整度：{{ completeness }}%
    </div>
    <ElButton
      type="primary"
      :disabled="profileIssues.length > 0"
      @click="handleSave"
    >
      保存船供资料
    </ElButton>
  </div>
</template>
