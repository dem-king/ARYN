
package com.aryn.cloud.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 支付材料密文。
 */
@Mapper
public interface PayConfigMaterialMapper extends BaseMapper<PayConfigMaterial> {

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			<script>
			SELECT * FROM pay_config_material
			WHERE tenant_id = #{tenantId} AND del_flag = '0'
			  AND id IN
			  <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
			</script>
			""")
	List<PayConfigMaterial> selectByTenantAndIds(@Param("tenantId") String tenantId,
			@Param("ids") Collection<String> ids);

}
