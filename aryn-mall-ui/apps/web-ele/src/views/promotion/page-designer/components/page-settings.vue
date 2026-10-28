<script setup lang="ts">
import type { CanvasThemeVars } from '../schema/theme-presets';
import type {
  DecorationSection,
  NavigationScrollEffect,
  NavigationScrollSurface,
  PageSettings,
} from '../schema/types';

import type { PageDesignType } from '#/api/promotion/page-design';

import { computed, reactive, watch } from 'vue';

import {
  ElColorPicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElRadioButton,
  ElRadioGroup,
  ElSlider,
  ElSwitch,
} from 'element-plus';

import { isEmbeddedPageType } from '#/api/promotion/page-design';

import { cloneDesignerValue, isSameDesignerValue } from '../schema/clone';
import { resolveEffectivePageTheme } from '../schema/effective-theme';
import { normalizeNavigationScrollEffect } from '../schema/navigation-scroll';
import { resolveNavigationAppearance } from '../schema/navigation-style';

const props = defineProps<{
  /** 首个区块：导航栏「跟随首个区块」用它实时预览实际生效的背景/文字色 */
  firstSection?: DecorationSection;
  modelValue: PageSettings;
  /**
   * 页面名称：导航标题留空时，画布/草稿预览/小程序实机都回退显示它
   * （三处统一 `navigation.title || 页面名称`）。面板必须把这个回退写出来，
   * 否则运营看到画布上有标题、设置面板里却为空，会误以为标题没保存或丢了。
   */
  pageName?: string;
  /**
   * 当前页面引用的主题名（「本页主题」已指定时传入）：
   * 配色随该主题，取色器与跟随开关一并停用，避免改了不生效的值。
   */
  pageThemeName?: string;
  /**
   * 页型：决定内嵌页型（商详/分类/个人中心）是否禁用「滚动效果」。
   *
   * 这三类页面的装修区只是原生页面中的一段内容，C 端 `DiyPage` 传 `embedded`
   * 后不渲染装修自带导航栏，滚动渐显没有渲染面；面板若可配置，运营改完到
   * 实机看不到变化，会被当成配置不生效。
   */
  pageType?: PageDesignType;
  /**
   * 有效主题配色（与画布 themeVars 同源，页面未指定主题时即商城默认主题）：
   * 跟随态下取色器展示该值并禁用，保证面板与画布/实机所见一致。
   */
  themeVars?: CanvasThemeVars;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: PageSettings] }>();

const form = reactive<PageSettings>(cloneDesignerValue(props.modelValue));

watch(
  () => props.modelValue,
  (value) => {
    if (!isSameDesignerValue(form, value)) {
      Object.assign(form, cloneDesignerValue(value));
    }
  },
  { deep: true },
);
watch(
  form,
  (value) => {
    if (!isSameDesignerValue(value, props.modelValue)) {
      emit('update:modelValue', cloneDesignerValue(value));
    }
  },
  { deep: true },
);

/**
 * 配色来源（三态互斥）：本页主题 > 商城默认主题（跟随，缺省） > 自定义配色。
 * followMallTheme 缺省视为跟随，存量页面与模板行为不变。
 */
const followMallTheme = computed({
  get: () => form.followMallTheme !== false,
  set: (value: boolean) => {
    form.followMallTheme = value;
  },
});

/** 取色器是否被上层接管：跟随商城主题 或 页面已指定主题 时改了也不生效 */
const colorControlled = computed(
  () => followMallTheme.value || !!props.pageThemeName,
);

/** 导航背景来源：缺省自定义（存量页面与模板行为不变） */
const navigationBackgroundMode = computed<'custom' | 'followFirstSection'>(
  () => form.navigation.backgroundMode ?? 'custom',
);
const followFirstSection = computed(
  () => navigationBackgroundMode.value === 'followFirstSection',
);

/** 跟随首个区块时是否自动取对比文字色（缺省开） */
const autoNavigationTextColor = computed(
  () => form.navigation.textColorAuto !== false,
);

/**
 * 导航标题的回退占位文案：留空时画布/草稿预览/实机都显示页面名称，
 * 与三端渲染同一优先级（`navigation.title || pageName`），
 * 让面板里的空输入框和画布上的那行字对得上。
 */
const navigationTitlePlaceholder = computed(() =>
  props.pageName
    ? `留空时显示页面名称「${props.pageName}」`
    : '留空时显示页面名称',
);

