package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.MemberLevelRecord;
import com.aryn.cloud.user.api.vo.MemberLevelRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会员等级变更记录
 *
 * @author 雨滴kian
 */
@Mapper
public interface MemberLevelRecordMapper extends BaseMapper<MemberLevelRecord> {

	IPage<MemberLevelRecordVO> selectRecordPage(Page page, @Param("userId") String userId);

}
