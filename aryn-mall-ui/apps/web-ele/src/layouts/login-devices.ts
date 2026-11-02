import type { SessionDevicesApi } from '#/api/core/session-devices';

import { computed, ref } from 'vue';

import dayjs from 'dayjs';

/**
 * 「登录设备」弹窗的纯数据整形与展示逻辑（便于单测，不依赖组件渲染）。
 *
 * 契约见《三期实现契约》§2 / §3.1：列表字段全部来自服务端，前端不拼 token；
 * 当前设备不允许下线（后端也会拒绝），页面只对非当前设备渲染「下线」。
 */

/** 最多展示的最近登录记录条数（契约 §3.1 固定 10 条）。 */
export const LOGIN_LOG_LIMIT = 10;

/** 设备展示名：浏览器 · 系统；缺失片段自动省略，全空时返回空串。 */
export function formatLoginDeviceLabel(
  device: null | undefined | { browser?: string; os?: string },
): string {
  if (!device) return '';
  const browser =
    typeof device.browser === 'string' ? device.browser.trim() : '';
  const os = typeof device.os === 'string' ? device.os.trim() : '';
  return [browser, os].filter(Boolean).join(' · ');
}

/** 登录时间展示：完整 `YYYY-MM-DD HH:mm`（会话与登录记录都要能看出具体日期）。 */
export function formatLoginTime(
  value: null | number | string | undefined,
): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm') : '';
}

/** 清洗活跃会话列表：保证是数组、字段安全；`id` 缺失的记录保留展示但不给下线入口。 */
export function normalizeLoginDevices(
  raw: unknown,
): SessionDevicesApi.SessionDevice[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((item) => item && typeof item === 'object')
    .map((item) => {
      const device = item as Record<string, unknown>;
      return {
        browser:
          typeof device.browser === 'string' ? device.browser : undefined,
        current: device.current === true,
        deviceType:
          typeof device.deviceType === 'string' ? device.deviceType : undefined,
        id: typeof device.id === 'string' ? device.id : '',
        location:
          typeof device.location === 'string' ? device.location : undefined,
        loginTime:
          typeof device.loginTime === 'string' ? device.loginTime : undefined,
        os: typeof device.os === 'string' ? device.os : undefined,
      } satisfies SessionDevicesApi.SessionDevice;
    });
}

/** 是否存在「非当前」会话；为空时展示单点策略说明（契约 §3.1）。 */
export function hasOtherLoginDevices(
  devices: SessionDevicesApi.SessionDevice[],
): boolean {
  return devices.some((device) => device.current !== true);
}

/** 是否还有可下线的会话（非当前且带服务端下发的 hash id）。 */
export function hasKickableDevice(
  devices: SessionDevicesApi.SessionDevice[],
): boolean {
  return devices.some(
    (device) => device.current !== true && Boolean(device.id),
  );
}

/** 登录记录结果：0=失败 1=成功（数字/字符串都兼容）。 */
export function isLoginLogSuccess(
  status: null | number | string | undefined,
): boolean {
  return status === 1 || status === '1';
}

/** 清洗最近登录记录：只取前 10 条，字段安全。 */
export function normalizeLoginLogs(raw: unknown): SessionDevicesApi.LoginLog[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((item) => item && typeof item === 'object')
    .slice(0, LOGIN_LOG_LIMIT)
    .map((item) => {
      const log = item as Record<string, unknown>;
      return {
        browser: typeof log.browser === 'string' ? log.browser : undefined,
        createTime:
          typeof log.createTime === 'string' ? log.createTime : undefined,
        // ipAddr 契约为脱敏/省略字段：前端不展示，仅保留类型以免误用
        ipAddr: typeof log.ipAddr === 'string' ? log.ipAddr : undefined,
        location: typeof log.location === 'string' ? log.location : undefined,
        msg: typeof log.msg === 'string' ? log.msg : undefined,
        os: typeof log.os === 'string' ? log.os : undefined,
        status:
          typeof log.status === 'number' || typeof log.status === 'string'
            ? log.status
            : undefined,
      } satisfies SessionDevicesApi.LoginLog;
    });
}

/**
 * 「登录设备」弹窗状态控制器（与渲染解耦，便于单测加载/空态/下线确认流程）。
 *
 * 组件负责把 Element Plus 的确认框/提示注入进来，控制器只做数据与流程编排；
 * `confirmKick` 返回 false（用户取消）时不得发起下线请求。
 */
export interface LoginDevicesControllerDeps {
  /** 二次确认；返回 true 才继续下线 */
  confirmKick: (device: SessionDevicesApi.SessionDevice) => Promise<boolean>;
  getDevices: () => Promise<unknown>;
  getLogs: (params: { current: number; size: number }) => Promise<unknown>;
  notifyKickError: (message?: string) => void;
  notifyKickSuccess: () => void;
  removeDevice: (id: string) => Promise<unknown>;
}

export function createLoginDevicesController(deps: LoginDevicesControllerDeps) {
  const devices = ref<SessionDevicesApi.SessionDevice[]>([]);
  const logs = ref<SessionDevicesApi.LoginLog[]>([]);
  const devicesLoading = ref(false);
  const logsLoading = ref(false);
  const devicesError = ref(false);
  const logsError = ref(false);
  const kickingId = ref('');

  /** 无其他会话时才展示单点策略说明（有多个会话说明策略与现状不一致，避免误导）。 */
  const showSinglePointNotice = computed(
    () =>
      !devicesLoading.value &&
      !devicesError.value &&
      !hasOtherLoginDevices(devices.value),
  );

  async function loadDevices() {
    devicesLoading.value = true;
    devicesError.value = false;
    try {
      devices.value = normalizeLoginDevices(await deps.getDevices());
    } catch {
      // 错误只影响本区域：清空列表并给可重试提示，不阻塞最近登录记录
      devices.value = [];
      devicesError.value = true;
    } finally {
      devicesLoading.value = false;
    }
  }

  async function loadLogs() {
    logsLoading.value = true;
    logsError.value = false;
    try {
      const page = (await deps.getLogs({
        current: 1,
        size: LOGIN_LOG_LIMIT,
      })) as null | { records?: unknown };
      logs.value = normalizeLoginLogs(page?.records);
    } catch {
      logs.value = [];
      logsError.value = true;
    } finally {
      logsLoading.value = false;
    }
  }

  function loadAll() {
    void loadDevices();
    void loadLogs();
  }

  async function kickDevice(device: SessionDevicesApi.SessionDevice) {
    // 当前设备/缺 id：直接忽略（后端同样会拒绝当前设备下线）
    if (!device.id || device.current) {
      return;
    }
    const confirmed = await deps.confirmKick(device).catch(() => false);
    if (!confirmed) {
      return;
    }
    kickingId.value = device.id;
    try {
      await deps.removeDevice(device.id);
      deps.notifyKickSuccess();
      // 成功后重新拉取，确保列表与在线记录一致
      await loadDevices();
    } catch (error) {
      // 后端拒绝（如当前设备）：只提示脱敏 msg，不打印异常对象
      deps.notifyKickError((error as { msg?: string })?.msg);
    } finally {
      kickingId.value = '';
    }
  }

  return {
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
  };
}
