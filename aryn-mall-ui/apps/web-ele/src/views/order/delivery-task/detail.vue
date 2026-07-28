<script setup lang="ts">
import type { DeliveryTask } from '#/api/order/delivery-task';

import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ArrowLeft, Refresh, UserFilled } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElImage,
  ElMessage,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus';

import {
  getDeliveryTask,
  isEvidenceAccessExpired,
  refreshEvidenceAccess,
} from '#/api/order/delivery-task';

import AssignDialog from './components/AssignDialog.vue';
import ExceptionDialog from './components/ExceptionDialog.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const task = ref<DeliveryTask>();
const assignVisible = ref(false);
const assignMode = ref<'ASSIGN' | 'REASSIGN'>('ASSIGN');
const exceptionVisible = ref(false);
const exceptionAction = ref<'CLOSE' | 'CONFIRM_RETURN' | 'RETURN_PENDING'>(
  'CLOSE',
);
const evidenceAccess = ref<
  Record<string, { accessUrl: string; expiresAt: string }>
>({});
const taskId = computed(() => String(route.query.id || ''));
const statusLabels: Record<string, string> = {
  ASSIGNED: '待配货',
  CLOSED: '已关闭',
  DELIVERED: '已送达',
  DELIVERING: '配送中',
  EXCEPTION: '异常',
  PICKING: '配货中',
  RETURN_PENDING: '待退回',
  WAITING_ASSIGNMENT: '待派单',
};

const load = async () => {
  if (!taskId.value) return;
  loading.value = true;
  try {
    task.value = await getDeliveryTask(taskId.value);
  } finally {
    loading.value = false;
  }
};
const openAssign = (mode: 'ASSIGN' | 'REASSIGN') => {
  assignMode.value = mode;
  assignVisible.value = true;
};
const openException = (action: typeof exceptionAction.value) => {
  exceptionAction.value = action;
  exceptionVisible.value = true;
};
const loadEvidence = async (evidenceId: string, force = false) => {
  const cached = evidenceAccess.value[evidenceId];
  if (!force && cached && !isEvidenceAccessExpired(cached.expiresAt)) return;
  const access = await refreshEvidenceAccess(taskId.value, evidenceId);
  evidenceAccess.value[evidenceId] = access;
  if (force) ElMessage.success('凭证访问地址已刷新');
};

load();
</script>

