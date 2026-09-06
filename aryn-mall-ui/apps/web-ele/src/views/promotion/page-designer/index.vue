<script setup lang="ts">
import type { DecorationDocument, PageSettings } from './schema/types';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';

import {
  ElLoading,
  ElMessage,
  ElMessageBox,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import {
  addObj,
  createPreviewToken,
  getEditor,
  getHomeDesign,
  saveDraft,
  submitRelease,
} from '#/api/promotion/page-design';

import PreviewDialog from '../page-design/components/preview-dialog.vue';
import { PREVIEW_TTL_MS } from '../page-design/components/preview-utils';
import AssetCheck from './components/asset-check.vue';
import ComponentLibrary from './components/component-library.vue';
import DesignerToolbar from './components/designer-toolbar.vue';
import PageOutline from './components/page-outline.vue';
import PageSettingsPanel from './components/page-settings.vue';
import PhoneCanvas from './components/phone-canvas.vue';
import PropertyPanel from './components/property-panel.vue';
import PublishDialog from './components/publish-dialog.vue';
import SectionSettings from './components/section-settings.vue';
import TemplateDialog from './components/template-dialog.vue';
import ThemeDialog from './components/theme-dialog.vue';
import { useDraftSave } from './composables/use-draft-save';
import { usePageDesigner } from './composables/use-page-designer';
import { getComponentDefinition } from './registry/component-registry';
import { createDefaultDecorationDocument } from './schema/defaults';
import { migratePageContent } from './schema/migrate';
import { toV3Document } from './schema/v3';

const route = useRoute();
const router = useRouter();
const pageId = ref(typeof route.params.id === 'string' ? route.params.id : '');
const pageName = ref('新建页面');
const pageType = ref<'0' | '1'>(route.query.type === 'home' ? '1' : '0');
const revision = ref(0);
const publishedStatus = ref<'0' | '1'>('0');
const zoom = ref(1);
const loading = ref(true);
const templateVisible = ref(false);
const publishVisible = ref(false);
const themeVisible = ref(false);
const assetVisible = ref(false);
const themeRef = ref('');
const previewState = ref({ expiresAt: 0, token: '', visible: false });

const designer = usePageDesigner({
  initialDocument: createDefaultDecorationDocument(),
});

const selectedComponent = computed(() =>
  designer.flatComponents.value.find(
    (component) => component.id === designer.selectedId.value,
  ),
);
const selectedSection = computed(() =>
  designer.document.value.sections.find(
    (section) => section.id === designer.activeSectionId.value,
  ),
);
const selectedIds = ref<string[]>([]);

function toggleSelect(id: string, checked: boolean) {
  selectedIds.value = checked
    ? [...new Set([id, ...selectedIds.value])]
    : selectedIds.value.filter((item) => item !== id);
}

function batchDuplicate() {
  changed(() => {
    designer.duplicateComponents(selectedIds.value);
    selectedIds.value = [];
  });
}

function batchRemove() {
  changed(() => {
    designer.removeComponents(selectedIds.value);
    selectedIds.value = [];
  });
}

function handleKeyboard(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null;
  if (target && ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName)) {
    return;
  }
  if (
    (event.key === 'Delete' || event.key === 'Backspace') &&
    selectedIds.value.length > 0
  ) {
    event.preventDefault();
    batchRemove();
    return;
  }
  if (
    (event.key === 'Delete' || event.key === 'Backspace') &&
    designer.selectedId.value
  ) {
    event.preventDefault();
    changed(() => designer.removeComponent(designer.selectedId.value!));
    return;
  }
  if (event.key === 'Escape') {
    designer.selectComponent(undefined);
    selectedIds.value = [];
    return;
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') {
    event.preventDefault();
    if (event.shiftKey) {
      changed(designer.redo);
    } else {
      changed(designer.undo);
    }
  }
}

async function ensurePage() {
  if (pageId.value) return pageId.value;
  pageId.value = await addObj({
    homeStatus: pageType.value === '1' ? '1' : '0',
    pageName: pageName.value,
    pageType: pageType.value,
    status: '0',
  });
  await router.replace({ name: 'PageDesigner', params: { id: pageId.value } });
  return pageId.value;
}

const draftSave = useDraftSave({
  buildPayload: () => ({
    draftRevision: revision.value,
    pageContent: toV3Document(designer.document.value, {
      themeRef: themeRef.value || undefined,
    }) as unknown as Record<string, unknown>,
    pageName: pageName.value,
    schemaVersion: 3,
  }),
  delay: 1800,
  revision,
  save: async (payload) => saveDraft(await ensurePage(), payload),
});

