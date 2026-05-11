
package com.aryn.cloud.visual.monitor;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 监控模块
 *
 * @author 雨滴kian
 * @since 2022/5/19 9:33
 */
@EnableAdminServer
@SpringBootApplication
public class ArynMonitorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArynMonitorApplication.class, args);

	}

}
