<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type {
  SeckillActivityForm,
  SeckillSessionForm,
} from '#/api/promotion/seckill';

import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ArrowLeft, Delete, Plus } from '@element-plus/icons-vue';
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
  ElMessageBox,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getById as getSpuById } from '#/api/product/goods-spu';
import { addObj, editObj, getById } from '#/api/promotion/seckill';

import { createDefaultForm, createDefaultSessionItem, formRules } from './data';

const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
);

const route = useRoute();
const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const selectGoods = ref();
/** 当前正在选择商品的场次索引 */
const currentSessionIndex = ref(0);

/** 商品选择器返回的 SPU 项 */
interface SeckillSpuItem {
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
interface SeckillSpuDetail extends SeckillSpuItem {
  /** SKU 列表 */
  goodsSkus?: SeckillSkuItem[];
}

/** SKU 项 */
interface SeckillSkuItem {
  /** SKU ID */
  id: string;
  /** 销售价 */
  salesPrice?: number;
  /** 库存 */
  stock?: number;
}

const state = reactive<{
  form: SeckillActivityForm;
}>({
  form: createDefaultForm(),
});

/** 从 URL 读取 id（编辑模式） */
const editId = (route.query.id as null | string) ?? null;

/** 返回列表页 */
const goBack = () => {
  router.push('/promotion/seckill-activity');
};

/** 选择商品 */
const selectSpu = (sessionIndex: number) => {
  currentSessionIndex.value = sessionIndex;
  selectGoods.value.initPage();
};

/**
 * 商品选择回调
 * 商品选择器返回的是 SPU 列表，需要根据 SPU ID 查询其下的 SKU 列表，
 * 取默认 SKU（第一个）作为秒杀目标，避免将 SPU ID 错赋为 SKU ID。
 */
const spuCurrent = async (spuList: SeckillSpuItem[]) => {
  if (!spuList || spuList.length === 0) return;
  const session = state.form.sessions[currentSessionIndex.value];
  if (!session) return;
  loading.value = true;
  try {
    await Promise.all(
      spuList.map(async (item) => {
        const spuDetail = (await getSpuById(item.id)) as SeckillSpuDetail;
        const skus = spuDetail?.goodsSkus || [];
        if (skus.length === 0) {
          ElMessage.warning(`商品【${item.name}】无可用 SKU，已跳过`);
          return;
        }
        // 取默认 SKU（第一个）
        const defaultSku = skus[0]!;
        session.goodsList.push({
          spuId: item.id,
          skuId: defaultSku.id,
          spuName: item.name,
          spuUrls: item.spuUrls || [],
          seckillPrice: 0,
          seckillStock: 0,
          limitPerUser: 1,
        });
      }),
    );
  } catch {
    ElMessage.error('加载商品 SKU 信息失败');
  } finally {
    loading.value = false;
  }
};

/** 删除场次 */
const removeSession = (index: number) => {
  if (state.form.sessions.length <= 1) {
    ElMessage.warning('至少保留一个场次');
    return;
  }
  ElMessageBox.confirm('确认删除该场次及其所有商品?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    state.form.sessions.splice(index, 1);
  });
};

/** 添加场次 */
const addSession = () => {
  state.form.sessions.push(createDefaultSessionItem());
};

