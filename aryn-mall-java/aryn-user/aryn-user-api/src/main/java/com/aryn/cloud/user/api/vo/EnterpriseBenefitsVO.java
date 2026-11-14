package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 企业会员权益聚合 VO（按主体维度下发）。
 *
 * <p>双轨会员的企业轨：岸上企业（subjectType=1）/ 船舶（subjectType=2）主体
 * 经绑定表解析企业等级，聚合该等级挂载的权益。权益分轨消费：
 * scene=1 读个人 member_benefits，scene=2/3 读本 VO，不交叉取优。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "企业会员权益聚合VO")
public class EnterpriseBenefitsVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主体类型：1岸上企业 2船舶")
	private String subjectType;

	@Schema(description = "主体ID（buyer_org_id / vessel_id）")
	private String subjectId;

	@Schema(description = "企业等级ID（未定级为 null）")
	private String levelId;

	@Schema(description = "企业等级名称")
	private String levelName;

	@Schema(description = "企业等级 sortOrder（越大越高；未定级为 null）")
	private Integer levelSortOrder;

	@Schema(description = "企业折扣率（多档取最优=最小值；未配置为 null）")
	private BigDecimal discountRate;

	@Schema(description = "是否免邮（scene=3 岸上公司配送生效；到船恒免邮与此无关）")
	private Boolean freeShipping;

	@Schema(description = "协议价政策池（等级可用的价格政策ID，空=无）")
	private List<String> policyPoolIds;

	@Schema(description = "开票资质（true=可申请企业发票）")
	private Boolean invoicingEnabled;

	@Schema(description = "专属客服/业务员服务（true=有专属服务）")
	private Boolean dedicatedService;

	@Schema(description = "返利率（百分数，预留字段，本期不生效）")
	private BigDecimal rebateRate;

	@Schema(description = "账期天数（预留字段，本期不生效）")
	private Integer creditDays;

}
