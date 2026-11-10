package com.aryn.cloud.upms.support;

import com.aryn.cloud.upms.api.entity.SysMaterialGroup;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 素材默认分组口径。
 *
 * <p>「未分组」原先是前端 unshift 出来的虚拟行（id='-1'），不能改名；现在改成每租户一条
 * 真实的默认分组记录。默认分组的 id 由公式决定而不是雪花 ID，原因是增量 SQL 也要为存量
 * 租户补这一条记录——脚本与后端各算一次，只有公式一致才不会出现「脚本建一条、懒创建再建
 * 一条」的双默认分组。公式与 SQL 严格对齐：</p>
 *
 * <pre>
 * MD5(CONCAT('material-default-group:', tenant_id))  → 32 位小写十六进制
 * </pre>
 *
 * <p>用 JDK 自带 {@link MessageDigest} 而不是 hutool：本类被 upms-api 的实体口径复用
 * （{@code SysMaterialGroup} 的常量），而 upms-api 不引 hutool。</p>
 */
@UtilityClass
public class MaterialDefaultGroupSupport {

	/**
	 * 默认分组 id 公式前缀，与增量 SQL 的 {@code MD5(CONCAT('material-default-group:', t.id))} 一字不差。
	 */
	public static final String DEFAULT_GROUP_ID_PREFIX = "material-default-group:";

	/**
	 * 默认分组初始名称。只在首次创建时使用；之后允许运营改名（可改名、不可删除）。
	 */
	public static final String DEFAULT_GROUP_NAME = "未分组";

	/**
	 * 默认分组的排序值：与建表口径一致取 0，实际置顶靠 is_default DESC 而不是 sort。
	 */
	public static final int DEFAULT_GROUP_SORT = 0;

	/**
	 * 默认分组的分组类型：1.图片（与 sys_material_group.type 默认值一致）。
	 */
	public static final String DEFAULT_GROUP_TYPE = "1";

	/**
	 * 计算某租户默认分组的主键。
	 * @param tenantId 租户 id
	 * @return 32 位小写十六进制 MD5
	 */
	public static String defaultGroupId(String tenantId) {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("租户 id 不能为空，无法计算默认素材分组");
		}
		return md5Hex(DEFAULT_GROUP_ID_PREFIX + tenantId);
	}

	/**
	 * 是否默认分组。以库中值为准，未命中 '1' 一律按普通分组处理（fail-safe：宁可允许删，
	 * 也不要把普通分组误判成默认分组而拒绝删除）。
	 * @param group 素材分组
	 * @return true 表示默认分组
	 */
	public static boolean isDefault(SysMaterialGroup group) {
		return group != null && SysMaterialGroup.DEFAULT_YES.equals(group.getIsDefault());
	}

	/**
	 * 按 id 判断是否默认分组：默认分组 id 由公式决定，因此不需要查库也能识别。
	 *
	 * <p>用于删除等「先判定再处理」的场景：即使该行已被改坏（is_default 丢了），
	 * 公式命中也按默认分组处理，避免默认分组被删掉后租户再也没有兜底分组。</p>
	 * @param id 分组 id
	 * @param tenantId 租户 id
	 * @return true 表示这是当前租户的默认分组 id
	 */
	public static boolean isDefaultGroupId(String id, String tenantId) {
		if (id == null || id.isBlank() || tenantId == null || tenantId.isBlank()) {
			return false;
		}
		return id.equalsIgnoreCase(defaultGroupId(tenantId));
	}

	private static String md5Hex(String source) {
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				// 手工转小写十六进制：String.format("%02x") 在循环里开销大，且这里必须保持小写
				hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			// MD5 是 JDK 强制实现的算法，走到这里说明运行环境被裁剪，属不可恢复的配置问题
			throw new IllegalStateException("当前 JVM 不支持 MD5，无法计算默认素材分组 id", e);
		}
	}

}
