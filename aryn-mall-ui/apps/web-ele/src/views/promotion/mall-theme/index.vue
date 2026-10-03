<script setup lang="ts">
import type {
  PageDesignTheme,
  PageDesignThemePayload,
} from '#/api/promotion/page-design';

import { computed, onMounted, reactive, ref } from 'vue';

import { Plus, Refresh, Star } from '@element-plus/icons-vue';
import {
  ElButton,
  ElColorPicker,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  createTheme,
  deleteTheme,
  getThemes,
  setDefaultTheme,
  updateTheme,
} from '#/api/promotion/page-design';

import {
  buildThemePayloadFromPrimary,
  derivePageBackgroundColor,
  deriveSecondaryColor,
  THEME_PRESETS,
} from '../page-designer/schema/theme-presets';

/**
 * 商城主题管理（租户级全局配置）。
 *
 * 与装修器里「本页主题」的分工：
 *   · 本页 = 主题库 CRUD + 设为商城默认（对整个小程序换肤）+ 效果预览；
 *   · 装修器只保留页级选择（跟随商城默认 / 指定主题），不再承载全局动作。
 *
 * 抽取自原 page-designer/components/theme-dialog.vue：逻辑一致，
 * 但作用域从「单页编辑器的弹窗」提升为独立菜单，「设为默认」的全局语义
 * 因此不再被单页容器误导。
 */

const loading = ref(false);
const themes = ref<PageDesignTheme[]>([]);
/** 预览用的主题：默认展示选中的行，未选中时展示商城默认（再退首个） */
const previewId = ref('');

const editing = ref<null | PageDesignTheme>(null);
const formVisible = ref(false);
const saving = ref(false);
const settingDefaultId = ref('');
const form = reactive<PageDesignThemePayload>({
  navigationColor: '#ffffff',
  navigationTextColor: '#222222',
  pageBackgroundColor: '#f5f5f5',
  primaryColor: '#ff5500',
  radius: 8,
  themeName: '',
});

const defaultTheme = computed(() =>
  themes.value.find((theme) => theme.mallDefaultFlag === '1'),
);

/** 预览主题：优先选中行，其次商城默认；都没有时用预设主色兜底 */
const previewTheme = computed<PageDesignThemePayload & { id?: string }>(() => {
  const picked = themes.value.find((theme) => theme.id === previewId.value);
  if (picked) return picked;
  if (defaultTheme.value) return defaultTheme.value;
  return buildThemePayloadFromPrimary(
    THEME_PRESETS[0]!.primaryColor,
    THEME_PRESETS[0]!.name,
  );
});

/** 预览用的衍生色（辅色/底色），与后端与 C 端口径一致 */
const previewColors = computed(() => {
  const primary = previewTheme.value.primaryColor || '#FF2237';
  return {
    background: previewTheme.value.pageBackgroundColor || '#F5F5F5',
    navigation: previewTheme.value.navigationColor || '#FFFFFF',
    navigationText: previewTheme.value.navigationTextColor || '#222222',
    primary,
    radius: previewTheme.value.radius ?? 8,
    secondary: deriveSecondaryColor(primary),
  };
});

/** 当前预览的主题名（无来源时标注「预设示例」） */
const previewLabel = computed(() => {
  const picked = themes.value.find((theme) => theme.id === previewId.value);
  if (picked) return picked.themeName;
  if (defaultTheme.value) return `${defaultTheme.value.themeName}（商城默认）`;
  return '预设示例';
});

