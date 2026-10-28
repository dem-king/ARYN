package com.aryn.cloud.order.support;

import cn.hutool.core.bean.BeanUtil;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 下单/结算请求的 SKU 明细归一化：购物车同 SKU 多行合并数量、共享车按成员拆行保留。
 *
 * <p><b>为什么会有同 SKU 多行：</b>购物车按靠港分组存放（唯一键含 vessel_call_id），
 * 同一 SKU 可以合法地占多行：一行挂在「已结束靠港」的滞留行（vessel_call_id 有值，
 * 但该靠港 callOrderable=false），一行挂在「当前无靠港」分组（vessel_call_id 为空）。
 * 这两行对订单没有区分意义——最终都按常规配送、打印同一张配送单，确认页也按 SKU
 * 展示为一条；若原样提交，同一份收货需求会因重复 SKU 被拒绝（确认页随之空白 ¥0.00）。
 * 因此按 skuId 把数量相加合并到首次出现的那一行。
 *
 * <p><b>为什么共享车拆行不能合并：</b>共享购物车刻意按成员拆行（同一 SKU 多位成员
 * 各一行），拆分粒度是仓库按人分拣、打印配送姓名标签的依据，contributorUserId /
 * contributorName 承载了「这货是谁要的」的归属信息；合并会丢掉归属，贴错标签。
 * 因此组内任一行带贡献者信息时，视为合法的按成员拆行，整组原样保留。
 *
 * <p>本类替代原先「订单商品不能包含重复SKU」的硬校验：不是拒绝重复，而是先按语义
 * 归一化——只有确认是同一份收货需求（无人货归属）的重复行才合并数量。
 *
 * <p>不修改入参对象：合并结果为浅拷贝首行后再累加数量；入参为 null/空时原样返回；
 * skuId 为空白的行无法判定归属，不参与合并、原样保留；数量为 null 的异常行（控制器
 * 已有 @NotNull 校验，这里只做防御）该组不合并，避免 NPE。
 *
 * @author aryn
 * @since 2026/10/25
 */
public final class OrderSkuReqNormalizer {

	private OrderSkuReqNormalizer() {
	}

	/**
	 * 归一化 SKU 明细：同 skuId 且无人货归属的多行合并数量，带贡献者的拆行整组保留。
	 *
	 * @param requests 原始 SKU 明细（不会被修改）
	 * @return 归一化后的明细（保持首次出现顺序）；入参为 null/空时原样返回
	 */
	public static List<CreateOrderSkuReqDTO> normalize(List<CreateOrderSkuReqDTO> requests) {
		if (requests == null || requests.isEmpty()) {
			return requests;
		}

		// 先按 skuId 分组（LinkedHashMap 保留首次出现顺序），并预先算好哪些组需要合并数量
		Map<String, List<CreateOrderSkuReqDTO>> groupBySkuId = new LinkedHashMap<>();
		for (CreateOrderSkuReqDTO request : requests) {
			if (request == null || !StringUtils.hasText(request.getSkuId())) {
				continue;
			}
			groupBySkuId.computeIfAbsent(request.getSkuId(), key -> new ArrayList<>()).add(request);
		}

		// 合并结果按 skuId 暂存：第二遍遍历时只在首行位置输出一次合并行，其余同 SKU 行丢弃
		Map<String, CreateOrderSkuReqDTO> mergedRowBySkuId = new LinkedHashMap<>();
		for (Map.Entry<String, List<CreateOrderSkuReqDTO>> entry : groupBySkuId.entrySet()) {
			List<CreateOrderSkuReqDTO> group = entry.getValue();
			// 单行无需合并；共享车拆行（带贡献者）必须整组保留，否则丢失配送贴标签依据
			if (group.size() < 2 || hasContributor(group) || hasNullQuantity(group)) {
				continue;
			}
			int totalQuantity = 0;
			for (CreateOrderSkuReqDTO row : group) {
				totalQuantity += row.getQuantity();
			}
			// 浅拷贝首行再改数量：其余行的展示/优惠字段以首行为准，且绝不回写入参对象
			CreateOrderSkuReqDTO merged = new CreateOrderSkuReqDTO();
			BeanUtil.copyProperties(group.get(0), merged);
			merged.setQuantity(totalQuantity);
			mergedRowBySkuId.put(entry.getKey(), merged);
		}

		// 需要合并的 skuId 集合：保持到第二遍遍历结束，用于判断后续同 SKU 行是否应丢弃
		Set<String> mergeableSkuIds = new LinkedHashSet<>(mergedRowBySkuId.keySet());
		List<CreateOrderSkuReqDTO> normalized = new ArrayList<>(requests.size());
		for (CreateOrderSkuReqDTO request : requests) {
			// null 行与空白 skuId 行无法判定归属，原样保留
			if (request == null || !StringUtils.hasText(request.getSkuId())) {
				normalized.add(request);
				continue;
			}
			if (!mergeableSkuIds.contains(request.getSkuId())) {
				normalized.add(request);
				continue;
			}
			// 组的首行位置输出合并行（取出即移除），后续同 SKU 行数量已累加，直接丢弃
			CreateOrderSkuReqDTO merged = mergedRowBySkuId.remove(request.getSkuId());
			if (merged != null) {
				normalized.add(merged);
			}
		}
		return normalized;
	}

	/**
	 * 组内是否任一行带贡献者信息（共享购物车按成员拆行的标记）。
	 */
	private static boolean hasContributor(List<CreateOrderSkuReqDTO> group) {
		return group.stream()
			.anyMatch(row -> StringUtils.hasText(row.getContributorUserId())
					|| StringUtils.hasText(row.getContributorName()));
	}

	/**
	 * 组内是否存在数量为 null 的异常行：无法相加，整组保持原样，避免 NPE。
	 */
	private static boolean hasNullQuantity(List<CreateOrderSkuReqDTO> group) {
		return group.stream().anyMatch(row -> row.getQuantity() == null);
	}

}
