
package com.aryn.cloud.pay;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 支付模块启动类
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:38
 */
@EnableDubbo
@SpringBootApplication
public class ArynPayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynPayApplication.class, args);
	}

}
