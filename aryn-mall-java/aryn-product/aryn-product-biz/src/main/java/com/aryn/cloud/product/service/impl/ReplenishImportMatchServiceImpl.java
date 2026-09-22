package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.aryn.cloud.product.mapper.ProductCodeMappingMapper;
import com.aryn.cloud.product.mapper.ReplenishImportMatchMapper;
import com.aryn.cloud.product.service.IReplenishImportMatchService;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.product.support.ReplenishImportSkuRow;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 补给单导入行匹配实现。
 *
 * <p>三步走，全部批量查询，避免逐行 N+1：
 * <ol>
 *   <li>编码列 → 能识别的 <b>全部</b> 编码类型反查 -> SKU 集合；</li>
 *   <li>编码未命中 → 品名精确 / 品名包含 -> SKU 候选；</li>
 *   <li>候选去重 + 规格比对：唯一候选即为命中，多候选按规格收敛，
 *       仍多于一个则进 AMBIGUOUS（人工补选），不赌自动匹配。</li>
 * </ol>
 *
 * @author aryn
 * @since 2026/9/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplenishImportMatchServiceImpl implements IReplenishImportMatchService {

	/** 单个导入文件允许的最大数据行数（与需求确认单一致） */
	public static final int MAX_ROWS = 2000;

	/** 单行候选上限 */
	private static final int MAX_CANDIDATES = 20;

	/** 参与自动匹配的编码类型（一个编码值可能同时命中多种类型） */
	private static final List<String> CODE_TYPES = List.of("IMPA", "ISSA", "INTERNAL", "BARCODE", "SUPPLIER");

	private final ProductCodeMappingMapper productCodeMappingMapper;

	private final ReplenishImportMatchMapper replenishImportMatchMapper;

	@Override
	public List<ReplenishImportMatchVO> matchRows(String tenantId, List<ReplenishImportMatchDTO> rows) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}
		if (rows.size() > MAX_ROWS) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException(
					"单个文件最多支持 " + MAX_ROWS + " 行数据");
		}

		// 一、编码 → SKU：一次取出所有可能与本次导入相关的映射
		List<String> codeValues = rows.stream()
			.map(ReplenishImportMatchDTO::getCode)
			.filter(StringUtils::hasText)
			.map(String::trim)
			.distinct()
			.toList();
		Map<String, List<ProductCodeMapping>> mappingsByCode = codeValues.isEmpty() ? Map.of()
				: productCodeMappingMapper.selectList(Wrappers.lambdaQuery(ProductCodeMapping.class)
					.eq(ProductCodeMapping::getTenantId, tenantId)
					.in(ProductCodeMapping::getCodeValue, codeValues)
					.in(ProductCodeMapping::getCodeType, CODE_TYPES))
					.stream()
					.collect(Collectors.groupingBy(ProductCodeMapping::getCodeValue, LinkedHashMap::new,
							Collectors.toList()));

		// 二、品名兜底所需的候选
		List<String> exactNames = rows.stream()
			.map(ReplenishImportMatchDTO::getName)
			.filter(StringUtils::hasText)
			.map(String::trim)
			.distinct()
			.toList();
		List<String> keywords = rows.stream()
			.map(ReplenishImportMatchDTO::getName)
			.filter(StringUtils::hasText)
			.map(String::trim)
			.filter(name -> name.length() >= 2)
			.distinct()
			.limit(200)
			.toList();

		Set<String> skuIds = mappingsByCode.values().stream()
			.flatMap(List::stream)
			.map(ProductCodeMapping::getSkuId)
			.filter(StringUtils::hasText)
			.collect(Collectors.toCollection(LinkedHashSet::new));

		List<ReplenishImportSkuRow> byIdRows = skuIds.isEmpty() ? List.of()
				: replenishImportMatchMapper.selectRowsBySkuIds(tenantId, new ArrayList<>(skuIds));

		Map<String, ReplenishImportSkuRow> rowBySkuId = new LinkedHashMap<>();
		for (ReplenishImportSkuRow row : byIdRows) {
			rowBySkuId.put(row.getSkuId(), row);
		}

		List<ReplenishImportSkuRow> nameRows = exactNames.isEmpty() ? List.of()
				: replenishImportMatchMapper.selectRowsByExactNames(tenantId, exactNames);
		List<ReplenishImportSkuRow> keywordRows = keywords.isEmpty() ? List.of()
				: replenishImportMatchMapper.selectRowsByKeywords(tenantId, keywords);
		Map<String, ReplenishImportSkuRow> nameIndex = new LinkedHashMap<>();
		for (ReplenishImportSkuRow row : nameRows) {
			nameIndex.put(row.getSkuId(), row);
		}
		for (ReplenishImportSkuRow row : keywordRows) {
			nameIndex.putIfAbsent(row.getSkuId(), row);
		}

		// 三、逐行归约
		List<ReplenishImportMatchVO> results = new ArrayList<>(rows.size());
		for (ReplenishImportMatchDTO dto : rows) {
			results.add(matchOne(dto, mappingsByCode, rowBySkuId, nameIndex));
		}
		return results;
	}

	@Override
	public List<ReplenishImportMatchVO> matchSkuIds(String tenantId, List<String> skuIds) {
		if (skuIds == null || skuIds.isEmpty()) {
			return List.of();
		}
		List<ReplenishImportSkuRow> rows = replenishImportMatchMapper.selectRowsBySkuIds(tenantId, skuIds);
		List<ReplenishImportMatchVO> result = new ArrayList<>(rows.size());
		for (ReplenishImportSkuRow row : rows) {
			ReplenishImportMatchVO vo = new ReplenishImportMatchVO();
			vo.setMatchType("CODE");
			vo.setSpuId(row.getSpuId());
			vo.setSkuId(row.getSkuId());
			vo.setName(row.getName());
			vo.setSpec(joinSpecs(row));
			vo.setPurchaseUnit(row.getPurchaseUnit());
			vo.setSalesPrice(row.getSalesPrice());
			vo.setStock(row.getStock());
			vo.setMoq(row.getMoq());
			vo.setStepQty(row.getStepQty());
			vo.setSkuStatus(row.getSkuStatus());
			vo.setSpuStatus(row.getSpuStatus());
			result.add(vo);
		}
		return result;
	}

	private ReplenishImportMatchVO matchOne(ReplenishImportMatchDTO dto,
			Map<String, List<ProductCodeMapping>> mappingsByCode,
			Map<String, ReplenishImportSkuRow> rowBySkuId,
			Map<String, ReplenishImportSkuRow> nameRows) {
		List<ReplenishImportSkuRow> byCode = resolveByCode(dto, mappingsByCode, rowBySkuId);
		List<ReplenishImportSkuRow> candidates = byCode.isEmpty()
				? resolveByName(dto, nameRows)
				: byCode;
		String matchType = byCode.isEmpty() ? "NAME" : "CODE";
		if (candidates.isEmpty()) {
			return notMatched(dto);
		}

		// 规格收敛：能唯一确定就唯一确定，否则给候选让用户选
		List<ReplenishImportSkuRow> narrowed = candidates;
		Integer quantity = dto.getQuantity();
		List<ReplenishImportSkuRow> specMatched = candidates.stream()
			.filter(row -> ReplenishMatchRules.specEquivalent(dto.getSpec(), joinSpecs(row)))
			.toList();
		if (specMatched.size() >= 1) {
			narrowed = specMatched;
		}

		if (narrowed.size() == 1) {
			return toMatched(dto, narrowed.get(0), matchType, quantity);
		}
		// 多候选：把全部候选如实返回（含下架），由用户补选
		ReplenishImportMatchVO ambiguous = notMatched(dto);
		ambiguous.setMatchType("AMBIGUOUS");
		ambiguous.setCandidates(candidates.stream()
			.limit(MAX_CANDIDATES)
			.map(this::toCandidate)
			.toList());
		return ambiguous;
	}

	private List<ReplenishImportSkuRow> resolveByCode(ReplenishImportMatchDTO dto,
			Map<String, List<ProductCodeMapping>> mappingsByCode,
			Map<String, ReplenishImportSkuRow> rowBySkuId) {
		if (!StringUtils.hasText(dto.getCode())) {
			return List.of();
		}
		List<ProductCodeMapping> mappings = mappingsByCode.getOrDefault(dto.getCode().trim(), List.of());
		if (mappings.isEmpty()) {
			return List.of();
		}
		// 按类型优先级排序后去重：IMPA > ISSA > INTERNAL > BARCODE > SUPPLIER
		List<ReplenishImportSkuRow> rows = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String codeType : CODE_TYPES) {
			for (ProductCodeMapping mapping : mappings) {
				if (!Objects.equals(codeType, mapping.getCodeType())) {
					continue;
				}
				ReplenishImportSkuRow row = rowBySkuId.get(mapping.getSkuId());
				if (row != null && seen.add(row.getSkuId())) {
					rows.add(row);
				}
			}
		}
		return rows;
	}

	private List<ReplenishImportSkuRow> resolveByName(ReplenishImportMatchDTO dto,
			Map<String, ReplenishImportSkuRow> nameRows) {
		String name = dto.getName();
		if (!StringUtils.hasText(name)) {
			return List.of();
		}
		String target = ReplenishMatchRules.normalizeName(name);
		Map<String, List<ReplenishImportSkuRow>> grouped = nameRows.values().stream()
			.collect(Collectors.groupingBy(row -> ReplenishMatchRules.normalizeName(row.getName()),
					LinkedHashMap::new, Collectors.toList()));
		List<ReplenishImportSkuRow> exact = grouped.get(target);
		if (exact != null && !exact.isEmpty()) {
			return exact;
		}
		// 包含匹配兜底：线下清单的规格常写在品名里（如「鲜牛奶 950ml」对「鲜牛奶」）
		return nameRows.values().stream()
			.filter(row -> {
				String candidate = ReplenishMatchRules.normalizeName(row.getName());
				return !candidate.isEmpty() && (candidate.contains(target) || target.contains(candidate));
			})
			.toList();
	}

	private ReplenishImportMatchVO toMatched(ReplenishImportMatchDTO dto, ReplenishImportSkuRow row,
			String matchType, Integer quantity) {
		ReplenishImportMatchVO vo = new ReplenishImportMatchVO();
		vo.setRowNo(dto.getRowNo());
		vo.setMatchType(matchType);
		vo.setSpuId(row.getSpuId());
		vo.setSkuId(row.getSkuId());
		vo.setName(row.getName());
		vo.setSpec(joinSpecs(row));
		vo.setPurchaseUnit(row.getPurchaseUnit());
		vo.setSalesPrice(row.getSalesPrice());
		vo.setStock(row.getStock());
		vo.setMoq(row.getMoq());
		vo.setStepQty(row.getStepQty());
		vo.setSkuStatus(row.getSkuStatus());
		vo.setSpuStatus(row.getSpuStatus());
		return vo;
	}

	private ReplenishImportMatchVO notMatched(ReplenishImportMatchDTO dto) {
		ReplenishImportMatchVO vo = new ReplenishImportMatchVO();
		vo.setRowNo(dto.getRowNo());
		vo.setMatchType("NONE");
		return vo;
	}

	private ReplenishImportMatchVO.Candidate toCandidate(ReplenishImportSkuRow row) {
		ReplenishImportMatchVO.Candidate candidate = new ReplenishImportMatchVO.Candidate();
		candidate.setSpuId(row.getSpuId());
		candidate.setSkuId(row.getSkuId());
		candidate.setName(row.getName());
		candidate.setSpec(joinSpecs(row));
		candidate.setPurchaseUnit(row.getPurchaseUnit());
		candidate.setSalesPrice(row.getSalesPrice());
		candidate.setStock(row.getStock());
		candidate.setSkuStatus(row.getSkuStatus());
		candidate.setSpuStatus(row.getSpuStatus());
		return candidate;
	}

	/** 规格描述：SKU 规格值按「；」拼接（与 C 端展示口径一致） */
	static String joinSpecs(ReplenishImportSkuRow row) {
		if (row.getSpecsArr() == null || row.getSpecsArr().isEmpty()) {
			return null;
		}
		String joined = row.getSpecsArr().stream()
			.map(com.aryn.cloud.product.api.entity.GoodsSku.Specs::getSpecsValueName)
			.filter(StringUtils::hasText)
			.collect(Collectors.joining(ReplenishMatchRules.SEPARATOR));
		return StringUtils.hasText(joined) ? joined : null;
	}

}
