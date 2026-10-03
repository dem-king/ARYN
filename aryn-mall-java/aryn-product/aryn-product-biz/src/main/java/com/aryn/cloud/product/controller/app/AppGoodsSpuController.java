
package com.aryn.cloud.product.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.vo.GoodsCatalogSummaryVO;
import com.aryn.cloud.product.api.vo.QuickCartInfoVO;
import com.aryn.cloud.product.service.IQuickCartService;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

	private final IQuickCartService quickCartService;

	@Operation(summary = "商品列表")
	@GetMapping("/page")
	public Result page(Page page, GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.apiPage(page, goodsSpu));
	}

	@Operation(summary = "场景选货目录（统一商品池，仅上架商品；路径保留 scene=2 兼容）")
	@GetMapping("/ship/page")
	public Result<IPage<GoodsCatalogSummaryVO>> shipPage(Page<GoodsCatalogSummaryVO> page,
			@RequestParam(value = "keyword", required = false) String keyword) {
		// C 端只展示上架商品。船供资料下线后目录即统一商品池，
		// 路径保留以兼容共享购物车补选入口。
		return Result.success(goodsSpuService.catalogPage(ArynTenantContextHolder.getTenantId(), page, keyword, "1"));
	}

	@Operation(summary = "商品搜索（关键词匹配名称/子标题；scene 参数保留兼容，商品池已统一）")
	@GetMapping("/search")
	public Result<IPage<GoodsCatalogSummaryVO>> search(Page<GoodsCatalogSummaryVO> page,
			@RequestParam(value = "scene", defaultValue = "1") String scene,
			@RequestParam(value = "keyword", required = false) String keyword) {
		return Result.success(goodsSpuService.catalogPage(ArynTenantContextHolder.getTenantId(), page, keyword, "1"));
	}

	@Operation(summary = "通过id查询商品")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsSpuService.getApiSpuById(id));
	}

	@Operation(summary = "快捷加购信息（列表页加购按钮按需查询 SKU/库存/MOQ）")
	@GetMapping("/quick-cart/{id}")
	public Result<QuickCartInfoVO> quickCart(@PathVariable String id) {
		return Result.success(quickCartService.getQuickCartInfo(id));
	}

	@Operation(summary = "通过ids查询商品")
	@GetMapping("/list/{ids}")
	public Result<List<GoodsSpu>> getById(@PathVariable List<String> ids) {
		// 查询与出参脱敏收拢在服务层（仅上架 + 成本价不下发），控制器保持轻薄
		return Result.success(goodsSpuService.apiListByIds(ids));
	}

	@Operation(summary = "获取热搜商品 Top10")
	@GetMapping("/hot-search/top10")
	public Result<List<GoodsSpu>> getTop10HotSearchGoods() {
		return Result.success(goodsSpuService.getTop10HotSearchGoods());
	}

}
