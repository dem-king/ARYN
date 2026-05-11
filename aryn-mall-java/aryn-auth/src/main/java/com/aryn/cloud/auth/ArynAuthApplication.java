
package com.aryn.cloud.auth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证授权模块
 *
 * @author 雨滴kian
 * @since 2022/2/18 14:45
 */
@EnableDubbo
@SpringBootApplication
public class ArynAuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynAuthApplication.class, args);
	}

}
