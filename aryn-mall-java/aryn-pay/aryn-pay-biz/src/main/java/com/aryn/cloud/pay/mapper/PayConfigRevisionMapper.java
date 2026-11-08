
package com.aryn.cloud.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 支付身份版本。
 */
@Mapper
public interface PayConfigRevisionMapper extends BaseMapper<PayConfigRevision> {

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT COALESCE(MAX(revision_no), 0)
			FROM pay_config_revision
			WHERE tenant_id = #{tenantId} AND config_id = #{configId} AND del_flag = '0'
			""")
	Integer selectMaxRevisionNo(@Param("tenantId") String tenantId, @Param("configId") String configId);

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT * FROM pay_config_revision
			WHERE tenant_id = #{tenantId} AND id = #{id} AND del_flag = '0'
			""")
	PayConfigRevision selectByTenantAndId(@Param("tenantId") String tenantId, @Param("id") String id);

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			<script>
			SELECT * FROM pay_config_revision
			WHERE tenant_id = #{tenantId} AND del_flag = '0'
			  AND id IN
			  <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
			</script>
			""")
	List<PayConfigRevision> selectByTenantAndIds(@Param("tenantId") String tenantId,
			@Param("ids") java.util.Collection<String> ids);

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT * FROM pay_config_revision
			WHERE tenant_id = #{tenantId} AND config_id = #{configId}
			  AND identity_hash = #{identityHash} AND del_flag = '0'
			LIMIT 1
			""")
	PayConfigRevision selectByIdentityHash(@Param("tenantId") String tenantId, @Param("configId") String configId,
			@Param("identityHash") String identityHash);

}
