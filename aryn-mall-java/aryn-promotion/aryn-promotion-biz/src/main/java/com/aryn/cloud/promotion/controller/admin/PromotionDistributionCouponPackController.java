package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.DistributionCouponPackSaveDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPack;
import com.aryn.cloud.promotion.api.vo.DistributionCouponPackVO;
import com.aryn.cloud.promotion.service.IDistributionCouponPackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分销券包配置（后台）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/distribution/coupon/pack")
@Tag(name = "分销券包", description = "分销佣金兑换券包配置")
public class PromotionDistributionCouponPackController {

	private final IDistributionCouponPackService distributionCouponPackService;

	@Operation(summary = "券包分页")
	@SaCheckPermission("promotion:distributioncouponpack:page")
	@GetMapping("/page")
	public Result<IPage<DistributionCouponPackVO>> page(Page<DistributionCouponPack> page,
			DistributionCouponPack query) {
		return Result.success(distributionCouponPackService.getPage(page, query));
	}

	@Operation(summary = "全部券包列表")
	@SaCheckPermission("promotion:distributioncouponpack:page")
	@GetMapping("/list")
	public Result<List<DistributionCouponPackVO>> list() {
		return Result.success(distributionCouponPackService.listAll());
	}

	@Operation(summary = "券包详情")
	@SaCheckPermission("promotion:distributioncouponpack:page")
	@GetMapping("/{id}")
	public Result<DistributionCouponPackVO> get(@PathVariable String id) {
		return Result.success(distributionCouponPackService.getDetail(id));
	}

	@SysLog("新增分销券包")
	@Operation(summary = "新增券包")
	@SaCheckPermission("promotion:distributioncouponpack:add")
	@PostMapping("/add")
	public Result<Boolean> add(@Valid @RequestBody DistributionCouponPackSaveDTO dto) {
		return Result.success(distributionCouponPackService.savePack(dto));
	}

	@SysLog("编辑分销券包")
	@Operation(summary = "编辑券包")
	@SaCheckPermission("promotion:distributioncouponpack:edit")
	@PutMapping("/edit")
	public Result<Boolean> edit(@Valid @RequestBody DistributionCouponPackSaveDTO dto) {
		return Result.success(distributionCouponPackService.updatePack(dto));
	}

	@SysLog("分销券包上下架")
	@Operation(summary = "券包上下架")
	@SaCheckPermission("promotion:distributioncouponpack:edit")
	@PostMapping("/status")
	public Result<Boolean> status(@RequestBody StatusRequest request) {
		if (request == null || request.getId() == null || request.getId().isBlank()) {
			return Result.fail("券包ID不能为空");
		}
		return Result.success(distributionCouponPackService.updateStatus(request.getId(), request.getStatus()));
	}

	@SysLog("删除分销券包")
	@Operation(summary = "删除券包")
	@SaCheckPermission("promotion:distributioncouponpack:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> remove(@PathVariable String id) {
		return Result.success(distributionCouponPackService.removePack(id));
	}

	/**
	 * 上下架请求体
	 */
	@Data
	public static class StatusRequest {

		private String id;

		private String status;

	}

}
