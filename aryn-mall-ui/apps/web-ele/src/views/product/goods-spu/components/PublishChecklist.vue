<script lang="ts" setup>
import { computed } from 'vue';

import { ElAlert } from 'element-plus';

import {
  isPublishReadyForShipSupply,
  validateQtyRule,
  validateSaleScope,
  validateShipCodes,
} from '../ship-product-validation';

const props = defineProps<{
  profile: {
    impaCode?: string;
    internalItemCode?: string;
    issaCode?: string;
    saleScope?: string;
    storageType?: string;
  };
  sku?: { moq?: number; purchaseUnit?: string; stepQty?: number };
}>();

const issues = computed(() => {
  const result: string[] = [];
  const scopeError = validateSaleScope(props.profile.saleScope);
  if (scopeError) {
    result.push(scopeError);
    return result;
  }
  const codeError = validateShipCodes(props.profile);
  if (codeError) result.push(codeError);
  const qtyError = validateQtyRule(props.profile.saleScope, props.sku ?? {});
  if (qtyError) result.push(qtyError);
  return result;
});

const ready = computed(() =>
  isPublishReadyForShipSupply(props.profile as any, props.sku ?? {}),
);
</script>

<template>
  <div>
    <ElAlert
      :title="
        ready
          ? '资料满足船供目录发布要求'
          : `尚不满足船供目录发布要求：${issues.join('；')}`
      "
      :type="ready ? 'success' : 'warning'"
      :closable="false"
    />
  </div>
</template>