async function loadPage() {
  loading.value = true;
  const loader = ElLoading.service({ lock: true, text: '正在加载装修草稿' });
  try {
    if (!pageId.value && pageType.value === '1') {
      const home = await getHomeDesign();
      pageId.value = home.id;
    }
    if (!pageId.value) return;
    const editor = await getEditor(pageId.value);
    pageName.value = editor.pageName;
    pageType.value = editor.pageType;
    revision.value = editor.draftRevision;
    publishedStatus.value = editor.publishedStatus;
    themeRef.value =
      typeof editor.pageContent.themeRef === 'string'
        ? editor.pageContent.themeRef
        : '';
    designer.reset(migratePageContent(editor.pageContent));
  } finally {
    loader.close();
    loading.value = false;
  }
}

function changed(action: () => void) {
  action();
  draftSave.markDirty();
}

function addComponent(type: string) {
  const definition = getComponentDefinition(type);
  if (!definition) return;
  changed(() =>
    designer.addComponent(
      {
        props: definition.createDefaultProps(),
        type,
        version: definition.version,
      },
      undefined,
      designer.activeSectionId.value,
    ),
  );
}

function addSection() {
  changed(() => designer.addSection(designer.activeSectionId.value));
}

function patchPage(value: PageSettings) {
  changed(() => designer.patchPage(value, 'page-settings'));
}

function updatePageName(value: string) {
  pageName.value = value;
  draftSave.markDirty();
}

async function preview() {
  await draftSave.saveNow();
  const token = await createPreviewToken(await ensurePage(), revision.value);
  previewState.value = {
    expiresAt: Date.now() + PREVIEW_TTL_MS,
    token,
    visible: true,
  };
}

async function publish(remark = '') {
  if (designer.flatComponents.value.length === 0) {
    ElMessage.warning('至少添加一个组件后再发布');
    return;
  }
  await draftSave.saveNow();
  const release = await submitRelease(await ensurePage(), {
    draftRevision: revision.value,
    publishRemark: remark || undefined,
  });
  if (release.releaseStatus === '1') {
    publishedStatus.value = '1';
    ElMessage.success('发布成功');
  } else {
    ElMessage.info('发布申请已提交，等待审批');
  }
  publishVisible.value = false;
}

function locateComponent(componentId?: string) {
  if (!componentId) return;
  designer.selectComponent(componentId);
  publishVisible.value = false;
}

function applyTemplate(document: DecorationDocument) {
  designer.reset(document);
  draftSave.markDirty();
}

function applyTheme(id: string) {
  themeRef.value = id;
  draftSave.markDirty();
  ElMessage.success('主题已应用到页面，保存草稿后生效');
}

async function back() {
  if (draftSave.isDirty.value) {
    await ElMessageBox.confirm('仍有未保存修改，确认离开？', '离开编辑器', {
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
      type: 'warning',
    });
  }
  await router.push('/promotion/page-design');
}

function beforeUnload(event: BeforeUnloadEvent) {
  draftSave.handleBeforeUnload(event);
}

onBeforeRouteLeave(async () => {
  if (!draftSave.isDirty.value) return true;
  try {
    await ElMessageBox.confirm('仍有未保存修改，确认离开？', '离开编辑器', {
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
      type: 'warning',
    });
    return true;
  } catch {
    return false;
  }
});

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload);
  void loadPage();
});
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload);
  draftSave.dispose();
});
</script>

