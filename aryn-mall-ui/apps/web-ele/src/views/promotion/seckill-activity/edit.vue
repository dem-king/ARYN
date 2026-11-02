<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type {
  SeckillActivityForm,
  SeckillActivityPayload,
  SeckillSessionForm,
  SeckillSessionTemplateForm,
} from '#/api/promotion/seckill';

import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ArrowLeft, Delete, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTimePicker,
} from 'element-plus';

import { getById as getSpuById } from '#/api/product/goods-spu';
import { addObj, editObj, getById } from '#/api/promotion/seckill';
import PageSection from '#/components/page/page-section.vue';

import {
  createDefaultForm,
  createDefaultSessionItem,
  createDefaultSessionTemplateItem,
  formRules,
  scheduleTypeOptions,
  weekdayOptions,
} from './data';

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
/** 商品选择器作用于哪种场次：一次性场次 / 每日排期模板 */
const currentSessionSource = ref<'sessions' | 'templates'>('sessions');

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
const selectSpu = (
  sessionIndex: number,
  source: 'sessions' | 'templates' = 'sessions',
) => {
  currentSessionIndex.value = sessionIndex;
  currentSessionSource.value = source;
  selectGoods.value.initPage();
};

/**
 * 商品选择回调
 * 商品选择器返回的是 SPU 列表，需要根据 SPU ID 查询其下的 SKU 列表，
 * 取默认 SKU（第一个）作为秒杀目标，避免将 SPU ID 错赋为 SKU ID。
 */
const spuCurrent = async (spuList: SeckillSpuItem[]) => {
  if (!spuList || spuList.length === 0) return;
  const session =
    currentSessionSource.value === 'templates'
      ? state.form.sessionTemplates[currentSessionIndex.value]
      : state.form.sessions[currentSessionIndex.value];
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

/** 删除每日排期场次 */
const removeTemplate = (index: number) => {
  if (state.form.sessionTemplates.length <= 1) {
    ElMessage.warning('至少保留一个排期场次');
    return;
  }
  ElMessageBox.confirm('确认删除该排期场次及其所有商品?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    state.form.sessionTemplates.splice(index, 1);
  });
};

/** 添加每日排期场次 */
const addTemplate = () => {
  state.form.sessionTemplates.push(createDefaultSessionTemplateItem());
};

/** 删除场次下的商品 */
const removeGoods = (sessionIndex: number, goodsIndex: number) => {
  const session = state.form.sessions[sessionIndex];
  if (!session) return;
  session.goodsList.splice(goodsIndex, 1);
};

/** 删除排期场次下的商品 */
const removeTemplateGoods = (templateIndex: number, goodsIndex: number) => {
  const template = state.form.sessionTemplates[templateIndex];
  if (!template) return;
  template.goodsList.splice(goodsIndex, 1);
};

/** 校验商品列表，返回是否通过 */
const validateGoodsList = (
  goodsList: SeckillSessionForm['goodsList'],
  label: string,
) => {
  if (!goodsList || goodsList.length === 0) {
    ElMessage.warning(`${label}至少添加一个商品`);
    return false;
  }
  for (const [index, goods] of goodsList.entries()) {
    const position = index + 1;
    if (!goods) {
      ElMessage.warning(`${label}的第 ${position} 个商品数据异常`);
      return false;
    }
    if (!goods.spuId) {
      ElMessage.warning(`${label}的第 ${position} 个商品未选择`);
      return false;
    }
    if (goods.seckillPrice <= 0) {
      ElMessage.warning(`${label}的第 ${position} 个商品秒杀价必须大于0`);
      return false;
    }
    if (goods.seckillStock <= 0) {
      ElMessage.warning(`${label}的第 ${position} 个商品库存必须大于0`);
      return false;
    }
  }
  return true;
};

/** 校验每日排期模板并返回提交体 */
const buildTemplatePayload = () => {
  if (state.form.sessionTemplates.length === 0) {
    ElMessage.warning('每日重复至少需要一个场次');
    return null;
  }
  const payload: SeckillActivityPayload['sessionTemplates'] = [];
  for (const [index, template] of state.form.sessionTemplates.entries()) {
    const position = index + 1;
    if (!template) {
      ElMessage.warning(`第 ${position} 个排期场次数据异常`);
      return null;
    }
    if (!template.sessionName) {
      ElMessage.warning(`请填写第 ${position} 个排期场次的名称`);
      return null;
    }
    if (
      !template.datatimes ||
      template.datatimes.length < 2 ||
      !template.datatimes[0] ||
      !template.datatimes[1]
    ) {
      ElMessage.warning(`请选择第 ${position} 个排期场次的每日时段`);
      return null;
    }
    const [startTime, endTime] = template.datatimes as [string, string];
    if (startTime >= endTime) {
      ElMessage.warning(`第 ${position} 个排期场次的结束时间必须晚于开始时间`);
      return null;
    }
    if (!template.weekdays || template.weekdays.length === 0) {
      ElMessage.warning(`请选择第 ${position} 个排期场次的生效星期`);
      return null;
    }
    if (!validateGoodsList(template.goodsList, `第 ${position} 个排期场次`)) {
      return null;
    }
    template.startTime = startTime;
    template.endTime = endTime;
    payload.push({
      sessionName: template.sessionName,
      startTime,
      endTime,
      weekdays: [...template.weekdays].sort((a, b) => a - b).join(','),
      goodsList: template.goodsList,
    });
  }
  return payload;
};

