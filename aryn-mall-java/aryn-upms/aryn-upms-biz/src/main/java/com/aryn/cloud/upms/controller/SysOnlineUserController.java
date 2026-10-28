
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysUserOnline;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 系统在线用户
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:45
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/onlineuser")
@Tag(description = "onlineuser", name = "系统在线用户")
public class SysOnlineUserController {

	private final StringRedisTemplate redisTemplate;

	@Operation(summary = "在线用户列表")
	@SaCheckPermission("upms:onlineuser:get")
	@GetMapping("/list")
	public Result<List<SysUserOnline>> page() {
		String tenantId = ArynTenantContextHolder.getTenantId();

		// 在线记录 key 只按 token 维度（见 ArynSaTokenListener#onlineUserKey），租户从值里判断；
		// 扫描全部记录后再按当前请求租户过滤。
		String pattern = CacheConstants.SYS_ONLINE_KEY + "*";

		List<SysUserOnline> result = new ArrayList<>();

		ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1000).build(); // count越大遍历越快但越耗资源
		Cursor<byte[]> cursor = null;

		try (RedisConnection connection = Objects.requireNonNull(redisTemplate.getConnectionFactory())
			.getConnection()) {
			cursor = connection.scan(options);
			while (cursor.hasNext()) {
				byte[] key = cursor.next();
				byte[] value = connection.get(key);
				if (value != null) {
					String jsonStr = new String(value, StandardCharsets.UTF_8);
					SysUserOnline online = JSONUtil.toBean(jsonStr, SysUserOnline.class);
					if (isDisplayable(online, tenantId)) {
						result.add(online);
					}
				}
			}
		}
		catch (Exception e) {
			return Result.fail("Redis SCAN 查询失败: " + e.getMessage());
		}

		result.sort(Comparator.comparing(SysUserOnline::getLoginTime).reversed());

		return Result.success(result);
	}

	/**
	 * 在线记录是否应展示给当前租户。
	 *
	 * <ul>
	 * <li>租户：key 不再携带租户（写入/删除统一用 token 维度），必须按值里的 tenantId 过滤，
	 * 否则会跨租户泄露在线用户；租户上下文为空时不展示任何记录（保持旧实现语义）。</li>
	 * <li>有效性：会话已注销/强退/被顶号时记录可能残留（历史 key 格式的旧记录只能等 TTL），
	 * 以 Sa-Token 实际登录态为准过滤，避免「在线用户」页把已失效会话显示成多端在线。</li>
	 * </ul>
	 */
	static boolean isDisplayable(SysUserOnline online, String tenantId) {
		if (online == null || online.getTokenId() == null) {
			return false;
		}
		if (StrUtil.isBlank(tenantId) || !tenantId.equals(online.getTenantId())) {
			return false;
		}
		// 商城端会话也写在线记录（供租户级会话撤销），但管理端「在线用户」只展示 TOB；
		// 历史记录没有 deviceType，按管理端兼容处理。
		if (online.getDeviceType() != null
				&& !DeviceTypeEnum.TOB.getDevice().equals(online.getDeviceType())) {
			return false;
		}
		return StpUtil.getLoginIdByToken(online.getTokenId()) != null;
	}

	@SysLog("强退用户")
	@Operation(summary = "强退用户")
	@SaCheckPermission("upms:onlineuser:forced")
	@DeleteMapping("/{token}")
	public Result<Void> kickoutByToken(@PathVariable String token) {
		SysUserOnline online = findOnline(token);
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (online == null || StrUtil.isBlank(tenantId) || !tenantId.equals(online.getTenantId())) {
			return Result.fail("在线用户不存在或不属于当前租户");
		}
		StpUtil.kickoutByTokenValue(token);
		redisTemplate.delete(CacheConstants.SYS_ONLINE_KEY + token);
		return Result.success();
	}

	private SysUserOnline findOnline(String token) {
		if (StrUtil.isBlank(token)) {
			return null;
		}
		try {
			String json = redisTemplate.opsForValue().get(CacheConstants.SYS_ONLINE_KEY + token);
			return StrUtil.isBlank(json) ? null : JSONUtil.toBean(json, SysUserOnline.class);
		}
		catch (Exception e) {
			// token 是 bearer 凭证，日志不得输出
			log.warn("读取在线会话失败", e);
			return null;
		}
	}

}
