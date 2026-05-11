
package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.service.IPageDesignService;
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

	private final IPageDesignService pageDesignService;

	@Operation(summary = "页面设计查询")
	@GetMapping
	public Result<PageDesign> getHomePage(PageDesign request) {
		return Result.success(pageDesignService.getHomePage(request));
	}

	@Operation(summary = "通过id查询")
	@GetMapping("/{id}")
	public Result<PageDesign> getById(@PathVariable("id") String id) {
		return Result.success(pageDesignService.getById(id));
	}

}
