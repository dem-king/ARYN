package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 秒杀场次管理端查询：装修器「秒杀」组件手选场次与按 ID 回填共用。
 * 活动详情内的场次仍走 /seckill/activity/{id}，这里只做跨活动的列表查询。
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/seckill/session")
@Tag(description = "seckill-session", name = "秒杀场次管理")
public class SeckillSessionController {

	private final ISeckillSessionService seckillSessionService;

	@Operation(summary = "秒杀场次列表")
	@SaCheckPermission("promotion:seckill:page")
	@GetMapping("/page")
	public Result page(Page page,
			@RequestParam(value = "ids", required = false) List<String> ids,
			@RequestParam(value = "sessionName", required = false) String sessionName) {
		return Result.success(seckillSessionService.getAdminPage(page, ids, sessionName));
	}

}
