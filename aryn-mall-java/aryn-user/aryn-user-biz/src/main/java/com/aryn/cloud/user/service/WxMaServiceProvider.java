package com.aryn.cloud.user.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.config.WxMiniAppConfiguration;
import org.springframework.stereotype.Component;

/**
 * WxMaService 构造入口。
 * <p>
 * 业务代码统一通过该组件获取小程序 SDK 实例，便于单元测试替换成 mock，
 * 避免直接依赖静态工具方法。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Component
public class WxMaServiceProvider {

	/**
	 * 用权威解析得到的配置快照构造 WxMaService。
	 * @param validatedConfig 已通过 AppID↔租户校验的小程序配置
	 * @return 小程序 SDK 实例
	 */
	public WxMaService create(SocialAccount validatedConfig) {
		return WxMiniAppConfiguration.createMaService(validatedConfig);
	}

}
