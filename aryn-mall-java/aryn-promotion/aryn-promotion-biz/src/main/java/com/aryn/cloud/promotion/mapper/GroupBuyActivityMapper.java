package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupBuyActivityMapper extends BaseMapper<GroupBuyActivity> {

	IPage<GroupBuyActivity> selectAdminPage(Page page, @Param("query") GroupBuyActivity activity);

	IPage<GroupBuyActivity> selectAppPage(Page page, @Param("query") GroupBuyActivity activity);
}
