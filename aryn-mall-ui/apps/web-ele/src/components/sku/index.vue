<script lang="ts" setup>
import { reactive, ref, watch } from 'vue';

import { cloneDeep } from '@vben/utils';

import { CloseBold } from '@element-plus/icons-vue';
import { ElButton, ElOption, ElSelect, ElTag } from 'element-plus';

import { addObj as addSpecsObj } from '#/api/product/goods-specs';
import {
  addObj as addSpecsValueObj,
  getList as getSpecsValueList,
} from '#/api/product/goods-specs-value';

import SkuTable from './sku-table/index.vue';

const props = defineProps({
  specsListProp: {
    type: Array,
    default: () => [],
  },
  skusData: {
    type: Array,
    default: () => [],
  },
  goodsSpuSpecs: {
    type: Array,
    default: () => [],
  },
  shopId: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['getGoodsSkus']);

const state = reactive({
  specsValueId: '',
  specsValueList: [] as any[],
  visible: true,
});

const spuSpecs = ref<any[]>([]);
const specsList = ref<any[]>([]);

const getGoodsSkus = (skus: any) => {
  emit('getGoodsSkus', skus, spuSpecs.value);
};

watch(
  () => props.goodsSpuSpecs,
  (val) => {
    spuSpecs.value = val ? cloneDeep(val) : [];
  },
  { deep: true, immediate: true },
);

watch(
  () => props.specsListProp,
  (val) => {
    specsList.value = val ? [...val] : [];
  },
  { deep: true, immediate: true },
);

/**
 * Add a new specification
 */
const addSpecs = () => {
  const selectedIds = new Set(
    spuSpecs.value.map((s: any) => s.specsId).filter((id: any) => !!id),
  );
  specsList.value.forEach((spec: any) => {
    spec.disabled = selectedIds.has(spec.id);
  });
  spuSpecs.value.push({
    specsId: '',
    specsName: '',
    goodsSkuSpecsValues: [],
  });
};

/**
 * Fetch spec values when a spec is selected/focused
 */
const fetchSpecsValues = (specsId: string, index: number) => {
  state.specsValueList = [];
  if (!specsId) return;

  getSpecsValueList({ specsId })
    .then((response) => {
      state.specsValueList = response;
      // Disable already selected values
      const currentValues = spuSpecs.value[index].goodsSkuSpecsValues || [];
      const selectedValueIds = new Set(
        currentValues.map((v: any) => v.specsValueId),
      );

      state.specsValueList.forEach((item: any) => {
        item.disabled = selectedValueIds.has(item.id);
      });
    })
    .catch(() => {
      state.specsValueList = [];
    });
};

/**
 * Handle spec selection change
 */
const changeSpecs = async (val: string, index: number) => {
  const targetSpec = spuSpecs.value[index];
  if (!targetSpec || !val) return;

  // Check if it's an existing spec or a new one
  const existingSpec = specsList.value.find((item: any) => item.id === val);

  if (existingSpec) {
    targetSpec.specsName = existingSpec.name;
    targetSpec.specsId = existingSpec.id;
  } else {
    // New spec
    try {
      const specsObj = await addSpecsObj({ name: val });
      specsList.value.push(specsObj);
      targetSpec.specsName = specsObj.name;
      targetSpec.specsId = specsObj.id;
    } catch (error) {
      console.error('Failed to add spec:', error);
      // Revert or handle error
      targetSpec.specsId = '';
      return;
    }
  }

  // Clear values when spec changes
  targetSpec.goodsSkuSpecsValues = [];
};

/**
 * Handle spec value selection change
 */
const changeSpecsValue = async (val: string, index: number) => {
  if (!val || !spuSpecs.value[index]) return;
  const targetSpec = spuSpecs.value[index];
  if (!targetSpec.specsId) return;

  const existingValue = state.specsValueList.find(
    (item: any) => item.id === val,
  );

  const newValue = {
    specsValueId: '',
    specsValueName: '',
    specsId: targetSpec.specsId,
    specsName: targetSpec.specsName,
    picUrl: '',
  };

  if (existingValue) {
    newValue.specsValueId = existingValue.id;
    newValue.specsValueName = existingValue.name;
    existingValue.disabled = true; // Disable locally
  } else {
    // Create new value
    try {
      const specsObj = await addSpecsValueObj({
        name: val,
        specsId: targetSpec.specsId,
      });
      newValue.specsValueId = specsObj.id;
      newValue.specsValueName = specsObj.name;
    } catch (error) {
      console.error('Failed to add spec value:', error);
      return;
    }
  }

  if (!targetSpec.goodsSkuSpecsValues) {
    targetSpec.goodsSkuSpecsValues = [];
  }
  targetSpec.goodsSkuSpecsValues.push(newValue);
  state.specsValueId = '';
};

/**
 * Delete a spec
 */
const delSpecs = (index: number) => {
  spuSpecs.value.splice(index, 1);
  // Re-evaluate disabled state for specs list if needed
};

/**
 * Delete a spec value
 */
const delSpecsValue = (indexValue: number, specsIndex: number) => {
  const targetSpec = spuSpecs.value[specsIndex];
  const removed = targetSpec.goodsSkuSpecsValues.splice(indexValue, 1)[0];

  // Re-enable in dropdown if currently viewing
  if (removed && state.specsValueList.length > 0) {
    const opt = state.specsValueList.find(
      (o: any) => o.id === removed.specsValueId,
    );
    if (opt) opt.disabled = false;
  }
};

const visibleChange = (visible: boolean) => {
  if (!visible) return;
  const selectedIds = new Set(
    spuSpecs.value.map((s: any) => s.specsId).filter((id: any) => !!id),
  );
  specsList.value.forEach((spec: any) => {
    spec.disabled = selectedIds.has(spec.id);
  });
};
</script>

<template>
  <div class="sku-container">
    <div class="specs-config">
      <div v-if="spuSpecs && spuSpecs.length > 0">
        <div v-for="(item, index) in spuSpecs" :key="index" class="specs-item">
          <div class="specs-header">
            <span class="label">规格名：</span>
            <ElSelect
              v-model="item.specsId"
              filterable
              allow-create
              placeholder="请输入或选择规格名"
              @visible-change="visibleChange"
              @change="(val: any) => changeSpecs(val, index)"
              class="specs-select"
            >
              <ElOption
                v-for="spec in specsList"
                :key="spec.id"
                :label="spec.name"
                :value="spec.id"
                :disabled="spec.disabled"
              />
            </ElSelect>
            <ElButton
              circle
              @click="delSpecs(index)"
              :icon="CloseBold"
              class="delete-btn"
            />
          </div>

          <div class="specs-values">
            <span class="label">规格值：</span>
            <div class="values-list">
              <div
                v-for="(itemValue, indexValue) in item.goodsSkuSpecsValues"
                :key="indexValue"
                class="value-item"
              >
                <ElTag
                  effect="plain"
                  closable
                  @close="delSpecsValue(indexValue, index)"
                >
                  {{ itemValue.specsValueName }}
                </ElTag>
              </div>

              <ElSelect
                v-model="state.specsValueId"
                filterable
                allow-create
                placeholder="请输入或选择规格值"
                class="value-input"
                @focus="fetchSpecsValues(item.specsId, index)"
                @change="(val: any) => changeSpecsValue(val, index)"
              >
                <ElOption
                  v-for="opt in state.specsValueList"
                  :key="opt.id"
                  :label="opt.name"
                  :value="opt.id"
                  :disabled="opt.disabled"
                />
              </ElSelect>
            </div>
          </div>
        </div>
      </div>

      <ElButton
        @click="addSpecs"
        v-if="spuSpecs.length < 3"
        type="primary"
        plain
        class="add-btn"
      >
        添加规格
      </ElButton>
    </div>

    <div class="sku-table-wrapper">
      <SkuTable
        :skus-data="skusData"
        :goods-spu-specs="spuSpecs"
        :shop-id="shopId"
        @get-goods-skus="getGoodsSkus"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.sku-container {
  .specs-config {
    padding: 20px;
    margin-bottom: 20px;
    border: 1px solid var(--el-border-color);
    border-radius: 4px;

    .specs-item {
      min-height: 200px;
      padding: 16px;
      margin-bottom: 16px;
      background-color: var(--el-fill-color-light);
      border-radius: 4px;

      .specs-header {
        display: flex;
        align-items: center;
        margin-bottom: 16px;

        .label {
          margin-right: 12px;
          font-weight: 500;
        }

        .specs-select {
          width: 240px;
          margin-right: auto;
        }
      }

      .specs-values {
        display: flex;
        align-items: flex-start;

        .label {
          margin-top: 6px;
          margin-right: 12px;
          font-weight: 500;
        }

        .values-list {
          display: flex;
          flex: 1;
          flex-wrap: wrap;
          gap: 12px;

          .value-item {
            gap: 18px;
            min-width: 80px;

            .value-image {
              height: 40px;
              margin: 0;
            }
          }

          .value-input {
            width: 200px;
          }
        }
      }
    }

    .add-btn {
      width: 100%;
      border-style: dashed;
    }
  }

  .sku-table-wrapper {
    margin-top: 20px;
  }
}
</style>
