/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.product.api.hanlder;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.product.api.entity.GoodsSku;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 自定义 TypeHandler：处理 GoodsSku.specsArr JSON 转 List<Specs>
 *
 * @author 雨滴kian
 */
@MappedTypes(List.class)
@MappedJdbcTypes({ JdbcType.VARCHAR, JdbcType.VARCHAR })
public class SpecsListTypeHandler extends BaseTypeHandler<List<GoodsSku.Specs>> {

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, List<GoodsSku.Specs> parameter, JdbcType jdbcType)
			throws SQLException {
		// 写入数据库前序列化为 JSON 字符串
		ps.setString(i, JSON.toJSONString(parameter));
	}

	@Override
	public List<GoodsSku.Specs> getNullableResult(ResultSet rs, String columnName) throws SQLException {
		String json = rs.getString(columnName);
		if (json == null || json.isEmpty()) {
			return null;
		}
		return JSON.parseArray(json, GoodsSku.Specs.class);
	}

	@Override
	public List<GoodsSku.Specs> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		String json = rs.getString(columnIndex);
		if (json == null || json.isEmpty()) {
			return null;
		}
		return JSON.parseArray(json, GoodsSku.Specs.class);
	}

	@Override
	public List<GoodsSku.Specs> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		String json = cs.getString(columnIndex);
		if (json == null || json.isEmpty()) {
			return null;
		}
		return JSON.parseArray(json, GoodsSku.Specs.class);
	}

}
