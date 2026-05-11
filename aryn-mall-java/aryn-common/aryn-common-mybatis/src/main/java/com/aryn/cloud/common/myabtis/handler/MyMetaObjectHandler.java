
package com.aryn.cloud.common.myabtis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.aryn.cloud.common.core.constant.CommonConstants;
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
	}

	@Override
	public void updateFill(MetaObject metaObject) {
		log.info("start update fill ....");
		fillValIfNullByName("updateTime", LocalDateTime.now(), metaObject, true);
		fillValIfNullByName("updateBy", getUserName(), metaObject, true);
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
