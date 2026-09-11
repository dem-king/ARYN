package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** ProductCodeMapping 持久层。 */
@Mapper
public interface ProductCodeMappingMapper extends BaseMapper<ProductCodeMapping> {

	/**
	 * 查询与指定编码值列表冲突的有效映射（同一租户同一编码已绑定其他 SKU）。
	 */
	List<ProductCodeMapping> selectActiveByCodeValues(@Param("tenantId") String tenantId,
			@Param("codeValues") List<String> codeValues);

	/**
	 * 按编码类型+值查询映射（忽略逻辑删除，用于复活历史映射，避免唯一键冲突）。
	 */
	ProductCodeMapping selectByCodeIgnoreDelFlag(@Param("tenantId") String tenantId, @Param("codeType") String codeType,
			@Param("codeValue") String codeValue);

	/**
	 * 全租户范围按编码类型+值查询映射（跨租户编码校验用）。
	 */
	ProductCodeMapping selectByCodeGlobal(@Param("codeType") String codeType, @Param("codeValue") String codeValue);

	/**
	 * 按编码值/别名搜索有效映射（船供搜索用）。
	 */
	List<ProductCodeMapping> searchActiveByKeyword(@Param("tenantId") String tenantId, @Param("keyword") String keyword);

}
