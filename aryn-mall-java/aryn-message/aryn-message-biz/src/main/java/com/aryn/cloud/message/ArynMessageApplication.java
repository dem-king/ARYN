package com.aryn.cloud.message;

import com.aryn.cloud.common.job.annotation.ArynEnableXxlJob;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 站内信与客服会话服务启动类。
 */
@EnableDubbo
@ArynEnableXxlJob
@SpringBootApplication
public class ArynMessageApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynMessageApplication.class, args);
	}

}
