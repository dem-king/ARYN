
package com.aryn.cloud;

import com.aryn.cloud.common.job.annotation.ArynEnableXxlJob;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 单体模块启动类
 *
 * @author 雨滴kian
 * @since 2022/2/18 13:52
 */

@ArynEnableXxlJob
@SpringBootApplication
public class ArynBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynBootApplication.class, args);
	}

}