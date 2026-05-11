
package com.aryn.cloud.order;

import com.aryn.cloud.common.job.annotation.ArynEnableXxlJob;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 订单服务启动类
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:38
 */
@EnableDubbo
@ArynEnableXxlJob
@SpringBootApplication
public class ArynOrderApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynOrderApplication.class, args);
	}

}
