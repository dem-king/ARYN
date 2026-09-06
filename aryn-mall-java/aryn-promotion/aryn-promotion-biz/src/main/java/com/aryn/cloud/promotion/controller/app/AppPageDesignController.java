
package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.api.vo.AppPageDesignVO;
import com.aryn.cloud.promotion.service.PageDesignMetricService;
import com.aryn.cloud.promotion.service.PageDesignPreviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 页面设计
 *
 * @author 雨滴kian
 * @date 2022/12/07
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/pagedesign")
@Tag(description = "app-pagedesign", name = "页面设计-API")
public class AppPageDesignController {

	private final PageDesignPreviewService pageDesignPreviewService;

	private final PageDesignMetricService pageDesignMetricService;

	@Operation(summary = "页面设计查询")
	@GetMapping
	public Result<AppPageDesignVO> getHomePage() {
		return Result.success(pageDesignPreviewService.getPublishedHome());
	}

	@Operation(summary = "通过id查询")
	@GetMapping("/{id}")
	public Result<AppPageDesignVO> getById(@PathVariable("id") String id) {
		return Result.success(pageDesignPreviewService.getPublished(id));
	}

	@Operation(summary = "Preview a page draft with a short-lived token")
	@GetMapping("/preview/{token}")
	public Result<AppPageDesignVO> preview(@PathVariable String token) {
		return Result.success(pageDesignPreviewService.getPreview(token));
	}

	@Operation(summary = "上报页面装修埋点事件")
	@PostMapping("/metrics")
	public Result<Integer> reportMetrics(@RequestBody List<PageDesignMetricService.MetricEvent> events) {
		return Result.success(pageDesignMetricService.report(events));
	}

}
