package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.MemberBenefit;

import java.util.List;

/**
 * 会员权益
 *
 * @author 雨滴kian
 */
public interface IMemberBenefitService extends IService<MemberBenefit> {

	IPage<MemberBenefit> getPage(Page page, MemberBenefit memberBenefit);

	boolean saveBenefit(MemberBenefit memberBenefit);

	boolean updateBenefit(MemberBenefit memberBenefit);

	boolean deleteBenefit(String id);

	/**
	 * 绑定权益与等级关联
	 * @param benefitId 权益ID
	 * @param levelIds 等级ID列表
	 */
	void bindLevels(String benefitId, List<String> levelIds);

	/**
	 * 根据等级ID查询关联的权益列表
	 * @param levelId 等级ID
	 * @return 权益列表
	 */
	List<MemberBenefit> getLevelBenefits(String levelId);

}
