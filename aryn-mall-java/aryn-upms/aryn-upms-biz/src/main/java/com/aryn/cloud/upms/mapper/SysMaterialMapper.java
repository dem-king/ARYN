
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.upms.api.entity.SysMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 素材
 *
 * @author 雨滴kian
 * @since 2022/2/22 15:02
 */
@Mapper
public interface SysMaterialMapper extends BaseMapper<SysMaterial> {

	/**
	 * 分页查询
	 * @param page
	 * @param material
	 * @return
	 */
	IPage<SysMaterial> selectMaterialPage(Page page, @Param("query") SysMaterial material);

	@Update("""
		UPDATE sys_material
		SET binding_status = 'RESERVED', reservation_id = #{reservationId},
			reservation_expire_time = #{expireTime}, update_time = NOW()
		WHERE tenant_id = #{tenantId} AND id = #{materialId} AND create_by = #{staffId}
			AND business_type = 'DELIVERY_EVIDENCE' AND del_flag = '0'
			AND (binding_status = 'UNBOUND'
				OR (binding_status = 'RESERVED' AND reservation_id = #{reservationId})
				OR (binding_status = 'RESERVED' AND reservation_expire_time < NOW()))
		""")
	int reserveDeliveryMaterial(@Param("tenantId") String tenantId, @Param("staffId") String staffId,
			@Param("materialId") String materialId, @Param("reservationId") String reservationId,
			@Param("expireTime") LocalDateTime expireTime);

	@Select("""
		SELECT * FROM sys_material
		WHERE tenant_id = #{tenantId} AND create_by = #{staffId} AND reservation_id = #{reservationId}
			AND binding_status = 'RESERVED' AND business_type = 'DELIVERY_EVIDENCE' AND del_flag = '0'
		ORDER BY id
		""")
	List<SysMaterial> selectReservedDeliveryMaterials(@Param("tenantId") String tenantId,
			@Param("staffId") String staffId, @Param("reservationId") String reservationId);

	@Update("""
		UPDATE sys_material
		SET binding_status = 'BOUND', business_id = #{businessId}, bound_time = NOW(),
			reservation_expire_time = NULL, update_time = NOW()
		WHERE tenant_id = #{tenantId} AND reservation_id = #{reservationId}
			AND business_type = 'DELIVERY_EVIDENCE'
			AND (binding_status = 'RESERVED'
				OR (binding_status = 'BOUND' AND business_id = #{businessId}))
			AND del_flag = '0'
		""")
	int confirmDeliveryBinding(@Param("tenantId") String tenantId, @Param("reservationId") String reservationId,
			@Param("businessId") String businessId);

	@Select("""
		SELECT COUNT(*) FROM sys_material
		WHERE tenant_id = #{tenantId} AND reservation_id = #{reservationId} AND business_id = #{businessId}
			AND binding_status = 'BOUND' AND del_flag = '0'
		""")
	int countConfirmedBinding(@Param("tenantId") String tenantId, @Param("reservationId") String reservationId,
			@Param("businessId") String businessId);

	@Select("""
		SELECT * FROM sys_material
		WHERE tenant_id = #{tenantId} AND id = #{id} AND binding_status = 'BOUND' AND del_flag = '0'
		LIMIT 1
		""")
	SysMaterial selectByTenantAndId(@Param("tenantId") String tenantId, @Param("id") String id);

	@Update("""
		UPDATE sys_material
		SET binding_status = 'UNBOUND', reservation_id = NULL, reservation_expire_time = NULL, update_time = NOW()
		WHERE binding_status = 'RESERVED' AND reservation_expire_time < NOW() AND del_flag = '0'
		""")
	@InterceptorIgnore(tenantLine = "true")
	int releaseExpiredDeliveryReservations();

}
