package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ReplenishDirectImportConfirmDTO;
import com.aryn.cloud.order.api.entity.ReplenishDirectImport;
import com.aryn.cloud.order.api.entity.ReplenishDirectImportRow;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportConfirmVO;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportItemVO;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportRowVO;
import com.aryn.cloud.order.api.vo.ReplenishDirectImportVO;
import com.aryn.cloud.order.mapper.ReplenishDirectImportMapper;
import com.aryn.cloud.order.mapper.ReplenishDirectImportRowMapper;
import com.aryn.cloud.order.service.IReplenishDirectImportService;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 历史清单直下单报告的只读查询与已确认清单恢复。
 *
 * <p>新导入统一使用持久采购工作台；旧接口只保留历史报告与已确认清单恢复。
 *
 * @author aryn
 * @since 2026/11/7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplenishDirectImportServiceImpl implements IReplenishDirectImportService {

	private final ReplenishDirectImportMapper replenishDirectImportMapper;

	private final ReplenishDirectImportRowMapper replenishDirectImportRowMapper;

	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private RemoteGoodsSpuService remoteGoodsSpuService;

	@Override
	public ReplenishDirectImportVO preview(String tenantId, String userId, String fileName, long fileSize,
			byte[] fileBytes) {
		throw new ArynBusinessException("请在采购工作台上传清单");
	}

	@Override
	public ReplenishDirectImportVO getImport(String tenantId, String userId, String importId) {
		ReplenishDirectImport job = requireImportOwned(tenantId, userId, importId);
		return toImportVO(job, listImportRows(tenantId, job.getId()));
	}

	@Override
	public ReplenishDirectImportVO findPending(String tenantId, String userId) {
		ReplenishDirectImport job = replenishDirectImportMapper.selectOne(Wrappers.lambdaQuery(ReplenishDirectImport.class)
			.eq(ReplenishDirectImport::getTenantId, tenantId)
			.eq(ReplenishDirectImport::getOperatorUserId, userId)
			.eq(ReplenishDirectImport::getStatus, ReplenishDirectImport.STATUS_PENDING)
			.orderByDesc(ReplenishDirectImport::getCreateTime)
			.last("LIMIT 1"));
		if (job == null) {
			return null;
		}
		return toImportVO(job, listImportRows(tenantId, job.getId()));
	}

	@Override
	public ReplenishDirectImportVO findLatest(String tenantId, String userId) {
		ReplenishDirectImport job = replenishDirectImportMapper.selectOne(Wrappers.lambdaQuery(ReplenishDirectImport.class)
			.eq(ReplenishDirectImport::getTenantId, tenantId)
			.eq(ReplenishDirectImport::getOperatorUserId, userId)
			.in(ReplenishDirectImport::getStatus,
					List.of(ReplenishDirectImport.STATUS_PENDING, ReplenishDirectImport.STATUS_CONFIRMED))
			.orderByDesc(ReplenishDirectImport::getCreateTime)
			.last("LIMIT 1"));
		if (job == null) {
			return null;
		}
		return toImportVO(job, listImportRows(tenantId, job.getId()));
	}

	@Override
	public ReplenishDirectImportConfirmVO resume(String tenantId, String userId, String importId) {
		ReplenishDirectImport job = requireImportOwned(tenantId, userId, importId);
		List<ReplenishDirectImportRow> rows = listImportRows(tenantId, job.getId());
		if (ReplenishDirectImport.STATUS_CANCELLED.equals(job.getStatus())) {
			throw new ArynBusinessException("该导入已取消，请重新上传");
		}
		if (!ReplenishDirectImport.STATUS_CONFIRMED.equals(job.getStatus())) {
			// 待确认任务不能直接进结算：返回不可结算回执，引导回报告页处置
			return buildConfirmVO(job, rows, List.of(), "该导入尚未确认，请先在报告中处置商品行");
		}
		// 只读重建：与 confirm 的已确认分支同一份合并清单，不改写解析行
		return buildConfirmVO(job, rows, buildItems(tenantId, rows), "已恢复上次确认的清单，可继续结算");
	}

	@Override
	public ReplenishDirectImportConfirmVO confirm(String tenantId, String userId, String importId,
			ReplenishDirectImportConfirmDTO dto) {
		throw new ArynBusinessException("历史报告只读，请在采购工作台重新解析并确认");
	}

	private ReplenishDirectImportConfirmVO buildConfirmVO(ReplenishDirectImport job,
			List<ReplenishDirectImportRow> rows, List<ReplenishDirectImportItemVO> items, String message) {
		ReplenishDirectImportConfirmVO vo = new ReplenishDirectImportConfirmVO();
		vo.setImportId(job.getId());
		vo.setStatus(job.getStatus());
		vo.setItems(items);
		vo.setRows(rows.stream().map(this::toRowVO).toList());
		vo.setConfirmedRows(items.size());
		vo.setSkippedRows(job.getSkippedRows() == null ? 0 : job.getSkippedRows());
		vo.setCheckoutReady(ReplenishDirectImport.STATUS_CONFIRMED.equals(job.getStatus()) && !items.isEmpty());
		vo.setMessage(message);
		return vo;
	}


	/**
	 * 从已复核行构建结算页明细清单：先按 SKU 合并数量，再批量补齐名称/规格/图片/现价。
	 *
	 * <p>合并必须在**读侧**统一完成：首次确认、重试、并发后的已确认分支都调用本方法，
	 * 若首次在内存合并、重试却返回逐行列表，接口契约就会在“同一份导入”上漂移。
	 * 数据库仍保留逐行数量（审计可追溯），这里只合并返回值，**不能**把首行改写为合计
	 * 又保留第二行，否则读侧再合并会重复计数。
	 *
	 * <p>远程失败降级为仅返回 ID 与数量——结算页会再按 SKU 重算，缺展示字段不至于阻塞下单。
	 */
	private List<ReplenishDirectImportItemVO> buildItems(String tenantId, List<ReplenishDirectImportRow> rows) {
		List<ReplenishDirectImportRow> confirmed = rows.stream()
			.filter(row -> StringUtils.hasText(row.getResolvedSkuId()))
			.filter(row -> !ReplenishDirectImportRow.ACTION_SKIP.equals(row.getResolvedAction()))
			.toList();
		if (confirmed.isEmpty()) {
			return List.of();
		}
		// SKU → 首行（展示字段口径）与合并数量；用 LinkedHashMap 保持首次出现顺序
		Map<String, ReplenishDirectImportRow> firstRowBySkuId = new LinkedHashMap<>();
		Map<String, Integer> quantityBySkuId = new LinkedHashMap<>();
		for (ReplenishDirectImportRow row : confirmed) {
			String skuId = row.getResolvedSkuId();
			if (row.getQuantity() == null) {
				continue;
			}
			firstRowBySkuId.putIfAbsent(skuId, row);
			long mergedQuantity = (long) quantityBySkuId.getOrDefault(skuId, 0) + row.getQuantity();
			if (mergedQuantity > Integer.MAX_VALUE) {
				throw new ArynBusinessException("合并后数量超出单 SKU 上限，请调整数量后重试");
			}
			quantityBySkuId.put(skuId, (int) mergedQuantity);
		}
		if (firstRowBySkuId.isEmpty()) {
			return List.of();
		}

		List<ReplenishDirectImportRow> mergedRows = new ArrayList<>(firstRowBySkuId.values());
		Map<String, GoodsSku> skuMap = loadSkuMap(tenantId, mergedRows);
		Map<String, GoodsSpu> spuMap = loadSpuMap(mergedRows);
		List<ReplenishDirectImportItemVO> items = new ArrayList<>(mergedRows.size());
		for (ReplenishDirectImportRow row : mergedRows) {
			GoodsSku sku = skuMap.get(row.getResolvedSkuId());
			ReplenishDirectImportItemVO item = new ReplenishDirectImportItemVO();
			item.setSkuId(row.getResolvedSkuId());
			item.setSpuId(StringUtils.hasText(row.getMatchedSpuId()) ? row.getMatchedSpuId()
					: sku == null ? null : sku.getSpuId());
			item.setQuantity(quantityBySkuId.get(row.getResolvedSkuId()));
			GoodsSpu spu = spuMap.get(item.getSpuId());
			// 名称以 SPU 为准（与补给单展示口径一致），远程取不到时回落匹配到的商品名
			item.setSpuName(spu != null && StringUtils.hasText(spu.getName()) ? spu.getName()
					: StringUtils.hasText(row.getMatchedName()) ? row.getMatchedName() : row.getResolvedSkuId());
			if (sku != null) {
				item.setPicUrl(resolvePicUrl(sku, spu));
				item.setSpecsInfo(joinSpecs(sku));
				item.setSalesPrice(sku.getSalesPrice());
			}
			else {
				item.setSalesPrice(row.getMatchedPrice());
			}
			items.add(item);
		}
		return items;
	}

	private Map<String, GoodsSku> loadSkuMap(String tenantId, List<ReplenishDirectImportRow> rows) {
		Set<String> skuIds = rows.stream()
			.map(ReplenishDirectImportRow::getResolvedSkuId)
			.filter(StringUtils::hasText)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (skuIds.isEmpty()) {
			return Map.of();
		}
		try {
			List<GoodsSku> skus = remoteGoodsSkuService.getSkuByIds(new ArrayList<>(skuIds));
			Map<String, GoodsSku> map = new HashMap<>();
			if (skus != null) {
				for (GoodsSku sku : skus) {
					map.put(sku.getId(), sku);
				}
			}
			return map;
		}
		catch (Exception exception) {
			log.warn("清单直下单补齐 SKU 展示信息失败，降级为仅返回ID", exception);
			return Map.of();
		}
	}

	private Map<String, GoodsSpu> loadSpuMap(List<ReplenishDirectImportRow> rows) {
		Set<String> spuIds = rows.stream()
			.map(ReplenishDirectImportRow::getMatchedSpuId)
			.filter(StringUtils::hasText)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (spuIds.isEmpty()) {
			return Map.of();
		}
		try {
			List<GoodsSpu> spus = remoteGoodsSpuService.getSpuByIds(new ArrayList<>(spuIds));
			Map<String, GoodsSpu> map = new HashMap<>();
			if (spus != null) {
				for (GoodsSpu spu : spus) {
					map.put(spu.getId(), spu);
				}
			}
			return map;
		}
		catch (Exception exception) {
			log.warn("清单直下单补齐 SPU 展示信息失败，降级为匹配名兜底", exception);
			return Map.of();
		}
	}

	/** 图片优先 SKU 图，缺省回落 SPU 主图（与补给单摘要同口径） */
	private String resolvePicUrl(GoodsSku sku, GoodsSpu spu) {
		if (sku != null && StringUtils.hasText(sku.getPicUrl())) {
			return sku.getPicUrl();
		}
		if (spu == null || spu.getSpuUrls() == null) {
			return null;
		}
		for (String url : spu.getSpuUrls()) {
			if (StringUtils.hasText(url) && !"[]".equals(url.trim())) {
				return url;
			}
		}
		return null;
	}

	/** 规格描述：SKU 规格 JSON 拼成一行；无规格（单规格商品）返回 null */
	private String joinSpecs(GoodsSku sku) {
		if (sku.getSpecsArr() == null) {
			return null;
		}
		String joined = sku.getSpecsArr().stream()
			.map(GoodsSku.Specs::getSpecsValueName)
			.filter(StringUtils::hasText)
			.collect(Collectors.joining("；"));
		return StringUtils.hasText(joined) ? joined : null;
	}

	private ReplenishDirectImport requireImportOwned(String tenantId, String userId, String importId) {
		if (!StringUtils.hasText(importId)) {
			throw new ArynBusinessException("导入任务ID不能为空");
		}
		ReplenishDirectImport job = replenishDirectImportMapper.selectOne(
				Wrappers.lambdaQuery(ReplenishDirectImport.class)
					.eq(ReplenishDirectImport::getTenantId, tenantId)
					.eq(ReplenishDirectImport::getId, importId));
		// 归属校验：报告与复核清单只对上传操作人可见，防止拿 importId 越权读他人清单
		if (job == null || !userId.equals(job.getOperatorUserId())) {
			throw new ArynBusinessException("导入任务不存在");
		}
		return job;
	}

	private List<ReplenishDirectImportRow> listImportRows(String tenantId, String importId) {
		return replenishDirectImportRowMapper.selectList(Wrappers.lambdaQuery(ReplenishDirectImportRow.class)
			.eq(ReplenishDirectImportRow::getTenantId, tenantId)
			.eq(ReplenishDirectImportRow::getImportId, importId)
			.orderByAsc(ReplenishDirectImportRow::getRowNo));
	}

	private ReplenishDirectImportVO toImportVO(ReplenishDirectImport job, List<ReplenishDirectImportRow> rows) {
		ReplenishDirectImportVO vo = new ReplenishDirectImportVO();
		vo.setImportId(job.getId());
		vo.setFileName(job.getFileName());
		vo.setStatus(job.getStatus());
		vo.setTotalRows(job.getTotalRows());
		vo.setMatchedRows(job.getMatchedRows());
		vo.setUnmatchedRows(job.getUnmatchedRows());
		vo.setSpecChangedRows(job.getSpecChangedRows());
		vo.setOverStockRows(job.getOverStockRows());
		vo.setInvalidRows(job.getInvalidRows());
		vo.setOffShelfRows(job.getOffShelfRows());
		vo.setNotFilledRows(job.getNotFilledRows());
		vo.setConfirmedRows(job.getConfirmedRows());
		vo.setSkippedRows(job.getSkippedRows());
		vo.setCreateTime(job.getCreateTime());
		vo.setConfirmedTime(job.getConfirmedTime());
		if (rows == null) {
			return vo;
		}
		List<ReplenishDirectImportRowVO> rowVOs = new ArrayList<>(rows.size());
		for (ReplenishDirectImportRow row : rows) {
			rowVOs.add(toRowVO(row));
		}
		vo.setRows(rowVOs);
		return vo;
	}

	private ReplenishDirectImportRowVO toRowVO(ReplenishDirectImportRow row) {
		ReplenishDirectImportRowVO rowVO = new ReplenishDirectImportRowVO();
		rowVO.setRowNo(row.getRowNo());
		rowVO.setRawCode(row.getRawCode());
		rowVO.setRawName(row.getRawName());
		rowVO.setRawSpec(row.getRawSpec());
		rowVO.setRawQuantity(row.getRawQuantity());
		rowVO.setRawRemark(row.getRawRemark());
		rowVO.setMatchType(row.getMatchType());
		rowVO.setMatchedSkuId(row.getMatchedSkuId());
		rowVO.setMatchedName(row.getMatchedName());
		rowVO.setMatchedSpec(row.getMatchedSpec());
		rowVO.setMatchedPrice(row.getMatchedPrice());
		rowVO.setMatchedStock(row.getMatchedStock());
		rowVO.setQuantity(row.getQuantity());
		rowVO.setResultType(row.getResultType());
		rowVO.setResultMessage(row.getResultMessage());
		rowVO.setResolvedAction(row.getResolvedAction());
		rowVO.setResolvedSkuId(row.getResolvedSkuId());
		rowVO.setResolvedQuantity(row.getResolvedQuantity());
		rowVO.setSuggestedQuantity(row.getSuggestedQuantity());
		return rowVO;
	}


}