/** 提交保存 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (!valid) return;

    // 校验活动时间：每日重复只按日期生效，统一收敛到 00:00:00 ~ 23:59:59
    if (
      !state.form.datatimes ||
      state.form.datatimes.length < 2 ||
      !state.form.datatimes[0]
    ) {
      ElMessage.warning('请选择活动时间范围');
      return;
    }
    const daily = state.form.scheduleType === 1;
    if (daily) {
      const [startDate, endDate] = state.form.datatimes as [string, string];
      state.form.startTime = `${startDate} 00:00:00`;
      state.form.endTime = `${endDate} 23:59:59`;
    } else {
      state.form.startTime = state.form.datatimes[0] as string;
      state.form.endTime = state.form.datatimes[1] as string;
    }

    if (daily) {
      const templates = buildTemplatePayload();
      if (!templates) return;
      submitPayload({
        id: state.form.id,
        activityName: state.form.activityName,
        description: state.form.description,
        scheduleType: 1,
        startTime: state.form.startTime,
        endTime: state.form.endTime,
        sessions: [],
        sessionTemplates: templates,
      });
      return;
    }

    // 校验场次
    for (const [index, session] of state.form.sessions.entries()) {
      const position = index + 1;
      if (!session) {
        ElMessage.warning(`第 ${position} 个场次数据异常`);
        return;
      }
      if (!session.sessionName) {
        ElMessage.warning(`请填写第 ${position} 个场次的名称`);
        return;
      }
      if (
        !session.datatimes ||
        session.datatimes.length < 2 ||
        !session.datatimes[0]
      ) {
        ElMessage.warning(`请选择第 ${position} 个场次的时间范围`);
        return;
      }
      session.startTime = session.datatimes[0] as string;
      session.endTime = session.datatimes[1] as string;
      if (!validateGoodsList(session.goodsList, `第 ${position} 个场次`)) {
        return;
      }
    }
    submitPayload({
      id: state.form.id,
      activityName: state.form.activityName,
      description: state.form.description,
      scheduleType: 0,
      startTime: state.form.startTime,
      endTime: state.form.endTime,
      sessions: state.form.sessions,
      sessionTemplates: [],
    });
  });
};

/** 按排期方式提交 */
const submitPayload = (payload: SeckillActivityPayload) => {
  loading.value = true;
  const request = state.form.id ? editObj(payload) : addObj(payload);
  request
    .then(() => {
      loading.value = false;
      ElMessage.success(state.form.id ? '修改成功' : '新增成功');
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
      const daily = (response.scheduleType ?? 0) === 1;
      state.form = {
        ...response,
        scheduleType: response.scheduleType ?? 0,
        datatimes: daily
          ? [
              (response.startTime ?? '').slice(0, 10),
              (response.endTime ?? '').slice(0, 10),
            ]
          : [response.startTime, response.endTime],
        sessions: response.sessions?.length
          ? response.sessions.map((session: SeckillSessionForm) => ({
              ...session,
              datatimes: [session.startTime, session.endTime],
            }))
          : [createDefaultSessionItem()],
        sessionTemplates: response.sessionTemplates?.length
          ? response.sessionTemplates.map(
              (template: SeckillSessionTemplateForm) => ({
                ...template,
                datatimes: [template.startTime, template.endTime],
                weekdays: parseWeekdays(template.weekdays as unknown),
              }),
            )
          : [createDefaultSessionTemplateItem()],
      } as SeckillActivityForm;
    })
    .catch(() => {
      loading.value = false;
    });
};

