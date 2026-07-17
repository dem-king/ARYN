<script lang="ts" setup>
import { reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElPopover,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import SelectMaterial from '#/components/select-material/index.vue';
import { arrEquals, descartes } from '#/utils/util';

export interface SpecItem {
  specsId: string;
  specsName: string;
  specsValueId: string;
  specsValueName: string;
}

interface SkuItem {
  costPrice: number;
  distributionFirstValue: number;
  distributionSecondValue: number;
  id?: string;
  ids: string[];
  originalPrice: number;
  picUrl: string;
  salesPrice: number;
  specsArr: SpecItem[];
  status: string;
  stock: number;
  volume: number;
  weight: number;
}

const props = withDefaults(
  defineProps<{
    goodsSpuSpecs: any[];
    shopId: string;
    skusData: any[];
  }>(),
  {
    skusData: () => [],
    shopId: '',
    goodsSpuSpecs: () => [],
  },
);

const emit = defineEmits(['getGoodsSkus']);

const state = reactive({
  skusList: [] as SkuItem[],
  form: {
    salesPrice: 0,
    originalPrice: 0,
    costPrice: 0,
    stock: 0,
    distributionFirstValue: 0,
    distributionSecondValue: 0,
    weight: 0,
    volume: 0,
  },
  batchPopover: false,
});
const formRef = ref();

watch(
  () => props.goodsSpuSpecs,
  (val) => {
    if (!val || val.length === 0) {
      state.skusList = [];
      return;
    }

    // Filter specs that have values selected
    const validSpecs = val.filter(
      (item) =>
        item.specsId &&
        item.specsName &&
        item.goodsSkuSpecsValues &&
        item.goodsSkuSpecsValues.length > 0,
    );

    if (validSpecs.length === 0) {
      state.skusList = [];
      return;
    }

    // Prepare data for Cartesian product
    const array = validSpecs.map((item) => {
      return item.goodsSkuSpecsValues.map((v: any) => ({
        specsId: item.specsId,
        specsName: item.specsName,
        specsValueId: v.specsValueId,
        specsValueName: v.specsValueName,
      }));
    });

    // Generate Cartesian product
    const combinations = descartes(array);

    // Map to SKU list
    const newSkus: SkuItem[] = combinations.map((combo: any) => {
      // Handle single spec vs multiple specs result from descartes
      const specsArrRaw = Array.isArray(combo) ? combo : [combo];

      // Create strictly typed specsArr
      const specsArr: SpecItem[] = specsArrRaw.map((item: any) => ({
        specsId: item.specsId,
        specsName: item.specsName,
        specsValueId: item.specsValueId,
        specsValueName: item.specsValueName,
      }));

      // Generate matching keys
      const ids = specsArr.map((s) => s.specsValueId);

      return {
        specsArr,
        ids, // Used for matching existing SKUs
        salesPrice: 0,
        originalPrice: 0,
        costPrice: 0,
        stock: 0,
        weight: 0,
        volume: 0,
        status: '0', // Default '0' for enabled based on existing logic
        distributionFirstValue: 0,
        distributionSecondValue: 0,
        picUrl: '',
      };
    });

    // Merge with existing data
    const existingSkus = props.skusData || [];

    newSkus.forEach((sku) => {
      const match = existingSkus.find((existing) => {
        // Match by specs value IDs
        if (existing.specsArr) {
          const existingIds = existing.specsArr.map((s: any) => s.specsValueId);
          return arrEquals(sku.ids, existingIds);
        }
        // Fallback for older data structure if needed (e.g. using specs_json or other matching)
        // For now, assume specsArr matches
        return false;
      });

      if (match) {
        sku.id = match.id;
        sku.salesPrice = match.salesPrice || 0;
        sku.originalPrice = match.originalPrice || 0;
        sku.costPrice = match.costPrice || 0;
        sku.stock = match.stock || 0;
        sku.weight = match.weight || 0;
        sku.volume = match.volume || 0;
        sku.status = match.status || '0';
        sku.distributionFirstValue = match.distributionFirstValue || 0;
        sku.distributionSecondValue = match.distributionSecondValue || 0;
        sku.picUrl = match.picUrl || '';
      }
    });

    state.skusList = newSkus;
  },
  { deep: true, immediate: true },
);

watch(
  () => state.skusList,
  (val) => {
    emit('getGoodsSkus', val);
  },
  { deep: true },
);

const batchAdd = () => {
  const fields = [
    'salesPrice',
    'originalPrice',
    'costPrice',
    'stock',
    'distributionFirstValue',
    'distributionSecondValue',
    'weight',
    'volume',
  ] as const;

  state.skusList.forEach((item) => {
    fields.forEach((field) => {
      const value = state.form[field];
      if (value !== undefined && value !== null) {
        item[field] = value;
      }
    });
  });
  popoverClose();
};

const popoverClose = () => {
  state.batchPopover = false;
  state.form = {
    salesPrice: 0,
    originalPrice: 0,
    costPrice: 0,
    stock: 0,
    distributionFirstValue: 0,
    distributionSecondValue: 0,
    weight: 0,
    volume: 0,
  };
};
</script>
<template>
  <div v-if="state.skusList.length > 0">
    <ElTable :data="state.skusList" border style="width: 100%">
      <!-- Dynamic Specs Columns -->
      <ElTableColumn
        show-overflow-tooltip
        v-for="(item, index) in state.skusList[0]?.specsArr || []"
        :key="index"
        :label="item.specsName || '规格'"
      >
        <template #default="scope">
          {{ scope.row.specsArr[index]?.specsValueName || '未选择' }}
        </template>
      </ElTableColumn>

      <ElTableColumn prop="picUrl" label="图片" width="100" key="static-pic">
        <template #default="scope">
          <SelectMaterial
            v-model="scope.row.picUrl"
            :can-choose-images-num="1"
            class="value-image"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="salesPrice"
        label="售价"
        min-width="120"
        key="static-sales-price"
      >
        <template #default="scope">
          <ElInputNumber
            v-model="scope.row.salesPrice"
            style="width: 100%"
            :min="0"
            :precision="2"
            :controls="false"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="originalPrice"
        label="原价"
        min-width="120"
        key="static-original-price"
      >
        <template #default="scope">
          <ElInputNumber
            v-model="scope.row.originalPrice"
            style="width: 100%"
            :min="0"
            :precision="2"
            :controls="false"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="costPrice"
        label="成本价"
        min-width="120"
        key="static-cost-price"
      >
        <template #default="scope">
          <ElInputNumber
            v-model="scope.row.costPrice"
            style="width: 100%"
            :min="0"
            :precision="2"
            :controls="false"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="stock"
        label="库存"
        min-width="100"
        key="static-stock"
      >
        <template #default="scope">
          <ElInputNumber
            v-model="scope.row.stock"
            style="width: 100%"
            :min="0"
            :precision="0"
            :controls="false"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="weight"
        label="重量(kg)"
        min-width="100"
        key="static-weight"
      >
        <template #default="scope">
          <ElInputNumber
            v-model="scope.row.weight"
            style="width: 100%"
            :min="0"
            :precision="2"
            :controls="false"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="volume"
        label="体积(m³)"
        min-width="100"
        key="static-volume"
      >
        <template #default="scope">
          <ElInputNumber
            v-model="scope.row.volume"
            style="width: 100%"
            :min="0"
            :precision="2"
            :controls="false"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="status"
        label="状态"
        width="90px"
        key="static-status"
      >
        <template #default="scope">
          <ElSwitch
            v-model="scope.row.status"
            inline-prompt
            active-value="0"
            inactive-value="1"
            active-text="启用"
            inactive-text="停用"
          />
        </template>
      </ElTableColumn>
    </ElTable>
    <ElPopover
      placement="right"
      :visible="state.batchPopover"
      :width="350"
      trigger="click"
    >
      <div class="popover-input">
        <ElForm ref="formRef" :model="state.form" label-width="120px">
          <ElFormItem label="售价">
            <ElInputNumber
              v-model="state.form.salesPrice"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="请输入商品售价"
            />
          </ElFormItem>
          <ElFormItem label="原价">
            <ElInputNumber
              v-model="state.form.originalPrice"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="请输入商品原价"
            />
          </ElFormItem>
          <ElFormItem label="成本价">
            <ElInputNumber
              v-model="state.form.costPrice"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="请输入商品成本价"
            />
          </ElFormItem>
          <ElFormItem label="库存">
            <ElInputNumber
              v-model="state.form.stock"
              :min="0"
              :precision="0"
              :controls="false"
              placeholder="请输入商品库存"
            />
          </ElFormItem>
          <ElFormItem label="重量">
            <ElInputNumber
              v-model="state.form.weight"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="请输入商品重量"
            />
          </ElFormItem>
          <ElFormItem label="体积">
            <ElInputNumber
              v-model="state.form.volume"
              :min="0"
              :precision="2"
              :controls="false"
              placeholder="请输入商品体积"
            />
          </ElFormItem>
          <ElFormItem label-width="120px">
            <ElButton @click="popoverClose">关闭</ElButton>
            <ElButton @click="batchAdd" type="primary">保存</ElButton>
          </ElFormItem>
        </ElForm>
      </div>
      <template #reference>
        <ElButton
          link
          @click="state.batchPopover = true"
          v-if="state.skusList.length > 0"
          type="primary"
        >
          批量设置
        </ElButton>
      </template>
    </ElPopover>
  </div>
</template>