/** 当前有效主题色：与画布/实机同一解析函数，跟随预览的兜底色也用它 */
const effectiveTheme = computed(() =>
  resolveEffectivePageTheme(
    form,
    props.themeVars
      ? {
          navigationColor: props.themeVars.navigationColor,
          navigationTextColor: props.themeVars.navigationTextColor,
          pageBackgroundColor: props.themeVars.pageBackgroundColor,
        }
      : undefined,
  ),
);

/** 跟随首个区块时的实际导航外观：取色器回显与提示都用它，保证与画布一致 */
const followedNavigation = computed(() =>
  resolveNavigationAppearance(
    form,
    effectiveTheme.value,
    props.firstSection ? [props.firstSection] : [],
  ),
);

/** 首个区块是否有可跟随的背景（纯色/渐变）；没有时导航回落主题/自定义色 */
const firstSectionFollowable = computed(() => {
  const style = props.firstSection?.style;
  if (!style) return false;
  if (style.backgroundImage) return Boolean(style.backgroundColor);
  const gradient = style.backgroundGradient;
  if (gradient && (gradient.startColor || gradient.endColor)) return true;
  return Boolean(style.backgroundColor);
});

function onNavigationBackgroundModeChange(
  value: boolean | number | string | undefined,
) {
  form.navigation.backgroundMode =
    value === 'followFirstSection' ? 'followFirstSection' : 'custom';
}

function onAutoNavigationTextColorChange(value: boolean | number | string) {
  form.navigation.textColorAuto = Boolean(value);
}

/**
 * 跟随/主题接管态下取色器展示有效主题色（与画布同源），而非页面自存值；
 * 主题色缺失（主题库加载失败）时回落页面自存值，保持可读。
 */
const pageBackgroundColorModel = computed<null | string | undefined>({
  get: () =>
    colorControlled.value
      ? props.themeVars?.pageBackgroundColor || form.backgroundColor
      : form.backgroundColor,
  set: (value) => {
    form.backgroundColor = value ?? '';
  },
});

const navigationBackgroundColorModel = computed<null | string | undefined>({
  get: () => {
    if (followFirstSection.value) {
      return followedNavigation.value.backgroundColor;
    }
    if (colorControlled.value) {
      return (
        props.themeVars?.navigationColor || form.navigation.backgroundColor
      );
    }
    return form.navigation.backgroundColor;
  },
  set: (value) => {
    form.navigation.backgroundColor = value ?? '';
  },
});

const navigationTextColorModel = computed<null | string | undefined>({
  get: () => {
    if (followFirstSection.value) {
      return followedNavigation.value.textColor;
    }
    if (colorControlled.value) {
      return props.themeVars?.navigationTextColor || form.navigation.textColor;
    }
    return form.navigation.textColor;
  },
  set: (value) => {
    form.navigation.textColor = value ?? '';
  },
});

/**
 * 滚动渐显（沉浸式导航）配置：一律经共享归一化函数读写。
 *
 * 面板只做「展示 + 改一个字段」，其余字段沿用当前值；归一化保证写回 form 的
 * 永远是完整对象（缺省 mode='none'、distance 40–400），存量页面零变化，
 * 也避免 JSON 克隆丢 undefined 键导致的「撤销无效 / 关不掉」。
 */
const scrollEffect = computed(() =>
  normalizeNavigationScrollEffect(form.navigation.scrollEffect),
);
const scrollEffectMode = computed(() => scrollEffect.value.mode);
const scrollFadeEnabled = computed(() => scrollEffect.value.mode === 'fade');
const scrollSurface = computed(() => scrollEffect.value.surface);

/** 内嵌页型不渲染装修导航栏，滚动效果整组禁用并提示（见 pageType prop 注释） */
const embeddedPage = computed(() => isEmbeddedPageType(props.pageType));

function patchScrollEffect(patch: Partial<NavigationScrollEffect>) {
  form.navigation.scrollEffect = normalizeNavigationScrollEffect({
    ...scrollEffect.value,
    ...patch,
  });
}

function onScrollEffectModeChange(
  value: boolean | number | string | undefined,
) {
  patchScrollEffect({ mode: value === 'fade' ? 'fade' : 'none' });
}

function onScrollSurfaceChange(value: boolean | number | string | undefined) {
  const surface: NavigationScrollSurface =
    value === 'theme' || value === 'custom' ? value : 'frosted';
  // 首次切到「自定义」时给一个显式色值：目标底色 = 自定义色（alpha 1、无毛玻璃），
  // 否则空串会被渲染端回落成磨砂白，运营会以为自定义没生效。
  if (surface === 'custom' && !scrollEffect.value.surfaceColor) {
    patchScrollEffect({ surface, surfaceColor: '#FFFFFF' });
    return;
  }
  patchScrollEffect({ surface });
}

