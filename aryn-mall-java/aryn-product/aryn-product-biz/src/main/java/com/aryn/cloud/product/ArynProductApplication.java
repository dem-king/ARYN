
package com.aryn.cloud.product;

import com.aryn.cloud.common.job.annotation.ArynEnableXxlJob;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商城商品启动类
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:38
 */
@EnableDubbo
@ArynEnableXxlJob
@SpringBootApplication
public class ArynProductApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynProductApplication.class, args);
	}

}
