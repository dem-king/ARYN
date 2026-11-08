

package com.aryn.cloud.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 支付配置
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
@Mapper
public interface PayConfigMapper extends BaseMapper<PayConfig> {

	/**
	 * 通过appid查询支付配置
	 * @param appId
	 * @return
	 */
	@InterceptorIgnore(tenantLine = "true")
	PayConfig selectByAppId(@Param("appId") String appId);

	/**
	 * 通过支付类型查询支付配置（安全字段，不返回任何密钥与路径）
	 * @param payType 支付类型
	 * @param terminalType 终端类型
	 * @return
	 */
	PayConfigVO selectConfig(@Param("payType") String payType, @Param("terminalType") String terminalType);

	/**
	 * 乐观锁启用身份版本。并发发布只有一个请求能把 active_revision_id 指向自己的版本。
	 */
	@Update("""
			UPDATE pay_config
			SET active_revision_id = #{revisionId},
				enabled = '1',
				app_id = #{appId},
				mch_id = #{mchId},
				config_version = config_version + 1,
				update_time = NOW()
			WHERE id = #{configId}
			  AND tenant_id = #{tenantId}
			  AND del_flag = '0'
			  AND config_version = #{expectedVersion}
			""")
	int activateRevision(@Param("tenantId") String tenantId, @Param("configId") String configId,
			@Param("revisionId") String revisionId, @Param("appId") String appId, @Param("mchId") String mchId,
			@Param("expectedVersion") Integer expectedVersion);

	/**
	 * 乐观锁停用。停用只禁止新发起，不影响历史回调、查单与退款。
	 */
	@Update("""
			UPDATE pay_config
			SET enabled = '0',
				config_version = config_version + 1,
				update_time = NOW()
			WHERE id = #{configId}
			  AND tenant_id = #{tenantId}
			  AND del_flag = '0'
			  AND config_version = #{expectedVersion}
			""")
	int disable(@Param("tenantId") String tenantId, @Param("configId") String configId,
			@Param("expectedVersion") Integer expectedVersion);

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT * FROM pay_config
			WHERE tenant_id = #{tenantId} AND id = #{configId} AND del_flag = '0'
			""")
	PayConfig selectByTenantAndId(@Param("tenantId") String tenantId, @Param("configId") String configId);

	/**
	 * 支付宝小程序授权码交换必须显式按租户取配置，不能依赖 Dubbo/injvm 线程上下文。
	 */
	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT * FROM pay_config
			WHERE tenant_id = #{tenantId} AND type = #{payType} AND terminal_type = #{terminalType}
			  AND del_flag = '0'
			LIMIT 1
			""")
	PayConfig selectByTenantRoute(@Param("tenantId") String tenantId, @Param("payType") String payType,
			@Param("terminalType") String terminalType);

}
