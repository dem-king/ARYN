package com.aryn.cloud.upms.support;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.upms.api.entity.SysUserOnline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 租户级会话撤销。
 *
 * <p>租户停用、删除或授权到期后，仅阻止新登录还不够：已签发的 token 在过期前仍然有效。
 * 在线记录按 token 维度存储、值里带 tenantId，这里扫描并逐个撤销该租户的全部会话
 * （管理端 TOB 与商城端 TOC 均覆盖；新登录由认证侧租户可用性校验拦截）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSessionRevoker {

	private final StringRedisTemplate redisTemplate;

	public int revokeTenant(String tenantId, String reason) {
		if (StrUtil.isBlank(tenantId)) {
			return 0;
		}
		List<String> tokens = collectTokens(tenantId);
		int revoked = 0;
		for (String token : tokens) {
			try {
				StpUtil.kickoutByTokenValue(token);
				revoked++;
			}
			catch (Exception e) {
				// 会话可能已自然过期或被顶号；token 是 bearer 凭证，日志不得输出
				log.warn("租户会话撤销失败 tenantId={}", tenantId, e);
			}
			finally {
				redisTemplate.delete(CacheConstants.SYS_ONLINE_KEY + token);
			}
		}
		log.info("租户会话撤销完成 tenantId={} reason={} sessions={}", tenantId, reason, revoked);
		return revoked;
	}

	private List<String> collectTokens(String tenantId) {
		List<String> tokens = new ArrayList<>();
		ScanOptions options = ScanOptions.scanOptions()
			.match(CacheConstants.SYS_ONLINE_KEY + "*")
			.count(1000)
			.build();
		try (RedisConnection connection = Objects.requireNonNull(redisTemplate.getConnectionFactory())
			.getConnection();
				Cursor<byte[]> cursor = connection.scan(options)) {
			while (cursor.hasNext()) {
				byte[] key = cursor.next();
				byte[] value = connection.get(key);
				if (value == null) {
					continue;
				}
				try {
					SysUserOnline online = JSONUtil.toBean(new String(value, StandardCharsets.UTF_8),
							SysUserOnline.class);
					if (tenantId.equals(online.getTenantId()) && StrUtil.isNotBlank(online.getTokenId())) {
						tokens.add(online.getTokenId());
					}
				}
				catch (Exception e) {
					log.warn("跳过无法解析的在线会话记录 key={}", new String(key, StandardCharsets.UTF_8), e);
				}
			}
		}
		catch (Exception e) {
			log.error("扫描租户在线会话失败 tenantId={}", tenantId, e);
		}
		return tokens;
	}

}
