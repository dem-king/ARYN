package com.aryn.cloud.user.service;

import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.user.api.entity.EnterpriseBenefit;
import com.aryn.cloud.user.api.entity.EnterpriseLevel;
import com.aryn.cloud.user.api.vo.EnterpriseBenefitsVO;

import java.util.List;

/**
 * 企业会员双轨服务（企业轨）：等级/权益配置、成长值累计、等级重算、权益聚合。
 *
 * @author aryn
 * @since 2026/11/14
 */
public interface IEnterpriseMemberService {

	// ------------------------------------------------------------------
	// 等级配置
	// ------------------------------------------------------------------

	List<EnterpriseLevel> listLevels(String tenantId, String status, String keyword);

	EnterpriseLevel saveLevel(String tenantId, EnterpriseLevel level);

	EnterpriseLevel updateLevel(String tenantId, String id, EnterpriseLevel level);

	void deleteLevel(String tenantId, String id);

	// ------------------------------------------------------------------
	// 权益配置
	// ------------------------------------------------------------------

	List<EnterpriseBenefit> listBenefits(String tenantId, String status, String keyword);

	List<EnterpriseBenefit> listBenefitsByLevel(String tenantId, String levelId);

	/** 权益挂载的企业等级ID集合（编辑回显）。 */
	List<String> listLevelIdsOfBenefit(String tenantId, String benefitId);

	EnterpriseBenefit saveBenefit(String tenantId, EnterpriseBenefit benefit, List<String> levelIds);

	EnterpriseBenefit updateBenefit(String tenantId, String id, EnterpriseBenefit benefit, List<String> levelIds);

	void deleteBenefit(String tenantId, String id);

	// ------------------------------------------------------------------
	// 成长值与等级重算
	// ------------------------------------------------------------------

	/**
	 * 订单完成事件累计主体采购额并重算等级（scene=2 按船舶、scene=3 按采购主体）。
	 *
	 * <p>fail-open：企业轨累计失败只记日志，不影响个人积分/等级主流程（事件消费各自独立）。
	 */
	void accumulateOrderGrowth(OrderCompleteEvent event);

	/** 单主体等级重算：近 12 期采购额求和 → 取满足门槛的最高档 → 绑定 upsert + 变更流水。 */
	void recalculateSubjectLevel(String tenantId, String subjectType, String subjectId, String reason);

	/** 月度对账：对全部已绑定主体重算等级（配置调整/历史数据修复后的兜底收敛）。 */
	int reconcileAllLevels(String tenantId, String reason);

	// ------------------------------------------------------------------
	// 权益聚合
	// ------------------------------------------------------------------

	/** 主体权益聚合（等级经绑定表解析；未定级/查询失败返回 null，fail-closed）。 */
	EnterpriseBenefitsVO getSubjectBenefits(String tenantId, String subjectType, String subjectId);

}
