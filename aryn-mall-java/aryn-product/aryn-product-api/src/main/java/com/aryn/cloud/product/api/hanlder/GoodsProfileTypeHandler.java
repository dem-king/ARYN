package com.aryn.cloud.product.api.hanlder;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.product.api.entity.GoodsProfile;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@code goods_spu.goods_profile} JSON 对象 ↔ {@link GoodsProfile}。
 *
 * <p>不标 {@code @MappedTypes}，只在实体字段和 resultMap 上显式指定，
 * 避免同模块多个 JSON 字段处理器互相抢占。</p>
 */
@MappedJdbcTypes({ JdbcType.VARCHAR })
public class GoodsProfileTypeHandler extends BaseTypeHandler<GoodsProfile> {

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, GoodsProfile parameter, JdbcType jdbcType)
			throws SQLException {
		ps.setString(i, JSON.toJSONString(parameter));
	}

	@Override
	public GoodsProfile getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return parse(rs.getString(columnName));
	}

	@Override
	public GoodsProfile getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return parse(rs.getString(columnIndex));
	}

	@Override
	public GoodsProfile getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return parse(cs.getString(columnIndex));
	}

	private GoodsProfile parse(String json) {
		if (json == null || json.trim().isEmpty()) {
			return null;
		}
		return JSON.parseObject(json, GoodsProfile.class);
	}

}
