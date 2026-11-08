package com.aryn.cloud.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.pay.api.entity.PayEventOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PayEventOutboxMapper extends BaseMapper<PayEventOutbox> {

	@InterceptorIgnore(tenantLine = "true")
	@Update("""
			UPDATE pay_event_outbox
			SET status = '0', update_time = NOW()
			WHERE status = '3' AND update_time < #{before} AND del_flag = '0'
			""")
	int resetStaleSending(@Param("before") LocalDateTime before);

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT * FROM pay_event_outbox
			WHERE status = '0' AND next_retry_time <= #{now} AND del_flag = '0'
			ORDER BY next_retry_time, create_time
			LIMIT #{limit}
			""")
	List<PayEventOutbox> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

	@InterceptorIgnore(tenantLine = "true")
	@Update("""
			UPDATE pay_event_outbox
			SET status = '3', update_time = NOW()
			WHERE id = #{id} AND status = '0' AND del_flag = '0'
			""")
	int claim(@Param("id") String id);

	@InterceptorIgnore(tenantLine = "true")
	@Update("""
			UPDATE pay_event_outbox
			SET status = '1', update_time = NOW(), last_error = NULL
			WHERE id = #{id} AND del_flag = '0'
			""")
	int markSent(@Param("id") String id);

	@InterceptorIgnore(tenantLine = "true")
	@Update("""
			UPDATE pay_event_outbox
			SET status = #{status}, attempts = attempts + 1, next_retry_time = #{nextRetryTime},
				last_error = #{lastError}, update_time = NOW()
			WHERE id = #{id} AND del_flag = '0'
			""")
	int markRetry(@Param("id") String id, @Param("status") String status,
			@Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("lastError") String lastError);

}
