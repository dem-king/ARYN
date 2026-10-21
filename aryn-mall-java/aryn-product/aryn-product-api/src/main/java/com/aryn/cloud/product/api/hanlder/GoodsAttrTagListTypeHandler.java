package com.aryn.cloud.product.api.hanlder;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.product.api.entity.GoodsAttrTag;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * {@code goods_spu.attr_tags} JSON 数组 ↔ {@code List<GoodsAttrTag>}。
 *
 * <p>刻意不标 {@code @MappedTypes(List.class)}：同模块已有
 * {@link SpecsListTypeHandler} 处理 {@code List<GoodsSku.Specs>}，
 * 两个 List 处理器全局注册会互相抢占。这里只在实体字段和 resultMap
 * 上按字段显式指定。</p>
 */
@MappedJdbcTypes({ JdbcType.VARCHAR })
public class GoodsAttrTagListTypeHandler extends BaseTypeHandler<List<GoodsAttrTag>> {

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, List<GoodsAttrTag> parameter, JdbcType jdbcType)
			throws SQLException {
		ps.setString(i, JSON.toJSONString(parameter));
	}

	@Override
	public List<GoodsAttrTag> getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return parse(rs.getString(columnName));
	}

	@Override
	public List<GoodsAttrTag> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return parse(rs.getString(columnIndex));
	}

	@Override
	public List<GoodsAttrTag> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return parse(cs.getString(columnIndex));
	}

	private List<GoodsAttrTag> parse(String json) {
		if (json == null || json.trim().isEmpty()) {
			return null;
		}
		return JSON.parseArray(json, GoodsAttrTag.class);
	}

}
