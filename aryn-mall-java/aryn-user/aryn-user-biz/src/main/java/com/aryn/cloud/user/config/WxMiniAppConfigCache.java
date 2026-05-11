
package com.aryn.cloud.user.config;

import com.aryn.cloud.user.api.entity.SocialAccount;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component

public class WxMiniAppConfigCache {

	// appId -> 配置（可从 appId 直接找配置）
	private final Map<String, SocialAccount> appIdMap = new ConcurrentHashMap<>();

	public void addConfigs(List<SocialAccount> configs) {
		configs.forEach(config -> appIdMap.put(config.getAppId(), config));
	}

	public SocialAccount getByAppId(String appId) {
		return appIdMap.get(appId);
	}

	public void updateConfig(SocialAccount config) {
		appIdMap.put(config.getAppId(), config);
	}

	public void removeByAppId(String appId) {
		appIdMap.remove(appId);
	}

	public Collection<SocialAccount> getAllConfigs() {
		return appIdMap.values();
	}

}
