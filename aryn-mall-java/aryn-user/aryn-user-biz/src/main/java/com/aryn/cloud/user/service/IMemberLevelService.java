package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.vo.MemberLevelRecordVO;

/**
 * 会员等级配置
 *
 * @author 雨滴kian
 */
public interface IMemberLevelService extends IService<MemberLevel> {

	IPage<MemberLevel> getPage(Page page, MemberLevel memberLevel);

	MemberLevel getDetailById(String id);

	boolean saveLevel(MemberLevel memberLevel);

	boolean updateLevel(MemberLevel memberLevel);

	boolean deleteLevel(String id);

	IPage<MemberLevelRecordVO> getRecordPage(Page page, String userId);

	/**
	 * 重新计算会员等级
	 * @param userId 用户ID
	 */
	void recalculateLevel(String userId);

	/**
	 * 成长值变动
	 * @param userId 用户ID
	 * @param value 变动值(正加负减)
	 * @param source 来源(order/sign_in/review/refund/admin)
	 * @param bizId 业务ID
	 */
	void addGrowthValue(String userId, Integer value, String source, String bizId);

	/**
	 * 根据成长值自动升降级
	 * @param userId 用户ID
	 */
	void recalculateLevelByGrowth(String userId);

}
