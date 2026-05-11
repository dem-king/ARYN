
package com.aryn.cloud.promotion;

import com.aryn.cloud.common.job.annotation.ArynEnableXxlJob;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商城营销启动类
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:38
 */
@EnableDubbo
@ArynEnableXxlJob
@SpringBootApplication
public class ArynPromotionApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynPromotionApplication.class, args);
	}

}
