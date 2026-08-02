<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type {
  DiscountActivityForm,
  DiscountGoodsForm,
} from '#/api/promotion/discount';

import { computed, defineAsyncComponent, reactive, ref, watch } from 'vue';
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
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getById as getSpuById } from '#/api/product/goods-spu';
import { addObj, editObj, getById } from '#/api/promotion/discount';

import { discountTypeOptions, formRules, scopeOptions } from './data';

const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
);

const route = useRoute();
const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const selectGoods = ref();

/** 商品选择器返回的 SPU 项 */
interface DiscountSpuItem {
  /** SPU ID */
  id: string;
  /** 商品名称 */
  name: string;
  /** 商品图片地址列表 */
  spuUrls?: string[];
  /** 销售价 */
  salesPrice?: number;
}

/** SPU 详情（含 SKU 列表） */
interface DiscountSpuDetail extends DiscountSpuItem {
  /** SKU 列表 */
  goodsSkus?: DiscountSkuItem[];
}

/** SKU 项 */
interface DiscountSkuItem {
  /** SKU ID */
  id: string;
  /** 销售价 */
  salesPrice?: number;
  /** 库存 */
  stock?: number;
}

const state = reactive<{
  form: DiscountActivityForm;
}>({
  form: {
    id: undefined as string | undefined,
    activityName: '',
    description: '',
    datatimes: [] as string[],
    startTime: '',
    endTime: '',
    discountType: 1,
    discountValue: 0.8,
    scope: 1,
    goodsList: [] as DiscountGoodsForm[],
  },
});

/** 当前折扣类型的提示文案 */
const discountHint = computed(() => {
  const found = discountTypeOptions.find(
    (item) => item.value === state.form.discountType,
  );
  return found ? found.hint : '';
});

/** 折扣值最小值 */
const discountValueMin = computed(() => {
  if (state.form.discountType === 1) return 0.01;
  if (state.form.discountType === 2) return 0.01;
  return 0.01;
});

/** 折扣值精度 */
const discountValuePrecision = computed(() => {
  if (state.form.discountType === 1) return 2;
  return 2;
});

/** 监听折扣类型变化，重置折扣值 */
watch(
  () => state.form.discountType,
  (newType) => {
    if (newType === 1 && state.form.discountValue > 1) {
      state.form.discountValue = 0.8;
    }
    if (newType === 3 && state.form.discountValue > 10_000) {
      state.form.discountValue = 99;
    }
  },
);

/** 从 URL 读取 id（编辑模式） */
const editId = (route.query.id as null | string) ?? null;

/** 返回列表页 */
const goBack = () => {
  router.push('/promotion/discount-activity');
};

/** 选择商品 */
const selectSpu = () => {
  if (state.form.scope !== 2) {
    ElMessage.warning('请先选择"指定商品"适用范围');
    return;
  }
  selectGoods.value.initPage();
};

/**
 * 商品选择回调
 * 商品选择器返回的是 SPU 列表，需要根据 SPU ID 查询其下的 SKU 列表，
 * 取默认 SKU（第一个）作为折扣目标，避免将 SPU ID 错赋为 SKU ID。
 */
const spuCurrent = async (spuList: DiscountSpuItem[]) => {
  if (!spuList || spuList.length === 0) return;
  loading.value = true;
  try {
    await Promise.all(
      spuList.map(async (item) => {
        // 去重
        const exists = state.form.goodsList.find((g) => g.spuId === item.id);
        if (exists) return;
        const spuDetail = (await getSpuById(item.id)) as DiscountSpuDetail;
        const skus = spuDetail?.goodsSkus || [];
        if (skus.length === 0) {
          ElMessage.warning(`商品【${item.name}】无可用 SKU，已跳过`);
          return;
        }
        // 取默认 SKU（第一个）
        const defaultSku = skus[0]!;
        state.form.goodsList.push({
          spuId: item.id,
          skuId: defaultSku.id,
          spuName: item.name,
          spuUrls: item.spuUrls || [],
        });
      }),
    );
  } catch {
    ElMessage.error('加载商品 SKU 信息失败');
  } finally {
    loading.value = false;
  }
};

/** 删除商品 */
const removeGoods = (index: number) => {
  state.form.goodsList.splice(index, 1);
};

/** 切换适用范围时清空商品列表 */
const handleScopeChange = (val: boolean | number | string | undefined) => {
  if (val === 1) {
    state.form.goodsList = [];
  }
};

