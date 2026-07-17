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
  publishPage,
  saveDraft,
} from '#/api/promotion/page-design';

import PreviewDialog from '../page-design/components/preview-dialog.vue';
import { PREVIEW_TTL_MS } from '../page-design/components/preview-utils';
import ComponentLibrary from './components/component-library.vue';
import DesignerToolbar from './components/designer-toolbar.vue';
import PageOutline from './components/page-outline.vue';
import PageSettingsPanel from './components/page-settings.vue';
import PhoneCanvas from './components/phone-canvas.vue';
import PropertyPanel from './components/property-panel.vue';
import PublishDialog from './components/publish-dialog.vue';
import TemplateDialog from './components/template-dialog.vue';
import { useDraftSave } from './composables/use-draft-save';
import { usePageDesigner } from './composables/use-page-designer';
import { getComponentDefinition } from './registry/component-registry';
import { createDefaultDecorationDocument } from './schema/defaults';
import { migratePageContent } from './schema/migrate';

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
const previewState = ref({ expiresAt: 0, token: '', visible: false });

const designer = usePageDesigner({
  initialDocument: createDefaultDecorationDocument(),
});

const selectedComponent = computed(() =>
  designer.document.value.components.find(
    (component) => component.id === designer.selectedId.value,
  ),
);

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
    pageContent: designer.document.value as unknown as Record<string, unknown>,
    pageName: pageName.value,
    schemaVersion: 2,
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
    designer.addComponent({
      props: definition.createDefaultProps(),
      type,
      version: definition.version,
    }),
  );
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
  if (designer.document.value.components.length === 0) {
    ElMessage.warning('至少添加一个组件后再发布');
    return;
  }
  await draftSave.saveNow();
  await publishPage(await ensurePage(), {
    draftRevision: revision.value,
    publishRemark: remark || undefined,
  });
  publishedStatus.value = '1';
  publishVisible.value = false;
  ElMessage.success('发布成功');
}

function applyTemplate(document: DecorationDocument) {
  designer.reset(document);
  draftSave.markDirty();
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
  <div v-loading="loading" class="page-designer">
    <DesignerToolbar
      v-model:page-name="pageName"
      v-model:zoom="zoom"
      :can-redo="designer.canRedo.value"
      :can-undo="designer.canUndo.value"
      :save-status="draftSave.status.value"
      @back="back"
      @preview="preview"
      @publish="publishVisible = true"
      @redo="changed(designer.redo)"
      @save="draftSave.saveNow"
      @template="templateVisible = true"
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
            <PageOutline
              :components="designer.document.value.components"
              :selected-id="designer.selectedId.value"
              @move="
                (id, index) => changed(() => designer.moveComponent(id, index))
              "
              @remove="(id) => changed(() => designer.removeComponent(id))"
              @select="designer.selectComponent"
            />
          </ElTabPane>
        </ElTabs>
      </aside>

      <PhoneCanvas
        :components="designer.document.value.components"
        :page="designer.document.value.page"
        :page-name="pageName"
        :selected-id="designer.selectedId.value"
        :zoom="zoom"
        @duplicate="(id) => changed(() => designer.duplicateComponent(id))"
        @move="(id, index) => changed(() => designer.moveComponent(id, index))"
        @remove="(id) => changed(() => designer.removeComponent(id))"
        @select="designer.selectComponent"
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
    <PublishDialog
      v-model="publishVisible"
      :document="designer.document.value"
      :page-name="pageName"
      @confirm="publish"
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
