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
import com.aryn.cloud.order.support.ReplenishImportClassifier;
import com.aryn.cloud.order.support.ReplenishImportExcel;
import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 清单直下单导入实现。
 *
 * <p>解析/分类/匹配/防篡改确认与补给单导入同源（{@code SharedCartServiceImpl}
 * 的 previewImport/confirmImport），差异只在终点——确认产出**已复核的
 * SKU 清单**返回给结算页，由既有下单链路（createWay=2）生成订单；
 * 不写 shared_cart_item，也不在这里建单（金额/支付方式必须过结算页评审）。
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
	private RemoteReplenishImportMatchService remoteReplenishImportMatchService;

	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private RemoteGoodsSpuService remoteGoodsSpuService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ReplenishDirectImportVO preview(String tenantId, String userId, String fileName, long fileSize,
			byte[] fileBytes) {
		List<ReplenishImportExcel.ParsedRow> parsed = ReplenishImportExcel.parse(
				new ByteArrayInputStream(fileBytes));
		List<ReplenishImportMatchDTO> requests = new ArrayList<>(parsed.size());
		for (int index = 0; index < parsed.size(); index++) {
			ReplenishImportExcel.ParsedRow row = parsed.get(index);
			ReplenishImportMatchDTO request = new ReplenishImportMatchDTO();
			request.setRowNo(index + 1);
			request.setCode(row.getCode());
			request.setName(row.getName());
			request.setSpec(row.getSpec());
			request.setQuantity(ReplenishMatchRules.parseQuantity(row.getQuantityText()));
			requests.add(request);
		}

		Map<Integer, ReplenishImportMatchVO> matchByRowNo = new LinkedHashMap<>();
		try {
			List<ReplenishImportMatchVO> matches = remoteReplenishImportMatchService.matchRows(tenantId, requests);
			for (int index = 0; index < matches.size(); index++) {
				ReplenishImportMatchVO match = matches.get(index);
				if (match == null) {
					continue;
				}
				// 商品域按契约回显 rowNo；万一没回显，按返回顺序对齐
				Integer rowNo = match.getRowNo() != null ? match.getRowNo() : index + 1;
				matchByRowNo.put(rowNo, match);
			}
		}
		catch (Exception exception) {
			// 商品域抖动时按「全部未匹配」出报告：用户仍能人工补选，好过整次上传失败
			log.warn("清单直下单导入匹配失败，按未匹配处理 operator={}", userId, exception);
		}

		ReplenishDirectImport job = new ReplenishDirectImport();
		job.setId(IdWorker.getIdStr());
		job.setFileName(fileName);
		job.setFileSize(fileSize);
		job.setFileSha256(sha256(fileBytes));
		job.setTotalRows(parsed.size());
		job.setStatus(ReplenishDirectImport.STATUS_PENDING);
		job.setOperatorUserId(userId);
		job.setTenantId(tenantId);
		job.setCreateTime(LocalDateTime.now());
		job.setDelFlag("0");

		int matched = 0;
		int unmatched = 0;
		int specChanged = 0;
		int overStock = 0;
		int invalid = 0;
		int offShelf = 0;
		int notFilled = 0;
		List<ReplenishDirectImportRow> entities = new ArrayList<>(parsed.size());
		for (int index = 0; index < parsed.size(); index++) {
			ReplenishImportExcel.ParsedRow row = parsed.get(index);
			int rowNo = index + 1;
			ReplenishImportMatchVO match = matchByRowNo.get(rowNo);
			Integer quantity = ReplenishMatchRules.parseQuantity(row.getQuantityText());
			// 分类以「数量列原文」为输入：空值代表客户不采购这一行
			ReplenishImportClassifier.Result classified = ReplenishImportClassifier.classify(match, row.getSpec(),
					row.getQuantityText());

			ReplenishDirectImportRow entity = new ReplenishDirectImportRow();
			entity.setId(IdWorker.getIdStr());
			entity.setImportId(job.getId());
			entity.setRowNo(rowNo);
			entity.setRawCode(row.getCode());
			entity.setRawName(row.getName());
			entity.setRawSpec(row.getSpec());
			entity.setRawQuantity(quantity);
			entity.setRawRemark(row.getRemark());
			entity.setMatchType(match == null ? "NONE" : match.getMatchType());
			entity.setMatchedSkuId(match == null ? null : match.getSkuId());
			entity.setMatchedSpuId(match == null ? null : match.getSpuId());
			entity.setMatchedName(match == null ? null : match.getName());
			entity.setMatchedSpec(match == null ? null : match.getSpec());
			entity.setMatchedPrice(match == null ? null : match.getSalesPrice());
			entity.setMatchedStock(match == null ? null : match.getStock());
			entity.setQuantity(quantity);
			entity.setSuggestedQuantity(classified.suggestedQuantity());
			entity.setResultType(classified.resultType());
			entity.setResultMessage(classified.message());
			entity.setTenantId(tenantId);
			entity.setCreateTime(LocalDateTime.now());
			entity.setDelFlag("0");
			replenishDirectImportRowMapper.insert(entity);
			entities.add(entity);

			switch (classified.resultType()) {
				case ReplenishDirectImportRow.RESULT_OK -> matched++;
				case ReplenishDirectImportRow.RESULT_UNMATCHED -> unmatched++;
				case ReplenishDirectImportRow.RESULT_SPEC_CHANGED -> specChanged++;
				case ReplenishDirectImportRow.RESULT_OVER_STOCK -> overStock++;
				case ReplenishDirectImportRow.RESULT_INVALID_QTY -> invalid++;
				case ReplenishDirectImportRow.RESULT_OFF_SHELF -> offShelf++;
				case ReplenishDirectImportRow.RESULT_NOT_FILLED -> notFilled++;
				default -> invalid++;
			}
		}

		job.setMatchedRows(matched);
		job.setUnmatchedRows(unmatched);
		job.setSpecChangedRows(specChanged);
		job.setOverStockRows(overStock);
		job.setInvalidRows(invalid);
		job.setOffShelfRows(offShelf);
		job.setNotFilledRows(notFilled);
		replenishDirectImportMapper.insert(job);
		log.info("清单直下单解析完成 import={} 行数={} 匹配={} 未匹配={} 未填数量={} 超库存={}", job.getId(), parsed.size(),
				matched, unmatched, notFilled, overStock);
		return toImportVO(job, entities);
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
	@Transactional(rollbackFor = Exception.class)
	public ReplenishDirectImportConfirmVO confirm(String tenantId, String userId, String importId,
			ReplenishDirectImportConfirmDTO dto) {
		// 行锁 + 归属校验：并发确认必须串行化，否则后提交的事务会用另一份处置
		// 覆盖先提交的复核结果；非法操作人查不到行，按不存在处理。
		ReplenishDirectImport job = replenishDirectImportMapper.selectForConfirm(tenantId, importId, userId);
		if (job == null) {
			throw new ArynBusinessException("导入任务不存在");
		}
		List<ReplenishDirectImportRow> rows = listImportRows(tenantId, job.getId());
		if (ReplenishDirectImport.STATUS_CONFIRMED.equals(job.getStatus())) {
			// 幂等重入：从解析行的 resolved_* 重建同一份合并清单（报告页反复确认不重复计价）
			return buildConfirmVO(job, rows, buildItems(tenantId, rows),
					job.getSkippedRows() != null && job.getSkippedRows() > 0 ? "该导入已确认，部分行未纳入订单" : "该导入已确认");
		}
		if (ReplenishDirectImport.STATUS_CANCELLED.equals(job.getStatus())) {
			throw new ArynBusinessException("该导入已取消，请重新上传");
		}

		Map<Integer, ReplenishDirectImportConfirmDTO.RowAction> actionByRowNo = new LinkedHashMap<>();
		if (dto != null && dto.getRows() != null) {
			for (ReplenishDirectImportConfirmDTO.RowAction action : dto.getRows()) {
				if (action != null && action.getRowNo() != null) {
					actionByRowNo.put(action.getRowNo(), action);
				}
			}
		}

		// 确认时重新取一次商品现状：报告页停留期间商品可能已下架或库存变化。
		// 商品域不可用必须中止并回滚，不能当成「全部商品下架」把任务永久标记为已确认。
		Map<String, ReplenishImportMatchVO> currentBySkuId = loadCurrentSkus(tenantId, rows, actionByRowNo);

		// 先解析逐行决策并按 SKU 聚合，再统一做库存/MOQ/步长校验：
		// 逐行校验会放过「4 + 6 件、库存 8」的合并超量，下单时才被库存守卫拒绝，
		// 用户看到的是「已确认」却下不了单。聚合校验失败时整组跳过并给出可处理原因。
		Map<String, SkuAggregate> aggregates = new LinkedHashMap<>();
		List<RowDecision> decisions = new ArrayList<>(rows.size());
		for (ReplenishDirectImportRow row : rows) {
			// 未填数量的行是「本次不采购」：不计入 skippedRows，也不写 SKIP 处置痕迹
			if (ReplenishDirectImportRow.RESULT_NOT_FILLED.equals(row.getResultType())) {
				continue;
			}
			ReplenishDirectImportConfirmDTO.RowAction action = actionByRowNo.get(row.getRowNo());
			Integer quantity = action == null && ReplenishDirectImportRow.RESULT_OK.equals(row.getResultType())
					? row.getQuantity()
					: resolvedQuantity(row, action);
			String skuId = action == null && ReplenishDirectImportRow.RESULT_OK.equals(row.getResultType())
					? row.getMatchedSkuId()
					: resolvedSkuId(row, action);
			RowDecision decision = new RowDecision(row, action, skuId, quantity);
			decisions.add(decision);
			decision.skipReason = resolveSkipReason(row, action, skuId, quantity, currentBySkuId);
			if (decision.skipReason != null) {
				continue;
			}
			SkuAggregate aggregate = aggregates.computeIfAbsent(skuId,
					key -> new SkuAggregate(currentBySkuId.get(key)));
			aggregate.add(quantity);
			decision.aggregate = aggregate;
		}
		aggregates.values().forEach(this::validateAggregate);

		LocalDateTime now = LocalDateTime.now();
		int skipped = 0;
		for (RowDecision decision : decisions) {
			ReplenishDirectImportRow row = decision.row;
			String skipReason = decision.skipReason != null ? decision.skipReason
					: decision.aggregate == null ? "未计入本次采购" : decision.aggregate.invalidReason;
			if (skipReason != null) {
				row.setResolvedAction(ReplenishDirectImportRow.ACTION_SKIP);
				row.setResolvedSkuId(null);
				row.setResolvedQuantity(null);
				row.setResultMessage(skipReason);
				row.setResolvedTime(now);
				replenishDirectImportRowMapper.updateById(row);
				skipped++;
				continue;
			}
			ReplenishImportMatchVO current = decision.aggregate.current;
			row.setResolvedAction(decision.action == null ? ReplenishDirectImportRow.ACTION_ACCEPT_SPEC
					: decision.action.getAction());
			row.setResolvedSkuId(decision.skuId);
			row.setResolvedQuantity(decision.quantity);
			row.setQuantity(decision.quantity);
			row.setMatchedSkuId(current.getSkuId());
			row.setMatchedSpuId(current.getSpuId());
			row.setMatchedSpec(current.getSpec());
			row.setResultMessage(acceptMessage(row.getResultType()));
			row.setResolvedTime(now);
			replenishDirectImportRowMapper.updateById(row);
		}

		int confirmedSkuCount = (int) aggregates.values().stream()
			.filter(aggregate -> aggregate.invalidReason == null)
			.count();
		List<ReplenishDirectImportItemVO> items = confirmedSkuCount == 0 ? List.of()
				: buildItems(tenantId, rows);
		// 没有可下单商品时保持待确认：全未匹配/全未处置/合并后无可行行都还能继续处置，
		// 不能伪终结成 CONFIRMED（否则报告页再补选也永远是空回执）。
		job.setConfirmedRows(confirmedSkuCount);
		job.setSkippedRows(skipped);
		if (items.isEmpty()) {
			replenishDirectImportMapper.updateById(job);
			log.info("清单直下单确认未产出可下单商品 import={} 跳过={}", importId, skipped);
			return buildConfirmVO(job, rows, items, skipped > 0
					? "本次没有可下单的商品：" + skipped + " 行未纳入订单，请按报告原因调整后重试"
					: "清单中没有填写采购数量的商品，请补填数量或重新上传");
		}

		job.setStatus(ReplenishDirectImport.STATUS_CONFIRMED);
		job.setConfirmedTime(now);
		replenishDirectImportMapper.updateById(job);
		log.info("清单直下单确认完成 import={} 复核={} 跳过={}", importId, confirmedSkuCount, skipped);
		return buildConfirmVO(job, rows, items,
				skipped > 0 ? "已确认，但 " + skipped + " 行未纳入本次订单，请核对报告中的跳过原因" : "已确认");
	}

	/** 行的跳过原因；返回 null 表示该行可参与合并校验 */
	private String resolveSkipReason(ReplenishDirectImportRow row, ReplenishDirectImportConfirmDTO.RowAction action,
			String skuId, Integer quantity, Map<String, ReplenishImportMatchVO> currentBySkuId) {
		if (action != null && !KNOWN_ACTIONS.contains(action.getAction())) {
			return "处置动作无效，未计入本次采购";
		}
		if (action != null && ReplenishDirectImportRow.ACTION_SKIP.equals(action.getAction())) {
			return "已跳过（本次不采购）";
		}
		if (action == null && !ReplenishDirectImportRow.RESULT_OK.equals(row.getResultType())) {
			// 需要处置的行没有动作：报告页还能继续处理，任务不会因此被确认
			return "未处置，未计入本次采购";
		}
		if (action != null && ReplenishDirectImportRow.ACTION_REPLACE_SKU.equals(action.getAction())
				&& !StringUtils.hasText(action.getSkuId())) {
			return "未选择补选商品，未计入本次采购";
		}
		if (!StringUtils.hasText(skuId)) {
			return "未匹配到商品且未人工补选，未计入本次采购";
		}
		if (quantity == null || quantity <= 0) {
			return "数量无效，未计入本次采购";
		}
		ReplenishImportMatchVO current = currentBySkuId.get(skuId);
		if (current == null) {
			return "商品已下架或不存在，已跳过";
		}
		if (!"0".equals(current.getSkuStatus()) || !"1".equals(current.getSpuStatus())) {
			return "商品已下架，已跳过";
		}
		return null;
	}

	/** 按合并后的 SKU 总量校验库存与起订规则；失败原因写到组内每一行 */
	private void validateAggregate(SkuAggregate aggregate) {
		ReplenishImportMatchVO current = aggregate.current;
		if (aggregate.totalQuantity > Integer.MAX_VALUE) {
			aggregate.invalidReason = "合并后数量超出单 SKU 上限，请拆分或调整数量";
			return;
		}
		int total = (int) aggregate.totalQuantity;
		if (!ReplenishMatchRules.quantityAcceptable(total, current.getMoq(), current.getStepQty())) {
			aggregate.invalidReason = "合并后共 " + total + " 件，不满足起订量或步长要求，请调整数量后重试";
			return;
		}
		if (current.getStock() != null && total > current.getStock()) {
			aggregate.invalidReason = "合并后共需 " + total + " 件，当前库存仅 " + current.getStock()
					+ " 件，相关行已跳过，请调整数量后重试";
		}
	}

	private String acceptMessage(String resultType) {
		return switch (resultType) {
			case ReplenishDirectImportRow.RESULT_OK -> "已匹配";
			case ReplenishDirectImportRow.RESULT_SPEC_CHANGED -> "已确认新规格";
			case ReplenishDirectImportRow.RESULT_UNMATCHED -> "已人工补选";
			case ReplenishDirectImportRow.RESULT_OVER_STOCK, ReplenishDirectImportRow.RESULT_INVALID_QTY ->
				"已按调整后数量计入";
			default -> "已计入清单";
		};
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

	private static final Set<String> KNOWN_ACTIONS = Set.of(ReplenishDirectImportRow.ACTION_ACCEPT_SPEC,
			ReplenishDirectImportRow.ACTION_ADJUST_QTY, ReplenishDirectImportRow.ACTION_REPLACE_SKU,
			ReplenishDirectImportRow.ACTION_SKIP);

	/** 单行确认决策：解析结果 + 所属合并组 + 跳过原因 */
	private static final class RowDecision {

		private final ReplenishDirectImportRow row;

		private final ReplenishDirectImportConfirmDTO.RowAction action;

		private final String skuId;

		private final Integer quantity;

		private String skipReason;

		private SkuAggregate aggregate;

		private RowDecision(ReplenishDirectImportRow row, ReplenishDirectImportConfirmDTO.RowAction action,
				String skuId, Integer quantity) {
			this.row = row;
			this.action = action;
			this.skuId = skuId;
			this.quantity = quantity;
		}

	}

	/** 同 SKU 合并组：总量用 long 累加，避免两行接近 Integer.MAX_VALUE 时溢出成负数 */
	private static final class SkuAggregate {

		private final ReplenishImportMatchVO current;

		private long totalQuantity;

		private String invalidReason;

		private SkuAggregate(ReplenishImportMatchVO current) {
			this.current = current;
		}

		private void add(int quantity) {
			totalQuantity += quantity;
		}

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

	/** 行最终数量：调整数量动作取客户端值，其余沿用服务端解析值 */
	private Integer resolvedQuantity(ReplenishDirectImportRow row, ReplenishDirectImportConfirmDTO.RowAction action) {
		if (action == null) {
			return null;
		}
		if (ReplenishDirectImportRow.ACTION_ADJUST_QTY.equals(action.getAction())) {
			return action.getQuantity();
		}
		return row.getQuantity();
	}

	/** 行最终 SKU：人工补选取客户端 SKU，其余动作沿用匹配结果；未处置动作返回 null */
	private String resolvedSkuId(ReplenishDirectImportRow row, ReplenishDirectImportConfirmDTO.RowAction action) {
		if (action == null || !StringUtils.hasText(action.getAction())
				|| ReplenishDirectImportRow.ACTION_SKIP.equals(action.getAction())) {
			return null;
		}
		if (ReplenishDirectImportRow.ACTION_REPLACE_SKU.equals(action.getAction())) {
			return action.getSkuId();
		}
		return row.getMatchedSkuId();
	}

	/**
	 * 一次性取出本次确认涉及的 SKU 现状（不过滤上下架），供确认阶段重新校验。
	 */
	private Map<String, ReplenishImportMatchVO> loadCurrentSkus(String tenantId, List<ReplenishDirectImportRow> rows,
			Map<Integer, ReplenishDirectImportConfirmDTO.RowAction> actionByRowNo) {
		Set<String> skuIds = new LinkedHashSet<>();
		for (ReplenishDirectImportRow row : rows) {
			ReplenishDirectImportConfirmDTO.RowAction action = actionByRowNo.get(row.getRowNo());
			// 匹配成功的行没有显式动作，按「沿用匹配结果」取 SKU，
			// 否则这些行在回查时被漏掉、确认阶段全部按跳过处理
			String skuId = action == null && ReplenishDirectImportRow.RESULT_OK.equals(row.getResultType())
					? row.getMatchedSkuId()
					: resolvedSkuId(row, action);
			if (StringUtils.hasText(skuId)) {
				skuIds.add(skuId);
			}
		}
		Map<String, ReplenishImportMatchVO> map = new LinkedHashMap<>();
		if (skuIds.isEmpty()) {
			return map;
		}
		try {
			for (ReplenishImportMatchVO match : remoteReplenishImportMatchService.matchSkuIds(tenantId,
					new ArrayList<>(skuIds))) {
				if (match != null && StringUtils.hasText(match.getSkuId())) {
					map.put(match.getSkuId(), match);
				}
			}
		}
		catch (Exception exception) {
			// 商品域抖动不是「商品下架」：吞掉异常会把所有行按跳过写库并确认任务，
			// 商品服务恢复后重试也永远拿到空清单。这里必须中止并回滚，
			// 保留待确认状态与用户已填的处置动作。
			log.error("清单直下单确认时回查商品失败，中止确认 tenantId={}", tenantId, exception);
			throw new ArynBusinessException("商品服务暂时不可用，请稍后重试");
		}
		return map;
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

	/** 文件内容摘要：仅用于「是不是同一个文件」的提示，不做去重拦截 */
	private static String sha256(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder builder = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				builder.append(Character.forDigit((value >> 4) & 0xF, 16));
				builder.append(Character.forDigit(value & 0xF, 16));
			}
			return builder.toString();
		}
		catch (NoSuchAlgorithmException exception) {
			// JDK 必带 SHA-256；真出现也只是失去"同一文件"提示能力
			return null;
		}
	}

}
