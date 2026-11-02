
package com.aryn.cloud.common.myabtis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.util.ClassUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * mybatis-plus 自动填充 https://blog.csdn.net/qq_38637558/article/details/126131838
 *
 * @author 雨滴kian
 * @since 2024/4/26 10:00
 */

@Slf4j
public class MyMetaObjectHandler implements MetaObjectHandler {

	@Override
	public void insertFill(MetaObject metaObject) {
		log.info("start insert fill ....");
		LocalDateTime now = LocalDateTime.now();

		fillValIfNullByName("createTime", now, metaObject, true);
		fillValIfNullByName("updateTime", now, metaObject, true);
		fillValIfNullByName("createBy", getUserName(), metaObject, true);
		fillValIfNullByName("updateBy", getUserName(), metaObject, true);
		fillValIfNullByName("delFlag", CommonConstants.NO, metaObject, true);
		fillTenantOnInsert(metaObject);
	}

	@Override
	public void updateFill(MetaObject metaObject) {
		log.info("start update fill ....");
		fillValIfNullByName("updateTime", LocalDateTime.now(), metaObject, true);
		fillValIfNullByName("updateBy", getUserName(), metaObject, true);
		pinTenantOnUpdate(metaObject);
	}

	/**
	 * 写入侧强制使用当前租户，覆盖客户端在实体中携带的 tenantId。
	 *
	 * <p>租户上下文为空时不改动实体值，兼容任务/消息消费等显式指定租户的后台场景。
	 */
	private static void fillTenantOnInsert(MetaObject metaObject) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (tenantId != null && !tenantId.isBlank() && metaObject.hasSetter("tenantId")) {
			metaObject.setValue("tenantId", tenantId);
		}
	}

	/**
	 * 更新时把实体 tenantId 固定为当前租户，禁止把当前租户的记录改挂到其它租户。
	 *
	 * <p><b>不能在这里置空 tenantId。</b>MyBatis-Plus 的 {@code updateById} 先在
	 * {@code MappedStatement#getBoundSql} 阶段按实体字段生成 SET 子句（非空的 {@code tenantId}
	 * 会生成 {@code tenant_id = #{et.tenantId}}），之后由 {@code MybatisParameterHandler} 触发
	 * 自动填充。填充时把 tenantId 置空，SET 里已存在的 {@code tenant_id} 参数就会绑定成 NULL，
	 * 命中 {@code tenant_id NOT NULL} 约束（2026-10-29 微信登录 500：Column 'tenant_id' cannot be null）。
	 * 因此这里改写为当前租户：SET 与租户插件的 WHERE 使用同一归属，既不会写入 NULL，也无法改挂租户。
	 */
	private static void pinTenantOnUpdate(MetaObject metaObject) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (tenantId != null && !tenantId.isBlank() && metaObject.hasSetter("tenantId")) {
			metaObject.setValue("tenantId", tenantId);
		}
	}

	/**
	 * 填充值，先判断是否有手动设置，优先手动设置的值，例如：job必须手动设置
	 * @param fieldName 属性名
	 * @param fieldVal 属性值
	 * @param metaObject MetaObject
	 * @param isCover 是否覆盖原有值,避免更新操作手动入参
	 */
	private static void fillValIfNullByName(String fieldName, Object fieldVal, MetaObject metaObject, boolean isCover) {
		if (Objects.isNull(fieldVal)) {
			return;
		}
		// 1. 没有 get 方法
		if (!metaObject.hasSetter(fieldName)) {
			return;
		}
		// 2. 如果用户有手动设置的值
		Object userSetValue = metaObject.getValue(fieldName);
		if (userSetValue != null && !isCover) {
			return;
		}
		// 3. field 类型相同时设置
		Class<?> getterType = metaObject.getGetterType(fieldName);
		if (ClassUtils.isAssignableValue(getterType, fieldVal)) {
			metaObject.setValue(fieldName, fieldVal);
		}
	}

	private String getUserName() {

		ArynUser hxUser = SecurityUtils.getUser();
		if (Objects.isNull(hxUser)) {
			return null;
		}
		return hxUser.getUsername();
	}

}
