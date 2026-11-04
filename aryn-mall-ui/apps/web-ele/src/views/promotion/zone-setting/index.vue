<script lang="ts" setup>
import { onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { getZoneSetting, saveZoneSetting } from '#/api/promotion/zone-setting';

interface ZoneForm {
  enabled: boolean;
  defaultDiscountRate?: number;
  costFloorMultiplier?: number;
  sortMode: string;
}

const form = reactive<Record<'clearance' | 'member', ZoneForm>>({
  member: emptyForm(),
  clearance: emptyForm(),
});
const loading = reactive<Record<'clearance' | 'member', boolean>>({
  clearance: false,
  member: false,
});
const saving = reactive<Record<'clearance' | 'member', boolean>>({
  clearance: false,
  member: false,
});

const router = useRouter();

function emptyForm(): ZoneForm {
  return {
    enabled: true,
    defaultDiscountRate: undefined,
    costFloorMultiplier: undefined,
    sortMode: 'default',
  };
}

function normalize(zone: 'clearance' | 'member', data: any) {
  form[zone].enabled = data?.enabled !== '0';
  form[zone].defaultDiscountRate =
    data?.defaultDiscountRate === null ||
    data?.defaultDiscountRate === undefined
      ? undefined
      : Number(data.defaultDiscountRate);
  form[zone].costFloorMultiplier =
    data?.costFloorMultiplier === null ||
    data?.costFloorMultiplier === undefined
      ? undefined
      : Number(data.costFloorMultiplier);
  form[zone].sortMode = data?.sortMode || 'default';
}

async function load(zone: 'clearance' | 'member') {
  loading[zone] = true;
  try {
    normalize(zone, await getZoneSetting(zone));
  } finally {
    loading[zone] = false;
  }
}

async function save(zone: 'clearance' | 'member') {
  saving[zone] = true;
  try {
    await saveZoneSetting(zone, {
      enabled: form[zone].enabled ? '1' : '0',
      defaultDiscountRate: form[zone].defaultDiscountRate ?? null,
      costFloorMultiplier: form[zone].costFloorMultiplier ?? null,
      sortMode: form[zone].sortMode,
    });
    ElMessage.success('专区设置已保存，价格已同步');
    await load(zone);
  } finally {
    saving[zone] = false;
  }
}

/**
 * 专区在 C 端的展示位置、Tab 标题与公告由装修承担：
 * 本页只配置「规则与价格」，跳转到页面设计列表，避免运营在两个页面之间找不到入口。
 */
function goPageDesign() {
  router.push('/promotion/page-design');
}

onMounted(() => {
  load('member');
  load('clearance');
});
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElAlert
        type="info"
        :closable="false"
        show-icon
        class="mb10"
        title="本页只配置专区的规则与价格（开关、默认折扣率、成本下限、出清排序）。专区的展示位置、Tab 标题与公告请在「页面设计 → 多 Tab 商品流」中配置。"
      >
        <ElButton link type="primary" @click="goPageDesign">
          去页面设计配置专区展示 →
        </ElButton>
      </ElAlert>
      <ElCard class="mb10" shadow="never">
        <template #header>
          <div class="zone-header">
            <span>会员专区</span>
            <ElButton
              type="primary"
              v-access:code="'promotion:shipactivity:save'"
              :loading="saving.member"
              @click="save('member')"
            >
              保存
            </ElButton>
          </div>
        </template>
        <ElForm
          v-loading="loading.member"
          :model="form.member"
          label-width="120px"
        >
          <ElFormItem label="专区开关">
            <div class="zone-field">
              <ElSwitch v-model="form.member.enabled" />
              <span class="zone-field-tip">
                关闭后 C
                端不再展示会员专区货盘、也不再计算会员价；商品标签保留，重新开启后自动恢复。
              </span>
            </div>
          </ElFormItem>
          <ElFormItem label="默认折扣率">
            <div class="zone-field">
              <ElInputNumber
                v-model="form.member.defaultDiscountRate"
                :min="0.0001"
                :max="0.9999"
                :step="0.01"
                :precision="4"
                controls-position="right"
                placeholder="空=仅用等级折扣率，如 0.9 表示 9 折"
                style="width: 320px"
              />
              <span class="zone-field-tip">
                商品未单独配置会员折扣率时使用；会员价仍与等级折扣率取优，不叠加。
              </span>
            </div>
          </ElFormItem>
          <ElFormItem label="成本兜底倍率">
            <div class="zone-field">
              <ElInputNumber
                v-model="form.member.costFloorMultiplier"
                :min="0.01"
                :max="10"
                :step="0.05"
                :precision="2"
                controls-position="right"
                placeholder="空=不启用；如 1.05 = 成本上浮 5%"
                style="width: 320px"
              />
              <span class="zone-field-tip">
                会员价下限 = 成本价 × 倍率；商品已配置专属下限时以商品为准；空 =
                不启用。
              </span>
            </div>
          </ElFormItem>
        </ElForm>
      </ElCard>

      <ElCard shadow="never">
        <template #header>
          <div class="zone-header">
            <span>低价捡漏专区</span>
            <ElButton
              type="primary"
              v-access:code="'promotion:shipactivity:save'"
              :loading="saving.clearance"
              @click="save('clearance')"
            >
              保存
            </ElButton>
          </div>
        </template>
        <ElForm
          v-loading="loading.clearance"
          :model="form.clearance"
          label-width="120px"
        >
          <ElFormItem label="专区开关">
            <div class="zone-field">
              <ElSwitch v-model="form.clearance.enabled" />
              <span class="zone-field-tip">
                关闭后 C
                端不再展示出清货盘、也不再计算出清价；商品标签保留，重新开启后自动恢复。
              </span>
            </div>
          </ElFormItem>
          <ElFormItem label="默认折扣率">
            <div class="zone-field">
              <ElInputNumber
                v-model="form.clearance.defaultDiscountRate"
                :min="0.0001"
                :max="0.9999"
                :step="0.01"
                :precision="4"
                controls-position="right"
                placeholder="空=未配置出清规则的商品不参与出清，如 0.8 表示 8 折"
                style="width: 360px"
              />
              <span class="zone-field-tip">
                商品未配置出清价/折扣率时使用（按原价折算）；空 =
                未配置规则的商品不参与出清。
              </span>
            </div>
          </ElFormItem>
          <ElFormItem label="专区排序">
            <div class="zone-field">
              <ElSelect v-model="form.clearance.sortMode" style="width: 220px">
                <ElOption label="出清价从低到高" value="default" />
                <ElOption label="折扣从高到低" value="discount_desc" />
              </ElSelect>
              <span class="zone-field-tip">
                作用于 C 端所有出清列表（包括装修 Tab）；装修 Tab
                里的排序选项不生效。
              </span>
            </div>
          </ElFormItem>
        </ElForm>
      </ElCard>
    </div>
  </div>
</template>

<style scoped>
.zone-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.zone-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.zone-field-tip {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
