package com.aryn.cloud.user.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SalesAttributionDTO;
import com.aryn.cloud.promotion.api.remote.RemoteSalesStaffService;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.vo.SalesQrcodeVO;
import com.aryn.cloud.user.mapper.SocialUserMapper;
import com.aryn.cloud.user.service.ISalesQrcodeService;
import com.aryn.cloud.user.service.MiniAppBindingResolver;
import com.aryn.cloud.user.service.WxMaServiceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 销售个人推广小程序码
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesQrcodeServiceImpl implements ISalesQrcodeService {

	/** 扫码进入的页面（携带 scene 后由小程序端解析 sid） */
	private static final String QRCODE_PAGE = "pages/home/index";

	/** scene 前缀，与小程序分享参数保持一致：sid=销售会员ID（23 字符，满足 scene≤32 限制） */
	private static final String SCENE_PREFIX = "sid=";

	/** 小程序码尺寸 */
	private static final int QRCODE_WIDTH = 430;

	private static final String CACHE_KEY_PREFIX = "sales:qrcode:";

	/** 失败结果短缓存标记 */
	private static final String FAILURE_CACHE_MARKER = "__FAILED__";

	private static final Duration SUCCESS_CACHE_TTL = Duration.ofDays(7);

	private static final Duration FAILURE_CACHE_TTL = Duration.ofMinutes(5);

	private static final String QRCODE_FAILURE_MESSAGE = "推广码生成失败，请稍后重试或使用分享链接";

	private static final String NOT_ACTIVE_SALES_MESSAGE = "当前账号不是在职销售，无法生成推广码";

	private final SocialUserMapper socialUserMapper;

	private final MiniAppBindingResolver miniAppBindingResolver;

	private final WxMaServiceProvider wxMaServiceProvider;

	private final StringRedisTemplate stringRedisTemplate;

	@DubboReference
	private final RemoteSalesStaffService remoteSalesStaffService;

	/** 小程序版本：release 正式版 / trial 体验版 / develop 开发版，生成失败时按 trial → develop 降级重试 */
	@Value("${wx.miniapp.env-version:release}")
	private String envVersion = "release";

	@Override
	public SalesQrcodeVO getQrcode(String tenantId, String memberUserId) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(memberUserId)) {
			throw new ArynBusinessException("缺少租户或会员信息，无法生成推广码");
		}
		String cacheKey = buildCacheKey(tenantId, memberUserId);
		String cached = readCache(cacheKey);
		if (FAILURE_CACHE_MARKER.equals(cached)) {
			throw new ArynBusinessException(QRCODE_FAILURE_MESSAGE);
		}
		if (StringUtils.hasText(cached)) {
			log.debug("命中推广码缓存 tenantId={}, memberUserId={}", tenantId, memberUserId);
			return buildVo(cached, memberUserId);
		}

		// 1. 校验在职销售（promotion 域 Dubbo，方向由 user → promotion）
		SalesAttributionDTO staff;
		try {
			staff = remoteSalesStaffService.getActiveStaffByMemberUserId(tenantId, memberUserId);
		}
		catch (Exception e) {
			log.error("校验销售身份失败 tenantId={}, memberUserId={}", tenantId, memberUserId, e);
			throw new ArynBusinessException("销售身份校验失败，请稍后重试");
		}
		if (staff == null) {
			throw new ArynBusinessException(NOT_ACTIVE_SALES_MESSAGE);
		}

		// 2. 当前会员 → social_user 反查 appId（按当前租户，取最近更新的一条）
		SocialUser socialUser = socialUserMapper.selectOne(Wrappers.<SocialUser>lambdaQuery()
			.eq(SocialUser::getMallUserId, memberUserId)
			.isNotNull(SocialUser::getOpenId)
			.ne(SocialUser::getOpenId, "")
			.orderByDesc(SocialUser::getUpdateTime)
			.last("LIMIT 1"));
		if (socialUser == null || !StringUtils.hasText(socialUser.getAppId())) {
			throw new ArynBusinessException("未找到当前会员的小程序账号信息，无法生成推广码");
		}

		// 3. 权威校验 AppID↔租户绑定后再消费微信接口
		SocialAccount socialAccount = miniAppBindingResolver.requireBinding(socialUser.getAppId(), tenantId);
		WxMaService wxMaService = wxMaServiceProvider.create(socialAccount);
		String scene = SCENE_PREFIX + memberUserId;
		byte[] imageBytes = generateQrcodeBytes(wxMaService, scene);
		if (imageBytes == null || imageBytes.length == 0) {
			writeCache(cacheKey, FAILURE_CACHE_MARKER, FAILURE_CACHE_TTL);
			throw new ArynBusinessException(QRCODE_FAILURE_MESSAGE);
		}
		String qrcodeUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
		writeCache(cacheKey, qrcodeUrl, SUCCESS_CACHE_TTL);
		log.info("生成销售推广码成功 tenantId={}, memberUserId={}, staffId={}", tenantId, memberUserId,
			staff.getSalesStaffId());
		return buildVo(qrcodeUrl, memberUserId);
	}

	/**
	 * 按配置版本生成小程序码，失败时依次降级 trial → develop 各重试一次。
	 * 不同版本返回的错误（未发布/无体验版权限）不同，全部失败才视为失败。
	 */
	private byte[] generateQrcodeBytes(WxMaService wxMaService, String scene) {
		List<String> versions = new ArrayList<>();
		addEnvVersion(versions, envVersion);
		addEnvVersion(versions, "trial");
		addEnvVersion(versions, "develop");
		for (String version : versions) {
			try {
				byte[] bytes = wxMaService.getQrcodeService()
					.createWxaCodeUnlimitBytes(scene, QRCODE_PAGE, false, version, QRCODE_WIDTH, false, null, true);
				if (bytes != null && bytes.length > 0) {
					if (!version.equals(envVersion)) {
						log.info("推广码使用降级版本生成成功 envVersion={}", version);
					}
					return bytes;
				}
				log.warn("推广码生成返回空内容 envVersion={}", version);
			}
			catch (Exception e) {
				log.warn("推广码生成失败 envVersion={}, error={}", version, e.getMessage());
			}
		}
		log.error("推广码生成失败，已尝试版本 {}", versions);
		return null;
	}

	private void addEnvVersion(List<String> versions, String version) {
		if (StringUtils.hasText(version) && !versions.contains(version)) {
			versions.add(version);
		}
	}

	private SalesQrcodeVO buildVo(String qrcodeUrl, String memberUserId) {
		SalesQrcodeVO vo = new SalesQrcodeVO();
		vo.setQrcodeUrl(qrcodeUrl);
		vo.setPage(QRCODE_PAGE);
		vo.setScene(SCENE_PREFIX + memberUserId);
		return vo;
	}

	private String buildCacheKey(String tenantId, String memberUserId) {
		return CACHE_KEY_PREFIX + tenantId + ":" + memberUserId;
	}

	private String readCache(String cacheKey) {
		try {
			return stringRedisTemplate.opsForValue().get(cacheKey);
		}
		catch (Exception e) {
			// 缓存不可用不影响生成推广码，降级为直接调用微信接口
			log.warn("读取推广码缓存失败 cacheKey={}, error={}", cacheKey, e.getMessage());
			return null;
		}
	}

	private void writeCache(String cacheKey, String value, Duration ttl) {
		try {
			stringRedisTemplate.opsForValue().set(cacheKey, value, ttl);
		}
		catch (Exception e) {
			log.warn("写入推广码缓存失败 cacheKey={}, error={}", cacheKey, e.getMessage());
		}
	}

}
