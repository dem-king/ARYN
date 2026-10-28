import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const loginSource = readFileSync(
  resolve(appRoot, 'src/views/_core/authentication/login.vue'),
  'utf8',
);
const authStoreSource = readFileSync(
  resolve(appRoot, 'src/store/auth.ts'),
  'utf8',
);
const authApiSource = readFileSync(
  resolve(appRoot, 'src/api/core/auth.ts'),
  'utf8',
);
const requestSource = readFileSync(
  resolve(appRoot, 'src/api/request.ts'),
  'utf8',
);
const envSource = readFileSync(resolve(appRoot, '.env'), 'utf8');
const productionEnvSource = readFileSync(
  resolve(appRoot, '.env.production'),
  'utf8',
);

describe('login security contract', () => {
  it('does not publish or auto-fill built-in accounts', () => {
    expect(loginSource).not.toContain('system/123456');
    expect(loginSource).not.toContain('admin/123456');
    expect(loginSource).not.toContain("password: '123456'");
    expect(loginSource).not.toContain("username: 'system'");
  });

  it('does not use a shared AES key for account passwords', () => {
    expect(authStoreSource).not.toContain("from '#/utils/aes'");
    expect(authStoreSource).not.toContain('VITE_PWD_PRIVICE_KEY');
    expect(envSource).not.toContain('VITE_PWD_PRIVICE_KEY');
  });

  it('uses the same-origin API proxy in production', () => {
    expect(productionEnvSource).toContain('VITE_GLOB_API_URL=/api');
    expect(productionEnvSource).not.toContain('VITE_GLOB_API_URL=http://');
  });

  it('uses the authenticated request client for logout in both boot and cloud modes', () => {
    const logoutApi =
      /export async function logoutApi[\s\S]*?\n\}/.exec(authApiSource)?.[0] ??
      '';
    // 裸 baseRequestClient 不带 satoken、不做 boot 路径改写，服务端无法撤销会话
    expect(logoutApi).toContain("requestClient.delete('/auth/token/logout'");
    expect(logoutApi).toContain('isSwitchTenant: false');
    expect(logoutApi).not.toContain('baseRequestClient');
    // refreshTokenApi 仍使用裸客户端：刷新请求不应携带已过期的 token
    const refreshApi =
      /export async function refreshTokenApi[\s\S]*?\n\}/.exec(
        authApiSource,
      )?.[0] ?? '';
    expect(refreshApi).toContain('baseRequestClient.post');
  });

  it('does not print raw request errors (may contain password or satoken)', () => {
    expect(authStoreSource).not.toContain('console.error(error)');
    expect(authStoreSource).toContain('getSafeErrorMessage(error)');
    expect(loginSource).not.toContain('console.error(error)');
  });

  it('does not send internal request-control flags as HTTP headers', () => {
    expect(requestSource).toContain('delete config.headers.isToken');
    expect(requestSource).toContain('delete config.headers.isSwitchTenant');
  });

  it('posts the SMS login code as a form field so the backend can verify it', () => {
    const mobileLoginApi =
      /export async function mobileLoginApi[\s\S]*?\n\}/.exec(
        authApiSource,
      )?.[0] ?? '';

    expect(mobileLoginApi).toContain('/auth/token/sms/login');
    // 后端 `SmsValidateCodeFilter` 从 request parameter 读 code 并与 Redis
    // `sms_code_<phone>` 比对；用 JSON body 时 phone/code 都绑不到（历史上该入口因此
    // 不可用），也不会有验证码校验（曾等于「知道管理员手机号即可登录」）。
    expect(mobileLoginApi).toContain(
      "'Content-Type': 'application/x-www-form-urlencoded'",
    );
  });
});
