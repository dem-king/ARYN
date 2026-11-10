package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.upms.api.dto.CopyrightConfigDTO;
import com.aryn.cloud.upms.api.entity.SysCopyrightConfig;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.vo.AppCopyrightConfigVO;
import com.aryn.cloud.upms.api.vo.CopyrightConfigVO;
import com.aryn.cloud.upms.mapper.SysCopyrightConfigMapper;
import com.aryn.cloud.upms.service.ISysCopyrightConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 版权配置。
 *
 * <p>缓存只存「已回落品牌」的下发值：登录页免登高频读取，而品牌回落要额外查一次租户表。
 * 租户改名/换 Logo 后由租户修改链路调用 {@link #evictTenantRenderCache} 失效缓存，
 * 避免页脚长期显示旧品牌。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysCopyrightConfigServiceImpl implements ISysCopyrightConfigService {

	private static final List<String> CLIENT_TYPES = List.of(SysCopyrightConfig.CLIENT_APP,
			SysCopyrightConfig.CLIENT_MERCHANT, SysCopyrightConfig.CLIENT_PLATFORM);

	/** 下发值缓存时长；保存与租户品牌变更都会主动失效，TTL 只兜底极端漏清场景 */
	private static final long CACHE_TTL_SECONDS = 300;

	private final SysCopyrightConfigMapper copyrightConfigMapper;

	private final StringRedisTemplate redisTemplate;

	@Override
	public CopyrightConfigVO getConfig(String tenantId, String clientType) {
		String type = requireClientType(clientType);
		String scopeTenantId = resolveConfigTenantId(tenantId, type);
		return toVO(copyrightConfigMapper.selectByTenantAndClient(scopeTenantId, type), type);
	}

	@Override
	public void saveConfig(String tenantId, CopyrightConfigDTO dto) {
		if (dto == null) {
			throw new ArynBusinessException("缺少版权配置内容");
		}
		String type = requireClientType(dto.getClientType());
		String scopeTenantId = resolveConfigTenantId(tenantId, type);
		String logoUrl = normalize(dto.getLogoUrl(), 500, "Logo");
		String name = normalize(dto.getName(), 30, "名称");
		String copyrightText = normalize(dto.getCopyrightText(), 100, "版权信息");
		String copyrightLink = normalizeLink(dto.getCopyrightLink(), "版权链接");
		String icp = normalize(dto.getIcp(), 100, "ICP备案号");
		String icpLink = normalizeLink(dto.getIcpLink(), "ICP网站链接");
		// 登录页三项只对平台端有意义，其它端一律清空，避免脏值随下发值流到 C 端
		boolean platform = SysCopyrightConfig.CLIENT_PLATFORM.equals(type);
		String loginTitle = platform ? normalize(dto.getLoginTitle(), 60, "登录页标题") : "";
		String loginSubtitle = platform ? normalize(dto.getLoginSubtitle(), 128, "登录页副标题") : "";
		String loginIcon = platform ? normalize(dto.getLoginIcon(), 500, "登录页图标") : "";

		SysCopyrightConfig existing = copyrightConfigMapper.selectByTenantAndClient(scopeTenantId, type);
		String operator = SecurityUtils.getUser() == null ? null : SecurityUtils.getUser().getUsername();
		// 平台端固定写平台租户那一条：平台管理员可能正处于切换后的租户上下文里，
		// 不临时切换的话 insert 会被租户拦截器写成当前租户，登录页读平台租户行时看不到改动
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		boolean switched = !scopeTenantId.equals(previousTenantId);
		if (switched) {
			ArynTenantContextHolder.setTenantId(scopeTenantId);
		}
		try {
			if (existing == null) {
				SysCopyrightConfig config = new SysCopyrightConfig();
				config.setClientType(type);
				config.setTenantId(scopeTenantId);
				config.setLogoUrl(logoUrl);
				config.setName(name);
				config.setCopyrightText(copyrightText);
				config.setCopyrightLink(copyrightLink);
				config.setIcp(icp);
				config.setIcpLink(icpLink);
				config.setLoginTitle(loginTitle);
				config.setLoginSubtitle(loginSubtitle);
				config.setLoginIcon(loginIcon);
				config.setCreateBy(operator);
				config.setUpdateBy(operator);
				config.setCreateTime(LocalDateTime.now());
				config.setUpdateTime(LocalDateTime.now());
				config.setDelFlag("0");
				copyrightConfigMapper.insert(config);
			}
			else {
				copyrightConfigMapper.overwriteByTenantAndId(existing.getId(), scopeTenantId, logoUrl, name,
						copyrightText, copyrightLink, icp, icpLink, loginTitle, loginSubtitle, loginIcon, operator);
			}
		}
		finally {
			if (switched) {
				if (StrUtil.isBlank(previousTenantId)) {
					ArynTenantContextHolder.removeTenantId();
				}
				else {
					ArynTenantContextHolder.setTenantId(previousTenantId);
				}
			}
		}
		evictRenderCache(scopeTenantId, type);
	}

	@Override
	public AppCopyrightConfigVO getRenderConfig(String tenantId, String clientType) {
		requireTenantId(tenantId);
		String type = requireClientType(clientType);
		String cacheKey = renderCacheKey(tenantId, type);
		AppCopyrightConfigVO cached = readCache(cacheKey);
		if (cached != null) {
			return cached;
		}
		SysCopyrightConfig config = copyrightConfigMapper.selectByTenantAndClient(tenantId, type);
		SysTenant tenant = copyrightConfigMapper.selectTenantBrand(tenantId);
		AppCopyrightConfigVO vo = toRenderVO(config, type, tenant);
		writeCache(cacheKey, vo);
		return vo;
	}

	@Override
	public AppCopyrightConfigVO getPlatformLoginConfig() {
		String platformTenantId = copyrightConfigMapper.selectPlatformTenantId();
		if (StrUtil.isBlank(platformTenantId)) {
			// 平台租户缺失属部署异常：登录页仍要能打开，回落到空配置由前端内置品牌兜底
			log.warn("未找到平台租户，登录页版权配置下发空值");
			return toRenderVO(null, SysCopyrightConfig.CLIENT_PLATFORM, null);
		}
		return getRenderConfig(platformTenantId, SysCopyrightConfig.CLIENT_PLATFORM);
	}

	/**
	 * 平台端是全局配置：无论当前会话在哪个租户上下文（平台管理员可切换租户），
	 * 读写都落到平台租户那一条，保证「配置页看到的」与「登录页渲染的」是同一条数据。
	 */
	private String resolveConfigTenantId(String tenantId, String clientType) {
		if (SysCopyrightConfig.CLIENT_PLATFORM.equals(clientType)) {
			String platformTenantId = copyrightConfigMapper.selectPlatformTenantId();
			if (StrUtil.isNotBlank(platformTenantId)) {
				return platformTenantId;
			}
			log.warn("未找到平台租户，平台端版权配置回落到当前租户上下文");
		}
		requireTenantId(tenantId);
		return tenantId;
	}

	/**
	 * 失效下发缓存。
	 *
	 * <p>平台端配置被所有租户的登录页共用，缓存 key 不带租户（见 renderCacheKey），
	 * 保存后只需清这一条；用户端/商户端按租户精确清理。刻意不用 {@code keys()} 通配扫描，
	 * 避免在共享 Redis 上做 O(N) 阻塞遍历。</p>
	 */
	private void evictRenderCache(String tenantId, String clientType) {
		try {
			redisTemplate.delete(renderCacheKey(tenantId, clientType));
		}
		catch (Exception ex) {
			// 缓存不可用不影响保存结果，最迟 TTL 到期后生效
			log.warn("失效版权配置缓存失败: {}", ex.getMessage());
		}
	}

	/**
	 * 租户名称/Logo 变更后失效该租户全部端的下发缓存（名称与 Logo 留空时跟随租户品牌）。
	 *
	 * <p>平台端 key 是全局的（不带租户），但平台登录页的品牌也来自平台租户，
	 * 因此一并清理——多清一条的代价远小于登录页长期显示旧品牌。</p>
	 */
	@Override
	public void evictTenantRenderCache(String tenantId) {
		if (StrUtil.isBlank(tenantId)) {
			return;
		}
		try {
			redisTemplate.delete(List.of(renderCacheKey(tenantId, SysCopyrightConfig.CLIENT_APP),
					renderCacheKey(tenantId, SysCopyrightConfig.CLIENT_MERCHANT),
					renderCacheKey(tenantId, SysCopyrightConfig.CLIENT_PLATFORM)));
		}
		catch (Exception ex) {
			log.warn("失效租户版权配置缓存失败: {}", ex.getMessage());
		}
	}

	/**
	 * 平台端是全局配置（所有租户登录页共用平台租户那一行），key 不带租户；
	 * 其余端按「租户 + 端」隔离，避免多租户串读。
	 */
	private String renderCacheKey(String tenantId, String clientType) {
		if (SysCopyrightConfig.CLIENT_PLATFORM.equals(clientType)) {
			return CacheConstants.COPYRIGHT_CONFIG_CACHE + SysCopyrightConfig.CLIENT_PLATFORM;
		}
		return CacheConstants.COPYRIGHT_CONFIG_CACHE + tenantId + ":" + clientType;
	}

	private AppCopyrightConfigVO readCache(String cacheKey) {
		try {
			String cached = redisTemplate.opsForValue().get(cacheKey);
			return StrUtil.isBlank(cached) ? null : JSON.parseObject(cached, AppCopyrightConfigVO.class);
		}
		catch (Exception ex) {
			log.warn("读取版权配置缓存失败，回源数据库: {}", ex.getMessage());
			return null;
		}
	}

	private void writeCache(String cacheKey, AppCopyrightConfigVO vo) {
		try {
			redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(vo), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
		}
		catch (Exception ex) {
			log.warn("写入版权配置缓存失败: {}", ex.getMessage());
		}
	}

	private static CopyrightConfigVO toVO(SysCopyrightConfig config, String clientType) {
		CopyrightConfigVO vo = new CopyrightConfigVO();
		vo.setClientType(clientType);
		vo.setLogoUrl(text(config == null ? null : config.getLogoUrl()));
		vo.setName(text(config == null ? null : config.getName()));
		vo.setCopyrightText(text(config == null ? null : config.getCopyrightText()));
		vo.setCopyrightLink(text(config == null ? null : config.getCopyrightLink()));
		vo.setIcp(text(config == null ? null : config.getIcp()));
		vo.setIcpLink(text(config == null ? null : config.getIcpLink()));
		vo.setLoginTitle(text(config == null ? null : config.getLoginTitle()));
		vo.setLoginSubtitle(text(config == null ? null : config.getLoginSubtitle()));
		vo.setLoginIcon(text(config == null ? null : config.getLoginIcon()));
		return vo;
	}

	private static AppCopyrightConfigVO toRenderVO(SysCopyrightConfig config, String clientType, SysTenant tenant) {
		AppCopyrightConfigVO vo = new AppCopyrightConfigVO();
		vo.setClientType(clientType);
		String logo = config == null ? "" : text(config.getLogoUrl());
		String name = config == null ? "" : text(config.getName());
		vo.setLogoUrl(StrUtil.isNotBlank(logo) ? logo : text(tenant == null ? null : tenant.getLogoUrl()));
		vo.setName(StrUtil.isNotBlank(name) ? name : text(tenant == null ? null : tenant.getName()));
		vo.setCopyrightText(text(config == null ? null : config.getCopyrightText()));
		vo.setCopyrightLink(text(config == null ? null : config.getCopyrightLink()));
		vo.setIcp(text(config == null ? null : config.getIcp()));
		vo.setIcpLink(text(config == null ? null : config.getIcpLink()));
		vo.setLoginTitle(text(config == null ? null : config.getLoginTitle()));
		vo.setLoginSubtitle(text(config == null ? null : config.getLoginSubtitle()));
		vo.setLoginIcon(text(config == null ? null : config.getLoginIcon()));
		return vo;
	}

	private static String requireClientType(String clientType) {
		String type = StrUtil.trimToEmpty(clientType);
		if (!CLIENT_TYPES.contains(type)) {
			throw new ArynBusinessException("端类型只能是 app、merchant 或 platform");
		}
		return type;
	}

	private static void requireTenantId(String tenantId) {
		if (StrUtil.isBlank(tenantId)) {
			throw new ArynBusinessException("缺少租户上下文");
		}
	}

	private static String text(String value) {
		return StrUtil.trimToEmpty(value);
	}

	private static String normalize(String value, int maxLength, String label) {
		String trimmed = StrUtil.trimToEmpty(value);
		if (trimmed.length() > maxLength) {
			throw new ArynBusinessException(label + "不能超过 " + maxLength + " 个字符");
		}
		return trimmed;
	}

	/**
	 * 链接只允许 http/https 或站内相对路径：这些值会直接绑到 a/href 与小程序跳转，
	 * 放开 javascript: 等协议等于给运营后台开了一个存储型 XSS 入口。
	 */
	private static String normalizeLink(String value, String label) {
		String trimmed = normalize(value, 500, label);
		if (trimmed.isEmpty() || trimmed.startsWith("/")) {
			return trimmed;
		}
		try {
			String scheme = URI.create(trimmed).getScheme();
			if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
				return trimmed;
			}
		}
		catch (IllegalArgumentException ex) {
			throw new ArynBusinessException(label + "格式不正确");
		}
		throw new ArynBusinessException(label + "只支持 http/https 链接或站内相对路径");
	}

}