/** 提交保存 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (!valid) return;

    // 校验活动时间
    if (
      !state.form.datatimes ||
      state.form.datatimes.length < 2 ||
      !state.form.datatimes[0]
    ) {
      ElMessage.warning('请选择活动时间范围');
      return;
    }
    state.form.startTime = state.form.datatimes[0] as string;
    state.form.endTime = state.form.datatimes[1] as string;

    // 校验折扣值
    if (state.form.discountValue <= 0) {
      ElMessage.warning('折扣值必须大于0');
      return;
    }
    if (state.form.discountType === 1 && state.form.discountValue >= 1) {
      ElMessage.warning('打折值必须小于1（如 0.8 表示 8 折）');
      return;
    }

    // 指定商品时校验商品列表
    if (
      state.form.scope === 2 &&
      (!state.form.goodsList || state.form.goodsList.length === 0)
    ) {
      ElMessage.warning('请至少选择一个商品');
      return;
    }

    loading.value = true;
    if (state.form.id) {
      edit();
    } else {
      add();
    }
  });
};

const add = () => {
  addObj(state.form)
    .then(() => {
      loading.value = false;
      ElMessage.success('新增成功');
      goBack();
    })
    .catch(() => {
      loading.value = false;
    });
};

const edit = () => {
  editObj(state.form)
    .then(() => {
      loading.value = false;
      ElMessage.success('修改成功');
      goBack();
    })
    .catch(() => {
      loading.value = false;
    });
};

/** 加载详情 */
const getDetail = (id: string) => {
  loading.value = true;
  getById(id)
    .then((response) => {
      loading.value = false;
      state.form = {
        ...response,
        datatimes: [response.startTime, response.endTime],
        goodsList: response.goodsList || [],
      } as DiscountActivityForm;
    })
    .catch(() => {
      loading.value = false;
    });
};

if (editId) {
  state.form.id = editId;
  getDetail(editId);
}
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 顶部操作栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton :icon="ArrowLeft" @click="goBack">返回列表</ElButton>
        </div>
        <div>
          <ElButton @click="goBack">取 消</ElButton>
          <ElButton
            type="primary"
            :loading="loading"
            @click="submitForm(formRef)"
          >
            保 存
          </ElButton>
        </div>
      </div>

      <ElForm
        ref="formRef"
        :model="state.form"
        :rules="formRules"
        label-width="120px"
        label-position="left"
        v-loading="loading"
      >
        <!-- 活动基本信息 -->
        <ElCard shadow="never" style="margin-bottom: 16px">
          <template #header>
            <span>活动基本信息</span>
          </template>
          <ElFormItem label="活动名称" prop="activityName">
            <ElInput
              v-model="state.form.activityName"
              maxlength="128"
              show-word-limit
              placeholder="请输入活动名称"
            />
          </ElFormItem>
          <ElFormItem label="活动时间" prop="datatimes">
            <ElDatePicker
              v-model="state.form.datatimes"
              type="datetimerange"
              range-separator="至"
              start-placeholder="活动开始时间"
              end-placeholder="活动结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem label="活动描述" prop="description">
            <ElInput
              v-model="state.form.description"
              type="textarea"
              :rows="3"
              maxlength="512"
              show-word-limit
              placeholder="请输入活动描述（可选）"
            />
          </ElFormItem>
        </ElCard>

        <!-- 折扣配置 -->
        <ElCard shadow="never" style="margin-bottom: 16px">
          <template #header>
            <span>折扣配置</span>
          </template>
          <ElFormItem label="折扣类型" prop="discountType">
            <ElRadioGroup v-model="state.form.discountType">
              <ElRadio
                v-for="item in discountTypeOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="折扣值" prop="discountValue">
            <div style="display: flex; gap: 8px; align-items: center">
              <ElInputNumber
                v-model="state.form.discountValue"
                :min="discountValueMin"
                :precision="discountValuePrecision"
                :controls="false"
                style="width: 200px"
              />
              <span style="color: #909399">{{ discountHint }}</span>
            </div>
          </ElFormItem>
          <ElFormItem label="适用范围" prop="scope">
            <ElRadioGroup
              v-model="state.form.scope"
              @change="handleScopeChange"
            >
              <ElRadio
                v-for="item in scopeOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
          </ElFormItem>
        </ElCard>

        <!-- 指定商品列表（仅 scope=2 时显示） -->
        <ElCard
          v-if="state.form.scope === 2"
          shadow="never"
          style="margin-bottom: 16px"
        >
          <template #header>
            <div
              style="
                display: flex;
                justify-content: space-between;
                align-items: center;
              "
            >
              <span>参与折扣的商品</span>
              <ElButton type="primary" @click="selectSpu">+ 添加商品</ElButton>
            </div>
          </template>
          <ElTable :data="state.form.goodsList" border>
            <ElTableColumn label="商品信息" min-width="300">
              <template #default="scope">
                <div style="display: flex; gap: 8px; align-items: center">
                  <ElImage
                    v-if="scope.row.spuUrls && scope.row.spuUrls.length > 0"
                    style="width: 50px; height: 50px"
                    :src="scope.row.spuUrls[0]"
                    :preview-src-list="scope.row.spuUrls"
                    fit="cover"
                    :preview-teleported="true"
                  />
                  <span>{{ scope.row.spuName || scope.row.spuId }}</span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="spuId"
              label="商品SPU ID"
              align="center"
              width="200"
            />
            <ElTableColumn label="操作" width="100" align="center">
              <template #default="scope">
                <ElButton
                  link
                  type="danger"
                  :icon="Delete"
                  @click="removeGoods(scope.$index)"
                >
                  移除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <div
            v-if="!state.form.goodsList || state.form.goodsList.length === 0"
            class="empty-goods"
          >
            暂未选择商品，请点击"添加商品"
          </div>
        </ElCard>
      </ElForm>

      <!-- 商品选择器 -->
      <SelectGoods
        ref="selectGoods"
        :limit-num="100"
        @current-row="spuCurrent"
      />
    </div>
  </div>
</template>
<style lang="scss" scoped>
.empty-goods {
  padding: 24px;
  text-align: center;
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-blank);
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}
</style>
