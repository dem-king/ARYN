package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.entity.ProductImportRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** ProductImportRow 持久层。 */
@Mapper
public interface ProductImportRowMapper extends BaseMapper<ProductImportRow> {

	/**
	 * 按任务读取解析行（按行号升序）。
	 */
	List<ProductImportRow> selectByJobId(@Param("tenantId") String tenantId, @Param("jobId") String jobId);

}
