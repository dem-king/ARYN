<script setup lang="ts">
import type {
  PageDesignTheme,
  PageDesignThemePayload,
} from '#/api/promotion/page-design';

import { reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElColorPicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  createTheme,
  deleteTheme,
  getThemes,
  updateTheme,
} from '#/api/promotion/page-design';

const props = defineProps<{
  modelValue: boolean;
  themeRef?: string;
}>();

const emit = defineEmits<{
  apply: [themeId: string];
  'update:modelValue': [value: boolean];
}>();

const loading = ref(false);
const themes = ref<PageDesignTheme[]>([]);
const editing = ref<null | PageDesignTheme>(null);
const formVisible = ref(false);
const form = reactive<PageDesignThemePayload>({
  navigationColor: '#ffffff',
  navigationTextColor: '#222222',
  pageBackgroundColor: '#f5f5f5',
  primaryColor: '#ff5500',
  radius: 8,
  themeName: '',
});

async function loadThemes() {
  if (!props.modelValue) return;
  loading.value = true;
  try {
    themes.value = await getThemes();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = null;
  form.themeName = '';
  form.primaryColor = '#ff5500';
  form.pageBackgroundColor = '#f5f5f5';
  form.navigationColor = '#ffffff';
  form.navigationTextColor = '#222222';
  form.radius = 8;
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

async function submitForm() {
  if (!form.themeName.trim()) {
    ElMessage.warning('请填写主题名称');
    return;
  }
  if (editing.value) {
    await updateTheme(editing.value.id, { ...form });
    ElMessage.success('主题已更新');
  } else {
    const created = await createTheme({ ...form });
    ElMessage.success('主题已创建');
    themes.value = [...themes.value, created];
  }
  formVisible.value = false;
  await loadThemes();
}

async function remove(theme: PageDesignTheme) {
  const { ElMessageBox } = await import('element-plus');
  await ElMessageBox.confirm(`确认删除主题“${theme.themeName}”？`, '删除主题', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await deleteTheme(theme.id);
  ElMessage.success('已删除');
  await loadThemes();
}

function apply(theme: PageDesignTheme) {
  emit('apply', theme.id);
  emit('update:modelValue', false);
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      void loadThemes();
    }
    formVisible.value = false;
  },
);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    title="装修主题"
    width="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="theme-toolbar">
      <ElButton
        v-access:code="'promotion:pagedesign:theme'"
        size="small"
        type="primary"
        @click="openCreate"
      >
        新建主题
      </ElButton>
      <span v-if="themeRef" class="theme-current">
        当前页面引用的主题令牌：{{ themeRef }}
      </span>
    </div>

    <ElTable v-loading="loading" :data="themes" max-height="360">
      <ElTableColumn label="主题" min-width="140">
        <template #default="{ row }">
          {{ row.themeName }}
          <ElTag
            v-if="row.systemFlag === '1'"
            class="theme-tag"
            effect="plain"
            size="small"
          >
            系统
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="主色" width="90">
        <template #default="{ row }">
          <span
            :style="{ background: row.primaryColor }"
            class="color-chip"
          ></span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="页面背景" width="90">
        <template #default="{ row }">
          <span
            :style="{ background: row.pageBackgroundColor }"
            class="color-chip"
          ></span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="导航背景 / 文字" width="120">
        <template #default="{ row }">
          <div class="color-pair">
            <span
              :style="{ background: row.navigationColor }"
              class="color-chip"
            ></span>
            <span
              :style="{ background: row.navigationTextColor }"
              class="color-chip"
            ></span>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="圆角" width="70" prop="radius" />
      <ElTableColumn label="操作" width="190" align="center">
        <template #default="{ row }">
          <ElButton
            :disabled="themeRef === row.id"
            link
            type="primary"
            @click="apply(row as PageDesignTheme)"
          >
            {{ themeRef === row.id ? '已应用' : '应用' }}
          </ElButton>
          <template v-if="row.systemFlag !== '1'">
            <ElButton
              v-access:code="'promotion:pagedesign:theme'"
              link
              @click="openEdit(row as PageDesignTheme)"
            >
              编辑
            </ElButton>
            <ElButton
              v-access:code="'promotion:pagedesign:theme'"
              link
              type="danger"
              @click="remove(row as PageDesignTheme)"
            >
              删除
            </ElButton>
          </template>
        </template>
      </ElTableColumn>
    </ElTable>

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
        <ElFormItem label="品牌主色">
          <ElColorPicker v-model="form.primaryColor" />
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
        <ElButton type="primary" @click="submitForm">保存</ElButton>
      </template>
    </ElDialog>
  </ElDialog>
</template>

<style scoped>
.theme-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.theme-current {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.theme-tag {
  margin-left: 6px;
}

.color-chip {
  display: inline-block;
  width: 22px;
  height: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 3px;
}

.color-pair {
  display: flex;
  gap: 6px;
}

.color-row {
  display: flex;
  gap: 8px;
}
</style>
