
package com.aryn.cloud.user;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商城用户启动类
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:38
 */
@EnableDubbo
@SpringBootApplication
public class ArynUserApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynUserApplication.class, args);
	}

}