const scrollSurfaceColorModel = computed<null | string | undefined>({
  get: () => scrollEffect.value.surfaceColor || '#FFFFFF',
  set: (value) => patchScrollEffect({ surfaceColor: value ?? '' }),
});

const scrollDistanceModel = computed<number>({
  get: () => scrollEffect.value.distance,
  set: (value) => patchScrollEffect({ distance: Number(value) }),
});

const scrollBlurModel = computed<boolean>({
  get: () => scrollEffect.value.blur,
  set: (value) => patchScrollEffect({ blur: Boolean(value) }),
});
</script>

<template>
  <ElForm :model="form" label-position="top" class="page-settings">
    <ElFormItem label="配色来源">
      <template v-if="pageThemeName">
        <p class="follow-hint">
          本页已指定主题「{{ pageThemeName }}」，页面背景与导航配色跟随该主题，
          可通过顶部「本页主题」改选。
        </p>
      </template>
      <template v-else>
        <div class="follow-row">
          <ElSwitch v-model="followMallTheme" />
          <span class="follow-label">
            {{ followMallTheme ? '跟随商城主题' : '自定义配色' }}
          </span>
        </div>
        <p class="follow-hint">
          {{
            followMallTheme
              ? '页面背景与导航配色由「商城装修 → 商城主题」统一控制，下方颜色暂不生效。'
              : '使用下方自定义配色，不再跟随商城主题；重新开启即可回到全局换肤。'
          }}
        </p>
      </template>
    </ElFormItem>
    <ElFormItem label="页面背景色">
      <ElColorPicker
        v-model="pageBackgroundColorModel"
        :disabled="colorControlled"
        show-alpha
      />
      <p class="follow-hint">
        页面背景铺在整页最底层；区块自行设置了背景时，会在该区块范围内覆盖它。
      </p>
    </ElFormItem>
    <ElFormItem label="页面背景图">
      <ElInput
        v-model="form.backgroundImage"
        clearable
        placeholder="图片地址"
      />
    </ElFormItem>
    <ElFormItem label="显示导航栏">
      <ElSwitch v-model="form.navigation.visible" />
    </ElFormItem>
    <ElFormItem label="导航标题">
      <ElInput
        v-model="form.navigation.title"
        maxlength="30"
        :placeholder="navigationTitlePlaceholder"
      />
      <p class="follow-hint">
        留空时画布、预览与小程序实机都显示页面名称{{
          pageName ? `「${pageName}」` : ''
        }}，在顶部修改页面名称会同步影响导航文案；填写后以导航标题为准。
      </p>
    </ElFormItem>
    <ElFormItem label="导航背景来源">
      <ElRadioGroup
        :model-value="navigationBackgroundMode"
        @change="onNavigationBackgroundModeChange"
      >
        <ElRadioButton value="custom">自定义颜色</ElRadioButton>
        <ElRadioButton value="followFirstSection">跟随首个区块</ElRadioButton>
      </ElRadioGroup>
      <p class="follow-hint">
        {{
          followFirstSection
            ? '自动取首个区块的背景：渐变取顶边起始色，左右渐变整段复用，上下渐变可与区块无缝衔接；文字默认按背景自动取黑/白。跟随态会覆盖商城主题的导航色。'
            : '自定义时，导航背景取跟随的商城主题导航色（跟随态）或下方颜色（自定义配色）。'
        }}
      </p>
      <p
        v-if="followFirstSection && !firstSectionFollowable"
        class="follow-hint"
      >
        首个区块还没有背景，导航暂用主题/自定义导航色；给首个区块设置纯色或渐变后会自动跟随。
      </p>
      <div v-if="followFirstSection" class="follow-row">
        <ElSwitch
          :model-value="autoNavigationTextColor"
          @change="onAutoNavigationTextColorChange"
        />
        <span class="follow-label">文字颜色自动对比</span>
      </div>
    </ElFormItem>
    <ElFormItem label="导航背景 / 文字">
      <div class="color-row">
        <ElColorPicker
          v-model="navigationBackgroundColorModel"
          :disabled="colorControlled || followFirstSection"
        />
        <ElColorPicker
          v-model="navigationTextColorModel"
          :disabled="
            colorControlled || (followFirstSection && autoNavigationTextColor)
          "
        />
      </div>
      <p v-if="followFirstSection" class="follow-hint">
        跟随态下上方两个色块只是回显：背景随区块、文字随对比度自动变化，不单独生效。
      </p>
    </ElFormItem>
    <ElFormItem label="滚动效果">
      <ElRadioGroup
        :disabled="embeddedPage"
        :model-value="scrollEffectMode"
        @change="onScrollEffectModeChange"
      >
        <ElRadioButton value="none">关闭（固定底色）</ElRadioButton>
        <ElRadioButton value="fade">沉浸式渐显</ElRadioButton>
      </ElRadioGroup>
      <p class="follow-hint">
        顶部与首屏同色，页面滚动后渐变为下方目标底色；标题与状态栏自动反色。
      </p>
      <p v-if="embeddedPage" class="follow-hint">
        内嵌装修不渲染导航栏（商详/分类/个人中心的导航由原生页面提供），滚动效果不可用。
      </p>
      <template v-if="scrollFadeEnabled">
        <div class="scroll-row scroll-row--block">
          <span class="follow-label">目标底色</span>
          <ElRadioGroup
            :disabled="embeddedPage"
            :model-value="scrollSurface"
            @change="onScrollSurfaceChange"
          >
            <ElRadioButton value="frosted">磨砂白（推荐）</ElRadioButton>
            <ElRadioButton value="theme">商城主题导航色</ElRadioButton>
            <ElRadioButton value="custom">自定义</ElRadioButton>
          </ElRadioGroup>
        </div>
        <div v-if="scrollSurface === 'custom'" class="color-row">
          <ElColorPicker
            v-model="scrollSurfaceColorModel"
            :disabled="embeddedPage || colorControlled"
          />
        </div>
        <p v-if="scrollSurface === 'theme'" class="follow-hint">
          目标底色取商城主题导航色（当前
          {{
            effectiveTheme.navigationBackgroundColor
          }}），标题与状态栏按对比度自动反色。
        </p>
        <p
          v-if="scrollSurface === 'custom' && colorControlled"
          class="follow-hint"
        >
          跟随商城主题或已指定本页主题时，自定义目标底色暂不生效；如需特殊页面配色，请先关闭跟随。
        </p>
        <div class="scroll-row">
          <span class="follow-label">渐显距离</span>
          <ElSlider
            v-model="scrollDistanceModel"
            :disabled="embeddedPage"
            :max="260"
            :min="60"
            :show-input-controls="false"
            :step="10"
            show-input
            class="scroll-slider"
          />
        </div>
        <p class="follow-hint">
          渐显距离
          {{
            scrollEffect.distance
          }}px：滚动到该距离时目标底色完全显现（约两个导航栏高度，推荐 140px）。
        </p>
        <div class="scroll-row">
          <ElSwitch v-model="scrollBlurModel" :disabled="embeddedPage" />
          <span class="follow-label">毛玻璃</span>
        </div>
        <p class="follow-hint">
          毛玻璃让内容从导航栏下方透出；Android
          旧机型可能不支持，会自动回退为半透明底色。
        </p>
        <p class="follow-hint">
          在顶部工具栏切换「顶部 /
          滚动后」或拖动滚动位置可预览；画布不画状态栏、毛玻璃与 fixed
          覆盖，最终以草稿预览与真机为准。
        </p>
      </template>
      <p class="follow-hint">
        保存草稿后生效；预览需重新生成预览链接（草稿保存会使旧 token 失效）。
      </p>
    </ElFormItem>
    <ElFormItem label="分享标题">
      <ElInput v-model="form.share.title" maxlength="60" />
    </ElFormItem>
    <ElFormItem label="分享描述">
      <ElInput
        v-model="form.share.description"
        maxlength="120"
        type="textarea"
      />
    </ElFormItem>
    <ElFormItem label="分享图片">
      <ElInput v-model="form.share.imageUrl" clearable placeholder="图片地址" />
    </ElFormItem>
    <ElFormItem label="下拉刷新">
      <ElSwitch v-model="form.enablePullDownRefresh" />
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.page-settings {
  padding: 16px;
}

.color-row {
  display: flex;
  gap: 12px;
}

.follow-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.follow-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.follow-hint {
  width: 100%;
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.scroll-row {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  margin-top: 4px;
}

/* 目标底色单选组较宽，单独一行避免与标题挤在一起换行错位 */
.scroll-row--block {
  flex-wrap: wrap;
  gap: 8px;
}

.scroll-slider {
  flex: 1;
  min-width: 140px;
}
</style>
