package com.aryn.cloud.order.support;

import com.aryn.cloud.order.api.entity.SharedCartImportRow;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;

/**
 * 补给单导入报告分类（纯函数）。
 *
 * <p>把「匹配结果 + 数量」判定成报告口径的**唯一入口**：
 * 报告页与确认导入都用同一份规则，避免出现
 * 「报告说匹配成功、确认时又被拦下」。
 *
 * <p>判定优先级（后者依赖前者）：
 * <ol>
 *   <li><b>数量列没填</b> → NOT_FILLED（未填数量 = 不需要这项，直接略过，不算错误）</li>
 *   <li>未命中 / 多规格歧义 → UNMATCHED</li>
 *   <li>SKU 或 SPU 下架 → OFF_SHELF（不计入未匹配，否则用户永远补不到）</li>
 *   <li>数量非法（非正 / 低于 MOQ / 非步长整数倍）→ INVALID_QTY</li>
 *   <li>数量超过库存 → OVER_STOCK</li>
 *   <li>规格文本不一致 → SPEC_CHANGED</li>
 *   <li>其余 → OK</li>
 * </ol>
 *
 * <p><b>为什么「没填数量」必须排在最前</b>：商品目录模板（按分类铺满在售商品）
 * 的用法就是「只填你要买的那几行数量」，其余行天然空着。
 * 若按数量非法处理，客户打开报告会看到几百条红色报错，
 * 真正的错误（编码填错、库存不足）会被淹没。
 * 因此它先于「未匹配」判定：没打算买的行，商品是否下架、编码对不对都不该报。
 *
 * @author aryn
 * @since 2026/9/22
 */
public final class ReplenishImportClassifier {

	private ReplenishImportClassifier() {
	}

	/** 分类结果：结果类型 + 给用户看的原因 + 建议数量（超库存/数量异常时可调到多少） */
	public record Result(String resultType, String message, Integer suggestedQuantity) {
	}

	/**
	 * 按「匹配结果 + 数量原文」分类。
	 *
	 * @param match 商品域匹配结果，可为 null（未命中）
	 * @param excelSpec 清单里的规格原文，可为空（空表示无法比较）
	 * @param quantityText 数量列原文；**空串/null 表示客户没填这一行**
	 */
	public static Result classify(ReplenishImportMatchVO match, String excelSpec, String quantityText) {
		if (quantityText == null || quantityText.isBlank()) {
			return new Result(SharedCartImportRow.RESULT_NOT_FILLED, "未填数量，本次不采购", null);
		}
		Integer quantity = ReplenishMatchRules.parseQuantity(quantityText);
		if (match == null || "NONE".equals(match.getMatchType())) {
			return new Result(SharedCartImportRow.RESULT_UNMATCHED, "未匹配到商品，请人工补选", null);
		}
		if ("AMBIGUOUS".equals(match.getMatchType())) {
			return new Result(SharedCartImportRow.RESULT_UNMATCHED, "匹配到多个规格，请人工确认其中一个", null);
		}
		if (!"0".equals(match.getSkuStatus()) || !"1".equals(match.getSpuStatus())) {
			return new Result(SharedCartImportRow.RESULT_OFF_SHELF, "商品已下架，无法导入", null);
		}

		int moq = match.getMoq() == null || match.getMoq() < 1 ? 1 : match.getMoq();
		int stepQty = match.getStepQty() == null || match.getStepQty() < 1 ? 1 : match.getStepQty();
		Integer stock = match.getStock();
		if (quantity == null || quantity <= 0) {
			// 填了但解析不出数字（如「十份」）：这才是真正的填写错误
			return new Result(SharedCartImportRow.RESULT_INVALID_QTY, "数量必须是正整数", null);
		}
		if (quantity < moq) {
			return new Result(SharedCartImportRow.RESULT_INVALID_QTY, "数量未达到最小起订量 " + moq, moq);
		}
		if (quantity % stepQty != 0) {
			int suggested = quantity - (quantity % stepQty) + stepQty;
			// 建议值必须同时受库存约束：否则「数量 99999 / 库存 3000」会建议 100000，
			// 用户点了「调整为建议数量」后仍会因超库存被跳过，动作指向一个不可行的值。
			if (stock != null && suggested > stock) {
				Integer capped = ReplenishMatchRules.suggestedQuantity(stock, moq, stepQty);
				if (capped == null) {
					return new Result(SharedCartImportRow.RESULT_INVALID_QTY,
							"数量必须是 " + stepQty + " 的整数倍，且库存不足起订量，请人工补选", null);
				}
				return new Result(SharedCartImportRow.RESULT_INVALID_QTY,
						"数量必须是 " + stepQty + " 的整数倍，库存仅 " + stock + "，建议调整为 " + capped, capped);
			}
			return new Result(SharedCartImportRow.RESULT_INVALID_QTY, "数量必须是 " + stepQty + " 的整数倍", suggested);
		}

		if (stock != null && quantity > stock) {
			Integer suggested = ReplenishMatchRules.suggestedQuantity(stock, moq, stepQty);
			String message = suggested == null
					? "库存仅 " + stock + "，低于起订量 " + moq + "，无法导入"
					: "库存仅 " + stock + "，建议调整为 " + suggested;
			return new Result(SharedCartImportRow.RESULT_OVER_STOCK, message, suggested);
		}

		if (!ReplenishMatchRules.specEquivalent(excelSpec, match.getSpec())) {
			String skuSpec = match.getSpec() == null ? "（无规格）" : match.getSpec();
			return new Result(SharedCartImportRow.RESULT_SPEC_CHANGED,
					"清单规格「" + excelSpec + "」与当前商品规格「" + skuSpec + "」不一致", null);
		}
		return new Result(SharedCartImportRow.RESULT_OK, "已匹配", null);
	}

}
