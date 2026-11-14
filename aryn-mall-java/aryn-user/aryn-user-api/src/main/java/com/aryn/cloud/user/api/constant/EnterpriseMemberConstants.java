package com.aryn.cloud.user.api.constant;

/**
 * 企业会员双轨常量：主体类型编码与权益类型编码。
 *
 * <p>双轨会员：scene=1 走个人轨（member_level/member_benefit），
 * scene=2/3 走企业轨（enterprise_level/enterprise_benefit）——
 * 权益按购买场景分轨消费，不交叉取优，杜绝个人等级与企业等级套利。
 *
 * @author aryn
 * @since 2026/11/14
 */
public final class EnterpriseMemberConstants {

	/** 主体类型：岸上企业（buyer_org，scene=3 企业采购） */
	public static final String SUBJECT_BUYER_ORG = "1";

	/** 主体类型：船舶（vessel，scene=2 船供采购） */
	public static final String SUBJECT_VESSEL = "2";

	/** 权益类型：企业折扣率（benefit_value 存 0.9500 形态折扣率） */
	public static final String BENEFIT_DISCOUNT = "1";

	/** 权益类型：免邮（benefit_value 固定 1；scene=3 岸上公司配送生效） */
	public static final String BENEFIT_FREE_SHIPPING = "2";

	/** 权益类型：协议价政策池（benefit_value 存 price_policy.id 集合，逗号分隔） */
	public static final String BENEFIT_POLICY_POOL = "3";

	/** 权益类型：开票资质（benefit_value 固定 1） */
	public static final String BENEFIT_INVOICING = "4";

	/** 权益类型：专属客服/业务员（benefit_value 固定 1） */
	public static final String BENEFIT_DEDICATED_SERVICE = "5";

	/** 权益类型：返利率（百分数，预留，本期不生效） */
	public static final String BENEFIT_REBATE_RATE = "6";

	/** 权益类型：账期天数（预留，本期不生效） */
	public static final String BENEFIT_CREDIT_DAYS = "7";

	private EnterpriseMemberConstants() {
	}

}
