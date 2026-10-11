package com.aryn.cloud.product.support;

import com.aryn.cloud.product.mapper.ReplenishImportMatchMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目录向量召回：把本租户在售 SKU 名向量化缓存，报货品名用余弦相似度取最相近的候选。
 *
 * <p>字面召回（精确/包含/二字组/单字）只能覆盖**有共享字符**的叫法，
 * 「小番茄 vs 西红柿」「橙子 vs 赣南脐橙」这类零重叠对永远够不着；
 * 向量召回用语义相似度补上这一层，也是「混合检索」里向量路的标准位置。
 *
 * <p>三条工程纪律：
 * <ol>
 *   <li><b>召回是确定性的</b>：同一份目录 + 同一个品名永远得到同一份候选
 *       （编码器无采样），报告可复现、可单测；</li>
 *   <li><b>目录小到不需要向量库</b>：在售 SKU 上限 2000（与导入上限对齐），
 *       进程内暴力余弦足够，缓存按租户常驻、TTL 过期后**异步刷新**——
 *       过期瞬间先回旧快照，重建期间召回不被阻塞，供应商抖动也不清缓存；</li>
 *   <li><b>同 SPU 多规格会整组进入候选</b>（同名不同规格向量相同），这是
 *       预期行为：规格选择本来就是人的事；单 SPU 占位上限 {@link #maxPerSpu}
 *       防止一组规格把其它候选挤出 top-K。</li>
 * </ol>
 *
 * @author aryn
 * @since 2026/10/11
 */
@Slf4j
@Service
public class CatalogVectorRecallService {

	/** 目录向量化上限：与导入行数上限同一量纲（单租户在售 SKU 的现实规模） */
	public static final int CATALOG_LIMIT = 2000;

	private final ReplenishImportMatchMapper replenishImportMatchMapper;

	private final EmbeddingClient embeddingClient;

	/** 缓存有效期：目录变更最多滞后一个 TTL 进入向量索引 */
	private final Duration cacheTtl;

	private final int topK;

	private final double scoreThreshold;

	/** 单个 SPU 在候选里最多占几条（多规格同组不挤掉别的商品） */
	private final int maxPerSpu;

	private final ConcurrentHashMap<String, CatalogVectors> cacheByTenant = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Object> buildLocks = new ConcurrentHashMap<>();

	private final Set<String> rebuilding = ConcurrentHashMap.newKeySet();

	public CatalogVectorRecallService(
			ReplenishImportMatchMapper replenishImportMatchMapper,
			EmbeddingClient embeddingClient,
			@Value("${aryn.embedding.catalog-ttl-minutes:60}") long catalogTtlMinutes,
			@Value("${aryn.embedding.recall-top-k:6}") int recallTopK,
			@Value("${aryn.embedding.score-threshold:0.55}") double scoreThreshold) {
		this.replenishImportMatchMapper = replenishImportMatchMapper;
		this.embeddingClient = embeddingClient;
		this.cacheTtl = Duration.ofMinutes(Math.max(1, catalogTtlMinutes));
		this.topK = Math.max(1, recallTopK);
		this.scoreThreshold = scoreThreshold;
		this.maxPerSpu = 2;
	}

	/** 未配 embedding key 时整体不可用，调用方跳过（走纯字面匹配） */
	public boolean isAvailable() {
		return embeddingClient.isAvailable();
	}

	/** 单条召回结果：候选 SKU 行 + 余弦相似度（0~1，已归一化的向量点积） */
	public record VectorMatch(ReplenishImportSkuRow row, double score) {
	}

	/**
	 * 按品名批量召回，返回与输入品名列表一一对应的候选列表（可能为空）。
	 *
	 * <p>同名品名由调用方去重后传入；embedding 调用失败会抛出，由调用方降级。
	 */
	public List<List<VectorMatch>> recall(String tenantId, List<String> names) {
		if (names.isEmpty() || !isAvailable()) {
			return names.stream().map(name -> List.<VectorMatch>of()).toList();
		}
		CatalogVectors catalog = catalogSnapshot(tenantId);
		List<float[]> queryVectors = embeddingClient.embed(names);
		List<List<VectorMatch>> result = new ArrayList<>(names.size());
		for (int index = 0; index < names.size(); index++) {
			result.add(recallOne(catalog, queryVectors.get(index)));
		}
		return result;
	}

	/** 单品名对全目录取 top-K：阈值过滤 + 单 SPU 占位上限 */
	private List<VectorMatch> recallOne(CatalogVectors catalog, float[] queryVector) {
		float[] normalized = normalize(queryVector);
		List<VectorMatch> scored = new ArrayList<>(catalog.rows().size());
		for (int index = 0; index < catalog.rows().size(); index++) {
			double score = dot(normalized, catalog.vectors()[index]);
			if (score >= scoreThreshold) {
				scored.add(new VectorMatch(catalog.rows().get(index), score));
			}
		}
		scored.sort((left, right) -> {
			int byScore = Double.compare(right.score(), left.score());
			if (byScore != 0) {
				return byScore;
			}
			return left.row().getSkuId() == null ? 0
					: left.row().getSkuId().compareTo(right.row().getSkuId());
		});
		List<VectorMatch> result = new ArrayList<>(topK);
		Set<String> spuCounts = new HashSet<>();
		Map<String, Integer> perSpu = new LinkedHashMap<>();
		for (VectorMatch match : scored) {
			if (result.size() >= topK) {
				break;
			}
			String spuId = match.row().getSpuId();
			if (spuId != null) {
				int used = perSpu.getOrDefault(spuId, 0);
				if (used >= maxPerSpu) {
					continue;
				}
				perSpu.put(spuId, used + 1);
			}
			result.add(match);
		}
		return result;
	}

	/**
	 * 取租户目录向量快照：无缓存同步构建（首次使用，一次性代价）；
	 * 过期后先回旧快照并触发异步重建（供应商抖动不清空可用缓存）。
	 */
	private CatalogVectors catalogSnapshot(String tenantId) {
		CatalogVectors cached = cacheByTenant.get(tenantId);
		if (cached == null) {
			synchronized (buildLock(tenantId)) {
				cached = cacheByTenant.get(tenantId);
				if (cached == null) {
					CatalogVectors built = buildCatalog(tenantId);
					cacheByTenant.put(tenantId, built);
					return built;
				}
			}
		}
		if (cached.builtAt().plus(cacheTtl).isBefore(LocalDateTime.now())
				&& rebuilding.add(tenantId)) {
			CompletableFuture.runAsync(() -> {
				try {
					synchronized (buildLock(tenantId)) {
						CatalogVectors fresh = buildCatalog(tenantId);
						cacheByTenant.put(tenantId, fresh);
						log.info("租户[{}]目录向量缓存已刷新，共 {} 条", tenantId, fresh.rows().size());
					}
				}
				catch (Exception exception) {
					log.warn("租户[{}]目录向量缓存异步刷新失败，沿用旧快照", tenantId, exception);
				}
				finally {
					rebuilding.remove(tenantId);
				}
			});
		}
		return cached;
	}

	private Object buildLock(String tenantId) {
		return buildLocks.computeIfAbsent(tenantId, key -> new Object());
	}

	/** 构建目录向量：在售 SKU（与模板目录同源同条件）按去重品名批量向量化 */
	private CatalogVectors buildCatalog(String tenantId) {
		List<ReplenishImportSkuRow> rows = replenishImportMatchMapper.selectCatalogRows(tenantId, CATALOG_LIMIT);
		List<ReplenishImportSkuRow> usable = rows.stream()
			.filter(row -> StringUtils.hasText(row.getName()) && StringUtils.hasText(row.getSkuId()))
			.toList();
		if (usable.isEmpty()) {
			return new CatalogVectors(LocalDateTime.now(), List.of(), new float[0][]);
		}
		// 同名 SKU（同 SPU 多规格）共享一个向量：省调用，也保证同名同分
		LinkedHashMap<String, float[]> vectorsByName = new LinkedHashMap<>();
		for (ReplenishImportSkuRow row : usable) {
			vectorsByName.putIfAbsent(row.getName().trim(), null);
		}
		List<String> distinctNames = List.copyOf(vectorsByName.keySet());
		List<float[]> embedded = embeddingClient.embed(distinctNames);
		for (int index = 0; index < distinctNames.size(); index++) {
			vectorsByName.put(distinctNames.get(index), normalize(embedded.get(index)));
		}
		List<ReplenishImportSkuRow> orderedRows = new ArrayList<>(usable.size());
		float[][] vectors = new float[usable.size()][];
		for (int index = 0; index < usable.size(); index++) {
			orderedRows.add(usable.get(index));
			vectors[index] = vectorsByName.get(usable.get(index).getName().trim());
		}
		log.info("租户[{}]目录向量构建完成，SKU {} 条、去重品名 {} 个", tenantId, orderedRows.size(), distinctNames.size());
		return new CatalogVectors(LocalDateTime.now(), List.copyOf(orderedRows), vectors);
	}

	/** 归一化后点积即余弦相似度 */
	static float[] normalize(float[] vector) {
		double sum = 0;
		for (float value : vector) {
			sum += (double) value * value;
		}
		float norm = (float) Math.sqrt(sum);
		if (norm == 0f) {
			return vector;
		}
		float[] result = new float[vector.length];
		for (int index = 0; index < vector.length; index++) {
			result[index] = vector[index] / norm;
		}
		return result;
	}

	static double dot(float[] left, float[] right) {
		int length = Math.min(left.length, right.length);
		double sum = 0;
		for (int index = 0; index < length; index++) {
			sum += (double) left[index] * right[index];
		}
		return sum;
	}

	/** 租户目录向量快照（不可变，发布后各线程安全读） */
	private record CatalogVectors(LocalDateTime builtAt, List<ReplenishImportSkuRow> rows, float[][] vectors) {
	}

}
