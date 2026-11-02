<script lang="ts" setup>
import type { SessionDevicesApi } from '#/api/core/session-devices';

import { watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  getMyDevices,
  getMyLoginLogs,
  removeDevice,
} from '#/api/core/session-devices';
import { $t } from '#/locales';

import {
  createLoginDevicesController,
  formatLoginDeviceLabel,
  formatLoginTime,
  isLoginLogSuccess,
} from './login-devices';

defineOptions({ name: 'LoginDevicesModal' });

/**
 * 登录设备弹窗（三期契约 §3.1）。
 *
 * - 活跃会话：设备/系统、地点、登录时间、当前标记；非当前行「下线」（二次确认 +
 *   DELETE + 刷新列表）；当前行不渲染下线入口（后端同样会拒绝，退出登录走既有 logout）。
 * - 无其他会话时展示单点策略说明，如实告知「新设备登录会顶掉本设备」。
 * - 最近登录记录：固定 10 条（时间 / 结果 / 浏览器+系统 / 地点）。
 * - 两块数据独立加载：任一块失败只影响自身区域（错误提示 + 重试），不阻塞另一块和页面。
 *
 * <p>会话列表 `id` 是服务端下发的 token hash，前端原样透传给下线接口，不拼 token。</p>
 */
const [Modal, modalApi] = useVbenModal();
const isOpen = modalApi.useStore((state) => state.isOpen);

const {
  devices,
  devicesError,
  devicesLoading,
  kickDevice,
  kickingId,
  loadAll,
  loadDevices,
  loadLogs,
  logs,
  logsError,
  logsLoading,
  showSinglePointNotice,
} = createLoginDevicesController({
  confirmKick: () =>
    ElMessageBox.confirm(
      $t('page.loginDevices.kickConfirm'),
      $t('page.loginDevices.kickTitle'),
      {
        cancelButtonText: $t('common.cancel'),
        confirmButtonText: $t('common.confirm'),
        type: 'warning',
      },
    )
      .then(() => true)
      .catch(() => false),
  getDevices: getMyDevices,
  getLogs: getMyLoginLogs,
  notifyKickError: (message) => {
    ElMessage.error(message || $t('page.loginDevices.kickFailed'));
  },
  notifyKickSuccess: () => {
    ElMessage.success($t('page.loginDevices.kickSuccess'));
  },
  removeDevice,
});

// 打开时才拉取（列表/记录每次都取最新，关闭不保留旧数据）
watch(
  isOpen,
  (open) => {
    if (open) {
      loadAll();
    }
  },
  { immediate: true },
);

function handleKick(row: Record<string, unknown>) {
  return kickDevice(row as unknown as SessionDevicesApi.SessionDevice);
}

function handleClose() {
  modalApi.close();
}
</script>

<template>
  <div>
    <Modal
      :footer="false"
      :fullscreen-button="false"
      :title="$t('page.loginDevices.title')"
      class="sm:w-[760px]"
    >
      <div class="login-devices">
        <section class="login-devices__section">
          <h4 class="login-devices__heading">
            {{ $t('page.loginDevices.activeTitle') }}
          </h4>
          <div v-if="devicesError" class="login-devices__error">
            <span>{{ $t('page.loginDevices.loadFailed') }}</span>
            <ElButton link type="primary" @click="loadDevices">
              {{ $t('page.loginDevices.retry') }}
            </ElButton>
          </div>
          <ElTable
            v-else
            v-loading="devicesLoading"
            :data="devices"
            border
            size="small"
          >
            <ElTableColumn
              :label="$t('page.loginDevices.device')"
              min-width="190"
            >
              <template #default="{ row }">
                {{ formatLoginDeviceLabel(row) || '-' }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('page.loginDevices.location')"
              min-width="110"
            >
              <template #default="{ row }">
                {{ row.location || '-' }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('page.loginDevices.loginTime')"
              min-width="130"
            >
              <template #default="{ row }">
                {{ formatLoginTime(row.loginTime) || '-' }}
              </template>
            </ElTableColumn>
            <ElTableColumn :label="$t('page.loginDevices.status')" width="100">
              <template #default="{ row }">
                <ElTag v-if="row.current" size="small" type="success">
                  {{ $t('page.loginDevices.current') }}
                </ElTag>
                <span v-else class="login-devices__muted">-</span>
              </template>
            </ElTableColumn>
            <ElTableColumn :label="$t('page.loginDevices.action')" width="100">
              <template #default="{ row }">
                <!-- 当前设备不允许从列表下线（后端也会拒绝），不渲染入口 -->
                <ElButton
                  v-if="!row.current && row.id"
                  link
                  :loading="kickingId === row.id"
                  type="danger"
                  @click="handleKick(row)"
                >
                  {{ $t('page.loginDevices.kick') }}
                </ElButton>
                <span v-else class="login-devices__muted">-</span>
              </template>
            </ElTableColumn>
            <template #empty>
              <span class="login-devices__muted">
                {{ $t('page.loginDevices.noDevices') }}
              </span>
            </template>
          </ElTable>
          <p v-if="showSinglePointNotice" class="login-devices__notice">
            {{ $t('page.loginDevices.singlePointNotice') }}
          </p>
        </section>

        <section class="login-devices__section">
          <h4 class="login-devices__heading">
            {{ $t('page.loginDevices.recentTitle') }}
          </h4>
          <div v-if="logsError" class="login-devices__error">
            <span>{{ $t('page.loginDevices.loadFailed') }}</span>
            <ElButton link type="primary" @click="loadLogs">
              {{ $t('page.loginDevices.retry') }}
            </ElButton>
          </div>
          <ElTable
            v-else
            v-loading="logsLoading"
            :data="logs"
            border
            size="small"
          >
            <ElTableColumn
              :label="$t('page.loginDevices.time')"
              min-width="140"
            >
              <template #default="{ row }">
                {{ formatLoginTime(row.createTime) || '-' }}
              </template>
            </ElTableColumn>
            <ElTableColumn :label="$t('page.loginDevices.result')" width="80">
              <template #default="{ row }">
                <ElTag
                  size="small"
                  :type="isLoginLogSuccess(row.status) ? 'success' : 'danger'"
                >
                  {{
                    isLoginLogSuccess(row.status)
                      ? $t('page.loginDevices.success')
                      : $t('page.loginDevices.failed')
                  }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('page.loginDevices.deviceInfo')"
              min-width="170"
            >
              <template #default="{ row }">
                {{ formatLoginDeviceLabel(row) || '-' }}
              </template>
            </ElTableColumn>
            <ElTableColumn
              :label="$t('page.loginDevices.location')"
              min-width="110"
            >
              <template #default="{ row }">
                {{ row.location || '-' }}
              </template>
            </ElTableColumn>
            <template #empty>
              <span class="login-devices__muted">
                {{ $t('page.loginDevices.noLogs') }}
              </span>
            </template>
          </ElTable>
        </section>

        <div class="login-devices__footer">
          <ElButton @click="handleClose">
            {{ $t('page.loginDevices.close') }}
          </ElButton>
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.login-devices {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.login-devices__heading {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: hsl(var(--foreground));
}

.login-devices__error {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border-radius: 8px;
}

.login-devices__notice {
  padding: 8px 12px;
  margin-top: 10px;
  font-size: 12.5px;
  line-height: 1.6;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted) / 60%);
  border-radius: 8px;
}

.login-devices__muted {
  color: hsl(var(--muted-foreground));
}

.login-devices__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
