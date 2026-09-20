
package com.aryn.cloud.product.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.vo.ShipProductSummaryVO;
import com.aryn.cloud.product.service.IShipProductProfileService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IGoodsSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品spu
 *
 * @author 雨滴kian
 * @since 2022/3/1 10:13
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/goodsspu")
@Tag(description = "app-goodsspu", name = "商品spu-API")
public class AppGoodsSpuController {

	private final IGoodsSpuService goodsSpuService;

	private final IShipProductProfileService shipProductProfileService;

	@Operation(summary = "商品列表")
	@GetMapping("/page")
	public Result page(Page page, GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.apiPage(page, goodsSpu));
	}

	@Operation(summary = "船供商品目录（sale_scope 2/3，仅上架商品）")
	@GetMapping("/ship/page")
	public Result<IPage<ShipProductSummaryVO>> shipPage(Page<ShipProductSummaryVO> page, ShipProductSummaryVO query) {
		// C 端只展示上架商品。管理端复用同一查询且需要看到下架商品，
		// 因此在此强制而非写死在 SQL 里。
		query.setStatus("1");
		return Result.success(
				shipProductProfileService.shipSummaryPage(ArynTenantContextHolder.getTenantId(), page, query));
	}

	@Operation(summary = "商品船供资料摘要（C 端详情页展示采购单位/箱规/MOQ/步长/储存条件）")
	@GetMapping("/ship-summary/{spuId}")
	public Result<java.util.Map<String, Object>> shipSummary(@PathVariable String spuId) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		java.util.Map<String, Object> summary = new java.util.HashMap<>();
		summary.put("profile", shipProductProfileService.getProfile(tenantId, spuId));
		summary.put("skuProfiles", shipProductProfileService.listSkuProfiles(tenantId, spuId));
		return Result.success(summary);
	}

	@Operation(summary = "船供/个人商品搜索（关键词匹配中英文、IMPA/ISSA、条码、别名）")
	@GetMapping("/search")
	public Result<IPage<ShipProductSummaryVO>> search(Page<ShipProductSummaryVO> page,
			@RequestParam(value = "scene", defaultValue = "1") String scene,
			@RequestParam(value = "keyword", required = false) String keyword) {
		ShipProductSummaryVO query = new ShipProductSummaryVO();
		// 统一关键词交给 SQL 在「编码组」与「名称组」之间取 OR。
		// 历史缺陷：分别赋值给 nameEn 与 impaCode，两个 <if> 是 AND 关系，
		// 按 IMPA 码或按中文品名单独搜索均返回 0 条。
		query.setKeyword(keyword);
		query.setStatus("1");
		IPage<ShipProductSummaryVO> result = "2".equals(scene)
				? shipProductProfileService.shipSummaryPage(ArynTenantContextHolder.getTenantId(), page, query)
				: shipProductProfileService.personalSummaryPage(ArynTenantContextHolder.getTenantId(), page, query);
		return Result.success(result);
	}

	@Operation(summary = "通过id查询商品")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsSpuService.getApiSpuById(id));
	}

	@Operation(summary = "通过ids查询商品")
	@GetMapping("/list/{ids}")
	public Result<List<GoodsSpu>> getById(@PathVariable List<String> ids) {
		return Result.success(goodsSpuService
			.list(Wrappers.<GoodsSpu>lambdaQuery().in(GoodsSpu::getId, ids).eq(GoodsSpu::getStatus, "1")));
	}

	@Operation(summary = "获取热搜商品 Top10")
	@GetMapping("/hot-search/top10")
	public Result<List<GoodsSpu>> getTop10HotSearchGoods() {
		return Result.success(goodsSpuService.getTop10HotSearchGoods());
	}

}
