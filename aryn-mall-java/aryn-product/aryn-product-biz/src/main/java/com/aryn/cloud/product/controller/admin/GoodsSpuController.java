
package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.dto.GoodsSpuShelfDTO;
import com.aryn.cloud.product.api.dto.ShipProductProfileDTO;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.aryn.cloud.product.api.entity.ShipGoodsProfile;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.vo.ShipProductSummaryVO;
import com.aryn.cloud.product.service.IGoodsSpuService;
import com.aryn.cloud.product.service.IShipProductProfileService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

	private final IShipProductProfileService shipProductProfileService;

	@Operation(summary = "商品列表")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/page")
	public Result page(Page page, GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.adminPage(page, goodsSpu));
	}

	@Operation(summary = "船供商品摘要分页")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/ship/page")
	public Result<IPage<ShipProductSummaryVO>> shipPage(Page<ShipProductSummaryVO> page, ShipProductSummaryVO query) {
		return Result.success(shipProductProfileService.shipSummaryPage(SecurityUtils.getTenantId(), page, query));
	}

	@Operation(summary = "船供目录导出（Excel）")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/ship/export")
	public void shipExport(ShipProductSummaryVO query, jakarta.servlet.http.HttpServletResponse response)
			throws java.io.IOException {
		Page<ShipProductSummaryVO> page = new Page<>(1, 10000);
		IPage<ShipProductSummaryVO> result = shipProductProfileService.shipSummaryPage(SecurityUtils.getTenantId(),
				page, query);
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setCharacterEncoding("utf-8");
		String fileName = java.net.URLEncoder.encode("船供商品目录", java.nio.charset.StandardCharsets.UTF_8)
				.replaceAll("\\+", "%20");
		response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
		java.util.List<java.util.List<String>> head = java.util.List.of(
				java.util.List.of("商品名称"), java.util.List.of("英文名"), java.util.List.of("销售范围"),
				java.util.List.of("IMPA"), java.util.List.of("ISSA"), java.util.List.of("内部编码"),
				java.util.List.of("条形码"), java.util.List.of("采购单位"), java.util.List.of("箱规"),
				java.util.List.of("MOQ"), java.util.List.of("步长"), java.util.List.of("库存"),
				java.util.List.of("售价"), java.util.List.of("完整度%"));
		java.util.List<java.util.List<Object>> rows = result.getRecords().stream()
				.<java.util.List<Object>>map(item -> java.util.List.of(
						(Object) nvl(item.getName()), nvl(item.getNameEn()), scopeText(item.getSaleScope()),
						nvl(item.getImpaCode()), nvl(item.getIssaCode()), nvl(item.getInternalItemCode()),
						nvl(item.getBarcode()), nvl(item.getPurchaseUnit()), nvl(item.getPackageSpec()),
						nvl(item.getMoq()), nvl(item.getStepQty()), nvl(item.getStock()),
						nvl(item.getSalesPrice()), nvl(item.getPublishCompleteness())))
				.toList();
		com.alibaba.excel.EasyExcel.write(response.getOutputStream()).head(head).sheet("船供商品")
				.doWrite(rows);
	}

	private String nvl(Object value) {
		return value != null ? value : "";
	}

	private String scopeText(String saleScope) {
		return switch (saleScope == null ? "" : saleScope) {
			case "1" -> "仅个人";
			case "2" -> "仅船供";
			case "3" -> "个人+船供";
			default -> "";
		};
	}

	@Operation(summary = "SPU 船供资料详情")
	@SaCheckPermission("product:goodsspu:get")
	@GetMapping("/profile/{spuId}")
	public Result<Map<String, Object>> profile(@PathVariable String spuId) {
		String tenantId = SecurityUtils.getTenantId();
		ShipGoodsProfile profile = shipProductProfileService.getProfile(tenantId, spuId);
		List<ShipSkuProfile> skuProfiles = shipProductProfileService.listSkuProfiles(tenantId, spuId);
		List<ProductCodeMapping> codeMappings = shipProductProfileService.listCodeMappings(tenantId, spuId);
		Map<String, Object> detail = new HashMap<>();
		detail.put("profile", profile);
		detail.put("skuProfiles", skuProfiles);
		detail.put("codeMappings", codeMappings);
		return Result.success(detail);
	}

	@SysLog("保存船供资料")
	@Operation(summary = "保存 SPU 船供资料（资料/SKU 包装/编码映射同事务）")
	@SaCheckPermission("product:goodsspu:edit")
	@PostMapping("/profile")
	public Result<ShipGoodsProfile> saveProfile(@RequestBody ShipProductProfileDTO dto) {
		return Result.success(shipProductProfileService.saveProfile(SecurityUtils.getTenantId(), dto));
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
	@SaCheckPermission("product:goodsspu:edit")
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
	@SaCheckPermission("product:goodsspu:page")
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

}