/** 后端 weekdays 为 "1,2,3" 字符串，编辑时转成数组 */
const parseWeekdays = (weekdays: unknown): number[] => {
  if (Array.isArray(weekdays)) {
    return weekdays.map(Number).filter((day) => day >= 1 && day <= 7);
  }
  if (typeof weekdays === 'string' && weekdays) {
    return weekdays
      .split(',')
      .map((day) => Number(day.trim()))
      .filter((day) => day >= 1 && day <= 7);
  }
  return [1, 2, 3, 4, 5, 6, 7];
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
        <div class="hx-form-footer">
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
        <PageSection title="活动基本信息">
          <ElFormItem label="活动名称" prop="activityName">
            <ElInput
              v-model="state.form.activityName"
              maxlength="128"
              show-word-limit
              placeholder="请输入活动名称"
            />
          </ElFormItem>
          <ElFormItem label="排期方式" prop="scheduleType">
            <ElRadioGroup v-model="state.form.scheduleType">
              <ElRadio
                v-for="item in scheduleTypeOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
            <div class="form-tip">
              一次性场次按绝对时间生效；每日重复按“每天时段 +
              生效星期”自动生成场次，适合每天固定几点的日播秒杀。
            </div>
          </ElFormItem>
          <ElFormItem
            :label="state.form.scheduleType === 1 ? '活动日期范围' : '活动时间'"
            prop="datatimes"
          >
            <ElDatePicker
              v-if="state.form.scheduleType === 1"
              v-model="state.form.datatimes"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
            <ElDatePicker
              v-else
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
        </PageSection>

        <!-- 场次管理（一次性模式） -->
        <PageSection
          v-if="state.form.scheduleType !== 1"
          title="场次与商品管理"
        >
          <template #extra>
            <ElButton type="primary" :icon="Plus" @click="addSession">
              添加场次
            </ElButton>
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
              <ElTable
                :data="session.goodsList"
                class="hx-data-table"
                border
                size="small"
              >
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
        </PageSection>

        <PageSection v-else title="每日排期与商品管理">
          <template #extra>
            <ElButton type="primary" :icon="Plus" @click="addTemplate">
              添加场次
            </ElButton>
          </template>

          <div class="schedule-tip">
            每日按“每天时段 +
            生效星期”自动生成场次；每场库存即当日配额的一份，场次结束未售库存自动顺延到当日下一场。活动时间范围决定排期从哪天生效、到哪天结束。
          </div>

          <div
            v-for="(template, tIndex) in state.form.sessionTemplates"
            :key="tIndex"
            class="session-block"
          >
            <div class="session-header">
              <span class="session-title">每日场次 {{ tIndex + 1 }}</span>
              <ElButton
                type="danger"
                link
                :icon="Delete"
                @click="removeTemplate(tIndex)"
              >
                删除场次
              </ElButton>
            </div>

            <ElFormItem label="场次名称">
              <ElInput
                v-model="template.sessionName"
                maxlength="64"
                placeholder="如：10:00场"
                style="width: 300px"
              />
            </ElFormItem>

            <ElFormItem label="每日时段">
              <ElTimePicker
                v-model="template.datatimes"
                is-range
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                format="HH:mm"
                value-format="HH:mm:ss"
                style="width: 300px"
              />
            </ElFormItem>

            <ElFormItem label="生效星期">
              <ElCheckboxGroup v-model="template.weekdays">
                <ElCheckbox
                  v-for="day in weekdayOptions"
                  :key="day.value"
                  :value="day.value"
                >
                  {{ day.label }}
                </ElCheckbox>
              </ElCheckboxGroup>
            </ElFormItem>

            <div class="goods-section">
              <div class="goods-section-header">
                <span>秒杀商品（每场计划库存）</span>
                <ElButton
                  type="success"
                  link
                  @click="selectSpu(tIndex, 'templates')"
                >
                  + 添加商品
                </ElButton>
              </div>
              <ElTable
                :data="template.goodsList"
                class="hx-data-table"
                border
                size="small"
              >
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
                <ElTableColumn label="每场库存" width="140" align="center">
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
                      @click="removeTemplateGoods(tIndex, gScope.$index)"
                    />
                  </template>
                </ElTableColumn>
              </ElTable>
              <div
                v-if="!template.goodsList || template.goodsList.length === 0"
                class="empty-goods"
              >
                暂无商品，请点击"添加商品"
              </div>
            </div>
          </div>
        </PageSection>
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
.form-tip,
.schedule-tip {
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.form-tip {
  margin-top: 6px;
  margin-bottom: 0;
}

.session-block {
  padding: 16px;
  margin-bottom: 16px;
  background-color: var(--el-fill-color-light);
  border-radius: 4px;

  .session-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 8px;
    margin-bottom: 16px;
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
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;
      font-weight: 600;
    }

    .empty-goods {
      padding: 24px;
      color: var(--el-text-color-secondary);
      text-align: center;
      background-color: var(--el-fill-color-blank);
      border: 1px dashed var(--el-border-color);
      border-radius: 4px;
    }
  }
}
</style>
