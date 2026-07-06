
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
import org.apache.ibatis.annotations.Update;

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
	 * CAS 扣减积分（乐观锁防并发） 余额不足时影响行数为0
	 * @param userId 用户ID
	 * @param points 扣减积分数（正整数）
	 * @return 影响行数
	 */
	@Update("UPDATE user_info SET point = point - #{points}, update_time = NOW() "
			+ "WHERE id = #{userId} AND del_flag = '0' AND point >= #{points}")
	int deductPointsCas(@Param("userId") String userId, @Param("points") Integer points);

	/**
	 * CAS 增加积分
	 * @param userId 用户ID
	 * @param points 增加积分数（正整数）
	 * @return 影响行数
	 */
	@Update("UPDATE user_info SET point = point + #{points}, update_time = NOW() "
			+ "WHERE id = #{userId} AND del_flag = '0'")
	int addPointsCas(@Param("userId") String userId, @Param("points") Integer points);

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
