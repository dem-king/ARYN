
package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.user.api.vo.UserRespVO;
import com.aryn.cloud.user.api.vo.UserStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.io.Serializable;
import java.util.List;

/**
 * 商城用户
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:32
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

	/**
	 * 分页查询商城用户列表
	 * @param page
	 * @param userInfo
	 * @author 雨滴kian
	 * @date 2022/7/12
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.UserInfo>
	 */
	IPage<UserRespVO> selectAdminPage(Page page, @Param("query") UserInfo userInfo);

	UserInfoVO selectUserById(Serializable id);

	List<UserStatisticsVO> sourceStatistics(@Param("query") UserInfo userInfo);

}
