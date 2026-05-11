package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.SignInRecord;
import com.aryn.cloud.user.api.vo.SignInRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 签到记录
 *
 * @author 雨滴kian
 */
@Mapper
public interface SignInRecordMapper extends BaseMapper<SignInRecord> {

	IPage<SignInRecordVO> selectRecordPage(Page page, @Param("nickname") String nickname,
			@Param("beginDate") String beginDate, @Param("endDate") String endDate);

	IPage<SignInRecordVO> selectUserRecordPage(Page page, @Param("userId") String userId);

}
