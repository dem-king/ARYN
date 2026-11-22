package com.aryn.cloud.order.api.vo;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.vo.CouponUserRespVO;
import com.aryn.cloud.user.api.vo.EnterpriseBenefitsVO;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** 内部情景计算事实，不作为HTTP出参；只读捕获，不持有查询回调。 */
@Data
public class OrderScenarioFactsVO implements Serializable {
	private PromotionContextDTO context;
	private List<GoodsSku> skus;
	private MemberBenefitsVO memberBenefits;
	private EnterpriseBenefitsVO enterpriseBenefits;
	private CouponUserRespVO coupon;
	/** 仅模拟提议可覆盖一次券事实，不与原用户券叠加、不发行。 */
	private CouponUserRespVO proposedCoupon;
	private BigDecimal memberCostFloorMultiplier;
	private boolean stackPolicyEnabled;
	private boolean minPayableEnabled;
	private BigDecimal minPayableAmount;
}
