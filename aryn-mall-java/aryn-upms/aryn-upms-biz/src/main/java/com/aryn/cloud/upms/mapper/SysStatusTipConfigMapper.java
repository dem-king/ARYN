package com.aryn.cloud.upms.mapper;

import com.aryn.cloud.upms.api.entity.SysStatusTipConfig;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 空态插画配置。
 *
 * <p>C 端空态图在未登录时也要渲染，读取时租户上下文可能为空，租户插件会拼出
 * {@code tenant_id = NULL} 而静默查不到数据。因此读取一律走显式带 tenant_id 的语句
 * 并关闭租户插件（与版权配置同口径）；租户归属由服务层决定，不接受请求方指定。</p>
 */
@Mapper
public interface SysStatusTipConfigMapper extends BaseMapper<SysStatusTipConfig> {

	@InterceptorIgnore(tenantLine = "true")
	@Select("""
			SELECT * FROM sys_status_tip_config
			WHERE tenant_id = #{tenantId} AND del_flag = '0'
			LIMIT 1
			""")
	SysStatusTipConfig selectByTenant(@Param("tenantId") String tenantId);

	/**
	 * 全字段覆盖保存。可编辑列必须全部写回：运营清空某一项表示「回落内置静态图」，
	 * 若沿用 updateById 的非空策略，清空的字段会被静默忽略、旧图长期残留。
	 */
	@InterceptorIgnore(tenantLine = "true")
	@Update("""
			UPDATE sys_status_tip_config
			SET search_url = #{searchUrl},
				network_url = #{networkUrl},
				content_url = #{contentUrl},
				collect_url = #{collectUrl},
				comment_url = #{commentUrl},
				halo_url = #{haloUrl},
				message_url = #{messageUrl},
				empty_cart_url = #{emptyCartUrl},
				empty_order_url = #{emptyOrderUrl},
				empty_seckill_url = #{emptySeckillUrl},
				update_by = #{updateBy},
				update_time = NOW()
			WHERE id = #{id} AND tenant_id = #{tenantId} AND del_flag = '0'
			""")
	int overwriteByTenantAndId(@Param("id") String id, @Param("tenantId") String tenantId,
			@Param("searchUrl") String searchUrl, @Param("networkUrl") String networkUrl,
			@Param("contentUrl") String contentUrl, @Param("collectUrl") String collectUrl,
			@Param("commentUrl") String commentUrl, @Param("haloUrl") String haloUrl,
			@Param("messageUrl") String messageUrl, @Param("emptyCartUrl") String emptyCartUrl,
			@Param("emptyOrderUrl") String emptyOrderUrl,
			@Param("emptySeckillUrl") String emptySeckillUrl, @Param("updateBy") String updateBy);

}