/** 删除场次下的商品 */
const removeGoods = (sessionIndex: number, goodsIndex: number) => {
  const session = state.form.sessions[sessionIndex];
  if (!session) return;
  session.goodsList.splice(goodsIndex, 1);
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

    // 校验场次
    for (let i = 0; i < state.form.sessions.length; i++) {
      const session = state.form.sessions[i];
      if (!session) {
        ElMessage.warning(`第 ${i + 1} 个场次数据异常`);
        return;
      }
      if (!session.sessionName) {
        ElMessage.warning(`请填写第 ${i + 1} 个场次的名称`);
        return;
      }
      if (
        !session.datatimes ||
        session.datatimes.length < 2 ||
        !session.datatimes[0]
      ) {
        ElMessage.warning(`请选择第 ${i + 1} 个场次的时间范围`);
        return;
      }
      session.startTime = session.datatimes[0] as string;
      session.endTime = session.datatimes[1] as string;

      if (!session.goodsList || session.goodsList.length === 0) {
        ElMessage.warning(`第 ${i + 1} 个场次至少添加一个商品`);
        return;
      }
      for (let j = 0; j < session.goodsList.length; j++) {
        const goods = session.goodsList[j];
        if (!goods) {
          ElMessage.warning(`第 ${i + 1} 个场次的第 ${j + 1} 个商品数据异常`);
          return;
        }
        if (!goods.spuId) {
          ElMessage.warning(`第 ${i + 1} 个场次的第 ${j + 1} 个商品未选择`);
          return;
        }
        if (goods.seckillPrice <= 0) {
          ElMessage.warning(
            `第 ${i + 1} 个场次的第 ${j + 1} 个商品秒杀价必须大于0`,
          );
          return;
        }
        if (goods.seckillStock <= 0) {
          ElMessage.warning(
            `第 ${i + 1} 个场次的第 ${j + 1} 个商品库存必须大于0`,
          );
          return;
        }
      }
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
        sessions: response.sessions?.map((session: SeckillSessionForm) => ({
          ...session,
          datatimes: [session.startTime, session.endTime],
        })) || [createDefaultSessionItem()],
      } as SeckillActivityForm;
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

        <!-- 场次管理 -->
        <ElCard shadow="never" style="margin-bottom: 16px">
          <template #header>
            <div
              style="
                display: flex;
                justify-content: space-between;
                align-items: center;
              "
            >
              <span>场次与商品管理</span>
              <ElButton type="primary" :icon="Plus" @click="addSession">
                添加场次
              </ElButton>
            </div>
          </template>

          <div
            v-for="(session, sIndex) in state.form.sessions"
            :key="sIndex"
            class="session-block"
          >
            <div class="session-header">
              <span class="session-title">场次 {{ sIndex + 1 }}</span>
              <ElButton
                type="danger"
                link
                :icon="Delete"
                @click="removeSession(sIndex)"
              >
                删除场次
              </ElButton>
            </div>

            <ElFormItem
              label="场次名称"
              :prop="`sessions.${sIndex}.sessionName`"
              :rules="{
                required: true,
                message: '请输入场次名称',
                trigger: 'change',
              }"
            >
              <ElInput
                v-model="session.sessionName"
                maxlength="64"
                placeholder="如：10:00场"
                style="width: 300px"
              />
            </ElFormItem>

            <ElFormItem
              label="场次时间"
              :prop="`sessions.${sIndex}.datatimes`"
              :rules="{
                required: true,
                message: '请选择场次时间范围',
                trigger: 'change',
              }"
            >
              <ElDatePicker
                v-model="session.datatimes"
                type="datetimerange"
                range-separator="至"
                start-placeholder="场次开始时间"
                end-placeholder="场次结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </ElFormItem>

            <!-- 场次商品列表 -->
            <div class="goods-section">
              <div class="goods-section-header">
                <span>秒杀商品</span>
                <ElButton type="success" link @click="selectSpu(sIndex)">
                  + 添加商品
                </ElButton>
              </div>
              <ElTable :data="session.goodsList" border size="small">
                <ElTableColumn label="商品信息" min-width="240">
                  <template #default="scope">
                    <div style="display: flex; gap: 8px; align-items: center">
                      <ElImage
                        v-if="scope.row.spuUrls && scope.row.spuUrls.length > 0"
                        style="width: 40px; height: 40px"
                        :src="scope.row.spuUrls[0]"
                        :preview-src-list="scope.row.spuUrls"
                        fit="cover"
                        :preview-teleported="true"
                      />
                      <span>{{ scope.row.spuName || scope.row.spuId }}</span>
                    </div>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="秒杀价(元)" width="140" align="center">
                  <template #default="scope">
                    <ElInputNumber
                      v-model="scope.row.seckillPrice"
                      :min="0.01"
                      :precision="2"
                      :controls="false"
                      style="width: 120px"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="秒杀库存" width="140" align="center">
                  <template #default="scope">
                    <ElInputNumber
                      v-model="scope.row.seckillStock"
                      :min="1"
                      :precision="0"
                      :controls="false"
                      style="width: 120px"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="每人限购" width="140" align="center">
                  <template #default="scope">
                    <ElInputNumber
                      v-model="scope.row.limitPerUser"
                      :min="1"
                      :precision="0"
                      :controls="false"
                      style="width: 120px"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" width="80" align="center">
                  <template #default="gScope">
                    <ElButton
                      link
                      type="danger"
                      :icon="Delete"
                      @click="removeGoods(sIndex, gScope.$index)"
                    />
                  </template>
                </ElTableColumn>
              </ElTable>
              <div
                v-if="!session.goodsList || session.goodsList.length === 0"
                class="empty-goods"
              >
                暂无商品，请点击"添加商品"
              </div>
            </div>
          </div>
        </ElCard>
      </ElForm>

      <!-- 商品选择器 -->
      <SelectGoods
        ref="selectGoods"
        :limit-num="50"
        @current-row="spuCurrent"
      />
    </div>
  </div>
</template>
<style lang="scss" scoped>
.session-block {
  padding: 16px;
  margin-bottom: 16px;
  background-color: var(--el-fill-color-light);
  border-radius: 4px;

  .session-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--el-border-color-lighter);

    .session-title {
      font-size: 15px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }
  }

  .goods-section {
    margin-top: 16px;

    .goods-section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      font-weight: 600;
    }

    .empty-goods {
      padding: 24px;
      text-align: center;
      color: var(--el-text-color-secondary);
      background-color: var(--el-fill-color-blank);
      border: 1px dashed var(--el-border-color);
      border-radius: 4px;
    }
  }
}
</style>
