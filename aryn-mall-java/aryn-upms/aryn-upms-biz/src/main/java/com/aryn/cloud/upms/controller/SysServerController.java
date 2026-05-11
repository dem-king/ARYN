
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.upms.api.vo.server.Server;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CPU相关信息
 *
 * @author 雨滴kian
 * @since 2022/5/21 15:35
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/sysserver")
@Tag(description = "sysserver", name = "服务器监控")
public class SysServerController {

	@Operation(summary = "查询服务器监控信息")
	@GetMapping
	@SaCheckPermission("upms:sysserver:get")
	public Result<Server> getServerInfo() throws Exception {
		Server server = new Server();
		server.copyTo();
		return Result.success(server);
	}

}
