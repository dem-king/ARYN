package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.upms.api.dto.StatusTipConfigDTO;
import com.aryn.cloud.upms.api.entity.SysStatusTipConfig;
import com.aryn.cloud.upms.api.vo.StatusTipConfigVO;
import com.aryn.cloud.upms.mapper.SysStatusTipConfigMapper;
import com.aryn.cloud.upms.service.ISysStatusTipConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 空态插画配置。
 *
 * <p>不做 Redis 缓存：空态图只在页面真正空/失败时渲染，读频率远低于商品与装修配置，
 * 每次一发轻量 SELECT 足够；省掉一层「后台改完还要等缓存失效」的解释成本。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysStatusTipConfigServiceImpl implements ISysStatusTipConfigService {

	private static final int URL_MAX_LENGTH = 500;

	private final SysStatusTipConfigMapper statusTipConfigMapper;

	@Override
	public StatusTipConfigVO getConfig(String tenantId) {
		String scopeTenantId = requireTenantId(tenantId);
		return toVO(statusTipConfigMapper.selectByTenant(scopeTenantId));
	}

	@Override
	public StatusTipConfigVO getRenderConfig(String tenantId) {
		return getConfig(tenantId);
	}

	@Override
	public void saveConfig(String tenantId, StatusTipConfigDTO dto) {
		if (dto == null) {
			throw new ArynBusinessException("缺少空态图配置内容");
		}
		String scopeTenantId = requireTenantId(tenantId);
		String searchUrl = normalizeUrl(dto.getSearchUrl(), "搜索无结果图");
		String networkUrl = normalizeUrl(dto.getNetworkUrl(), "网络异常图");
		String contentUrl = normalizeUrl(dto.getContentUrl(), "内容为空图");
		String collectUrl = normalizeUrl(dto.getCollectUrl(), "收藏为空图");
		String commentUrl = normalizeUrl(dto.getCommentUrl(), "评论为空图");
		String haloUrl = normalizeUrl(dto.getHaloUrl(), "券/资产为空图");
		String messageUrl = normalizeUrl(dto.getMessageUrl(), "消息为空图");
		String emptyCartUrl = normalizeUrl(dto.getEmptyCartUrl(), "购物车空态图");
		String emptyOrderUrl = normalizeUrl(dto.getEmptyOrderUrl(), "订单列表空态图");
		String emptySeckillUrl = normalizeUrl(dto.getEmptySeckillUrl(), "秒杀会场空态图");

		SysStatusTipConfig existing = statusTipConfigMapper.selectByTenant(scopeTenantId);
		String operator = SecurityUtils.getUser() == null ? null : SecurityUtils.getUser().getUsername();
		if (existing == null) {
			SysStatusTipConfig config = new SysStatusTipConfig();
			config.setTenantId(scopeTenantId);
			config.setSearchUrl(searchUrl);
			config.setNetworkUrl(networkUrl);
			config.setContentUrl(contentUrl);
			config.setCollectUrl(collectUrl);
			config.setCommentUrl(commentUrl);
			config.setHaloUrl(haloUrl);
			config.setMessageUrl(messageUrl);
			config.setEmptyCartUrl(emptyCartUrl);
			config.setEmptyOrderUrl(emptyOrderUrl);
			config.setEmptySeckillUrl(emptySeckillUrl);
			config.setCreateBy(operator);
			config.setUpdateBy(operator);
			config.setCreateTime(LocalDateTime.now());
			config.setUpdateTime(LocalDateTime.now());
			config.setDelFlag("0");
			statusTipConfigMapper.insert(config);
		}
		else {
			statusTipConfigMapper.overwriteByTenantAndId(existing.getId(), scopeTenantId, searchUrl, networkUrl,
					contentUrl, collectUrl, commentUrl, haloUrl, messageUrl, emptyCartUrl, emptyOrderUrl,
					emptySeckillUrl, operator);
		}
	}

	private StatusTipConfigVO toVO(SysStatusTipConfig config) {
		StatusTipConfigVO vo = new StatusTipConfigVO();
		vo.setSearchUrl(config == null ? "" : StrUtil.nullToEmpty(config.getSearchUrl()));
		vo.setNetworkUrl(config == null ? "" : StrUtil.nullToEmpty(config.getNetworkUrl()));
		vo.setContentUrl(config == null ? "" : StrUtil.nullToEmpty(config.getContentUrl()));
		vo.setCollectUrl(config == null ? "" : StrUtil.nullToEmpty(config.getCollectUrl()));
		vo.setCommentUrl(config == null ? "" : StrUtil.nullToEmpty(config.getCommentUrl()));
		vo.setHaloUrl(config == null ? "" : StrUtil.nullToEmpty(config.getHaloUrl()));
		vo.setMessageUrl(config == null ? "" : StrUtil.nullToEmpty(config.getMessageUrl()));
		vo.setEmptyCartUrl(config == null ? "" : StrUtil.nullToEmpty(config.getEmptyCartUrl()));
		vo.setEmptyOrderUrl(config == null ? "" : StrUtil.nullToEmpty(config.getEmptyOrderUrl()));
		vo.setEmptySeckillUrl(config == null ? "" : StrUtil.nullToEmpty(config.getEmptySeckillUrl()));
		return vo;
	}

	/**
	 * 图片地址只允许 http/https 或站内相对路径：值会直接绑到 {@code <image src>}，
	 * 放开其它协议（如 javascript:/data:）等于给配置入口开了个注入口子。
	 */
	private String normalizeUrl(String value, String label) {
		String trimmed = StrUtil.trimToEmpty(value);
		if (StrUtil.isBlank(trimmed)) {
			return "";
		}
		if (trimmed.length() > URL_MAX_LENGTH) {
			throw new ArynBusinessException(label + "地址不能超过 " + URL_MAX_LENGTH + " 个字符");
		}
		if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("/")) {
			throw new ArynBusinessException(label + "仅支持 http/https 地址或站内相对路径");
		}
		return trimmed;
	}

	private String requireTenantId(String tenantId) {
		if (StrUtil.isBlank(tenantId)) {
			throw new ArynBusinessException("租户标识缺失");
		}
		return tenantId;
	}

}
