package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.vo.PointsRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 积分记录
 *
 * @author 雨滴kian
 */
@Mapper
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

	IPage<PointsRecordVO> selectRecordPage(Page page, @Param("nickname") String nickname,
			@Param("changeType") String changeType, @Param("beginTime") String beginTime,
			@Param("endTime") String endTime);

	IPage<PointsRecordVO> selectUserRecordPage(Page page, @Param("userId") String userId);

}
