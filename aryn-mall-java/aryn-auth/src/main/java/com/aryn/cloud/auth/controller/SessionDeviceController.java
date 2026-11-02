package com.aryn.cloud.auth.controller;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.auth.controller.vo.SessionDeviceVO;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.core.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 当前账号的登录设备（在线会话）自助管理。
 *
 * <p>数据来源：Sa-Token 账号会话的终端列表（token + 设备类型 + 登录时间）
 * 与 {@code ArynSaTokenListener} 写入的在线记录（browser/os/location）关联。
 * 只返回<b>当前登录账号自己</b>的会话，且 token 只以 SHA-256 前 12 位形式出现。</p>
 *
 * <p>当前是全局单点策略（{@code is-concurrent=false}，同设备类型只保留一个会话），
 * 因此列表通常只有「当前设备」一条；接口按多会话设计，未来放开多端无需改契约。
 * 当前设备不允许从列表下线（走退出登录），避免用户把自己踢掉后无法恢复上下文。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/session")
@Tag(description = "session", name = "登录设备会话")
public class SessionDeviceController {

	/** token SHA-256 截断长度：够区分、不可反推 */
	static final int TOKEN_HASH_LENGTH = 12;

	/** 单账号最多返回的会话数（防御异常数据） */
	private static final int MAX_DEVICES = 20;

	/** 在线记录写入时归属地接口不可用会落这个值，读取时按「未解析」处理 */
	private static final String UNKNOWN_LOCATION = "未知";

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final StringRedisTemplate redisTemplate;

	@Operation(summary = "当前账号的登录设备列表")
	@GetMapping("/devices")
	public Result<List<SessionDeviceVO>> devices() {
		Object loginId = StpUtil.getLoginId();
		String deviceType = StpUtil.getLoginDeviceType();
		List<SaTerminalInfo> terminals = StpUtil.getTerminalListByLoginId(loginId, deviceType);
		String currentHash = hash(StpUtil.getTokenValue());
		String clientIp = IpUtils.getClientIp(currentRequest());

		List<SessionDeviceVO> devices = new ArrayList<>();
		if (terminals != null) {
			for (SaTerminalInfo terminal : terminals) {
				if (terminal == null || StrUtil.isBlank(terminal.getTokenValue())) {
					continue;
				}
				SessionDeviceVO device = new SessionDeviceVO();
				device.setId(hash(terminal.getTokenValue()));
				device.setDeviceType(deviceType);
				device.setCurrent(device.getId().equals(currentHash));
				device.setLoginTime(toIso(terminal.getCreateTime()));
				// 当前设备就是本次请求的来源，其他会话只能用登录时写入的在线记录
				fillOnlineRecord(device, terminal.getTokenValue(), device.isCurrent() ? clientIp : null);
				devices.add(device);
				if (devices.size() >= MAX_DEVICES) {
					break;
				}
			}
		}
		devices.sort(Comparator.comparing(SessionDeviceVO::isCurrent).reversed()
			.thenComparing(SessionDeviceVO::getLoginTime, Comparator.nullsLast(Comparator.reverseOrder())));
		return Result.success(devices);
	}

	@Operation(summary = "下线指定登录设备（不能下线当前设备）")
	@DeleteMapping("/devices/{id}")
	public Result<Void> remove(@PathVariable String id) {
		if (StrUtil.isBlank(id)) {
			return Result.fail("参数不能为空");
		}
		Object loginId = StpUtil.getLoginId();
		String deviceType = StpUtil.getLoginDeviceType();
		String currentHash = hash(StpUtil.getTokenValue());
		List<SaTerminalInfo> terminals = StpUtil.getTerminalListByLoginId(loginId, deviceType);
		if (terminals == null || terminals.isEmpty()) {
			return Result.fail("会话不存在或不属于当前账号");
		}
		for (SaTerminalInfo terminal : terminals) {
			if (terminal == null || StrUtil.isBlank(terminal.getTokenValue())) {
				continue;
			}
			String tokenValue = terminal.getTokenValue();
			if (!hash(tokenValue).equals(id.trim())) {
				continue;
			}
			if (hash(tokenValue).equals(currentHash)) {
				return Result.fail("当前设备请使用退出登录");
			}
			try {
				StpUtil.kickoutByTokenValue(tokenValue);
				redisTemplate.delete(CacheConstants.SYS_ONLINE_KEY + tokenValue);
				log.info("用户自助下线登录设备 id={} deviceType={}", id, deviceType);
				return Result.success();
			}
			catch (Exception e) {
				log.warn("用户自助下线登录设备失败 id={}", id, e);
				return Result.fail("下线失败，请稍后重试");
			}
		}
		return Result.fail("会话不存在或不属于当前账号");
	}

	/**
	 * 用在线记录补齐浏览器/系统/地点；记录缺失时留空（前端展示「未知设备」并保留时间）。
	 * <p>{@code clientIp} 仅当前设备传入：历史记录的地点可能是在归属地接口不可用时写入的
	 * 「未知」，用本次请求的真实 IP 重新解析，用户无需重新登录就能看到地点。</p>
	 */
	private void fillOnlineRecord(SessionDeviceVO device, String tokenValue, String clientIp) {
		try {
			String location = null;
			String json = redisTemplate.opsForValue().get(CacheConstants.SYS_ONLINE_KEY + tokenValue.trim());
			if (StrUtil.isNotBlank(json)) {
				JSONObject record = JSONUtil.parseObj(json);
				device.setBrowser(StrUtil.trimToNull(record.getStr("browser")));
				device.setOs(StrUtil.trimToNull(record.getStr("os")));
				location = StrUtil.trimToNull(record.getStr("location"));
			}
			if (clientIp != null && (location == null || UNKNOWN_LOCATION.equals(location))) {
				location = StrUtil.trimToNull(IpUtils.getWhoisAddress(clientIp));
			}
			device.setLocation(location);
		}
		catch (Exception e) {
			log.warn("读取登录设备在线记录失败，展示信息将降级: {}", e.getMessage());
		}
	}

	private static HttpServletRequest currentRequest() {
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return attributes != null ? attributes.getRequest() : null;
	}

	/**
	 * token → 不可反推的设备标识（SHA-256 前 12 位）。
	 */
	static String hash(String tokenValue) {
		if (StrUtil.isBlank(tokenValue)) {
			return "";
		}
		return DigestUtil.sha256Hex(tokenValue.trim()).substring(0, TOKEN_HASH_LENGTH);
	}

	private static String toIso(Long epochMilli) {
		if (epochMilli == null || epochMilli <= 0) {
			return null;
		}
		return Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).format(ISO_FORMATTER);
	}

}
