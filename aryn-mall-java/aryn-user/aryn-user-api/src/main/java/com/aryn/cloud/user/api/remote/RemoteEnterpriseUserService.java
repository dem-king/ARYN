package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.vo.EnterpriseBenefitsVO;

import java.util.List;

/**
 * 远程企业会员服务：双轨会员企业轨的跨域查询（promotion 引擎协议价标签、
 * 订单计价企业权益消费、C 端企业会员卡展示）。
 *
 * <p>所有方法以入参 tenantId 显式锁定租户上下文；查询失败一律返回 null/空列表
 * （fail-closed），调用方按无权益处理，不得默认放权。
 *
 * @author aryn
 * @since 2026/11/14
 */
public interface RemoteEnterpriseUserService {

	/**
	 * 租户内用户命中的会员标签 ID 集合（价格政策 apply_type=3 客群标签圈定使用）。
	 * @param tenantId 租户ID
	 * @param userId 用户ID
	 * @return 标签ID列表；无标签或查询失败返回空列表（fail-closed）
	 */
	List<String> getUserTagIds(String tenantId, String userId);

	/**
	 * 采购主体的企业会员权益聚合（等级经绑定表解析，未定级/主体不存在返回 null）。
	 *
	 * <p>主体归属校验由调用方负责（订单侧已在 requireForOrder / 船上下文校验过成员与主体），
	 * 本方法只做「主体ID → 等级 → 权益」解析，不做成员资格校验。
	 *
	 * @param tenantId 租户ID
	 * @param subjectType 主体类型：1岸上企业 2船舶
	 * @param subjectId 主体ID（buyer_org_id / vessel_id）
	 * @return 权益聚合 VO；未定级/查询失败返回 null
	 */
	EnterpriseBenefitsVO getSubjectBenefits(String tenantId, String subjectType, String subjectId);

}
