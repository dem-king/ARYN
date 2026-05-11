
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 素材
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:02
 */
@Mapper
public interface SysMaterialMapper extends BaseMapper<SysMaterial> {

	/**
	 * 分页查询
	 * @param page
	 * @param material
	 * @return
	 */
	IPage<SysMaterial> selectMaterialPage(Page page, @Param("query") SysMaterial material);

}
