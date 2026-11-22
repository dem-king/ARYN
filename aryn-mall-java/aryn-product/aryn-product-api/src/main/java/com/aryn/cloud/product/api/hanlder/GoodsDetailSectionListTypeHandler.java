package com.aryn.cloud.product.api.hanlder;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.product.api.entity.GoodsDetailSection;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** {@code goods_spu.detail_sections} JSON 数组 ↔ {@code List<GoodsDetailSection>}。 */
@MappedJdbcTypes({ JdbcType.VARCHAR })
public class GoodsDetailSectionListTypeHandler extends BaseTypeHandler<List<GoodsDetailSection>> {

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, List<GoodsDetailSection> parameter, JdbcType jdbcType)
			throws SQLException {
		ps.setString(i, JSON.toJSONString(parameter));
	}

	@Override
	public List<GoodsDetailSection> getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return parse(rs.getString(columnName));
	}

	@Override
	public List<GoodsDetailSection> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return parse(rs.getString(columnIndex));
	}

	@Override
	public List<GoodsDetailSection> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return parse(cs.getString(columnIndex));
	}

	private List<GoodsDetailSection> parse(String json) {
		if (json == null || json.trim().isEmpty()) {
			return null;
		}
		return JSON.parseArray(json, GoodsDetailSection.class);
	}

}