<template>
  <div class="hx-layout-container" v-loading="loading">
    <div
      class="hx-layout-container-auto hx-layout-container-view detail-page"
      v-if="task"
    >
      <div class="detail-heading">
        <div class="heading-main">
          <ElButton text :icon="ArrowLeft" @click="router.back()"
            >返回</ElButton
          >
          <div>
            <div class="eyebrow">{{ task.taskNo }}</div>
            <h2>订单 {{ task.orderNo }} 的配送履约</h2>
          </div>
          <ElTag size="large">{{
            statusLabels[task.status] || task.status
          }}</ElTag>
        </div>
        <div class="actions">
          <ElButton :icon="Refresh" @click="load">刷新</ElButton>
          <ElButton
            v-if="task.status === 'WAITING_ASSIGNMENT'"
            type="primary"
            :icon="UserFilled"
            v-access:code="'order:delivery:assign'"
            @click="openAssign('ASSIGN')"
            >派单</ElButton
          >
          <ElButton
            v-if="['ASSIGNED', 'PICKING', 'EXCEPTION'].includes(task.status)"
            type="warning"
            v-access:code="'order:delivery:reassign'"
            @click="openAssign('REASSIGN')"
            >改派</ElButton
          >
          <ElButton
            v-if="
              ['DELIVERING', 'DELIVERED', 'EXCEPTION'].includes(task.status)
            "
            v-access:code="'order:delivery:return'"
            @click="openException('RETURN_PENDING')"
            >标记待退回</ElButton
          >
          <ElButton
            v-if="task.status === 'RETURN_PENDING'"
            type="primary"
            v-access:code="'order:delivery:return'"
            @click="openException('CONFIRM_RETURN')"
            >确认退回</ElButton
          >
          <ElButton
            v-if="
              [
                'WAITING_ASSIGNMENT',
                'ASSIGNED',
                'PICKING',
                'EXCEPTION',
              ].includes(task.status)
            "
            type="danger"
            plain
            v-access:code="'order:delivery:exception'"
            @click="openException('CLOSE')"
            >关闭任务</ElButton
          >
        </div>
      </div>

      <section class="section-block">
        <h3>履约概览</h3>
        <ElDescriptions :column="3" border>
          <ElDescriptionsItem label="配送员">{{
            task.assigneeName || '尚未派单'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="联系电话">{{
            task.assigneeMobile || '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="配送尝试"
            >第 {{ task.attemptNo }} 次</ElDescriptionsItem
          >
          <ElDescriptionsItem label="开始配货">{{
            task.pickingStartedAt || '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="取货出发">{{
            task.pickedUpAt || '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="送达时间">{{
            task.deliveredAt || '—'
          }}</ElDescriptionsItem>
          <ElDescriptionsItem label="异常说明" :span="3">{{
            task.exceptionSummary || '无'
          }}</ElDescriptionsItem>
        </ElDescriptions>
      </section>

      <section class="section-block">
        <div class="section-heading">
          <h3>履约凭证</h3>
          <span>图片地址短期有效，过期后可单张刷新。</span>
        </div>
        <ElEmpty
          v-if="!task.evidences?.length"
          description="暂未上传履约凭证"
          :image-size="72"
        />
        <div v-else class="evidence-grid">
          <article
            v-for="evidence in task.evidences"
            :key="evidence.id"
            class="evidence-item"
          >
            <ElImage
              v-if="evidenceAccess[evidence.id]?.accessUrl"
              :src="evidenceAccess[evidence.id]?.accessUrl"
              :preview-src-list="[evidenceAccess[evidence.id]!.accessUrl]"
              fit="cover"
              class="evidence-image"
            />
            <button
              v-else
              class="evidence-placeholder"
              type="button"
              @click="loadEvidence(evidence.id)"
            >
              加载凭证
            </button>
            <div class="evidence-meta">
              <span>{{
                evidence.evidenceType === 'DELIVERED' ? '送达凭证' : '异常凭证'
              }}</span>
              <ElButton
                link
                type="primary"
                @click="loadEvidence(evidence.id, true)"
                >刷新地址</ElButton
              >
            </div>
          </article>
        </div>
      </section>

      <section class="section-block">
        <h3>操作记录</h3>
        <ElEmpty
          v-if="!task.logs?.length"
          description="暂无操作记录"
          :image-size="72"
        />
        <ElTimeline v-else>
          <ElTimelineItem
            v-for="log in task.logs"
            :key="log.id"
            :timestamp="log.createTime"
          >
            <strong>{{ log.action }}</strong>
            <span class="log-transition"
              >{{ log.fromStatus }} → {{ log.toStatus }}</span
            >
            <div v-if="log.description" class="muted">
              {{ log.description }}
            </div>
          </ElTimelineItem>
        </ElTimeline>
      </section>

      <AssignDialog
        v-model:visible="assignVisible"
        :mode="assignMode"
        :task="task"
        @success="load"
      />
      <ExceptionDialog
        v-model:visible="exceptionVisible"
        :action="exceptionAction"
        :task="task"
        @success="load"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.detail-page {
  gap: 24px;
}
.detail-heading,
.heading-main,
.actions,
.section-heading,
.evidence-meta {
  display: flex;
  align-items: center;
}
.detail-heading {
  justify-content: space-between;
  gap: 20px;
}
.heading-main {
  gap: 14px;
}
.heading-main h2 {
  margin: 2px 0 0;
  font-size: 20px;
}
.eyebrow,
.muted,
.section-heading span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.actions {
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}
.section-block {
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.section-block h3 {
  margin: 0 0 16px;
  font-size: 16px;
}
.section-heading {
  justify-content: space-between;
}
.evidence-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}
.evidence-item {
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}
.evidence-image,
.evidence-placeholder {
  width: 100%;
  height: 150px;
}
.evidence-placeholder {
  border: 0;
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
  cursor: pointer;
}
.evidence-meta {
  justify-content: space-between;
  padding: 8px 12px;
}
.log-transition {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
}
</style>
