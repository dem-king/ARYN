<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type { NewbieForm, NewbieSkuPrice } from './data';

import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ArrowLeft, Delete } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getById as getSpuById } from '#/api/product/goods-spu';
import {
  addNewbieActivity,
  getNewbieDetail,
  updateNewbieActivity,
} from '#/api/promotion/newbie-activity';

import {
  createDefaultForm,
  formRules,
  fromActivity,
  toActivityPayload,
} from './data';

const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
);

const route = useRoute();
const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const selectGoods = ref();

interface SpuItem {
  id: string;
  name: string;
  salesPrice?: number;
  spuUrls?: string[];
}

interface SkuItem {
  id: string;
  salesPrice?: number;
}

const state = reactive<{ form: NewbieForm }>({
  form: createDefaultForm(),
});

const editId = (route.query.id as null | string) ?? null;

const goBack = () => {
  router.push('/promotion/newbie-activity');
};

/** 商品选择：SPU → 默认 SKU（第一个），与秒杀/折扣选品口径一致 */
const onSelectGoods = () => {
  selectGoods.value.initPage();
};

const spuCurrent = async (spuList: SpuItem[]) => {
  if (!spuList || spuList.length === 0) return;
  loading.value = true;
  try {
    await Promise.all(
      spuList.map(async (item) => {
        const spuDetail: any = await getSpuById(item.id);
        const skus: SkuItem[] = spuDetail?.goodsSkus || [];
        if (skus.length === 0) {
          ElMessage.warning(`商品【${item.name}】无可用 SKU，已跳过`);
          return;
        }
        const defaultSku = skus[0]!;
        // 已配置过的 SKU 不重复添加（发布校验要求 skus 与范围一一对应且不重复）
        if (state.form.skuPrices.some((row) => row.skuId === defaultSku.id)) {
          return;
        }
        const salePrice = Number(defaultSku.salesPrice ?? item.salesPrice ?? 0);
        state.form.skuPrices.push({
          originalPrice: salePrice,
          price: salePrice,
          skuId: defaultSku.id,
          spuId: item.id,
          // 供列表展示与校验用（rules 只持久化 skuId/spuId/price 三键）
          spuName: item.name,
          spuUrls: item.spuUrls || [],
        } as NewbieSkuPrice);
      }),
    );
  } catch {
    ElMessage.error('加载商品 SKU 信息失败');
  } finally {
    loading.value = false;
  }
};

const removeSku = (index: number) => {
  state.form.skuPrices.splice(index, 1);
};

const onSubmit = async () => {
  await formRef.value?.validate();
  if (state.form.skuPrices.length === 0) {
    ElMessage.warning('请至少选择一个新人专享商品');
    return;
  }
  const invalid = state.form.skuPrices.find((row) => !(Number(row.price) > 0));
  if (invalid) {
    ElMessage.warning('每个商品都必须填写大于 0 的新人价');
    return;
  }
  const above = state.form.skuPrices.find(
    (row) =>
      row.originalPrice !== undefined &&
      Number(row.price) >= Number(row.originalPrice),
  );
  if (above) {
    ElMessage.warning(`商品【${above.spuName}】的新人价必须低于当前售价`);
    return;
  }
  loading.value = true;
  try {
    const payload = toActivityPayload(state.form);
    if (editId) {
      await updateNewbieActivity(editId, payload);
      ElMessage.success('保存成功');
    } else {
      await addNewbieActivity(payload);
      ElMessage.success('创建成功，请在列表中发布后生效');
    }
    goBack();
  } finally {
    loading.value = false;
  }
};

/** 编辑回填：补商品名/图（rules 里只存 skuId/spuId 与价格） */
const initForm = async () => {
  if (!editId) return;
  loading.value = true;
  try {
    const activity: any = await getNewbieDetail(editId);
    state.form = fromActivity(activity);
    // 一次性批量取 SPU（含 goodsSkus 明细），按 spuId → SKU 匹配回填展示字段
    const spuIdSet = new Set<string>();
    for (const row of state.form.skuPrices) {
      if (row.spuId) {
        spuIdSet.add(row.spuId);
      }
    }
    const spuIds = [...spuIdSet];
    if (spuIds.length === 0) {
      return;
    }
    const spuList: any[] = await requestSpusByIds(spuIds);
    const spuMap = new Map((spuList ?? []).map((spu) => [spu.id, spu]));
    for (const row of state.form.skuPrices) {
      const spu: any = row.spuId ? spuMap.get(row.spuId) : undefined;
      if (!spu) {
        row.spuName = row.skuId;
        continue;
      }
      row.spuName = spu.name ?? row.skuId;
      row.spuUrls = spu.spuUrls ?? [];
      const sku = (spu.goodsSkus ?? []).find(
        (item: any) => item.id === row.skuId,
      );
      row.originalPrice = sku?.salesPrice ?? spu.salesPrice;
    }
  } finally {
    loading.value = false;
  }
};

