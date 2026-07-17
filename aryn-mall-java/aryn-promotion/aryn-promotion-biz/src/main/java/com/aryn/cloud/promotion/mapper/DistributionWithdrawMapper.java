package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.DistributionWithdraw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DistributionWithdrawMapper extends BaseMapper<DistributionWithdraw> {

	/**
	 * 管理端分页查询分销提现
	 * @param page 分页参数
	 * @param query 查询条件
	 * @return 分页结果
	 */
	IPage<DistributionWithdraw> selectAdminPage(Page<DistributionWithdraw> page, @Param("query") DistributionWithdraw query);

	List<DistributionWithdraw> selectLegacyAccounts(@Param("limit") int limit);
}
