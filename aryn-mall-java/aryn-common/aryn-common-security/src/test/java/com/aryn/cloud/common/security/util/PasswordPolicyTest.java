package com.aryn.cloud.common.security.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密码强度策略契约：服务端为权威校验，前端表单只是同规则提示。
 */
class PasswordPolicyTest {

	@Test
	void acceptsStrongPassword() {
		assertThat(PasswordPolicy.validate("Aryn2026Pass")).isNull();
		assertThat(PasswordPolicy.isValid("abc12345X")).isTrue();
	}

	@Test
	void rejectsBlankPassword() {
		assertThat(PasswordPolicy.validate(null)).isEqualTo(PasswordPolicy.MSG_REQUIRED);
		assertThat(PasswordPolicy.validate("")).isEqualTo(PasswordPolicy.MSG_REQUIRED);
		assertThat(PasswordPolicy.validate("   ")).isEqualTo(PasswordPolicy.MSG_REQUIRED);
	}

	@Test
	void rejectsTooShortOrTooLongPassword() {
		assertThat(PasswordPolicy.validate("a1b2c3d")).isEqualTo(PasswordPolicy.MSG_LENGTH);
		assertThat(PasswordPolicy.validate("a1" + "x".repeat(63))).isEqualTo(PasswordPolicy.MSG_LENGTH);
	}

	@Test
	void requiresLetterAndDigit() {
		assertThat(PasswordPolicy.validate("12345678")).isEqualTo(PasswordPolicy.MSG_NEED_LETTER);
		assertThat(PasswordPolicy.validate("abcdefgh")).isEqualTo(PasswordPolicy.MSG_NEED_DIGIT);
	}

	@Test
	void rejectsCommonWeakPasswordIgnoringCase() {
		assertThat(PasswordPolicy.validate("Password1")).isEqualTo(PasswordPolicy.MSG_WEAK);
		assertThat(PasswordPolicy.validate("ADMIN123")).isEqualTo(PasswordPolicy.MSG_WEAK);
		assertThat(PasswordPolicy.validate("1qaz2wsx")).isEqualTo(PasswordPolicy.MSG_WEAK);
	}

	@Test
	void allowsSymbolsAndUnicodeButStillNeedsLetterAndDigit() {
		assertThat(PasswordPolicy.validate("测试Pass2026!")).isNull();
		// 中文/符号不算英文字母：只有数字与中文仍拒绝
		assertThat(PasswordPolicy.validate("测试密码2026")).isEqualTo(PasswordPolicy.MSG_NEED_LETTER);
		assertThat(PasswordPolicy.validate("测试abc密码！")).isEqualTo(PasswordPolicy.MSG_NEED_DIGIT);
	}

}
