
package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.excel.ExcelUtils;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.product.api.dto.GoodsSpuShelfDTO;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.excel.GoodsSpuExportVO;
import com.aryn.cloud.product.excel.GoodsSpuImportDTO;
import com.aryn.cloud.product.excel.GoodsSpuImportListener;
import com.aryn.cloud.product.service.IBrandService;
import com.aryn.cloud.product.service.IGoodsCategoryService;
import com.aryn.cloud.product.service.IGoodsSkuService;
import com.aryn.cloud.product.service.IGoodsSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品spu前端控制器
 *
 * @author 雨滴kian
 * @since 2022/2/22 14:33
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/goodsspu")
@Tag(description = "goodsspu", name = "商品spu")
public class GoodsSpuController {

	private final IGoodsSpuService goodsSpuService;

	private final IGoodsCategoryService goodsCategoryService;

	private final IBrandService brandService;

	private final IGoodsSkuService goodsSkuService;

	@Operation(summary = "商品列表")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/page")
	public Result page(Page page, GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.adminPage(page, goodsSpu));
	}

	@Operation(summary = "商品库列表")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/warehouse/page")
	public Result warehousePage(Page page, GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.warehousePage(page, goodsSpu));
	}

	@Operation(summary = "通过id查询商品")
	@SaCheckPermission("product:goodsspu:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsSpuService.getSpuById(id));
	}

	@Operation(summary = "通过ids查询商品")
	@SaCheckPermission("product:goodsspu:get")
	@GetMapping("/byIds/{ids}")
	public Result getByIds(@PathVariable List<String> ids) {
		return Result.success(goodsSpuService.listByIds(ids));
	}

	@SysLog("新增商品")
	@Operation(summary = "商品新增")
	@SaCheckPermission("product:goodsspu:add")
	@PostMapping
	public Result add(@RequestBody GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.saveGoods(goodsSpu));
	}

	@SysLog("修改商品")
	@Operation(summary = "商品修改")
	@SaCheckPermission("product:goodsspu:edit")
	@PutMapping
	public Result edit(@RequestBody GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.updateGoods(goodsSpu));
	}

	@SysLog("删除商品")
	@Operation(summary = "商品删除")
	@SaCheckPermission("product:goodsspu:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(goodsSpuService.removeById(id));
	}

	@SysLog("修改商品状态")
	@Operation(summary = "商品状态修改")
	@SaCheckPermission("product:goodsspu:add")
	@PostMapping("/update/status")
	public Result updateStatus(@RequestBody GoodsSpu goodsSpu) {
		GoodsSpu goodsSpu1 = goodsSpuService.getById(goodsSpu.getId());
		if (ObjectUtil.isNull(goodsSpu1)) {
			return Result.fail("商品不存在");
		}
		goodsSpu1.setStatus(goodsSpu.getStatus());
		goodsSpuService.updateById(goodsSpu1);
		return Result.success(goodsSpu1);
	}

	@SysLog("商品上下架")
	@Operation(summary = "商品上下架")
	@SaCheckPermission("product:goodsspu:edit")
	@PostMapping("/goods-shelf")
	public Result goodsShelf(@RequestBody GoodsSpuShelfDTO goodsSpuShelfDTO) {
		GoodsSpu goodsSpu = new GoodsSpu();
		goodsSpu.setStatus(goodsSpuShelfDTO.getStatus());
		goodsSpuService.update(goodsSpu,
				Wrappers.<GoodsSpu>lambdaQuery().in(GoodsSpu::getId, goodsSpuShelfDTO.getSpuIds()));
		return Result.success();
	}

	@Operation(summary = "商品数量统计查询")
	@GetMapping("/count")
	public Result count() {
		long allCount = goodsSpuService.count(Wrappers.lambdaQuery());
		LocalDateTime localDateTime = LocalDateTime.now();
		long todayCount = goodsSpuService.count(Wrappers.<GoodsSpu>lambdaQuery()
			.ge(GoodsSpu::getCreateTime, LocalDateTimeUtil.beginOfDay(localDateTime))
			.le(GoodsSpu::getCreateTime, LocalDateTimeUtil.endOfDay(localDateTime)));
		Map<String, Object> rt = new HashMap<>();
		// 今日数量
		rt.put("todayCount", todayCount);
		// 全部数量
		rt.put("allCount", allCount);
		return Result.success(rt);
	}

	@SysLog("商品导出")
	@Operation(summary = "商品导出")
	@SaCheckPermission("product:goodsspu:export")
	@GetMapping("/export")
	public void export(GoodsSpu goodsSpu, HttpServletResponse response) {
		List<GoodsSpu> list = goodsSpuService.list(Wrappers.lambdaQuery(goodsSpu));
		List<GoodsSpuExportVO> exportList = list.stream().map(this::toGoodsSpuExportVO).toList();
		ExcelUtils.export(response, "商品列表", GoodsSpuExportVO.class, exportList);
	}

	@SysLog("商品批量导入")
	@Operation(summary = "商品批量导入")
	@SaCheckPermission("product:goodsspu:import")
	@PostMapping("/import")
	public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
		GoodsSpuImportListener listener = new GoodsSpuImportListener(goodsSpuService, goodsCategoryService,
				brandService, goodsSkuService);
		ExcelUtils.importExcel(file, listener);
		Map<String, Object> result = new HashMap<>();
		result.put("successCount", listener.getSuccessCount());
		result.put("errorMessages", listener.getErrorMessages());
		return Result.success(result);
	}

	@Operation(summary = "商品导入模板下载")
	@SaCheckPermission("product:goodsspu:import")
	@GetMapping("/import-template")
	public void importTemplate(HttpServletResponse response) {
		ExcelUtils.downloadTemplate(response, "商品导入模板", GoodsSpuImportDTO.class);
	}

	@SysLog("全量重建搜索索引")
	@Operation(summary = "全量重建搜索索引")
	@SaCheckPermission("product:goodsspu:search-rebuild")
	@PostMapping("/search/rebuild")
	public Result<Long> rebuildSearchIndex() {
		long count = goodsSpuService.rebuildSearchIndex();
		return Result.success(count);
	}

	private GoodsSpuExportVO toGoodsSpuExportVO(GoodsSpu spu) {
		GoodsSpuExportVO vo = new GoodsSpuExportVO();
		vo.setName(spu.getName());
		vo.setSubTitle(spu.getSubTitle());
		vo.setCategoryName(spu.getCategoryName());
		vo.setBrandName(spu.getBrandName());
		vo.setSalesPrice(spu.getSalesPrice());
		vo.setOriginalPrice(spu.getOriginalPrice());
		vo.setCostPrice(spu.getCostPrice());
		vo.setStock(spu.getStock());
		vo.setSalesVolume(spu.getSalesVolume());
		vo.setStatus(spu.getStatus());
		vo.setFreightType(spu.getFreightType());
		vo.setFixedFreightPrice(spu.getFixedFreightPrice());
		vo.setCreateTime(spu.getCreateTime() != null ? spu.getCreateTime().toString() : null);
		return vo;
	}

}
