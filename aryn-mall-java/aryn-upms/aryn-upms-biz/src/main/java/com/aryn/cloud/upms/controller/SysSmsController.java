
package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.upms.service.ISysSmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sms")
@Tag(description = "sms", name = "短信服务")
public class SysSmsController {

	private final ISysSmsService sysSmsService;

	/**
	 * 发送短信
	 * @param type 短信类型
	 * @param mobile 手机号
	 * @return
	 */
	@Operation(summary = "发送短信")
	@GetMapping("/{type}/{mobile}")
	public Result<Boolean> send(@PathVariable String type, @PathVariable String mobile) {
		return Result.success(sysSmsService.sendSmsCode(mobile, type));
	}

}
