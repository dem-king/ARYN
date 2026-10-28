package com.aryn.cloud.auth.config;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 登录安全防护配置。
 *
 * <p>涵盖两类策略：</p>
 * <ol>
 *   <li>密码登录失败限流（账号 + IP 双维度，Redis 计数），防止在线暴力破解与撞库；</li>
 *   <li>管理端（TOB）会话有效期，缩短后台账号在 token 泄露后的可用窗口。</li>
 * </ol>
 *
 * <p>boot 单体模式见 {@code aryn-boot/src/main/resources/application.yml}；
 * cloud 微服务模式见 Nacos {@code aryn-auth-{profile}.yml}
 * （基线 {@code db/cloud/3aryn_nacos.sql}，存量环境升级脚本
 * {@code db/cloud/170login_hardening_nacos_config.sql}）。缺省值在 Java 中兜底，
 * 未显式配置也能生效。</p>
 *
 * @author aryn
 * @since 2026/10/28
 */
@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "security.login")
public class LoginProtectionProperties {

	/** 是否启用登录失败限流。关闭仅用于临时排障，生产必须为 true。 */
	private boolean enabled = true;

	/** 账号维度：锁定窗口内允许的最大失败次数 */
	private int maxAccountFailures = 5;

	/** 账号维度：失败计数 TTL / 锁定时长（秒）。每次失败滑动续期。 */
	private long accountLockSeconds = 900;

	/** IP 维度：锁定窗口内允许的最大失败次数（防同一出口换账号撞库） */
	private int maxIpFailures = 30;

	/** IP 维度：失败计数 TTL / 锁定时长（秒）。每次失败滑动续期。 */
	private long ipLockSeconds = 900;

	/** 管理端（TOB）会话绝对有效期（秒），默认 12 小时；<=0 时沿用全局 sa-token.timeout。
	 *
	 * <p>只缩短绝对有效期、不启用空闲超时：Sa-Token 的按登录维度空闲超时依赖全局
	 * {@code dynamic-active-timeout=true}，开启后存量 token（无最后活跃时间记录）会被判定为
	 * 已冻结，等于发布即强制全端重新登录；C 端商城不适合承担该影响，故留作后续维护窗口再评估。</p>
	 */
	private long tobTimeoutSeconds = 43200;

	/**
	 * 构造管理端（TOB，含管理端 Web 与配送端）本次登录的 Sa-Token 参数。
	 *
	 * <p>只覆盖绝对有效期；单点登录（isConcurrent / isShare）等策略继续以全局配置为准。</p>
	 */
	public SaLoginParameter tobLoginParameter() {
		SaLoginParameter loginParameter = new SaLoginParameter();
		if (tobTimeoutSeconds > 0) {
			loginParameter.setTimeout(tobTimeoutSeconds);
		}
		return loginParameter;
	}

}