/**
 * 批量取 SPU 详情。
 *
 * 活动 rules 只持久化 skuId/spuId 与新人价，编辑回填需要的商品名/图/售价
 * 必须另行查询，否则运营看到的是裸 SKU ID、无法核对调价是否合理。
 */
async function requestSpusByIds(spuIds: string[]) {
  const { getByIds } = await import('#/api/product/goods-spu');
  return await getByIds(spuIds.join(','));
}

initForm();
</script>

<template>
  <div class="hx-layout-container p-4">
    <ElCard>
      <template #header>
        <div class="flex items-center justify-between">
          <span>{{ editId ? '编辑新人专享活动' : '新增新人专享活动' }}</span>
          <ElButton :icon="ArrowLeft" @click="goBack">返回</ElButton>
        </div>
      </template>

      <ElForm
        ref="formRef"
        v-loading="loading"
        :model="state.form"
        :rules="formRules"
        label-width="120px"
      >
        <ElFormItem label="活动名称" prop="activityName">
          <ElInput
            v-model="state.form.activityName"
            maxlength="30"
            placeholder="如：新人专享"
            style="width: 320px"
          />
        </ElFormItem>
        <ElFormItem label="活动时间" prop="startTime">
          <ElDatePicker
            v-model="state.form.startTime"
            placeholder="开始时间"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
          <span class="mx-2">至</span>
          <ElDatePicker
            v-model="state.form.endTime"
            placeholder="结束时间"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </ElFormItem>
        <ElFormItem label="注册时效">
          <ElInputNumber
            v-model="state.form.registerWithinDays"
            :min="1"
            :precision="0"
            placeholder="不限"
          />
          <span class="ml-2 text-gray-500">
            天内注册的用户可享（留空表示不限时效）
          </span>
        </ElFormItem>
        <ElFormItem label="每人限购">
          <span>每 SKU 1 件（固定，超量下单会被拦截）</span>
        </ElFormItem>
        <ElFormItem label="优先级">
          <ElInputNumber v-model="state.form.priority" :min="0" />
        </ElFormItem>
        <ElFormItem label="专享商品">
          <ElButton type="primary" @click="onSelectGoods">选择商品</ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable :data="state.form.skuPrices" border>
        <ElTableColumn label="商品图" width="90">
          <template #default="{ row }">
            <ElImage
              v-if="row.spuUrls && row.spuUrls[0]"
              :preview-src-list="row.spuUrls"
              :src="row.spuUrls[0]"
              fit="contain"
              style="width: 48px; height: 48px"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="商品名称" min-width="180">
          <template #default="{ row }">
            {{ row.spuName || row.skuId }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="当前售价" width="120">
          <template #default="{ row }">
            {{
              row.originalPrice !== undefined ? `¥${row.originalPrice}` : '-'
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="新人专享价" width="180">
          <template #default="{ row }">
            <ElInputNumber
              v-model="row.price"
              :min="0.01"
              :precision="2"
              :step="1"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" label="操作" width="90">
          <template #default="{ $index }">
            <ElButton
              :icon="Delete"
              link
              type="danger"
              @click="removeSku($index)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <span>尚未选择商品，点击上方「选择商品」添加</span>
        </template>
      </ElTable>

      <div class="mt-4 flex justify-end gap-2">
        <ElButton @click="goBack">取消</ElButton>
        <ElButton :loading="loading" type="primary" @click="onSubmit">
          保存
        </ElButton>
      </div>
    </ElCard>

    <SelectGoods ref="selectGoods" :limit-num="50" @current-row="spuCurrent" />
  </div>
</template>