async function loadThemes() {
  loading.value = true;
  try {
    themes.value = await getThemes();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = null;
  Object.assign(form, buildThemePayloadFromPrimary('#ff5500', ''));
  formVisible.value = true;
}

function openEdit(theme: PageDesignTheme) {
  editing.value = theme;
  form.themeName = theme.themeName;
  form.primaryColor = theme.primaryColor;
  form.pageBackgroundColor = theme.pageBackgroundColor;
  form.navigationColor = theme.navigationColor;
  form.navigationTextColor = theme.navigationTextColor;
  form.radius = theme.radius;
  formVisible.value = true;
}

/** 预设：整表单覆盖（名称也预填，保存即得一套可用主题） */
function applyPreset(presetIndex: number) {
  const preset = THEME_PRESETS[presetIndex];
  if (!preset) return;
  if (formVisible.value && editing.value) {
    ElMessage.info('正在编辑主题，请先保存或关闭表单后再应用预设');
    return;
  }
  Object.assign(
    form,
    buildThemePayloadFromPrimary(preset.primaryColor, preset.name),
  );
  editing.value = null;
  formVisible.value = true;
}

/** 改主色时同步重算底色，避免商家手动对齐两个色值 */
function onPrimaryChange(value: null | string) {
  if (value) {
    form.pageBackgroundColor = derivePageBackgroundColor(value);
  }
}

async function submitForm() {
  if (!form.themeName.trim()) {
    ElMessage.warning('请填写主题名称');
    return;
  }
  if (!form.primaryColor) {
    ElMessage.warning('请选择品牌主色');
    return;
  }
  saving.value = true;
  try {
    if (editing.value) {
      await updateTheme(editing.value.id, { ...form });
      ElMessage.success('主题已更新');
    } else {
      await createTheme({ ...form });
      ElMessage.success('主题已创建');
    }
    formVisible.value = false;
    await loadThemes();
  } finally {
    saving.value = false;
  }
}

async function remove(theme: PageDesignTheme) {
  await ElMessageBox.confirm(`确认删除主题“${theme.themeName}”？`, '删除主题', {
    cancelButtonText: '取消',
    confirmButtonText: '删除',
    type: 'warning',
  });
  await deleteTheme(theme.id);
  if (previewId.value === theme.id) previewId.value = '';
  ElMessage.success('已删除');
  await loadThemes();
}

/**
 * 设为商城默认：C 端全商城换肤（免登接口下发，冷启动生效）。
 * 后端保证租户内唯一默认，成功后刷新列表展示标记。
 */
async function setAsDefault(theme: PageDesignTheme) {
  if (settingDefaultId.value) return;
  await ElMessageBox.confirm(
    `将“${theme.themeName}”设为商城默认主题后，整个小程序（按钮、价格、标签、tabBar）都会换成该配色，用户下次打开生效。确认设置？`,
    '设为商城默认',
    {
      cancelButtonText: '取消',
      confirmButtonText: '确认设置',
      type: 'warning',
    },
  );
  settingDefaultId.value = theme.id;
  try {
    await setDefaultTheme(theme.id);
    ElMessage.success(`已把“${theme.themeName}”设为商城默认主题`);
    await loadThemes();
  } finally {
    settingDefaultId.value = '';
  }
}

function selectPreview(theme: PageDesignTheme) {
  previewId.value = theme.id;
}

onMounted(loadThemes);
</script>

<template>
  <div class="hx-layout-container mall-theme">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="theme-body">
        <!-- 左：主题库 -->
        <section class="theme-panel">
          <header class="panel-head">
            <div>
              <h3 class="panel-title">主题库</h3>
              <p class="panel-tip">
                设为「商城默认」的主题将对整个小程序生效（用户下次打开更新）；
                装修页面内可单独指定主题覆盖。
              </p>
            </div>
            <div class="panel-actions">
              <ElButton :icon="Refresh" plain @click="loadThemes">
                刷新
              </ElButton>
              <ElButton
                v-access:code="'promotion:pagedesign:theme'"
                :icon="Plus"
                type="primary"
                @click="openCreate"
              >
                新建主题
              </ElButton>
            </div>
          </header>

          <div class="preset-bar">
            <span class="preset-label">预设色板</span>
            <ElTooltip
              v-for="(preset, index) in THEME_PRESETS"
              :key="preset.primaryColor"
              :content="`${preset.name} · 主色 ${preset.primaryColor}`"
              placement="top"
            >
              <button
                :style="{ background: preset.primaryColor }"
                class="preset-chip"
                type="button"
                @click="applyPreset(index)"
              >
                <span
                  :style="{ background: preset.pageBackgroundColor }"
                  class="preset-chip-bg"
                ></span>
              </button>
            </ElTooltip>
            <span class="preset-hint">点色块按预设新建主题</span>
          </div>

          <div v-loading="loading" class="theme-list">
            <ElEmpty v-if="themes.length === 0" description="暂无主题" />
            <article
              v-for="theme in themes"
              v-else
              :key="theme.id"
              :class="{ 'is-active': previewId === theme.id }"
              class="theme-card"
              @click="selectPreview(theme)"
            >
              <div class="card-swatches">
                <span
                  :style="{ background: theme.primaryColor }"
                  class="swatch swatch-primary"
                ></span>
                <span
                  :style="{
                    background: deriveSecondaryColor(theme.primaryColor || ''),
                  }"
                  class="swatch"
                ></span>
                <span
                  :style="{ background: theme.pageBackgroundColor }"
                  class="swatch"
                ></span>
                <span
                  :style="{ background: theme.navigationColor }"
                  class="swatch swatch-nav"
                ></span>
              </div>

              <div class="card-main">
                <div class="card-title">
                  <strong>{{ theme.themeName }}</strong>
                  <ElTag
                    v-if="theme.systemFlag === '1'"
                    effect="plain"
                    size="small"
                  >
                    系统
                  </ElTag>
                  <ElTag
                    v-if="theme.mallDefaultFlag === '1'"
                    effect="dark"
                    size="small"
                    type="warning"
                  >
                    商城默认
                  </ElTag>
                </div>
                <div class="card-meta">
                  主色 {{ theme.primaryColor || '—' }} · 圆角
                  {{ theme.radius ?? 0 }}px
                </div>
              </div>

              <div class="card-actions" @click.stop>
                <ElTooltip
                  :content="
                    theme.mallDefaultFlag === '1'
                      ? '当前商城默认主题'
                      : '设为商城默认主题，整个小程序换肤（C 端冷启动生效）'
                  "
                  placement="top"
                >
                  <ElButton
                    v-access:code="'promotion:pagedesign:theme'"
                    :disabled="
                      theme.mallDefaultFlag === '1' || settingDefaultId !== ''
                    "
                    :icon="Star"
                    :loading="settingDefaultId === theme.id"
                    link
                    type="warning"
                    @click="setAsDefault(theme)"
                  >
                    {{
                      theme.mallDefaultFlag === '1' ? '商城默认' : '设为默认'
                    }}
                  </ElButton>
                </ElTooltip>
                <template v-if="theme.systemFlag !== '1'">
                  <ElButton
                    v-access:code="'promotion:pagedesign:theme'"
                    link
                    @click="openEdit(theme)"
                  >
                    编辑
                  </ElButton>
                  <ElButton
                    v-access:code="'promotion:pagedesign:theme'"
                    link
                    type="danger"
                    @click="remove(theme)"
                  >
                    删除
                  </ElButton>
                </template>
              </div>
            </article>
          </div>
        </section>

        <!-- 右：效果预览（口径与 C 端一致：主色 / 辅色 / 底色 / 圆角） -->
        <aside class="preview-panel">
          <header class="panel-head">
            <h3 class="panel-title">效果预览</h3>
            <p class="panel-tip">{{ previewLabel }}</p>
          </header>
          <div
            class="preview-phone"
            :style="{ background: previewColors.background }"
          >
            <div
              class="preview-nav"
              :style="{
                background: previewColors.navigation,
                color: previewColors.navigationText,
              }"
            >
              悦航购商城
            </div>
            <div class="preview-body">
              <div
                class="preview-card"
                :style="{ borderRadius: `${previewColors.radius}px` }"
              >
                <div
                  class="preview-tag"
                  :style="{ background: previewColors.primary }"
                >
                  限时秒杀
                </div>
                <div class="preview-goods">
                  <span class="preview-thumb"></span>
                  <div class="preview-info">
                    <span class="preview-name">示例商品名称</span>
                    <span
                      class="preview-price"
                      :style="{ color: previewColors.primary }"
                    >
                      ¥ 19.90
                    </span>
                  </div>
                </div>
                <div
                  class="preview-btn"
                  :style="{
                    background: previewColors.primary,
                    borderRadius: `${previewColors.radius}px`,
                  }"
                >
                  立即购买
                </div>
              </div>
              <div class="preview-tabbar">
                <span
                  class="tabbar-item is-active"
                  :style="{ color: previewColors.primary }"
                >
                  <i
                    :style="{ background: previewColors.primary }"
                    class="tabbar-dot"
                  ></i>
                  首页
                </span>
                <span class="tabbar-item">
                  <i class="tabbar-dot"></i>
                  分类
                </span>
                <span class="tabbar-item">
                  <i class="tabbar-dot"></i>
                  我的
                </span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>

    <ElDialog
      v-model="formVisible"
      :title="editing ? '编辑主题' : '新建主题'"
      width="440px"
      append-to-body
    >
      <ElForm :model="form" label-position="top">
        <ElFormItem label="主题名称" required>
          <ElInput v-model="form.themeName" maxlength="30" />
        </ElFormItem>
        <ElFormItem label="品牌主色（改主色自动重算页面底色）">
          <ElColorPicker
            v-model="form.primaryColor"
            @change="onPrimaryChange"
          />
        </ElFormItem>
        <ElFormItem label="页面背景色">
          <ElColorPicker v-model="form.pageBackgroundColor" show-alpha />
        </ElFormItem>
        <ElFormItem label="导航栏背景 / 文字">
          <div class="color-row">
            <ElColorPicker v-model="form.navigationColor" />
            <ElColorPicker v-model="form.navigationTextColor" />
          </div>
        </ElFormItem>
        <ElFormItem label="全局圆角（px）">
          <ElInputNumber
            v-model="form.radius"
            :max="28"
            :min="0"
            controls-position="right"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="formVisible = false">取消</ElButton>
        <ElButton :loading="saving" type="primary" @click="submitForm">
          保存
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.mall-theme {
  height: 100%;
}

