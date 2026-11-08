<script lang="ts" setup>
import type { AudienceSnapshot } from './meta';

import type { NoticeRecord } from '#/api/message/types';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTreeSelect,
} from 'element-plus';

import { createNotice, updateNotice } from '#/api/message/notice';
import { getTreeList as getDeptTree } from '#/api/upms/dept';
import { getList as getRoleList } from '#/api/upms/sys-role';
import { getList as getMemberLevelList } from '#/api/user/member-level';
import { getList as getMemberTagList } from '#/api/user/member-tag';

import { CATEGORY_OPTIONS, parseAudienceSnapshot } from './meta';

/**
 * 通知草稿编辑器（右侧抽屉）。
 *
 * 为什么用抽屉而不是弹窗：改动前基线实测弹窗高 765px、在 1000px 视口下只剩 84px
 * 余量，浏览器 chrome 一占位就溢出；正文本身是长文本，抽屉能给出更长的可视高度，
 * 且打开时列表仍可见（保留上下文），符合「写完一条再发一条」的连续操作节奏。
 *
 * 分段：内容（标题/分类/优先级/摘要/正文）→ 受众（目标端 + 会员/工作人员条件），
 * 一步一步展开，避免一次性铺 10 个字段。
 */
const props = defineProps<{
  /** 由父页面控制显隐 */
  modelValue: boolean;
  /** 传入即编辑，不传即新建 */
  record?: NoticeRecord;
}>();

const emit = defineEmits<{
  saved: [];
  'update:modelValue': [value: boolean];
}>();

interface DeptOption {
  children?: DeptOption[];
  id: string;
  name: string;
}

interface MemberLevelOption {
  id: string;
  levelName: string;
}

interface MemberTagOption {
  id: string;
  tagName: string;
}

interface RoleOption {
  id: string;
  roleName: string;
}

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const isEdit = computed(() => Boolean(props.record));
const title = computed(() => (isEdit.value ? '编辑通知草稿' : '新建通知草稿'));

const saving = ref(false);
const formRef = ref();

const form = reactive({
  category: 'SYSTEM',
  content: '',
  deptIds: [] as string[],
  mallUserIds: undefined as string[] | undefined,
  memberLevelId: undefined as string | undefined,
  memberTagId: undefined as string | undefined,
  priority: 'NORMAL',
  roleIds: [] as string[],
  staffUserIds: undefined as string[] | undefined,
  summary: '',
  targetTypes: ['MALL_USER'] as Array<'MALL_USER' | 'SYS_USER'>,
  title: '',
});

const memberLevels = ref<MemberLevelOption[]>([]);
const memberTags = ref<MemberTagOption[]>([]);
const roles = ref<RoleOption[]>([]);
const depts = ref<DeptOption[]>([]);
const optionsLoaded = ref(false);
const optionsLoading = ref(false);

/** 受众选项只在第一次打开时拉取，失败静默降级为空下拉（不阻断写通知）。 */
async function loadOptions() {
  if (optionsLoaded.value || optionsLoading.value) {
    return;
  }
  optionsLoading.value = true;
  const [levelResult, tagResult, roleResult, deptResult] =
    await Promise.allSettled([
      getMemberLevelList(),
      getMemberTagList(),
      getRoleList({}),
      getDeptTree(),
    ]);
  if (levelResult.status === 'fulfilled') {
    memberLevels.value = levelResult.value ?? [];
  }
  if (tagResult.status === 'fulfilled') {
    memberTags.value = tagResult.value ?? [];
  }
  if (roleResult.status === 'fulfilled') {
    roles.value = roleResult.value ?? [];
  }
  if (deptResult.status === 'fulfilled') {
    depts.value = deptResult.value ?? [];
  }
  optionsLoading.value = false;
  optionsLoaded.value = true;
}

function resetForm() {
  Object.assign(form, {
    category: 'SYSTEM',
    content: '',
    deptIds: [],
    mallUserIds: undefined,
    memberLevelId: undefined,
    memberTagId: undefined,
    priority: 'NORMAL',
    roleIds: [],
    staffUserIds: undefined,
    summary: '',
    targetTypes: ['MALL_USER'],
    title: '',
  });
  formRef.value?.clearValidate?.();
}

function fillForm(record: NoticeRecord) {
  const audience: AudienceSnapshot = parseAudienceSnapshot(
    record.audienceSnapshot,
  );
  Object.assign(form, {
    category: record.category,
    content: record.content,
    deptIds: audience.deptIds ?? [],
    mallUserIds: audience.mallUserIds,
    memberLevelId: audience.memberLevelId,
    memberTagId: audience.memberTagId,
    priority: record.priority || 'NORMAL',
    roleIds: audience.roleIds ?? [],
    staffUserIds: audience.staffUserIds,
    summary: record.summary ?? '',
    targetTypes: (record.targetTypes || 'MALL_USER')
      .split(',')
      .map((type) => type.trim())
      .filter(Boolean) as Array<'MALL_USER' | 'SYS_USER'>,
    title: record.title,
  });
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return;
    }
    void loadOptions();
    if (props.record) {
      fillForm(props.record);
    } else {
      resetForm();
    }
  },
);

/** 取消勾选目标端时同步清掉对应受众条件，避免提交出「条件残留但无目标端」。 */
function syncAudienceFields() {
  if (!form.targetTypes.includes('MALL_USER')) {
    form.memberLevelId = undefined;
    form.memberTagId = undefined;
    form.mallUserIds = undefined;
  }
  if (!form.targetTypes.includes('SYS_USER')) {
    form.roleIds = [];
    form.deptIds = [];
    form.staffUserIds = undefined;
  }
}

