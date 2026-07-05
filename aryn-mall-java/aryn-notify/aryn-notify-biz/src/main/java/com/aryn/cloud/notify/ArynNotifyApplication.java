
package com.aryn.cloud.notify;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 站内信通知服务启动类
 *
 * @author aryn
 * @since 2026/07/05
 */
@EnableDubbo
@SpringBootApplication
public class ArynNotifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynNotifyApplication.class, args);
	}

}
