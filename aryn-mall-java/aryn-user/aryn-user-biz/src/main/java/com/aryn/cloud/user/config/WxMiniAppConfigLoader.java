
package com.aryn.cloud.user.config;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.service.ISocialAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WxMiniAppConfigLoader implements ApplicationRunner {

	private final WxMiniAppConfigCache configCache;

	private final ISocialAccountService socialAccountService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		List<SysTenant> tenants = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(tenants)) {
			tenants.forEach(tenant -> {
				ArynTenantContextHolder.setTenantId(tenant.getId());
				List<SocialAccount> configs = socialAccountService.list();
				configCache.addConfigs(configs);
				ArynTenantContextHolder.removeTenantId();
			});
			log.info("全部三方账号配置信息条数：{} 条", configCache.getAllConfigs().size());

		}
	}

}
