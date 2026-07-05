import CryptoJS from 'crypto-js';

/**
 * AES 加解密工具
 *
 * 安全说明：
 * - 使用 CBC 模式（替代不安全的 ECB）
 * - IV 使用固定值（与密钥相同），虽非最佳实践但比 ECB 更安全
 * - 密钥应通过环境变量注入，不应硬编码在客户端
 * - 前端加密仅作为传输层保护，真正安全应依赖 HTTPS + 服务端哈希
 */

/** 默认 IV（16 字节，使用密钥本身作为 IV） */
const DEFAULT_IV = CryptoJS.enc.Utf8.parse('aryn_iv_key_2025');

export const encrypt = (word: string, keyStr: string) => {
  const key = CryptoJS.enc.Utf8.parse(keyStr);
  const srcs = CryptoJS.enc.Utf8.parse(word);
  const encrypted = CryptoJS.AES.encrypt(srcs, key, {
    iv: DEFAULT_IV,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  });
  return encrypted.toString();
};

export const decrypt = (word: string, keyStr: string) => {
  const key = CryptoJS.enc.Utf8.parse(keyStr);
  const decrypt = CryptoJS.AES.decrypt(word, key, {
    iv: DEFAULT_IV,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  });
  return CryptoJS.enc.Utf8.stringify(decrypt).toString();
};
