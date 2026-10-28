/**
 * 管理端密码强度策略（前端表单提示）。
 *
 * <p>规则与后端 `com.aryn.cloud.common.security.util.PasswordPolicy` 逐字一致，
 * 后端为权威校验；本文件只用于提前提示，不能替代服务端校验。任何一处规则调整
 * （长度、字符类别、弱口令黑名单、错误文案）都必须同步修改两处。</p>
 */

/** 密码最小长度 */
export const PASSWORD_MIN_LENGTH = 8;

/** 密码最大长度（与后端一致；BCrypt 只取前 72 字节，限制 64 字符留出余量） */
export const PASSWORD_MAX_LENGTH = 64;

/** 常见弱口令黑名单（全部小写，比较时统一转小写） */
export const WEAK_PASSWORDS: readonly string[] = [
  '12345678',
  '123456789',
  '1234567890',
  '11111111',
  '88888888',
  'password',
  'password1',
  'passw0rd',
  'admin123',
  'a1234567',
  'abc12345',
  'qwerty123',
  '1qaz2wsx',
  'aa123456',
  'a12345678',
];

const LETTER_PATTERN = /[A-Z]/i;
const DIGIT_PATTERN = /\d/;

/**
 * 校验密码强度。
 *
 * @param password 待校验的明文密码
 * @returns 通过返回 `null`；不通过返回可直接展示给用户的中文错误文案
 */
export function validatePassword(password: string): null | string {
  if (!password || password.trim() === '') {
    return '请输入密码';
  }
  if (
    password.length < PASSWORD_MIN_LENGTH ||
    password.length > PASSWORD_MAX_LENGTH
  ) {
    return '密码长度必须为8到64个字符';
  }
  if (!LETTER_PATTERN.test(password)) {
    return '密码必须包含字母';
  }
  if (!DIGIT_PATTERN.test(password)) {
    return '密码必须包含数字';
  }
  if (WEAK_PASSWORDS.includes(password.toLowerCase())) {
    return '密码过于简单，请更换更复杂的密码';
  }
  return null;
}
