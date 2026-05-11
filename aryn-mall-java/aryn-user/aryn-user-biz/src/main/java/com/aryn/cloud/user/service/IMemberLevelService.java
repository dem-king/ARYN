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

}
