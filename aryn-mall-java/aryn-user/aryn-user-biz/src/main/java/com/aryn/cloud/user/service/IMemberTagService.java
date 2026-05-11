package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.MemberTag;

import java.util.List;

/**
 * 会员标签
 *
 * @author 雨滴kian
 */
public interface IMemberTagService extends IService<MemberTag> {

	IPage<MemberTag> getPage(Page page, MemberTag memberTag);

	boolean saveTag(MemberTag memberTag);

	boolean updateTag(MemberTag memberTag);

	boolean deleteTag(String id);

	/**
	 * 给用户打标签
	 * @param userId 用户ID
	 * @param tagIds 标签ID列表
	 */
	void tagUser(String userId, List<String> tagIds);

	/**
	 * 取消用户标签
	 * @param userId 用户ID
	 * @param tagIds 标签ID列表
	 */
	void untagUser(String userId, List<String> tagIds);

	/**
	 * 获取用户标签列表
	 * @param userId 用户ID
	 * @return 标签列表
	 */
	List<MemberTag> getUserTags(String userId);

}
