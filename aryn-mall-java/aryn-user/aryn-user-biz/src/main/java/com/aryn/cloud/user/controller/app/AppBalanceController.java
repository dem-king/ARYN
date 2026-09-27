package com.aryn.cloud.user.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.vo.AppBalanceRecordVO;
import com.aryn.cloud.user.service.IBalanceRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端余额变动记录
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/balance")
@Tag(name = "余额记录-API")
public class AppBalanceController {

	private final IBalanceRecordService balanceRecordService;

	@Operation(summary = "我的余额变动记录")
	@GetMapping("/records")
	public Result<IPage<AppBalanceRecordVO>> records(Page page) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(balanceRecordService.getUserPage(page, userId).convert(AppBalanceRecordVO::from));
	}

}
