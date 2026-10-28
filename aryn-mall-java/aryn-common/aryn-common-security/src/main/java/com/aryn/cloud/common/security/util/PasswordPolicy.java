package com.aryn.cloud.common.security.util;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 密码强度策略（服务端权威校验）。
 *
 * <p>背景：员工账号新增与改密此前只做 BCrypt 哈希，没有长度、字符类别或常见弱口令约束，
 * 空串/极短密码也能落库。这里把「创建密码 / 修改密码」统一到同一套规则，
 * 前端 `apps/web-ele/src/utils/password-policy.ts` 只是同规则的表单提示，**不能**替代本校验。</p>
 *
 * <p>规则（保持简单、可预期）：</p>
 * <ol>
 *   <li>非空，长度 8~64；</li>
 *   <li>至少包含 1 个英文字母与 1 个数字（允许符号，不强制大小写/符号）；</li>
 *   <li>不在常见弱口令黑名单内（大小写不敏感）。</li>
 * </ol>
 *
 * <p>不做「必须含特殊字符」的强制：这类规则容易催生 `Password@1` 式伪强密码，
 * 且与 C 端既有 8~64 位约束保持一致；后续如需收紧只改这里与前端镜像即可。</p>
 *
 * @author aryn
 * @since 2026/10/28
 */
public final class PasswordPolicy {

	/** 密码最小长度 */
	public static final int MIN_LENGTH = 8;

	/** 密码最大长度（BCrypt 只取前 72 字节，限制 64 字符留出余量） */
	public static final int MAX_LENGTH = 64;

	/** 错误文案：与前端表单提示保持一致 */
	public static final String MSG_REQUIRED = "请输入密码";

	public static final String MSG_LENGTH = "密码长度必须为8到64个字符";

	public static final String MSG_NEED_LETTER = "密码必须包含字母";

	public static final String MSG_NEED_DIGIT = "密码必须包含数字";

	public static final String MSG_WEAK = "密码过于简单，请更换更复杂的密码";

	private static final Pattern LETTER = Pattern.compile("[A-Za-z]");

	private static final Pattern DIGIT = Pattern.compile("\\d");

	/** 常见弱口令（小写）。新增时保持与前端 password-policy.ts 的 WEAK_PASSWORDS 同步。 */
	private static final Set<String> WEAK_PASSWORDS = Set.of(
			"12345678", "123456789", "1234567890", "11111111", "88888888",
			"password", "password1", "passw0rd", "admin123", "a1234567",
			"abc12345", "qwerty123", "1qaz2wsx", "aa123456", "a12345678");

	private PasswordPolicy() {
	}

	/**
	 * 校验密码强度。
	 * @param password 待校验的明文密码
	 * @return 通过返回 {@code null}；不通过返回可直接展示给用户的中文错误文案
	 */
	public static String validate(String password) {
		if (StrUtil.isBlank(password)) {
			return MSG_REQUIRED;
		}
		if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
			return MSG_LENGTH;
		}
		if (!LETTER.matcher(password).find()) {
			return MSG_NEED_LETTER;
		}
		if (!DIGIT.matcher(password).find()) {
			return MSG_NEED_DIGIT;
		}
		if (WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
			return MSG_WEAK;
		}
		return null;
	}

	/**
	 * 仅判断是否通过（不需要错误文案时使用）。
	 */
	public static boolean isValid(String password) {
		return validate(password) == null;
	}

}
