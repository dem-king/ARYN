<script setup lang="ts">
import type { CanvasThemeVars } from '../schema/theme-presets';
import type { DecorationSection, PageSettings } from '../schema/types';

import type { PageDesignType } from '#/api/promotion/page-design';

import { computed } from 'vue';

import { ElEmpty } from 'element-plus';

import { resolveEffectivePageTheme } from '../schema/effective-theme';
import CanvasPageShell from './canvas-page-shell.vue';
import CanvasSectionList from './canvas-section-list.vue';

const props = defineProps<{
  page: PageSettings;
  pageName: string;
  pageType: PageDesignType;
  sections: DecorationSection[];
  selectedId?: string;
  /** 是否展示原生页面骨架（内嵌页型默认开启，运营可关掉只看装修块） */
  showPageShell: boolean;
  /**
   * 主题 CSS 变量（页面引用主题 > 商城默认主题 > 内置默认红），
   * 与 C 端 App.ku.vue / diy 页面级覆盖使用同一套 `--wot-color-theme-*` 变量名，
   * 画布组件用 var() 消费即可与管理端预览、小程序实机三端同源。
   * 除主色/辅色外还包含页面底色与导航配色：画布用它们覆盖页面自存的
   * 品牌色（含模板种子写死的颜色），与 C 端 diy 渲染器有效主题口径一致。
   */
  themeVars?: CanvasThemeVars;
  zoom: number;
}>();

const emit = defineEmits<{
  duplicate: [id: string];
  move: [id: string, toIndex: number, sectionId: string];
  remove: [id: string];
  select: [id?: string];
  selectSection: [sectionId: string];
}>();

/**
 * 内嵌页型（商详/分类/个人中心）：装修块只是页面中的一段内容。
 *
 * C 端这些页面的 `DiyPage` 都传了 `embedded`，渲染器据此**不渲染装修自带的导航栏**
 * 且不撑满 100vh（见 aryn-mall-uniapp/src/components/diy/index.vue）。画布必须同构，
 * 否则运营在画布里看到一条页面中间的导航栏、保存后到实机却没有。
 */
const isEmbeddedPage = computed(() => ['2', '3', '4'].includes(props.pageType));

/** 只有整页 DIY（微页面/首页）才按 Schema 的 navigation 设置画导航栏 */
const showNavigation = computed(
  () => !isEmbeddedPage.value && props.page.navigation.visible,
);

const showShell = computed(() => isEmbeddedPage.value && props.showPageShell);

/**
 * 页面有效主题色：编辑器传入了主题（页面指定主题或商城默认主题）时覆盖
 * 页面自存配色，未传（主题库不可用）时保留页面原色。
 * 与 C 端 diy 渲染器同一解析函数（preview-parity.test.ts 守卫两端同源）。
 */
const effectivePage = computed(() =>
  resolveEffectivePageTheme(props.page, props.themeVars),
);

/**
 * 页面背景色/图在**整页 DIY**（微页面/首页）下铺满整个手机壳；
 * 内嵌页型下改由骨架内的装修段落承接（见 embeddedStyle）。
 */
const canvasStyle = computed(() => ({
  backgroundColor: showShell.value
    ? undefined
    : effectivePage.value.backgroundColor,
  backgroundImage:
    !showShell.value && props.page.backgroundImage
      ? `url(${props.page.backgroundImage})`
      : undefined,
  // 主题变量沿手机壳节点树继承，画布组件里的 var(--wot-color-theme-*) 由此取值
  '--wot-color-theme-primary': props.themeVars?.primaryColor || undefined,
  '--wot-color-theme-secondary': props.themeVars?.secondaryColor || undefined,
  transform: `scale(${props.zoom})`,
}));

/**
 * 内嵌页型下页面背景只作用在装修块自己那一段。
 *
 * C 端 `diy/index.vue` 的 `pageStyle`（页面背景色/图）绑在 `.diy-page` 根节点上，
 * 内嵌场景该节点只包住装修区块，整页底色仍由各页面自己决定（分类页是 #f4f5f7）。
 */
const embeddedStyle = computed(() => ({
  backgroundColor: effectivePage.value.backgroundColor,
  backgroundImage: props.page.backgroundImage
    ? `url(${props.page.backgroundImage})`
    : undefined,
  backgroundPosition: props.page.backgroundImage ? 'top center' : undefined,
  backgroundRepeat: props.page.backgroundImage ? 'no-repeat' : undefined,
  backgroundSize: props.page.backgroundImage ? '100% auto' : undefined,
}));

const isEmpty = computed(() =>
  props.sections.every((section) => section.components.length === 0),
);
</script>

<template>
  <div class="canvas-stage" @click.self="emit('select', undefined)">
    <div class="phone-shell" :style="canvasStyle">
      <div
        v-if="showNavigation"
        class="phone-navigation"
        :style="{
          backgroundColor: effectivePage.navigationBackgroundColor,
          color: effectivePage.navigationTextColor,
        }"
      >
        {{ page.navigation.title || pageName }}
      </div>

      <!--
        内嵌页型套原生骨架：让运营看到装修块落在页面哪一段、能用的真实宽度是多少
        （分类页装修在右栏商品流内，可用宽度只有 375 - 88 = 287px）。
        骨架里的插槽就是装修块的真实落点，区块仍在外层受拖拽/选中逻辑管辖。
      -->
      <CanvasPageShell
        v-if="showShell"
        :page-type="pageType"
        @click.self="emit('select', undefined)"
      >
        <div :style="embeddedStyle">
          <ElEmpty
            v-if="isEmpty"
            :image-size="72"
            description="从左侧添加组件"
          />
          <CanvasSectionList
            v-else
            :sections="sections"
            :selected-id="selectedId"
            @duplicate="emit('duplicate', $event)"
            @move="(id, index, sectionId) => emit('move', id, index, sectionId)"
            @remove="emit('remove', $event)"
            @select="emit('select', $event)"
            @select-section="emit('selectSection', $event)"
          />
        </div>
      </CanvasPageShell>

      <template v-else>
        <ElEmpty v-if="isEmpty" :image-size="72" description="从左侧添加组件" />
        <CanvasSectionList
          v-else
          :sections="sections"
          :selected-id="selectedId"
          @duplicate="emit('duplicate', $event)"
          @move="(id, index, sectionId) => emit('move', id, index, sectionId)"
          @remove="emit('remove', $event)"
          @select="emit('select', $event)"
          @select-section="emit('selectSection', $event)"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.canvas-stage {
  display: flex;
  justify-content: center;
  min-width: 520px;
  min-height: 100%;
  padding: 32px 72px 100px;
  overflow: auto;
  background: var(--el-fill-color-light);
}

.phone-shell {
  /* 内容宽必须正好是设备屏宽 375px（= 750rpx）：骨架里分类页左栏 88px 是实数，
     内容区若被边框吃掉 2px，右栏量出来就是 285px 而不是真实的 287px。
     故用 content-box + 内偏移 outline（不影响布局）画那圈描边。 */
  box-sizing: content-box;
  width: 375px;
  min-height: 667px;
  overflow: visible;
  outline: 1px solid var(--el-border-color);
  outline-offset: -1px;
  background: #fff;
  background-repeat: no-repeat;
  background-position: top center;
  background-size: 100% auto;
  box-shadow: 0 12px 32px rgb(15 23 42 / 12%);
  transform-origin: top center;
}

.phone-navigation {
  display: flex;
  align-items: end;
  justify-content: center;
  height: 84px;
  padding: 0 44px 10px;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
}
</style>