function buildPayload() {
  const mall = form.targetTypes.includes('MALL_USER');
  const staff = form.targetTypes.includes('SYS_USER');
  return {
    category: form.category,
    content: form.content,
    deptIds: staff ? form.deptIds : [],
    mallUserIds: mall ? form.mallUserIds : undefined,
    memberLevelId: mall ? form.memberLevelId || undefined : undefined,
    memberTagId: mall ? form.memberTagId || undefined : undefined,
    priority: form.priority,
    roleIds: staff ? form.roleIds : [],
    staffUserIds: staff ? form.staffUserIds : undefined,
    summary: form.summary,
    targetTypes: form.targetTypes,
    title: form.title.trim(),
  };
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写通知标题');
    return;
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写通知正文');
    return;
  }
  if (form.targetTypes.length === 0) {
    ElMessage.warning('请选择至少一个目标端');
    return;
  }
  saving.value = true;
  try {
    const payload = buildPayload();
    await (props.record
      ? updateNotice(props.record.id, payload)
      : createNotice(payload));
    ElMessage.success(isEdit.value ? '草稿已更新' : '草稿已保存');
    visible.value = false;
    emit('saved');
  } catch {
    // 请求层已统一提示，保留表单现场便于修改后重试
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <ElDrawer
    v-model="visible"
    class="nt-editor"
    size="min(720px, 92vw)"
    :title="title"
    :close-on-click-modal="false"
  >
    <ElForm ref="formRef" label-position="top" class="nt-editor__form">
      <section class="nt-section">
        <h3 class="nt-section__title">内容</h3>
        <ElFormItem label="标题" required>
          <ElInput
            v-model="form.title"
            maxlength="200"
            placeholder="一句话说明这条通知要传达什么"
            show-word-limit
          />
        </ElFormItem>

        <div class="nt-grid">
          <ElFormItem label="分类">
            <ElSelect
              v-model="form.category"
              allow-create
              default-first-option
              filterable
              placeholder="选择或输入分类"
            >
              <ElOption
                v-for="option in CATEGORY_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="优先级">
            <ElRadioGroup v-model="form.priority">
              <ElRadio value="NORMAL">普通</ElRadio>
              <ElRadio value="HIGH">重要</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
        </div>

        <ElFormItem label="摘要">
          <ElInput
            v-model="form.summary"
            maxlength="500"
            placeholder="收件箱列表里展示的一行摘要，留空则显示正文开头"
            show-word-limit
          />
        </ElFormItem>

        <ElFormItem label="正文（纯文本）" required>
          <ElInput
            v-model="form.content"
            :autosize="{ minRows: 8, maxRows: 16 }"
            maxlength="10000"
            placeholder="通知正文，发布后不可修改，只能撤回"
            show-word-limit
            type="textarea"
          />
        </ElFormItem>
      </section>

      <section class="nt-section">
        <h3 class="nt-section__title">受众</h3>
        <ElFormItem label="目标端" required>
          <ElCheckboxGroup
            v-model="form.targetTypes"
            @change="syncAudienceFields"
          >
            <ElCheckbox value="MALL_USER">会员</ElCheckbox>
            <ElCheckbox value="SYS_USER">工作人员</ElCheckbox>
          </ElCheckboxGroup>
        </ElFormItem>

        <template v-if="form.targetTypes.includes('MALL_USER')">
          <div class="nt-grid">
            <ElFormItem label="会员等级">
              <ElSelect
                v-model="form.memberLevelId"
                clearable
                placeholder="全部会员"
              >
                <ElOption
                  v-for="level in memberLevels"
                  :key="level.id"
                  :label="level.levelName"
                  :value="level.id"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="会员标签">
              <ElSelect
                v-model="form.memberTagId"
                clearable
                placeholder="全部会员"
              >
                <ElOption
                  v-for="tag in memberTags"
                  :key="tag.id"
                  :label="tag.tagName"
                  :value="tag.id"
                />
              </ElSelect>
            </ElFormItem>
          </div>
          <ElAlert
            v-if="form.mallUserIds?.length"
            class="nt-alert"
            :closable="false"
            type="info"
          >
            已指定 {{ form.mallUserIds.length }} 名会员，优先于等级/标签条件。
          </ElAlert>
        </template>

        <template v-if="form.targetTypes.includes('SYS_USER')">
          <ElFormItem label="角色">
            <ElSelect
              v-model="form.roleIds"
              clearable
              collapse-tags
              filterable
              multiple
              placeholder="全部在职工作人员"
            >
              <ElOption
                v-for="role in roles"
                :key="role.id"
                :label="role.roleName"
                :value="role.id"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="部门">
            <ElTreeSelect
              v-model="form.deptIds"
              check-strictly
              clearable
              multiple
              node-key="id"
              :data="depts"
              :props="{ children: 'children', label: 'name' }"
              placeholder="全部在职工作人员"
              show-checkbox
            />
          </ElFormItem>
          <ElAlert
            v-if="form.staffUserIds?.length"
            class="nt-alert"
            :closable="false"
            type="info"
          >
            已指定
            {{ form.staffUserIds.length }} 名工作人员，优先于角色/部门条件。
          </ElAlert>
        </template>

        <p class="nt-hint">
          不选等级/标签/角色/部门时发给全部目标端；同时选择多个条件时取交集。
        </p>
      </section>
    </ElForm>

    <template #footer>
      <div class="nt-footer">
        <ElButton @click="visible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">
          保存草稿
        </ElButton>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped>
.nt-editor__form {
  padding-bottom: 4px;
}

.nt-section + .nt-section {
  padding-top: 16px;
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.nt-section__title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.nt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.nt-alert {
  margin-bottom: 14px;
}

.nt-hint {
  margin: 0 0 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--nt-text-muted);
}

.nt-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
}

.nt-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (width <= 640px) {
  .nt-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
