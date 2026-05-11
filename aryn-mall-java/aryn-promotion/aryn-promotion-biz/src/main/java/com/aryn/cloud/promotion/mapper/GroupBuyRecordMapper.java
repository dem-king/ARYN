package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupBuyRecordMapper extends BaseMapper<GroupBuyRecord> {

	IPage<GroupBuyRecord> selectPageByActivityId(Page page, @Param("activityId") String activityId);

	List<GroupBuyRecord> selectExpiredGroup();
}
