import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const appRoot = resolve(process.cwd(), 'apps/web-ele');
const codeLoginSource = readFileSync(
  resolve(appRoot, 'src/views/_core/authentication/code-login.vue'),
  'utf8',
);
const smsApiSource = readFileSync(
  resolve(appRoot, 'src/api/upms/sms.ts'),
  'utf8',
);
const authStoreSource = readFileSync(
  resolve(appRoot, 'src/store/auth.ts'),
  'utf8',
);

/** 截取源码片段；找不到起止标记时返回空串，让断言失败而不是误判通过 */
function sliceBetween(source: string, startMarker: string, endMarker: string) {
  const start = source.indexOf(startMarker);
  const end =
    start === -1 ? -1 : source.indexOf(endMarker, start + startMarker.length);
  if (start === -1 || end === -1) {
    return '';
  }
  return source.slice(start, end);
}

const handleSendCodeBlock = sliceBetween(
  codeLoginSource,
  'handleSendCode: async () => {',
  "placeholder: $t('authentication.code'),",
);
const requestCaptchaBlock = sliceBetween(
  codeLoginSource,
  'function requestCaptchaVerification(phone: string) {',
  'async function verifySuccess(',
);
const verifySuccessBlock = sliceBetween(
  codeLoginSource,
  'async function verifySuccess(',
  '// 关闭图形验证码弹窗',
);
const cancelWatchBlock = sliceBetween(
  codeLoginSource,
  '() => verifyRef.value?.clickShow',
  'const formSchema = computed',
);
const sendCodeBlock = sliceBetween(
  smsApiSource,
  'export async function sendCode(',
  '\n}',
);
const sendSmsCodeBlock = sliceBetween(
  authStoreSource,
  'async function sendSmsCode(',
  'async function logout(',
);

describe('sms code login captcha contract', () => {
  it('code-login 复用 verifition 的 Verify 组件并以滑动拼图弹窗展示', () => {
    expect(codeLoginSource).toContain(
      "import Verify from '#/components/verifition/index.vue';",
    );
    expect(codeLoginSource).toContain('<Verify');
    expect(codeLoginSource).toContain(':captcha-type="captchaType"');
    expect(codeLoginSource).toContain("ref('blockPuzzle')");
    expect(codeLoginSource).toContain('@success="verifySuccess"');
    // 与 login.vue 同款：pop 弹窗 teleport 到 body，避免被登录页布局裁剪
    expect(codeLoginSource).toContain('<teleport to="body">');

    expect(requestCaptchaBlock).not.toBe('');
    expect(requestCaptchaBlock).toContain('verify.show()');
  });

  it('api 层用 requestClient.get 的 params 传 captchaVerification，为空则不传', () => {
    expect(sendCodeBlock).not.toBe('');
    expect(sendCodeBlock).toMatch(/captchaVerification\?:\s*string/);
    expect(sendCodeBlock).toContain('requestClient.get(');
    expect(sendCodeBlock).toContain('{ params }');
    // 不能手工把校验串拼进 URL，否则 axios 会二次编码且为空时也会带空参数
    expect(sendCodeBlock).not.toContain('?captchaVerification=');
    expect(sendCodeBlock).toContain('if (captchaVerification) {');
  });

  it('store 透传 captchaVerification 到发送短信 api', () => {
    expect(sendSmsCodeBlock).not.toBe('');
    expect(sendSmsCodeBlock).toMatch(/captchaVerification\?:\s*string/);
    expect(sendSmsCodeBlock).toContain(
      'sendCode(type, phone, captchaVerification)',
    );
  });

  it('点击获取验证码只拉起图形验证，验证成功后才带 captchaVerification 发送短信', () => {
    // 按钮回调里只启动图形验证，绝不直接调短信接口
    expect(handleSendCodeBlock).not.toBe('');
    expect(handleSendCodeBlock).toContain(
      'await requestCaptchaVerification(phoneNumber);',
    );
    expect(handleSendCodeBlock).not.toContain('sendSmsCode(');

    // 图形验证 success 事件携带 captchaVerification 才真正发送
    expect(verifySuccessBlock).not.toBe('');
    expect(verifySuccessBlock).toMatch(
      /await authStore\.sendSmsCode\(\s*'login',\s*pending\.phone,\s*captchaVerification,?\s*\)/,
    );
    // 先 await 短信发送，成功后才 resolve 挂起 Promise
    expect(
      verifySuccessBlock.indexOf('await authStore.sendSmsCode'),
    ).toBeLessThan(verifySuccessBlock.indexOf('pending.resolve()'));
    // 顺序上也保证「先图形验证、后短信发送」
    expect(codeLoginSource.indexOf('verify.show()')).toBeLessThan(
      codeLoginSource.indexOf('await authStore.sendSmsCode'),
    );
  });

  it('取消/未完成图形验证或发送失败时不启动倒计时', () => {
    // handleSendCode 返回挂起 Promise：PinInput 只有 await 返回后才开始 60s 倒计时
    expect(codeLoginSource).toContain(
      'new Promise<void>((resolve, reject) => {',
    );
    expect(codeLoginSource).toContain('pendingSendCode = pending;');

    // 取消弹窗不触发 success，通过 clickShow true -> false 感知并 reject 挂起 Promise
    expect(cancelWatchBlock).not.toBe('');
    expect(cancelWatchBlock).toContain(
      'previousVisible === true && visible === false',
    );
    expect(cancelWatchBlock).toContain('rejectPendingSendCode');

    // 发送失败走 reject 分支，不会调用 resolve
    expect(verifySuccessBlock).toContain('pending.reject(error)');

    // 短信发送中再次点击直接拒绝，避免并发下发短信/启动多条倒计时链
    expect(requestCaptchaBlock).toContain('if (sendingSms) {');
    expect(verifySuccessBlock).toContain('sendingSms = true;');
    expect(verifySuccessBlock).toContain('sendingSms = false;');
  });
});
