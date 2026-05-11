package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.BalanceRecord;
import com.aryn.cloud.user.api.vo.BalanceRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 余额变动记录
 *
 * @author 雨滴kian
 */
@Mapper
public interface BalanceRecordMapper extends BaseMapper<BalanceRecord> {

	IPage<BalanceRecordVO> selectRecordPage(Page page, @Param("nickname") String nickname,
			@Param("changeType") String changeType, @Param("beginTime") String beginTime,
			@Param("endTime") String endTime);

	IPage<BalanceRecordVO> selectUserRecordPage(Page page, @Param("userId") String userId);

}
