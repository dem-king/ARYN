import { requestClient } from '#/api/request';

/**
 * 登录设备 / 最近登录记录 API（三期契约 §2）。
 *
 * <p>统一走 {@link requestClient}：请求拦截器会自动注入 satoken、补充
 * Accept-Language，并按 `VITE_OPEN_BOOT` 做 boot/cloud 双模式路径改写
 * （boot 下 `/auth/session/devices` → `/boot/session/devices`，禁止在
 * 本文件硬编码 `/boot`）。</p>
 */
export namespace SessionDevicesApi {
  /** 活跃会话（契约 §2.1）。`id` 是服务端下发的 token hash，前端只透传、不拼接。 */
  export interface SessionDevice {
    browser?: string;
    /** 是否为当前设备（当前设备不允许从列表下线，需走退出登录） */
    current?: boolean;
    deviceType?: string;
    id: string;
    location?: string;
    loginTime?: string;
    os?: string;
  }

  /** 我的最近登录记录（契约 §2.3）。 */
  export interface LoginLog {
    browser?: string;
    createTime?: string;
    /** 脱敏或省略，前端不展示完整 IP */
    ipAddr?: string;
    location?: string;
    msg?: string;
    os?: string;
    /** 0=失败 1=成功 */
    status?: number | string;
  }

  export interface LoginLogPage {
    current?: number;
    pages?: number;
    records?: LoginLog[];
    size?: number;
    total?: number;
  }
}

/** 当前登录账号的活跃会话列表（current 在前，其余按登录时间倒序由服务端排好）。 */
export async function getMyDevices() {
  return requestClient.get<SessionDevicesApi.SessionDevice[]>(
    '/auth/session/devices',
  );
}

/**
 * 下线指定会话。
 *
 * <p>`id` 直接使用列表下发的 token hash，不拼 token；当前设备的 `id` 后端会拒绝，
 * 前端也不会渲染该操作。</p>
 */
export async function removeDevice(id: string) {
  return requestClient.delete(
    `/auth/session/devices/${encodeURIComponent(id)}`,
  );
}

/**
 * 当前账号最近登录记录（管理端固定 10 条）。
 *
 * <p>分页参数沿用仓库 `PageArgumentResolver` 的约定：`current` / `size`
 * （不是 pageNum/pageSize）；后端单页上限 20。</p>
 */
export async function getMyLoginLogs(params: {
  current?: number;
  size?: number;
}) {
  return requestClient.get<SessionDevicesApi.LoginLogPage>(
    '/upms/sysloginlog/my',
    { params },
  );
}
