
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.upms.api.entity.SysMaterialGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 素材分组
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:27
 */
@Mapper
public interface SysMaterialGroupMapper extends BaseMapper<SysMaterialGroup> {

	/**
	 * 分页查询
	 * @param page
	 * @param sysMaterialGroup
	 * @return
	 */
	IPage<SysMaterialGroup> selectMaterialGroupPage(Page page, @Param("query") SysMaterialGroup sysMaterialGroup);

	/**
	 * 查询全部
	 * @param sysMaterialGroup
	 * @return
	 */
	List<SysMaterialGroup> selectMaterialGroupList(@Param("query") SysMaterialGroup sysMaterialGroup);

}
