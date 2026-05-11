
package com.aryn.cloud.common.core.util;

import cn.hutool.crypto.symmetric.AES;

public class AesUtils {

	private static final String AES_MODE = "ECB";

	private static final String AES_PADDING = "PKCS7Padding";

	public static String decrypt(String encodeKey, String value) {
		AES aes = new AES(AES_MODE, AES_PADDING, encodeKey.getBytes());
		return aes.decryptStr(value);
	}

}
