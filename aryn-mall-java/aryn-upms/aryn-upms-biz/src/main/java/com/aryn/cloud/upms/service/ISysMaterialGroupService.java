
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysMaterialGroup;

import java.util.List;

/**
 * 素材分组
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:36
 */
public interface ISysMaterialGroupService extends IService<SysMaterialGroup> {

	/**
	 * 分页查询
	 * @param page
	 * @param sysMaterialGroup
	 * @return
	 */
	IPage<SysMaterialGroup> getPage(Page page, SysMaterialGroup sysMaterialGroup);

	/**
	 * 查询全部
	 * @param sysMaterialGroup
	 * @return
	 */
	List<SysMaterialGroup> getList(SysMaterialGroup sysMaterialGroup);

}
