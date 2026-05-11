
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysMaterial;

/**
 * 素材
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
public interface ISysMaterialService extends IService<SysMaterial> {

	/**
	 * 分页查询
	 * @param page
	 * @param material
	 * @return
	 */
	IPage<SysMaterial> getPage(Page page, SysMaterial material);

}
