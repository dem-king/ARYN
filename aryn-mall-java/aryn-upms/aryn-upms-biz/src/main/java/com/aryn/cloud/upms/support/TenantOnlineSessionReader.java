package com.aryn.cloud.upms.support;

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
 * 按租户读取在线会话记录。
 *
 * <p>在线记录按 token 维度存储、值中带 tenantId，无法按 key 前缀过滤，只能扫描后按值筛选。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantOnlineSessionReader {

	private final StringRedisTemplate redisTemplate;

	public List<SysUserOnline> listByTenant(String tenantId) {
		List<SysUserOnline> result = new ArrayList<>();
		if (tenantId == null || tenantId.isBlank()) {
			return result;
		}
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
					if (tenantId.equals(online.getTenantId())) {
						result.add(online);
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
		return result;
	}

}
