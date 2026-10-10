package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.UserTagRel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户标签关联
 *
 * @author 雨滴kian
 */
@Mapper
public interface UserTagRelMapper extends BaseMapper<UserTagRel> {

	/**
	 * 批量插入标签关联，命中唯一键 uk_user_tag_tenant 时跳过重复行。
	 *
	 * <p>打标签的「先查后插」在并发下会同时通过查重，靠唯一键兜底：
	 * 用 INSERT IGNORE 让重复的那次静默跳过，而不是抛 DuplicateKeyException
	 * （异常会让当前事务只能回滚，无法继续处理同批其它标签）。
	 *
	 * @param rels 关联列表（调用方保证非空，且已填好 tenantId/createTime）
	 * @return 实际插入行数
	 */
	@Insert("""
			<script>
			INSERT IGNORE INTO user_tag_rel (id, user_id, tag_id, tenant_id, create_by, create_time)
			VALUES
			<foreach collection="rels" item="rel" separator=",">
				(#{rel.id}, #{rel.userId}, #{rel.tagId}, #{rel.tenantId}, #{rel.createBy}, #{rel.createTime})
			</foreach>
			</script>
			""")
	int insertIgnoreBatch(@Param("rels") List<UserTagRel> rels);

}