<template>
  <div
    v-loading="loading"
    class="page-designer"
    tabindex="0"
    @keydown="handleKeyboard"
  >
    <DesignerToolbar
      v-model:page-name="pageName"
      v-model:zoom="zoom"
      :can-redo="designer.canRedo.value"
      :can-undo="designer.canUndo.value"
      :save-status="draftSave.status.value"
      @assets="assetVisible = true"
      @back="back"
      @preview="preview"
      @publish="publishVisible = true"
      @redo="changed(designer.redo)"
      @save="draftSave.saveNow"
      @template="templateVisible = true"
      @theme="themeVisible = true"
      @undo="changed(designer.undo)"
      @update:page-name="updatePageName"
    />
    <main class="designer-workspace">
      <aside class="left-rail">
        <ElTabs type="border-card" class="left-tabs">
          <ElTabPane label="组件库">
            <ComponentLibrary @add="addComponent" />
          </ElTabPane>
          <ElTabPane label="页面大纲">
            <div class="outline-pane">
              <ElButton
                class="outline-add-section"
                size="small"
                @click="addSection"
              >
                新增区块
              </ElButton>
              <PageOutline
                :active-section-id="designer.activeSectionId.value"
                :sections="designer.document.value.sections"
                :selected-id="designer.selectedId.value"
                :selected-ids="selectedIds"
                @batch-duplicate="batchDuplicate"
                @batch-remove="batchRemove"
                @move="
                  (id, index, sectionId) =>
                    changed(() => designer.moveComponent(id, index, sectionId))
                "
                @move-section="
                  (sectionId, index) =>
                    changed(() => designer.moveSection(sectionId, index))
                "
                @remove="(id) => changed(() => designer.removeComponent(id))"
                @remove-section="
                  (sectionId) => {
                    if (designer.document.value.sections.length <= 1) {
                      ElMessage.warning('至少保留一个区块');
                      return;
                    }
                    changed(() => designer.removeSection(sectionId));
                  }
                "
                @select="designer.selectComponent"
                @select-section="designer.selectSection"
                @toggle-select="toggleSelect"
              />
            </div>
          </ElTabPane>
        </ElTabs>
      </aside>

      <PhoneCanvas
        :page="designer.document.value.page"
        :page-name="pageName"
        :sections="designer.document.value.sections"
        :selected-id="designer.selectedId.value"
        :zoom="zoom"
        @duplicate="(id) => changed(() => designer.duplicateComponent(id))"
        @move="
          (id, index, sectionId) =>
            changed(() => designer.moveComponent(id, index, sectionId))
        "
        @remove="(id) => changed(() => designer.removeComponent(id))"
        @select="designer.selectComponent"
        @select-section="designer.selectSection"
      />

      <aside class="right-rail">
        <ElTabs type="border-card" class="right-tabs">
          <ElTabPane label="组件设置">
            <PropertyPanel
              :component="selectedComponent"
              @patch="
                (patch, group) =>
                  changed(() =>
                    designer.patchComponent(
                      designer.selectedId.value!,
                      patch,
                      group,
                    ),
                  )
              "
            />
          </ElTabPane>
          <ElTabPane label="区块设置">
            <SectionSettings
              :section="selectedSection"
              @patch="
                (patch, group) =>
                  changed(() =>
                    designer.patchSection(
                      designer.activeSectionId.value!,
                      patch,
                      group,
                    ),
                  )
              "
            />
          </ElTabPane>
          <ElTabPane label="页面设置">
            <PageSettingsPanel
              :model-value="designer.document.value.page"
              @update:model-value="patchPage"
            />
          </ElTabPane>
        </ElTabs>
      </aside>
    </main>
    <TemplateDialog
      v-model="templateVisible"
      :current-document="designer.document.value"
      :page-type="pageType"
      @apply="applyTemplate"
    />
    <ThemeDialog
      v-model="themeVisible"
      :theme-ref="themeRef || undefined"
      @apply="applyTheme"
    />
    <AssetCheck v-model="assetVisible" :page-id="pageId" />
    <PublishDialog
      v-model="publishVisible"
      :document="designer.document.value"
      :page-id="pageId"
      :page-name="pageName"
      @confirm="publish"
      @locate="locateComponent"
    />
    <PreviewDialog
      v-model="previewState.visible"
      :expires-at="previewState.expiresAt"
      :page-name="pageName"
      :token="previewState.token"
    />
  </div>
</template>

<style scoped>
.page-designer {
  min-width: 1180px;
  height: 100vh;
  overflow: hidden;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

.outline-pane {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
}

.outline-add-section {
  align-self: flex-end;
}

.designer-workspace {
  display: grid;
  grid-template-columns: 260px minmax(540px, 1fr) 380px;
  height: calc(100vh - 56px);
}

.left-rail,
.right-rail {
  min-width: 0;
  overflow: hidden;
  background: var(--el-bg-color);
}

.left-rail {
  border-right: 1px solid var(--el-border-color-light);
}

.right-rail {
  border-left: 1px solid var(--el-border-color-light);
}

.left-tabs,
.right-tabs {
  height: 100%;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.left-tabs :deep(.el-tabs__content),
.right-tabs :deep(.el-tabs__content),
.left-tabs :deep(.el-tab-pane),
.right-tabs :deep(.el-tab-pane) {
  height: calc(100% - 40px);
  padding: 0;
  overflow: auto;
}

@media (max-width: 1365px) {
  .designer-workspace {
    grid-template-columns: 240px minmax(520px, 1fr) 340px;
  }
}
</style>