.theme-body {
  display: grid;
  grid-template-columns: minmax(520px, 1fr) 360px;
  gap: 16px;
  height: calc(100vh - 130px);
}

.theme-panel,
.preview-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.panel-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.panel-tip {
  width: 100%;
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.preset-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.preset-label,
.preset-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.preset-chip {
  position: relative;
  width: 32px;
  height: 24px;
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.preset-chip-bg {
  position: absolute;
  inset: 4px;
  border-radius: 2px;
}

.theme-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
}

.theme-card {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  cursor: pointer;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.theme-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.theme-card.is-active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}

.card-swatches {
  display: flex;
  gap: 4px;
}

.swatch {
  width: 20px;
  height: 20px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.swatch-primary {
  width: 28px;
}

.swatch-nav {
  background-image: linear-gradient(45deg, #ddd 25%, transparent 25%);
}

.card-main {
  flex: 1;
  min-width: 160px;
}

.card-title {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 14px;
}

.card-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.preview-phone {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 20px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 10%);
}

.preview-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 52px;
  font-size: 15px;
  font-weight: 600;
}

.preview-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
  min-height: 0;
  padding: 14px;
}

.preview-card {
  padding: 14px;
  background: #fff;
}

.preview-tag {
  display: inline-block;
  padding: 2px 8px;
  font-size: 11px;
  color: #fff;
  border-radius: 4px;
}

.preview-goods {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: 12px;
}

.preview-thumb {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #f0f0f0, #e0e0e0);
  border-radius: 6px;
}

.preview-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview-name {
  font-size: 13px;
  color: #333;
}

.preview-price {
  font-size: 16px;
  font-weight: 700;
}

.preview-btn {
  padding: 10px 0;
  margin-top: 14px;
  font-size: 14px;
  color: #fff;
  text-align: center;
}

.preview-tabbar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding-top: 12px;
  margin-top: 12px;
  font-size: 12px;
  color: #999;
  border-top: 1px solid rgb(0 0 0 / 6%);
}

.tabbar-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
}

.tabbar-dot {
  width: 16px;
  height: 16px;
  background: #c0c4cc;
  border-radius: 50%;
}

.tabbar-item.is-active .tabbar-dot {
  background: currentcolor;
}

.color-row {
  display: flex;
  gap: 8px;
}
</style>
