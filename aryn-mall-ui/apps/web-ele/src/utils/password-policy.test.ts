import { describe, expect, it } from 'vitest';

import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  validatePassword,
  WEAK_PASSWORDS,
} from './password-policy';

describe('validatePassword', () => {
  it('accepts normal strong passwords', () => {
    expect(validatePassword('Aryn2026Pass')).toBeNull();
    expect(validatePassword('Ship#2026chandler')).toBeNull();
    expect(validatePassword('a1b2c3d4')).toBeNull();
  });

  it('rejects empty and whitespace-only passwords', () => {
    expect(validatePassword('')).toBe('请输入密码');
    expect(validatePassword('   ')).toBe('请输入密码');
  });

  it('enforces the 8-64 length range', () => {
    expect(PASSWORD_MIN_LENGTH).toBe(8);
    expect(PASSWORD_MAX_LENGTH).toBe(64);
    expect(validatePassword('Ab1')).toBe('密码长度必须为8到64个字符');
    expect(validatePassword(`${'a'.repeat(PASSWORD_MAX_LENGTH)}1`)).toBe(
      '密码长度必须为8到64个字符',
    );
    // 恰好 8 位与恰好 64 位都应通过
    expect(validatePassword('Aryn2026')).toBeNull();
    expect(validatePassword(`${'a'.repeat(63)}1`)).toBeNull();
  });

  it('requires at least one English letter', () => {
    expect(validatePassword('1234567890')).toBe('密码必须包含字母');
  });

  it('requires at least one digit', () => {
    expect(validatePassword('abcdefghij')).toBe('密码必须包含数字');
  });

  it('rejects every password in the weak-password blacklist', () => {
    for (const weakPassword of WEAK_PASSWORDS) {
      expect(
        validatePassword(weakPassword),
        `weak password should be rejected: ${weakPassword}`,
      ).not.toBeNull();
    }
  });

  it('reports weak passwords that pass the character checks explicitly', () => {
    // 同时包含字母与数字的弱口令会走到黑名单校验，应返回“过于简单”文案
    for (const weakPassword of [
      'password1',
      'passw0rd',
      'admin123',
      'a1234567',
      'abc12345',
      'qwerty123',
      '1qaz2wsx',
      'aa123456',
      'a12345678',
    ]) {
      expect(validatePassword(weakPassword), weakPassword).toBe(
        '密码过于简单，请更换更复杂的密码',
      );
    }
  });

  it('rejects weak passwords case-insensitively', () => {
    expect(validatePassword('PassWord1')).toBe(
      '密码过于简单，请更换更复杂的密码',
    );
    expect(validatePassword('ADMIN123')).toBe(
      '密码过于简单，请更换更复杂的密码',
    );
    expect(validatePassword('Aa123456')).toBe(
      '密码过于简单，请更换更复杂的密码',
    );
    expect(validatePassword('Qwerty123')).toBe(
      '密码过于简单，请更换更复杂的密码',
    );
  });
});
