package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.product.api.vo.ReplenishCatalogRowVO;
import com.aryn.cloud.product.api.vo.ReplenishCatalogVO;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.aryn.cloud.product.mapper.ReplenishImportMatchMapper;
import com.aryn.cloud.product.service.IReplenishImportMatchService;
import com.aryn.cloud.product.support.CatalogVectorRecallService;
import com.aryn.cloud.product.support.ReplenishImportSkuRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 补给单导入行匹配实现。
 *
 * <p>三步走，全部批量查询，避免逐行 N+1：
 * <ol>
 *   <li>编码列 → <b>SKU 编号</b>精确反查（目录模板的「商品编码」列由导出写入
 *       {@code goods_sku.id}，回查与导出天然闭环）；</li>
 *   <li>编码未命中 → 品名精确 / 品名包含 → SKU 候选；</li>
 *   <li>候选去重 + 规格比对：唯一候选即为命中，多候选按规格收敛，
 *       仍多于一个则进 AMBIGUOUS（人工补选），不赌自动匹配。</li>
 * </ol>
 *
 * <p>历史上编码列取自船供编码（IMPA/ISSA/内部编码/条码，码源为船供资料两表）；
 * 船供资料已下线（2026-09-29），改为 SKU 编号主匹配 —— 导出与回查用的是同一个
 * 稳定键，客户只改「数量」列的目录模板必然整份命中。
 *
 * <p>另提供 {@link #exportCatalog}：把在售商品按分类铺成模板，
 * 客户只填「数量」列即可。
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

	/**
	 * 模板目录默认行数上限，与单文件导入上限（{@link #MAX_ROWS}）一致：
	 * 导出的模板本身必须能重新上传，导出超过上限等于给客户一份注定被拒的文件。
	 */
	private static final int DEFAULT_CATALOG_LIMIT = MAX_ROWS;

	private final ReplenishImportMatchMapper replenishImportMatchMapper;

	/** 目录向量召回（未配 embedding key 时 isAvailable=false，matchRows 跳过向量步） */
	private final CatalogVectorRecallService catalogVectorRecallService;

	@Override
	public List<ReplenishImportMatchVO> matchRows(String tenantId, List<ReplenishImportMatchDTO> rows) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}
		if (rows.size() > MAX_ROWS) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException(
					"单个文件最多支持 " + MAX_ROWS + " 行数据");
		}

		// 一、编码列 → SKU 编号精确反查（不过滤上下架：报告要能区分「未匹配」与「已下架」）
		List<String> codeValues = rows.stream()
			.map(ReplenishImportMatchDTO::getCode)
			.filter(StringUtils::hasText)
			.map(String::trim)
			.distinct()
			.toList();
		Map<String, ReplenishImportSkuRow> rowByCode = codeValues.isEmpty() ? Map.of()
				: loadRowsByCodes(tenantId, codeValues);

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

		// 模糊行（接龙粘贴）的额外召回：品名拆成相邻二字组再做一次 LIKE。
		// 「青香蕉」对「国产香蕉」这类修饰词夹心的品名双向包含必失败，
		// 只有把重叠的核心词拆出来才能进候选池。单独建索引——混进 nameIndex
		// 会放大 Excel 行包含匹配的候选池，等于顺手改了既有行为。
		// 口语短名（剥完修饰/规格后 ≤3 个汉字，如「橙子」）再补**单字**召回：
		// 它与商城名可能连二字组都不共享（橙子/赣南脐橙），共享汉字是最后的线索。
		List<String> recallKeywords = new ArrayList<>();
		for (ReplenishImportMatchDTO dto : rows) {
			if (!Boolean.TRUE.equals(dto.getFuzzy()) || !StringUtils.hasText(dto.getName())) {
				continue;
			}
			recallKeywords.addAll(ReplenishMatchRules.characterBigrams(dto.getName()));
			List<String> variants = ReplenishMatchRules.nameVariants(dto.getName());
			String probe = variants.get(variants.size() - 1);
			List<String> chars = ReplenishMatchRules.distinctCjkChars(probe);
			if (chars.size() <= 3) {
				recallKeywords.addAll(chars);
			}
		}
		Map<String, ReplenishImportSkuRow> fuzzyIndex = new LinkedHashMap<>();
		if (!recallKeywords.isEmpty()) {
			List<String> distinctKeywords = recallKeywords.stream().distinct().limit(300).toList();
			for (ReplenishImportSkuRow row : replenishImportMatchMapper.selectRowsByKeywords(tenantId, distinctKeywords)) {
				if (StringUtils.hasText(row.getSkuId())) {
					fuzzyIndex.putIfAbsent(row.getSkuId(), row);
				}
			}
		}

		// 三、逐行归约
		List<ReplenishImportMatchVO> results = new ArrayList<>(rows.size());
		for (ReplenishImportMatchDTO dto : rows) {
			results.add(matchOne(dto, rowByCode, nameIndex, fuzzyIndex));
		}
		// 四、向量语义召回：字面层全军覆没的模糊行用余弦相似度补候选（确定性，失败降级）
		recallByVectors(tenantId, rows, results);
		return results;
	}

	/**
	 * 向量语义召回（{@code fuzzy=true} 的接龙行专用，Excel 行不受影响）。
	 *
	 * <p>精确/包含/二字组/单字全是字面匹配，叫法与商城名**零共享字符**时
	 * （小番茄 vs 西红柿）候选池为空——这是字面层的原理性边界，不是 bug。
	 * 向量召回用语义相似度补候选：同一品名永远同一份候选（编码器无采样），
	 * 「时灵时不灵」的采样波动不存在。候选仍只能来自数据库在售商品，
	 * matchType 保持 NONE——语义相近 ≠ 同一种商品（小番茄≠西红柿是品种差异），
	 * 定品永远由人在报告页完成；本步失败只降级不抛出。
	 */
	private void recallByVectors(String tenantId, List<ReplenishImportMatchDTO> rows,
			List<ReplenishImportMatchVO> results) {
		if (!catalogVectorRecallService.isAvailable()) {
			return;
		}
		Map<Integer, String> missingByIndex = new LinkedHashMap<>();
		for (int index = 0; index < results.size() && index < rows.size(); index++) {
			ReplenishImportMatchVO result = results.get(index);
			ReplenishImportMatchDTO dto = rows.get(index);
			if (!Boolean.TRUE.equals(dto.getFuzzy()) || !StringUtils.hasText(dto.getName())) {
				continue;
			}
			if (result == null || StringUtils.hasText(result.getSkuId())
					|| (result.getCandidates() != null && !result.getCandidates().isEmpty())) {
				continue;
			}
			missingByIndex.put(index, dto.getName().trim());
		}
		if (missingByIndex.isEmpty()) {
			return;
		}
		// 同名行共享一份召回（一次粘贴常见同人同品多行），embedding 也只算一次
		List<String> distinctNames = missingByIndex.values().stream().distinct().toList();
		List<List<CatalogVectorRecallService.VectorMatch>> recalled;
		try {
			recalled = catalogVectorRecallService.recall(tenantId, distinctNames);
		}
		catch (Exception exception) {
			log.warn("向量召回失败，缺品行维持字面候选/人工搜索 tenant={}", tenantId, exception);
			return;
		}
		Map<String, List<CatalogVectorRecallService.VectorMatch>> matchesByName = new LinkedHashMap<>();
		for (int index = 0; index < distinctNames.size() && index < recalled.size(); index++) {
			matchesByName.put(distinctNames.get(index), recalled.get(index));
		}
		for (Map.Entry<Integer, String> entry : missingByIndex.entrySet()) {
			List<CatalogVectorRecallService.VectorMatch> matches = matchesByName.get(entry.getValue());
			if (matches == null || matches.isEmpty()) {
				continue;
			}
			ReplenishImportMatchVO result = results.get(entry.getKey());
			result.setCandidates(matches.stream()
				.limit(MAX_CANDIDATES)
				.map(match -> toCandidate(match.row()))
				.toList());
		}
	}

	/**
	 * 编码列按 SKU 编号批量反查商品行，按编号建索引。
	 *
	 * <p>编码值不是合法 SKU 编号时自然查不到，交由品名兜底 —— 与历史
	 * 「编码映射未命中回落品名」的口径一致。
	 */
	private Map<String, ReplenishImportSkuRow> loadRowsByCodes(String tenantId, List<String> codeValues) {
		List<ReplenishImportSkuRow> rows = replenishImportMatchMapper.selectRowsBySkuIds(tenantId, codeValues);
		Map<String, ReplenishImportSkuRow> rowByCode = new LinkedHashMap<>();
		for (ReplenishImportSkuRow row : rows) {
			if (StringUtils.hasText(row.getSkuId())) {
				rowByCode.put(row.getSkuId(), row);
			}
		}
		return rowByCode;
	}

	@Override
	public ReplenishCatalogVO exportCatalog(String tenantId, int limit) {
		int effectiveLimit = limit <= 0 ? DEFAULT_CATALOG_LIMIT : limit;
		List<ReplenishImportSkuRow> rows = replenishImportMatchMapper.selectCatalogRows(tenantId, effectiveLimit);
		int total = replenishImportMatchMapper.countCatalogRows(tenantId);

		List<ReplenishCatalogRowVO> catalogRows = new ArrayList<>(rows.size());
		for (ReplenishImportSkuRow row : rows) {
			ReplenishCatalogRowVO vo = new ReplenishCatalogRowVO();
			vo.setSkuId(row.getSkuId());
			vo.setCategoryName(joinCategory(row));
			vo.setName(row.getName());
			vo.setSpec(joinSpecs(row));
			vo.setStock(row.getStock());
			vo.setSalesPrice(row.getSalesPrice());
			// 编码列即 SKU 编号：导出与 matchRows 回查共用同一稳定键
			vo.setCode(row.getSkuId());
			catalogRows.add(vo);
		}

		ReplenishCatalogVO catalog = new ReplenishCatalogVO();
		catalog.setRows(catalogRows);
		catalog.setTotalCount(total);
		catalog.setTruncated(total > catalogRows.size());
		return catalog;
	}

	/** 分类展示名：一级/二级；只有一级时给一级，都没有则空（前端按「未分类」展示） */
	private String joinCategory(ReplenishImportSkuRow row) {
		String first = row.getCategoryFirstName();
		String second = row.getCategorySecondName();
		if (StringUtils.hasText(first) && StringUtils.hasText(second)) {
			return first + "/" + second;
		}
		if (StringUtils.hasText(second)) {
			return second;
		}
		return StringUtils.hasText(first) ? first : null;
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
			vo.setSalesPrice(row.getSalesPrice());
			vo.setStock(row.getStock());
			vo.setSkuStatus(row.getSkuStatus());
			vo.setSpuStatus(row.getSpuStatus());
			result.add(vo);
		}
		return result;
	}

	private ReplenishImportMatchVO matchOne(ReplenishImportMatchDTO dto,
			Map<String, ReplenishImportSkuRow> rowByCode,
			Map<String, ReplenishImportSkuRow> nameRows,
			Map<String, ReplenishImportSkuRow> fuzzyRows) {
		List<ReplenishImportSkuRow> byCode = resolveByCode(dto, rowByCode);
		List<ReplenishImportSkuRow> candidates = byCode.isEmpty()
				? resolveByName(dto, nameRows)
				: byCode;
		String matchType = byCode.isEmpty() ? "NAME" : "CODE";
		if (candidates.isEmpty()) {
			if (Boolean.TRUE.equals(dto.getFuzzy())) {
				return fuzzyMatch(dto, fuzzyRows);
			}
			return notMatched(dto);
		}

		// 规格收敛：能唯一确定就唯一确定，否则给候选让用户选
		List<ReplenishImportSkuRow> narrowed = candidates;
		List<ReplenishImportSkuRow> specMatched = candidates.stream()
			.filter(row -> ReplenishMatchRules.specEquivalent(dto.getSpec(), joinSpecs(row)))
			.toList();
		if (specMatched.size() >= 1) {
			narrowed = specMatched;
		}

		if (narrowed.size() == 1) {
			return toMatched(dto, narrowed.get(0), matchType);
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

	/**
	 * 按编码列反查 SKU 行：编码列即 SKU 编号，命中即唯一。
	 */
	private List<ReplenishImportSkuRow> resolveByCode(ReplenishImportMatchDTO dto,
			Map<String, ReplenishImportSkuRow> rowByCode) {
		if (!StringUtils.hasText(dto.getCode())) {
			return List.of();
		}
		ReplenishImportSkuRow row = rowByCode.get(dto.getCode().trim());
		return row == null ? List.of() : List.of(row);
	}

	private List<ReplenishImportSkuRow> resolveByName(ReplenishImportMatchDTO dto,
			Map<String, ReplenishImportSkuRow> nameRows) {
		String name = dto.getName();
		if (!StringUtils.hasText(name)) {
			return List.of();
		}
		String target = ReplenishMatchRules.normalizeName(name);
		Map<String, List<ReplenishImportSkuRow>> grouped = nameRows.values().stream()
			.collect(java.util.stream.Collectors.groupingBy(row -> ReplenishMatchRules.normalizeName(row.getName()),
					LinkedHashMap::new, java.util.stream.Collectors.toList()));
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

	/** 模糊打分中间态：候选行 + 二字组覆盖率 */
	private record ScoredSku(ReplenishImportSkuRow row, double score) {
	}

	/**
	 * 二字组模糊匹配（{@code fuzzy=true}，接龙粘贴专用）。
	 *
	 * <p>打分口径见 {@link ReplenishMatchRules#bigramScore}，且按
	 * {@link ReplenishMatchRules#nameVariants} 的各变体（原文/剥修饰词/剥规格）
	 * 取最高分——「卤牛腱子优质」要剥出「卤牛腱子」、「上海盐汽水600ml*24瓶」
	 * 要剥出「上海盐汽水」才对得上商城名。重叠强
	 * （≥ {@link ReplenishMatchRules#FUZZY_AUTO_THRESHOLD}）且唯一最高分才自动
	 * 命中；中等重叠（≥ 建议阈值）按分数降序返回候选，交报告页一键确认。
	 * 多个 SKU 同分（常见于同 SPU 多规格）一律不自动定品——规格选择本来
	 * 就该人来。
	 */
	private ReplenishImportMatchVO fuzzyMatch(ReplenishImportMatchDTO dto,
			Map<String, ReplenishImportSkuRow> fuzzyRows) {
		List<String> variants = ReplenishMatchRules.nameVariants(dto.getName());
		if (variants.isEmpty()) {
			return notMatched(dto);
		}
		List<ScoredSku> scored = new ArrayList<>();
		for (ReplenishImportSkuRow row : fuzzyRows.values()) {
			double best = 0.0;
			for (String variant : variants) {
				best = Math.max(best, ReplenishMatchRules.bigramScore(variant, row.getName()));
			}
			if (best >= ReplenishMatchRules.FUZZY_SUGGEST_THRESHOLD) {
				scored.add(new ScoredSku(row, best));
			}
		}
		if (scored.isEmpty()) {
			return charFallbackMatch(dto, fuzzyRows, variants);
		}
		scored.sort((left, right) -> {
			int byScore = Double.compare(right.score(), left.score());
			return byScore != 0 ? byScore
					: ReplenishMatchRules.normalizeName(left.row().getName())
						.compareTo(ReplenishMatchRules.normalizeName(right.row().getName()));
		});
		ScoredSku best = scored.get(0);
		boolean uniqueBest = scored.size() == 1 || best.score() > scored.get(1).score();
		if (uniqueBest && best.score() >= ReplenishMatchRules.FUZZY_AUTO_THRESHOLD) {
			return toMatched(dto, best.row(), "NAME");
		}
		return ambiguousOf(dto, scored);
	}

	/**
	 * 单字兜底（只建议，绝不自动）：口语短名（≤3 个汉字）与商城名**零二字组
	 * 重叠**时的最后一步——「橙子」与「赣南脐橙」连二字组都不共享，
	 * 共享汉字「橙」是唯一检索线索。单字重叠噪声大（橙汁/橙味全家桶都会进
	 * 候选），必须由人在报告页把关，所以这一层只出候选、不自动定品。
	 */
	private ReplenishImportMatchVO charFallbackMatch(ReplenishImportMatchDTO dto,
			Map<String, ReplenishImportSkuRow> fuzzyRows, List<String> variants) {
		// 用剥得最干净的变体探测：修饰词/规格汉字不该参与共享计数
		Set<String> targetChars = new HashSet<>(ReplenishMatchRules.distinctCjkChars(
				variants.get(variants.size() - 1)));
		if (targetChars.isEmpty() || targetChars.size() > 3) {
			return notMatched(dto);
		}
		List<ScoredSku> scored = new ArrayList<>();
		for (ReplenishImportSkuRow row : fuzzyRows.values()) {
			long shared = ReplenishMatchRules.distinctCjkChars(row.getName()).stream()
				.filter(targetChars::contains)
				.count();
			double score = shared / (double) targetChars.size();
			if (score >= ReplenishMatchRules.FUZZY_SUGGEST_THRESHOLD) {
				scored.add(new ScoredSku(row, score));
			}
		}
		if (scored.isEmpty()) {
			return notMatched(dto);
		}
		scored.sort((left, right) -> {
			int byScore = Double.compare(right.score(), left.score());
			return byScore != 0 ? byScore
					: ReplenishMatchRules.normalizeName(left.row().getName())
						.compareTo(ReplenishMatchRules.normalizeName(right.row().getName()));
		});
		return ambiguousOf(dto, scored);
	}

	private ReplenishImportMatchVO ambiguousOf(ReplenishImportMatchDTO dto, List<ScoredSku> scored) {
		ReplenishImportMatchVO ambiguous = notMatched(dto);
		ambiguous.setMatchType("AMBIGUOUS");
		ambiguous.setCandidates(scored.stream()
			.limit(MAX_CANDIDATES)
			.map(scoredSku -> toCandidate(scoredSku.row()))
			.toList());
		return ambiguous;
	}

	private ReplenishImportMatchVO toMatched(ReplenishImportMatchDTO dto, ReplenishImportSkuRow row,
			String matchType) {
		ReplenishImportMatchVO vo = new ReplenishImportMatchVO();
		vo.setRowNo(dto.getRowNo());
		vo.setMatchType(matchType);
		vo.setSpuId(row.getSpuId());
		vo.setSkuId(row.getSkuId());
		vo.setName(row.getName());
		vo.setSpec(joinSpecs(row));
		vo.setSalesPrice(row.getSalesPrice());
		vo.setStock(row.getStock());
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
		candidate.setSalesPrice(row.getSalesPrice());
		candidate.setStock(row.getStock());
		candidate.setSkuStatus(row.getSkuStatus());
		candidate.setSpuStatus(row.getSpuStatus());
		return candidate;
	}

	/**
	 * 规格描述：SKU 规格值按「；」拼接（与 C 端展示口径一致）。
	 *
	 * <p>拼接逻辑收敛在 {@link ReplenishMatchRules#specText}：模板导出写进「规格」列的
	 * 文本与这里回传的文本必须逐字一致，否则客户没改规格也会被判成「规格变更」。
	 */
	static String joinSpecs(ReplenishImportSkuRow row) {
		if (row.getSpecsArr() == null || row.getSpecsArr().isEmpty()) {
			return null;
		}
		return ReplenishMatchRules.specText(row.getSpecsArr().stream()
			.map(com.aryn.cloud.product.api.entity.GoodsSku.Specs::getSpecsValueName)
			.toList());
	}

}
