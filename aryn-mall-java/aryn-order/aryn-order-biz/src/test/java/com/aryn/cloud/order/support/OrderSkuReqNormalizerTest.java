package com.aryn.cloud.order.support;

import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 下单/结算 SKU 归一化测试：购物车同 SKU 多行合并数量，共享车按成员拆行原样保留。
 *
 * <p>背景（真实缺陷）：购物车按靠港分组，同一 SKU 可分别落在「已结束靠港滞留行」与
 * 「无靠港归属行」，全选结算会提交两行同 SKU，被原「订单商品不能包含重复SKU」硬校验
 * 拒绝，确认页空白 ¥0.00；而共享购物车刻意按成员拆行（同 SKU 多人各一行），
 * 那是配送贴标签的依据，不能被合并。
 */
class OrderSkuReqNormalizerTest {

	private static CreateOrderSkuReqDTO sku(String skuId, int quantity) {
		CreateOrderSkuReqDTO request = new CreateOrderSkuReqDTO();
		request.setSkuId(skuId);
		request.setQuantity(quantity);
		return request;
	}

	private static CreateOrderSkuReqDTO sharedSku(String skuId, int quantity, String contributorUserId,
			String contributorName) {
		CreateOrderSkuReqDTO request = sku(skuId, quantity);
		request.setContributorUserId(contributorUserId);
		request.setContributorName(contributorName);
		return request;
	}

	/**
	 * 同一 SKU 无人货归属的两行（滞留行 + 无靠港行）合并数量，顺序按首次出现位置。
	 */
	@Test
	void mergesDuplicateSkuWithoutContributorAndKeepsFirstSeenOrder() {
		CreateOrderSkuReqDTO stranded = sku("sku-1", 2);
		CreateOrderSkuReqDTO other = sku("sku-2", 1);
		CreateOrderSkuReqDTO unattributed = sku("sku-1", 3);

		List<CreateOrderSkuReqDTO> normalized = OrderSkuReqNormalizer
			.normalize(new ArrayList<>(List.of(stranded, other, unattributed)));

		assertThat(normalized).hasSize(2);
		assertThat(normalized.get(0).getSkuId()).isEqualTo("sku-1");
		assertThat(normalized.get(0).getQuantity()).isEqualTo(5);
		assertThat(normalized.get(1).getSkuId()).isEqualTo("sku-2");
		assertThat(normalized.get(1).getQuantity()).isEqualTo(1);
	}

	/**
	 * 共享车按成员拆行：同 SKU 两行都带贡献者，数量与归属必须原样保留（贴标签依据）。
	 */
	@Test
	void keepsSharedCartRowsSplitByMember() {
		CreateOrderSkuReqDTO first = sharedSku("sku-1", 2, "user-a", "张三");
		CreateOrderSkuReqDTO second = sharedSku("sku-1", 3, "user-b", "李四");

		List<CreateOrderSkuReqDTO> normalized = OrderSkuReqNormalizer
			.normalize(new ArrayList<>(List.of(first, second)));

		assertThat(normalized).hasSize(2);
		assertThat(normalized).containsExactly(first, second);
		assertThat(normalized.get(0).getQuantity()).isEqualTo(2);
		assertThat(normalized.get(1).getQuantity()).isEqualTo(3);
		assertThat(normalized.get(1).getContributorName()).isEqualTo("李四");
	}

	/**
	 * 混合组：组内任一行带贡献者即视为共享车拆行，整组保留（不能只合并不带贡献者的行，
	 * 否则同一 SKU 仍会重复，且归属语义被割裂）。
	 */
	@Test
	void keepsWholeGroupWhenAnyRowHasContributor() {
		CreateOrderSkuReqDTO noContributor = sku("sku-1", 1);
		CreateOrderSkuReqDTO withContributor = sharedSku("sku-1", 2, "user-a", "张三");

		List<CreateOrderSkuReqDTO> normalized = OrderSkuReqNormalizer
			.normalize(new ArrayList<>(List.of(noContributor, withContributor)));

		assertThat(normalized).hasSize(2);
		assertThat(normalized).containsExactly(noContributor, withContributor);
	}

	/**
	 * 不同 SKU 各自成行，数量原样。
	 */
	@Test
	void keepsDifferentSkusUntouched() {
		CreateOrderSkuReqDTO first = sku("sku-1", 2);
		CreateOrderSkuReqDTO second = sku("sku-2", 3);

		List<CreateOrderSkuReqDTO> normalized = OrderSkuReqNormalizer
			.normalize(new ArrayList<>(List.of(first, second)));

		assertThat(normalized).hasSize(2);
		assertThat(normalized).containsExactly(first, second);
	}

	/**
	 * 不修改入参对象：合并结果是首行的浅拷贝，原始行数量保持不变（避免把购物车/确认页
	 * 的请求对象写脏，也避免重复调用时数量被反复放大）。
	 */
	@Test
	void doesNotMutateInputRows() {
		CreateOrderSkuReqDTO first = sku("sku-1", 2);
		first.setSpecsInfo("红色");
		CreateOrderSkuReqDTO second = sku("sku-1", 3);
		List<CreateOrderSkuReqDTO> requests = new ArrayList<>(List.of(first, second));

		List<CreateOrderSkuReqDTO> normalized = OrderSkuReqNormalizer.normalize(requests);

		assertThat(requests).hasSize(2);
		assertThat(first.getQuantity()).isEqualTo(2);
		assertThat(second.getQuantity()).isEqualTo(3);
		assertThat(normalized.get(0)).isNotSameAs(first);
		// 首行的其它字段（规格等）随浅拷贝带出，合并行不丢展示信息
		assertThat(normalized.get(0).getSpecsInfo()).isEqualTo("红色");
	}

	/**
	 * 空输入安全：null 与空列表原样返回（null 进 null 出，避免调用方额外判空）。
	 */
	@Test
	void handlesNullAndEmptyInput() {
		assertThat(OrderSkuReqNormalizer.normalize(null)).isNull();
		List<CreateOrderSkuReqDTO> empty = new ArrayList<>();
		assertThat(OrderSkuReqNormalizer.normalize(empty)).isSameAs(empty);
	}

	/**
	 * skuId 为空白的行无法判定归属：不参与合并、各自原样保留。
	 */
	@Test
	void keepsRowsWithBlankSkuIdUntouched() {
		CreateOrderSkuReqDTO blankSku = new CreateOrderSkuReqDTO();
		blankSku.setSkuId("  ");
		blankSku.setQuantity(2);
		CreateOrderSkuReqDTO nullSku = new CreateOrderSkuReqDTO();
		nullSku.setQuantity(3);

		List<CreateOrderSkuReqDTO> normalized = OrderSkuReqNormalizer
			.normalize(new ArrayList<>(List.of(blankSku, nullSku)));

		assertThat(normalized).containsExactly(blankSku, nullSku);
	}

	/**
	 * 数量为 null 属异常输入（控制器已有 @NotNull 校验）：该组不合并、原样保留，不能抛 NPE。
	 */
	@Test
	void keepsGroupWhenQuantityIsNull() {
		CreateOrderSkuReqDTO validQuantity = sku("sku-1", 2);
		CreateOrderSkuReqDTO nullQuantity = new CreateOrderSkuReqDTO();
		nullQuantity.setSkuId("sku-1");
		nullQuantity.setQuantity(null);

		List<CreateOrderSkuReqDTO> normalized = OrderSkuReqNormalizer
			.normalize(new ArrayList<>(List.of(validQuantity, nullQuantity)));

		assertThat(normalized).containsExactly(validQuantity, nullQuantity);
	}

}
