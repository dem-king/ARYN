package com.aryn.cloud.common.security.config;

import com.aryn.cloud.common.core.security.UserSupplier;
import com.aryn.cloud.common.security.util.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserSupplierConfiguration {

	@Bean
	public UserSupplier userSupplier() {
		return () -> {
			var user = SecurityUtils.getUser();
			return user != null ? user.getUsername() : null;
		};
	}

}
