package com.aryn.cloud.vessel;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 船舶与靠港计划服务启动类。
 *
 * @author aryn
 * @since 2026/9/11
 */
@EnableDubbo
@SpringBootApplication
public class ArynVesselApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynVesselApplication.class, args);
	}

}
