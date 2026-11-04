
package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.dto.GoodsSpuShelfDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagQueryDTO;
import com.aryn.cloud.product.api.dto.GoodsZoneTagSaveDTO;
import com.aryn.cloud.product.api.dto.ZoneGoodsIdsDTO;
import com.aryn.cloud.product.api.dto.ZoneGoodsQueryDTO;
import com.aryn.cloud.product.api.entity.GoodsPriceHistory;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsZoneTagVO;
import com.aryn.cloud.product.api.vo.ZoneGoodsIdsVO;
import com.aryn.cloud.product.api.vo.ZoneGoodsRowVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchDetailVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchResultVO;
import com.aryn.cloud.product.api.vo.ZoneTagBatchVO;
import com.aryn.cloud.product.api.vo.ZoneTagItemStateVO;
import com.aryn.cloud.product.api.vo.ZoneTagPreviewVO;
import com.aryn.cloud.product.api.vo.ZoneTagUndoResultVO;
import com.aryn.cloud.product.service.IGoodsSpuService;
import com.aryn.cloud.product.service.IGoodsZoneTagBatchService;
import com.aryn.cloud.product.service.IGoodsZoneTagService;
import com.aryn.cloud.product.service.IZoneGoodsQueryService;
import com.aryn.cloud.product.service.IZoneTagBatchRecordService;
import com.aryn.cloud.product.service.impl.GoodsPriceHistoryService;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSyncService;
import com.aryn.cloud.promotion.api.vo.MallZoneSyncResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.util.StringUtils;
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

	private final IGoodsZoneTagService goodsZoneTagService;

	private final IGoodsZoneTagBatchService goodsZoneTagBatchService;

	private final IZoneGoodsQueryService zoneGoodsQueryService;

	private final IZoneTagBatchRecordService zoneTagBatchRecordService;

	private final GoodsPriceHistoryService goodsPriceHistoryService;

	/**
	 * 打标后同步系统托管活动。
	 *
	 * <p>非 final + @DubboReference（构造器注入远程接口曾在 boot 启动失败）；
	 * 同步失败不回滚打标结果，响应中返回 FAILED 供前端提示，每日兜底 Job 会重试。
	 */
	@DubboReference
	private RemoteMallZoneSyncService remoteMallZoneSyncService;

	@Operation(summary = "批量专区操作预检（只读）")
	@SaCheckPermission("product:goodsspu:edit")
	@PostMapping("/tags/batch/preview")
	public Result<ZoneTagPreviewVO> previewBatchTags(@RequestBody GoodsZoneTagBatchDTO dto) {
		return Result.success(goodsZoneTagBatchService.preview(dto));
	}

	@Operation(summary = "批量打/取消会员与出清标签")
	@SysLog("商品批量专区操作")
	@SaCheckPermission("product:goodsspu:edit")
	@PostMapping("/tags/batch")
	public Result<ZoneTagBatchResultVO> batchTags(@RequestBody GoodsZoneTagBatchDTO dto) {
		ZoneTagBatchResultVO result = goodsZoneTagBatchService.execute(dto);
		result.setSync(syncBatchZones(result));
		return Result.success(result);
	}

	@Operation(summary = "批量读取商品专区状态")
	@SaCheckPermission("product:goodsspu:get")
	@PostMapping("/tags/query")
	public Result<List<ZoneTagItemStateVO>> queryTags(@RequestBody GoodsZoneTagQueryDTO dto) {
		return Result.success(goodsZoneTagBatchService.query(dto));
	}

	@Operation(summary = "按筛选条件取商品 ID（全选筛选结果 / 换一批）")
	@SaCheckPermission("product:goodsspu:page")
	@PostMapping("/ids")
	public Result<ZoneGoodsIdsVO> zoneIds(@RequestBody ZoneGoodsIdsDTO dto) {
		return Result.success(zoneGoodsQueryService.ids(dto));
	}

	@Operation(summary = "专区商品管理分页")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/zone/page")
	public Result<IPage<ZoneGoodsRowVO>> zonePage(ZoneGoodsQueryDTO query) {
		return Result.success(zoneGoodsQueryService.page(query));
	}

	@Operation(summary = "批量专区操作记录分页")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/tags/batch/page")
	public Result<IPage<ZoneTagBatchVO>> batchPage(
			@RequestParam(value = "current", defaultValue = "1") long current,
			@RequestParam(value = "size", defaultValue = "10") long size) {
		return Result.success(zoneTagBatchRecordService.page(current, size));
	}

	@Operation(summary = "批量专区操作批次详情")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/tags/batch/{batchId}")
	public Result<ZoneTagBatchDetailVO> batchDetail(@PathVariable String batchId) {
		return Result.success(zoneTagBatchRecordService.detail(batchId));
	}

	@Operation(summary = "撤销批量专区操作批次")
	@SysLog("撤销商品批量专区操作")
	@SaCheckPermission("product:goodsspu:edit")
	@PostMapping("/tags/batch/{batchId}/undo")
	public Result<ZoneTagUndoResultVO> undoBatch(@PathVariable String batchId) {
		ZoneTagUndoResultVO result = zoneTagBatchRecordService.undo(batchId);
		syncZonesQuietly();
		return Result.success(result);
	}

	/**
	 * 批量操作后同步系统活动：仅在确有变更时触发，并回写批次同步状态。
	 */
	private ZoneTagBatchResultVO.Sync syncBatchZones(ZoneTagBatchResultVO result) {
		ZoneTagBatchResultVO.Sync sync = new ZoneTagBatchResultVO.Sync();
		sync.setFinishedAt(LocalDateTime.now());
		Integer changed = result.getSummary() == null ? null : result.getSummary().getChanged();
		if (changed == null || changed <= 0) {
			sync.setStatus("SKIPPED");
			updateBatchSyncStatus(result.getBatchId(), sync);
			return sync;
		}
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			sync.setStatus("SKIPPED");
			updateBatchSyncStatus(result.getBatchId(), sync);
			return sync;
		}
		try {
			MallZoneSyncResultVO syncResult = remoteMallZoneSyncService.syncTenant(tenantId);
			boolean success = syncResult != null && Boolean.TRUE.equals(syncResult.getSuccess());
			sync.setStatus(success ? "SUCCESS" : "FAILED");
			sync.setMessage(syncResult == null ? "同步结果为空" : syncResult.getMessage());
			if (syncResult != null && syncResult.getFinishedAt() != null) {
				sync.setFinishedAt(syncResult.getFinishedAt());
			}
		}
		catch (Exception ex) {
			log.warn("商品批量专区操作后同步失败 tenant={}", tenantId, ex);
			sync.setStatus("FAILED");
			sync.setMessage(truncateMessage(ex.getMessage()));
		}
		updateBatchSyncStatus(result.getBatchId(), sync);
		return sync;
	}

	private void updateBatchSyncStatus(String batchId, ZoneTagBatchResultVO.Sync sync) {
		if (!StringUtils.hasText(batchId)) {
			return;
		}
		try {
			zoneTagBatchRecordService.updateSyncStatus(batchId, sync.getStatus(), sync.getMessage());
		}
		catch (Exception ex) {
			log.warn("回写批次同步状态失败 batchId={}", batchId, ex);
		}
	}

	private String truncateMessage(String message) {
		if (!StringUtils.hasText(message)) {
			return "专区价格同步失败，请稍后重试";
		}
		return message.length() > 480 ? message.substring(0, 480) : message;
	}

	@Operation(summary = "读取商品专区标签")
	@SaCheckPermission("product:goodsspu:get")
	@GetMapping("/tags/{spuId}")
	public Result<GoodsZoneTagVO> tags(@PathVariable String spuId) {
		return Result.success(goodsZoneTagService.getTags(spuId));
	}

	@Operation(summary = "商品价格变更历史（先涨后降/专区可信度审计）")
	@SaCheckPermission("product:goodsspu:get")
	@GetMapping("/{spuId}/price-history")
	public Result<List<GoodsPriceHistory>> priceHistory(@PathVariable String spuId,
			@RequestParam(value = "limit", required = false, defaultValue = "100") int limit) {
		return Result.success(goodsPriceHistoryService.listBySpu(spuId, limit));
	}

	@Operation(summary = "单商品专区标签精调（含 SKU 覆盖）")
	@SysLog("商品专区标签精调")
	@SaCheckPermission("product:goodsspu:edit")
	@PutMapping("/{spuId}/tags")
	public Result<Void> saveTags(@PathVariable String spuId, @RequestBody GoodsZoneTagSaveDTO dto) {
		goodsZoneTagService.saveTags(spuId, dto);
		syncZonesQuietly();
		return Result.success();
	}

	private void syncZonesQuietly() {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return;
		}
		try {
			remoteMallZoneSyncService.syncTenant(tenantId);
		}
		catch (Exception ex) {
			log.warn("商品打标后专区活动同步失败，等待每日兜底任务 tenant={}", tenantId, ex);
		}
	}

	@Operation(summary = "商品列表")
	@SaCheckPermission("product:goodsspu:page")
	@GetMapping("/page")
	public Result page(Page page, GoodsSpu goodsSpu,
			@RequestParam(value = "zoneType", required = false) String zoneType) {
		return Result.success(goodsSpuService.adminPage(page, goodsSpu, zoneType));
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
		boolean saved = goodsSpuService.saveGoods(goodsSpu);
		if (goodsSpu.isZoneChanged()) {
			syncZonesQuietly();
		}
		return Result.success(saved);
	}

	@SysLog("修改商品")
	@Operation(summary = "商品修改")
	@SaCheckPermission("product:goodsspu:edit")
	@PutMapping
	public Result edit(@RequestBody GoodsSpu goodsSpu) {
		boolean updated = goodsSpuService.updateGoods(goodsSpu);
		// 只有专区字段变化才全量重算系统活动，普通商品编辑不触发租户级同步
		if (goodsSpu.isZoneChanged()) {
			syncZonesQuietly();
		}
		return Result.success(updated);
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
