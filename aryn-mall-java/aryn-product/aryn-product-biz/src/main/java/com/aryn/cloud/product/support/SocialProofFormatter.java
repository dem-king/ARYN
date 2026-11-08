package com.aryn.cloud.product.support;

import java.math.BigDecimal;

/**
 * 卡面社证文案格式化（门槛 + 向下取整，口径单点收口）。
 *
 * <p>文案与类型一起下发：类型（{@code kind}）供前端按语义配证言图标。
 * 文案要有「活人感」——主语是人不是报表：数字两侧留空格（盘古之白），
 * 量词用「人/位」，回购率说成「N% 的人回购」而不是「回购率N%」指标腔。
 * 两条硬规则不变：
 * <ul>
 *   <li>只报量级不报精确数——floor 到千/万位加「+」，向下取整不可夸大（合规），
 *       也避免给竞对留精确情报；</li>
 *   <li>低于门槛不下发——新品/滞销品绝不暴露难看的小数字，宁缺毋滥；</li>
 *   <li>单卡单条，优先级：回购率 → 回头客 → 下单人数（复购质量强于体量）。</li>
 * </ul>
 *
 * <p>纯函数无状态；门槛由调用方传入（租户级配置 {@code goods-card.social-proof.*}），
 * 本类不读配置不查库。
 *
 * @author aryn
 * @since 2026/11/08
 */
public final class SocialProofFormatter {

	/** 计数格式化的千位分界：达到千位才做「X000+」取整 */
	private static final long THOUSAND = 1_000;

	/** 计数格式化的万位分界：达到万位才做「X万+」取整 */
	private static final long TEN_THOUSAND = 10_000;

	/** 社证类型：回购率达标（前端据此配证言图标，未知类型前端兜底） */
	public static final String KIND_REPURCHASE = "repurchase";

	/** 社证类型：回头客数达标 */
	public static final String KIND_REPEAT = "repeat";

	/** 社证类型：月售体量达标 */
	public static final String KIND_BUYER = "buyer";

	/** 社证展示结果：成文文案 + 类型，两者一一对应、同生共死 */
	public record Proof(String text, String kind) {
	}

	private SocialProofFormatter() {
	}

	/**
	 * 组装单卡社证（单卡单条，按优先级取一）。
	 *
	 * @param buyerCnt       近N天去重买家数
	 * @param repeatBuyerCnt 近N天购买≥2次的去重买家数
	 * @param repurchaseRate 回购率%（repeat/buyer*100）
	 * @param minBuyerCnt    买家/回头客数门槛（低于不下发）
	 * @param minRate        回购率门槛%（低于不下发）
	 * @return 文案+类型；全部未达门槛返回 null（卡面不渲染社证行）
	 */
	public static Proof socialProof(Integer buyerCnt, Integer repeatBuyerCnt, BigDecimal repurchaseRate,
			int minBuyerCnt, int minRate) {
		if (repurchaseRate != null && buyerCnt != null && buyerCnt >= minBuyerCnt
				&& repurchaseRate.doubleValue() >= minRate) {
			return new Proof(repurchaseRate.intValue() + "% 的人回购", KIND_REPURCHASE);
		}
		String repeatText = formatCount(repeatBuyerCnt, minBuyerCnt);
		if (repeatText != null) {
			return new Proof("月 " + repeatText + " 位回头客", KIND_REPEAT);
		}
		String buyerText = formatCount(buyerCnt, minBuyerCnt);
		if (buyerText != null) {
			return new Proof("月 " + buyerText + " 人已下单", KIND_BUYER);
		}
		return null;
	}

	/**
	 * 计数取整文案：n≥1万 → floor(n/1万)+"万+"；n≥1000 → floor(n/1000)+"000+"；
	 * 其余（门槛低于 1000 的配置下）原值展示；n &lt; 门槛返回 null。
	 */
	public static String formatCount(Integer count, int minCount) {
		if (count == null || count < minCount) {
			return null;
		}
		if (count >= TEN_THOUSAND) {
			return (count / TEN_THOUSAND) + "万+";
		}
		if (count >= THOUSAND) {
			return (count / THOUSAND) + "000+";
		}
		return String.valueOf(count);
	}

}
