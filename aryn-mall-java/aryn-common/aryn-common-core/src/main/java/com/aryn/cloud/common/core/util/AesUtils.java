
package com.aryn.cloud.common.core.util;

import cn.hutool.crypto.symmetric.AES;

import java.nio.charset.StandardCharsets;

/**
 * AES 加解密工具类
 *
 * <p>安全说明：
 * <ul>
 *   <li>ECB 模式不使用 IV，相同明文产生相同密文，存在模式分析风险，仅保留用于解密历史数据</li>
 *   <li>新代码应使用 {@link #decryptCBC} / {@link #encryptCBC}（CBC + IV）</li>
 *   <li>密钥必须通过环境变量注入，不得硬编码</li>
 * </ul>
 */
public class AesUtils {

	private static final String ECB_MODE = "ECB";

	private static final String CBC_MODE = "CBC";

	private static final String PKCS7_PADDING = "PKCS7Padding";

	/**
	 * ECB 模式解密（不安全，仅用于兼容历史数据）
	 * @param encodeKey 加密密钥
	 * @param value 密文（Base64）
	 * @return 明文
	 */
	@Deprecated
	public static String decrypt(String encodeKey, String value) {
		AES aes = new AES(ECB_MODE, PKCS7_PADDING, encodeKey.getBytes(StandardCharsets.UTF_8));
		return aes.decryptStr(value);
	}

	/**
	 * CBC 模式解密（推荐）
	 * @param encodeKey 加密密钥
	 * @param value 密文（Base64）
	 * @param iv 初始化向量（16 字节）
	 * @return 明文
	 */
	public static String decryptCBC(String encodeKey, String value, String iv) {
		AES aes = new AES(CBC_MODE, PKCS7_PADDING, encodeKey.getBytes(StandardCharsets.UTF_8),
				iv.getBytes(StandardCharsets.UTF_8));
		return aes.decryptStr(value);
	}

	/**
	 * CBC 模式加密（推荐）
	 * @param encodeKey 加密密钥
	 * @param value 明文
	 * @param iv 初始化向量（16 字节）
	 * @return 密文（Base64）
	 */
	public static String encryptCBC(String encodeKey, String value, String iv) {
		AES aes = new AES(CBC_MODE, PKCS7_PADDING, encodeKey.getBytes(StandardCharsets.UTF_8),
				iv.getBytes(StandardCharsets.UTF_8));
		return aes.encryptBase64(value);
	}

}
