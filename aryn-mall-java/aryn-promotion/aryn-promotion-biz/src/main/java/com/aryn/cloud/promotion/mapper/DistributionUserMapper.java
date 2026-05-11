package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DistributionUserMapper extends BaseMapper<DistributionUser> {

	/**
	 * 管理端分页查询分销用户
	 * @param page 分页参数
	 * @param query 查询条件
	 * @return 分页结果
	 */
	IPage<DistributionUser> selectAdminPage(Page<DistributionUser> page, @Param("query") DistributionUser query);
}
