package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀库存管理器（Redis + Lua 原子扣减）
 *
 * @author aryn
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockManager {

	private final StringRedisTemplate redisTemplate;

	private DefaultRedisScript<Long> seckillScript;

	private DefaultRedisScript<Long> rollbackScript;

	/**
	 * Lua 脚本：原子扣减库存 + 限购校验
	 * KEYS[1] = stock key
	 * KEYS[2] = bought key
	 * ARGV[1] = quantity
	 * ARGV[2] = limit per user
	 * 返回：1成功 / 0库存不足 / -1超出限购
	 */
	private static final String LUA_SCRIPT = """
			local stock = tonumber(redis.call('GET', KEYS[1]))
			if not stock or stock < tonumber(ARGV[1]) then
			    return 0
			end
			local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
			if bought + tonumber(ARGV[1]) > tonumber(ARGV[2]) then
			    return -1
			end
			redis.call('DECRBY', KEYS[1], ARGV[1])
			redis.call('INCRBY', KEYS[2], ARGV[1])
			return 1
			""";

	/**
	 * Lua 脚本：安全回滚库存（仅当 key 存在才操作，避免凭空创建）
	 * KEYS[1] = stock key
	 * KEYS[2] = bought key
	 * ARGV[1] = quantity
	 * 返回：1成功 / 0 key不存在(跳过)
	 */
	private static final String ROLLBACK_SCRIPT = """
			if redis.call('EXISTS', KEYS[1]) == 1 then
			    redis.call('INCRBY', KEYS[1], ARGV[1])
			    if redis.call('EXISTS', KEYS[2]) == 1 then
			        redis.call('DECRBY', KEYS[2], ARGV[1])
			    end
			    return 1
			end
			return 0
			""";

	@PostConstruct
	public void init() {
		seckillScript = new DefaultRedisScript<>();
		seckillScript.setScriptText(LUA_SCRIPT);
		seckillScript.setResultType(Long.class);

		rollbackScript = new DefaultRedisScript<>();
		rollbackScript.setScriptText(ROLLBACK_SCRIPT);
		rollbackScript.setResultType(Long.class);
	}

	/**
	 * 初始化库存到 Redis
	 */
	public void initStock(String activityId, String sessionId, String skuId,
			Integer stock, Integer limitPerUser, Duration ttl) {
		String stockKey = buildStockKey(activityId, sessionId, skuId);
		String boughtKeyPrefix = buildBoughtKeyPrefix(activityId, sessionId, skuId);
		redisTemplate.opsForValue().set(stockKey, String.valueOf(stock), ttl);
		redisTemplate.opsForValue().set(boughtKeyPrefix + ":limit", String.valueOf(limitPerUser), ttl);
		log.info("秒杀库存初始化, activityId={}, sessionId={}, skuId={}, stock={}", activityId, sessionId, skuId, stock);
	}

	/**
	 * 原子扣减库存（Lua 脚本）
	 *
	 * @return 1成功 / 0库存不足 / -1超出限购
	 */
	public long deductStock(String activityId, String sessionId, String skuId,
			String userId, Integer quantity, Integer limitPerUser) {
		String stockKey = buildStockKey(activityId, sessionId, skuId);
		String boughtKey = buildBoughtKey(activityId, sessionId, skuId, userId);
		List<String> keys = List.of(stockKey, boughtKey);
		Long result = redisTemplate.execute(seckillScript, keys,
				String.valueOf(quantity), String.valueOf(limitPerUser));
		if (result == null) {
			return 0;
		}
		log.info("秒杀库存扣减, activityId={}, sessionId={}, skuId={}, userId={}, quantity={}, result={}",
				activityId, sessionId, skuId, userId, quantity, result);
		return result;
	}

	/**
	 * 安全回滚库存（Lua 脚本：仅当 key 存在才回滚，避免凭空创建）
	 */
	public long rollbackStock(String activityId, String sessionId, String skuId,
			String userId, Integer quantity) {
		String stockKey = buildStockKey(activityId, sessionId, skuId);
		String boughtKey = buildBoughtKey(activityId, sessionId, skuId, userId);
		List<String> keys = List.of(stockKey, boughtKey);
		Long result = redisTemplate.execute(rollbackScript, keys, String.valueOf(quantity));
		if (result == null) {
			return 0;
		}
		log.info("秒杀库存安全回滚, activityId={}, sessionId={}, skuId={}, userId={}, quantity={}, result={}",
				activityId, sessionId, skuId, userId, quantity, result);
		return result;
	}

	/**
	 * 获取剩余库存
	 */
	public Integer getRemainingStock(String activityId, String sessionId, String skuId) {
		String stockKey = buildStockKey(activityId, sessionId, skuId);
		String value = redisTemplate.opsForValue().get(stockKey);
		if (value == null) {
			return null;
		}
		return Integer.valueOf(value);
	}

	/**
	 * 批量获取剩余库存（mget 优化，避免 N 次 Redis 调用）
	 *
	 * @param stockKeysParams 每个元素为 [activityId, sessionId, skuId]
	 * @return 对应的剩余库存列表（null 表示 key 不存在）
	 */
	public List<Integer> batchGetRemainingStock(List<String[]> stockKeysParams) {
		if (stockKeysParams == null || stockKeysParams.isEmpty()) {
			return List.of();
		}
		List<String> keys = stockKeysParams.stream()
				.map(p -> buildStockKey(p[0], p[1], p[2]))
				.toList();
		List<String> values = redisTemplate.opsForValue().multiGet(keys);
		if (values == null) {
			return List.of();
		}
		return values.stream()
				.map(v -> v != null ? Integer.valueOf(v) : null)
				.toList();
	}

	/**
	 * 删除库存缓存
	 */
	public void removeStock(String activityId, String sessionId, String skuId) {
		String stockKey = buildStockKey(activityId, sessionId, skuId);
		redisTemplate.delete(stockKey);
	}

	private String buildStockKey(String activityId, String sessionId, String skuId) {
		String tenantId = currentTenantId();
		return "seckill:stock:" + tenantId + ":" + activityId + ":" + sessionId + ":" + skuId;
	}

	private String buildBoughtKeyPrefix(String activityId, String sessionId, String skuId) {
		String tenantId = currentTenantId();
		return "seckill:bought:" + tenantId + ":" + activityId + ":" + sessionId + ":" + skuId;
	}

	private String buildBoughtKey(String activityId, String sessionId, String skuId, String userId) {
		return buildBoughtKeyPrefix(activityId, sessionId, skuId) + ":" + userId;
	}

	/**
	 * 获取当前租户ID，用于 Redis key 隔离。若上下文无租户则抛异常，避免跨租户混用。
	 */
	private String currentTenantId() {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new IllegalStateException("租户上下文未初始化，秒杀库存操作拒绝执行");
		}
		return tenantId;
	}
}
