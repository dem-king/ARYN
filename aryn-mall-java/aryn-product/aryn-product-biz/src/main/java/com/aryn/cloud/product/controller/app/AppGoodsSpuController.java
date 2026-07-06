package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Searchable;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.search.MeilisearchTemplate;
import com.aryn.cloud.common.search.SearchConstants;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IGoodsSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品spu - C端接口
 *
 * @author 雨滴kian
 * @since 2022/3/1 10:13
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/product")
@Tag(description = "app-goodsspu", name = "商品spu-API")
public class AppGoodsSpuController {

	private final IGoodsSpuService goodsSpuService;

	private final MeilisearchTemplate meilisearchTemplate;

	@Operation(summary = "商品列表")
	@GetMapping("/goodsspu/page")
	public Result page(Page page, GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.apiPage(page, goodsSpu));
	}

	@Operation(summary = "通过id查询商品")
	@GetMapping("/goodsspu/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsSpuService.getApiSpuById(id));
	}

	@Operation(summary = "通过ids查询商品")
	@GetMapping("/goodsspu/list/{ids}")
	public Result<List<GoodsSpu>> getById(@PathVariable List<String> ids) {
		return Result.success(goodsSpuService.listByIds(ids));
	}

	@Operation(summary = "获取热搜商品 Top10")
	@GetMapping("/goodsspu/hot-search/top10")
	public Result<List<GoodsSpu>> getTop10HotSearchGoods() {
		return Result.success(goodsSpuService.getTop10HotSearchGoods());
	}

	/**
	 * C端商品搜索（Meilisearch + DB 降级）
	 * @param q           搜索关键词
	 * @param category    分类ID（分面过滤）
	 * @param priceRange  价格区间，格式：min-max（如 100-500）
	 * @param sort        排序方式：sales/price_asc/price_desc/comprehensive
	 * @param page        页码（从1开始）
	 * @param limit       每页条数
	 * @return 搜索结果
	 */
	@Operation(summary = "商品搜索")
	@GetMapping("/search")
	public Result<Map<String, Object>> search(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String category,
			@RequestParam(name = "price_range", required = false) String priceRange,
			@RequestParam(defaultValue = "comprehensive") String sort,
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "10") Integer limit) {

		String tenantId = ArynTenantContextHolder.getTenantId();
		try {
			Map<String, Object> result = searchFromMeilisearch(q, category, priceRange, sort, page, limit, tenantId);
			return Result.success(result);
		}
		catch (Exception e) {
			log.warn("[搜索降级] Meilisearch不可用，回退到DB查询, error={}", e.getMessage());
			return Result.success(searchFromDb(q, category, priceRange, sort, page, limit, tenantId));
		}
	}

	/**
	 * 搜索建议（联想词）
	 * @param q 搜索关键词前缀
	 * @return 联想词列表
	 */
	@Operation(summary = "搜索建议")
	@GetMapping("/suggest")
	public Result<List<String>> suggest(@RequestParam String q) {
		if (!StringUtils.hasText(q)) {
			return Result.success(Collections.emptyList());
		}
		String tenantId = ArynTenantContextHolder.getTenantId();
		try {
			List<String> suggestions = searchSuggestions(q, tenantId);
			return Result.success(suggestions);
		}
		catch (Exception e) {
			log.warn("[搜索建议降级] Meilisearch不可用, error={}", e.getMessage());
			return Result.success(Collections.emptyList());
		}
	}

	/**
	 * 从 Meilisearch 执行搜索
	 */
	private Map<String, Object> searchFromMeilisearch(String q, String category, String priceRange,
			String sort, Integer page, Integer limit, String tenantId) {

		// 构建 filter
		List<String> filters = new ArrayList<>();
		filters.add(SearchConstants.SPU_FIELD_STATUS + " = 1");
		if (StringUtils.hasText(tenantId)) {
			filters.add(SearchConstants.SPU_FIELD_TENANT_ID + " = " + tenantId);
		}
		if (StringUtils.hasText(category)) {
			filters.add(SearchConstants.SPU_FIELD_CATEGORY_ID + " = " + category);
		}
		if (StringUtils.hasText(priceRange)) {
			String[] parts = priceRange.split("-");
			if (parts.length == 2) {
				filters.add(SearchConstants.SPU_FIELD_PRICE + " >= " + parts[0]
						+ " AND " + SearchConstants.SPU_FIELD_PRICE + " <= " + parts[1]);
			}
		}

		// 构建 sort
		String[] sortArr = buildSort(sort);

		SearchRequest.SearchRequestBuilder builder = SearchRequest.builder()
				.q(StringUtils.hasText(q) ? q : "")
				.page(page)
				.hitsPerPage(limit)
				.filter(filters.toArray(new String[0]))
				.sort(sortArr);

		SearchRequest searchRequest = builder.build();
		Searchable searchable = meilisearchTemplate.search(SearchConstants.SPU_INDEX, q, searchRequest);

		// 组装结果
		Map<String, Object> result = new HashMap<>();
		result.put("hits", searchable.getHits());
		result.put("query", searchable.getQuery());
		result.put("processingTimeMs", searchable.getProcessingTimeMs());
		return result;
	}

	/**
	 * 从 DB 执行搜索（降级方案）
	 */
	private Map<String, Object> searchFromDb(String q, String category, String priceRange,
			String sort, Integer page, Integer limit, String tenantId) {

		LambdaQueryWrapper<GoodsSpu> wrapper = Wrappers.<GoodsSpu>lambdaQuery()
				.eq(GoodsSpu::getStatus, "1")
				.eq(GoodsSpu::getDelFlag, "0");

		if (StringUtils.hasText(tenantId)) {
			wrapper.eq(GoodsSpu::getTenantId, tenantId);
		}
		if (StringUtils.hasText(q)) {
			wrapper.and(w -> w.like(GoodsSpu::getName, q).or().like(GoodsSpu::getSubTitle, q));
		}
		if (StringUtils.hasText(category)) {
			wrapper.eq(GoodsSpu::getCategorySecondId, category);
		}
		if (StringUtils.hasText(priceRange)) {
			String[] parts = priceRange.split("-");
			if (parts.length == 2) {
				wrapper.ge(GoodsSpu::getSalesPrice, new java.math.BigDecimal(parts[0]))
						.le(GoodsSpu::getSalesPrice, new java.math.BigDecimal(parts[1]));
			}
		}

		// 排序
		switch (sort) {
			case "sales" -> wrapper.orderByDesc(GoodsSpu::getSalesVolume);
			case "price_asc" -> wrapper.orderByAsc(GoodsSpu::getSalesPrice);
			case "price_desc" -> wrapper.orderByDesc(GoodsSpu::getSalesPrice);
			default -> wrapper.orderByDesc(GoodsSpu::getCreateTime);
		}

		Page<GoodsSpu> pageParam = new Page<>(page, limit);
		Page<GoodsSpu> pageResult = goodsSpuService.page(pageParam, wrapper);

		Map<String, Object> result = new HashMap<>();
		result.put("hits", pageResult.getRecords());
		result.put("total", pageResult.getTotal());
		result.put("page", pageResult.getCurrent());
		result.put("limit", pageResult.getSize());
		return result;
	}

	/**
	 * 构建排序规则
	 */
	private String[] buildSort(String sort) {
		return switch (sort) {
			case "sales" -> new String[] { SearchConstants.SPU_FIELD_SALES + ":desc" };
			case "price_asc" -> new String[] { SearchConstants.SPU_FIELD_PRICE + ":asc" };
			case "price_desc" -> new String[] { SearchConstants.SPU_FIELD_PRICE + ":desc" };
			default -> new String[] { SearchConstants.SPU_FIELD_CREATE_TIME + ":desc" };
		};
	}

	/**
	 * 从 Meilisearch 获取搜索建议
	 */
	private List<String> searchSuggestions(String q, String tenantId) {
		List<String> filters = new ArrayList<>();
		filters.add(SearchConstants.SPU_FIELD_STATUS + " = 1");
		if (StringUtils.hasText(tenantId)) {
			filters.add(SearchConstants.SPU_FIELD_TENANT_ID + " = " + tenantId);
		}

		SearchRequest searchRequest = SearchRequest.builder()
				.q(q)
				.limit(10)
				.attributesToRetrieve(new String[] { SearchConstants.SPU_FIELD_NAME })
				.filter(filters.toArray(new String[0]))
				.build();

		Searchable searchable = meilisearchTemplate.search(SearchConstants.SPU_INDEX, q, searchRequest);
		if (searchable.getHits() == null) {
			return Collections.emptyList();
		}
		return searchable.getHits().stream()
				.map(hit -> (String) hit.get(SearchConstants.SPU_FIELD_NAME))
				.filter(Objects::nonNull)
				.distinct()
				.limit(10)
				.collect(Collectors.toList());
	}

}
