package com.aryn.cloud.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;

/**
 * 代码生成器
 *
 * @author 雨滴kian
 * @since 2025/10/22
 */
@EnableDubbo
@SpringBootApplication
public class ArynGenApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynGenApplication.class, args);
	}

}
