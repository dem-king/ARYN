
package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.user.api.vo.UserRespVO;
import com.aryn.cloud.user.api.vo.UserStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.io.Serializable;
import java.math.BigDecimal;
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

	@Update("""
			UPDATE user_info
			SET point = COALESCE(point, 0) + #{changePoint},
				total_point = COALESCE(total_point, 0) + #{changePoint}
			WHERE id = #{userId} AND del_flag = '0'
			""")
	int acquirePoints(@Param("userId") String userId, @Param("changePoint") Integer changePoint);

	@Update("""
			UPDATE user_info
			SET point = COALESCE(point, 0) - #{changePoint}
			WHERE id = #{userId} AND del_flag = '0'
				AND COALESCE(point, 0) >= #{changePoint}
			""")
	int consumePoints(@Param("userId") String userId, @Param("changePoint") Integer changePoint);

	@Update("""
			UPDATE user_info
			SET balance = COALESCE(balance, 0) + #{changeAmount}
			WHERE id = #{userId} AND del_flag = '0'
				AND COALESCE(balance, 0) + #{changeAmount} >= 0
			""")
	int changeBalance(@Param("userId") String userId, @Param("changeAmount") BigDecimal changeAmount);

	@Update("""
			UPDATE user_info
			SET total_consume = COALESCE(total_consume, 0) + #{consumeAmount}
			WHERE id = #{userId} AND del_flag = '0' AND #{consumeAmount} >= 0
			""")
	int increaseTotalConsume(@Param("userId") String userId, @Param("consumeAmount") BigDecimal consumeAmount);

	@Update("""
			UPDATE user_info
			SET member_level_id = #{levelId}
			WHERE id = #{userId} AND del_flag = '0'
			""")
	int updateMemberLevel(@Param("userId") String userId, @Param("levelId") String levelId);

	List<UserMessageRecipientVO> selectMessageRecipients(@Param("query") UserMessageAudienceRequest request,
			@Param("fetchSize") int fetchSize);

}
