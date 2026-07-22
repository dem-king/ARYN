<script setup lang="ts">
import type { NoticeSavePayload } from '#/api/message/notice';
import type { NoticeRecord } from '#/api/message/types';

import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  createNotice,
  getNoticePage,
  publishNotice,
  revokeNotice,
  updateNotice,
} from '#/api/message/notice';

const loading = ref(false);
const records = ref<NoticeRecord[]>([]);
const total = ref(0);
const dialogOpen = ref(false);
const editingId = ref<string>();
const query = reactive({ current: 1, size: 10, status: '' });
const form = reactive<NoticeSavePayload>({
  category: 'SYSTEM',
  content: '',
  jumpType: 'NONE',
  priority: 'NORMAL',
  summary: '',
  targetTypes: ['MALL_USER'],
  title: '',
});

const statusTone: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
  DRAFT: 'info',
  PUBLISHED: 'success',
  REVOKED: 'warning',
};

async function load() {
  loading.value = true;
  try {
    const page = await getNoticePage(query);
    records.value = page.records;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  editingId.value = undefined;
  Object.assign(form, {
    category: 'SYSTEM',
    content: '',
    jumpPayload: undefined,
    jumpType: 'NONE',
    priority: 'NORMAL',
    summary: '',
    targetTypes: ['MALL_USER'],
    title: '',
  });
}

function openCreate() {
  resetForm();
  dialogOpen.value = true;
}

function openEdit(row: NoticeRecord) {
  editingId.value = row.id;
  Object.assign(form, {
    category: row.category,
    content: row.content,
    jumpPayload: row.jumpPayload,
    jumpType: row.jumpType || 'NONE',
    priority: row.priority,
    summary: row.summary,
    targetTypes: row.targetTypes.split(','),
    title: row.title,
  });
  dialogOpen.value = true;
}

async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题和正文不能为空');
    return;
  }
  await (editingId.value
    ? updateNotice(editingId.value, form)
    : createNotice(form));
  ElMessage.success('草稿已保存');
  dialogOpen.value = false;
  await load();
}

async function publish(row: NoticeRecord) {
  await ElMessageBox.confirm(
    `发布后正文不可修改，将面向 ${row.targetTypes === 'MALL_USER,SYS_USER' ? '会员和工作人员' : row.targetTypes} 分发。`,
    '确认发布',
    { type: 'warning' },
  );
  await publishNotice(row.id);
  ElMessage.success('已提交分发任务');
  await load();
}

async function revoke(row: NoticeRecord) {
  await ElMessageBox.confirm(
    '撤回后普通收件箱将隐藏该通知，审计历史仍保留。',
    '确认撤回',
    {
      type: 'warning',
    },
  );
  await revokeNotice(row.id);
  await load();
}

onMounted(load);
</script>

<template>
  <div class="notice-command min-h-full p-5">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">MESSAGE CONTROL / 通知编排</p>
        <h1>让每一条通知都有明确的受众、状态和去向</h1>
        <p class="hero-copy">
          纯文本发布、受众冻结、异步分发与撤回审计集中在同一工作面板。
        </p>
      </div>
      <ElButton type="primary" size="large" @click="openCreate">
        新建通知草稿
      </ElButton>
    </section>

    <section class="data-panel">
      <div class="toolbar">
        <ElSelect
          v-model="query.status"
          clearable
          placeholder="全部状态"
          class="w-40"
          @change="load"
        >
          <ElOption label="草稿" value="DRAFT" />
          <ElOption label="已发布" value="PUBLISHED" />
          <ElOption label="已撤回" value="REVOKED" />
        </ElSelect>
        <span class="record-count">{{ total }} 条通知</span>
      </div>
      <ElTable v-loading="loading" :data="records" class="message-table">
        <ElTableColumn label="通知" min-width="310">
          <template #default="{ row }">
            <div class="notice-title">{{ row.title }}</div>
            <div class="notice-summary">{{ row.summary || row.content }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="category" label="分类" width="120" />
        <ElTableColumn label="目标端" width="170">
          <template #default="{ row }">
            {{
              row.targetTypes
                .replace('MALL_USER', '会员')
                .replace('SYS_USER', '工作人员')
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="statusTone[row.status]">
              {{ row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-if="row.status === 'DRAFT'"
              link
              type="primary"
              @click="openEdit(row as NoticeRecord)"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="row.status === 'DRAFT'"
              link
              type="success"
              @click="publish(row as NoticeRecord)"
            >
              发布
            </ElButton>
            <ElButton
              v-if="row.status === 'PUBLISHED'"
              link
              type="warning"
              @click="revoke(row as NoticeRecord)"
            >
              撤回
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElPagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        class="mt-5 justify-end"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </section>

    <ElDialog
      v-model="dialogOpen"
      :title="editingId ? '编辑通知草稿' : '新建通知草稿'"
      width="680px"
    >
      <ElForm label-position="top">
        <div class="grid grid-cols-2 gap-x-5">
          <ElFormItem label="标题" class="col-span-2">
            <ElInput v-model="form.title" maxlength="200" show-word-limit />
          </ElFormItem>
          <ElFormItem label="分类">
            <ElInput v-model="form.category" />
          </ElFormItem>
          <ElFormItem label="优先级">
            <ElSelect v-model="form.priority" class="w-full">
              <ElOption label="普通" value="NORMAL" /><ElOption
                label="重要"
                value="HIGH"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="目标端" class="col-span-2">
            <ElCheckboxGroup v-model="form.targetTypes">
              <ElCheckbox value="MALL_USER">会员</ElCheckbox
              ><ElCheckbox value="SYS_USER"> 工作人员 </ElCheckbox>
            </ElCheckboxGroup>
          </ElFormItem>
          <ElFormItem label="摘要" class="col-span-2">
            <ElInput v-model="form.summary" maxlength="500" />
          </ElFormItem>
          <ElFormItem label="纯文本正文" class="col-span-2">
            <ElInput
              v-model="form.content"
              type="textarea"
              :rows="8"
              maxlength="10000"
              show-word-limit
            />
          </ElFormItem>
        </div>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogOpen = false">取消</ElButton
        ><ElButton type="primary" @click="save">保存草稿</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.notice-command {
  --ink: #102a43;
  --signal: #0f766e;

  background:
    radial-gradient(circle at 88% 4%, rgb(15 118 110 / 12%), transparent 26%),
    #f4f7f8;
}

.hero-panel {
  display: flex;
  gap: 32px;
  align-items: end;
  justify-content: space-between;
  padding: 34px;
  color: white;
  background: linear-gradient(125deg, #102a43 0%, #173f5f 62%, #0f766e 100%);
  border-radius: 18px;
  box-shadow: 0 18px 55px rgb(16 42 67 / 20%);
}

.hero-panel h1 {
  max-width: 720px;
  margin: 7px 0 10px;
  font-family: 'Noto Serif SC', serif;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: #5eead4;
  letter-spacing: 0.18em;
}

.hero-copy {
  color: rgb(255 255 255 / 68%);
}

.data-panel {
  padding: 22px;
  margin-top: 18px;
  background: rgb(255 255 255 / 92%);
  border: 1px solid rgb(15 118 110 / 12%);
  border-radius: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.record-count {
  font-size: 13px;
  color: #627d98;
}

.notice-title {
  font-weight: 700;
  color: var(--ink);
}

.notice-summary {
  max-width: 520px;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: #829ab1;
  white-space: nowrap;
}
</style>
