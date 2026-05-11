
package com.aryn.cloud.user.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.hutool.core.util.ObjectUtil;
import com.aryn.cloud.common.core.util.SpringUtils;
import com.aryn.cloud.user.api.entity.SocialAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 微信小程序配置
 *
 * @author 雨滴kian
 * @date 2022/6/10
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WxMiniAppConfiguration {

	public static WxMaService getMaService(String appId) {
		WxMiniAppConfigCache wxMiniAppConfigCache = SpringUtils.getBean(WxMiniAppConfigCache.class);
		StringRedisTemplate stringRedisTemplate = SpringUtils.getBean(StringRedisTemplate.class);

		SocialAccount socialAccount = wxMiniAppConfigCache.getByAppId(appId);
		if (ObjectUtil.isNull(socialAccount)) {
			throw new RuntimeException("三方账号配置不存在");
		}
		WxMiniAppRedisConfigStorage configStorage = new WxMiniAppRedisConfigStorage(stringRedisTemplate);
		configStorage.setAppid(socialAccount.getAppId());
		configStorage.setSecret(socialAccount.getAppSecret());
		WxMaService wxMaService = new WxMaServiceImpl();
		wxMaService.setWxMaConfig(configStorage);

		return wxMaService;
	}

}
