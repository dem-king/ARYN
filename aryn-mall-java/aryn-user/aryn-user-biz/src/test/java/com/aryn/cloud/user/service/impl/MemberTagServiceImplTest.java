package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.MemberTag;
import com.aryn.cloud.user.api.entity.UserTagRel;
import com.aryn.cloud.user.mapper.MemberTagMapper;
import com.aryn.cloud.user.mapper.UserTagRelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MemberTagServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class MemberTagServiceImplTest {

	@Mock
	private UserTagRelMapper userTagRelMapper;

	@Mock
	private MemberTagMapper memberTagMapper;

	@InjectMocks
	private MemberTagServiceImpl memberTagService;

	@BeforeEach
	void setUp() {
		// 设置 baseMapper
		memberTagService.baseMapper = memberTagMapper;
	}

	@Test
	@DisplayName("保存标签 - 标签名唯一时正常保存")
	void saveTag_uniqueName_success() {
		// given
		MemberTag tag = new MemberTag();
		tag.setTagName("VIP用户");
		when(memberTagMapper.selectCount(any())).thenReturn(0L);
		when(memberTagMapper.insert(any(MemberTag.class))).thenReturn(1);

		// when
		boolean result = memberTagService.saveTag(tag);

		// then
		assertTrue(result);
		verify(memberTagMapper).insert(tag);
	}

	@Test
	@DisplayName("保存标签 - 标签名重复时抛出异常")
	void saveTag_duplicateName() {
		// given
		MemberTag tag = new MemberTag();
		tag.setTagName("VIP用户");
		when(memberTagMapper.selectCount(any())).thenReturn(1L);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				memberTagService.saveTag(tag));

		assertEquals("标签名称已存在", exception.getMsg());
		verify(memberTagMapper, never()).insert(any());
	}

	@Test
	@DisplayName("更新标签 - 排除自身后名称唯一时正常更新")
	void updateTag_uniqueNameExcludingSelf_success() {
		// given
		MemberTag tag = new MemberTag();
		tag.setId("tag001");
		tag.setTagName("VIP用户");
		when(memberTagMapper.selectCount(any())).thenReturn(0L);
		when(memberTagMapper.updateById(any(MemberTag.class))).thenReturn(1);

		// when
		boolean result = memberTagService.updateTag(tag);

		// then
		assertTrue(result);
		verify(memberTagMapper).updateById(tag);
	}

	@Test
	@DisplayName("更新标签 - 排除自身后名称重复时抛出异常")
	void updateTag_duplicateNameExcludingSelf() {
		// given
		MemberTag tag = new MemberTag();
		tag.setId("tag001");
		tag.setTagName("VIP用户");
		when(memberTagMapper.selectCount(any())).thenReturn(1L);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				memberTagService.updateTag(tag));

		assertEquals("标签名称已存在", exception.getMsg());
		verify(memberTagMapper, never()).updateById(any());
	}

	@Test
	@DisplayName("删除标签 - 级联删除标签关联关系")
	void deleteTag_cascadeDeleteRelations() {
		// given
		when(userTagRelMapper.delete(any())).thenReturn(2);
		when(memberTagMapper.deleteById("tag001")).thenReturn(1);

		// when
		boolean result = memberTagService.deleteTag("tag001");

		// then
		assertTrue(result);
		// 先删除关联关系
		verify(userTagRelMapper).delete(any());
		// 再删除标签本身
		verify(memberTagMapper).deleteById("tag001");
	}

	@Test
	@DisplayName("打标签 - 新标签正常插入关联")
	void tagUser_newTags_insertRelations() {
		// given
		String userId = "user001";
		List<String> tagIds = Arrays.asList("tag001", "tag002");

		// 无已存在的关联
		when(userTagRelMapper.selectList(any())).thenReturn(Collections.emptyList());
		when(userTagRelMapper.insert(anyList())).thenReturn(2);

		// when
		memberTagService.tagUser(userId, tagIds);

		// then
		verify(userTagRelMapper).insert(argThat(list -> {
			@SuppressWarnings("unchecked")
			List<UserTagRel> rels = (List<UserTagRel>) list;
			return rels.size() == 2
					&& rels.stream().allMatch(r -> userId.equals(r.getUserId()))
					&& rels.stream().map(UserTagRel::getTagId).collect(java.util.stream.Collectors.toList())
					.containsAll(tagIds);
		}));
	}

	@Test
	@DisplayName("打标签 - 已存在的标签不重复插入")
	void tagUser_existingTags_skipDuplicates() {
		// given
		String userId = "user001";
		List<String> tagIds = Arrays.asList("tag001", "tag002");

		// tag001已存在关联
		UserTagRel existRel = new UserTagRel();
		existRel.setUserId(userId);
		existRel.setTagId("tag001");
		when(userTagRelMapper.selectList(any())).thenReturn(Collections.singletonList(existRel));
		when(userTagRelMapper.insert(anyList())).thenReturn(1);

		// when
		memberTagService.tagUser(userId, tagIds);

		// then - 只插入tag002
		verify(userTagRelMapper).insert(argThat(list -> {
			@SuppressWarnings("unchecked")
			List<UserTagRel> rels = (List<UserTagRel>) list;
			return rels.size() == 1
					&& "tag002".equals(rels.get(0).getTagId())
					&& userId.equals(rels.get(0).getUserId());
		}));
	}

	@Test
	@DisplayName("打标签 - 所有标签都已存在时不插入")
	void tagUser_allTagsExist_noInsert() {
		// given
		String userId = "user001";
		List<String> tagIds = Arrays.asList("tag001", "tag002");

		UserTagRel rel1 = new UserTagRel();
		rel1.setTagId("tag001");
		rel1.setUserId(userId);
		UserTagRel rel2 = new UserTagRel();
		rel2.setTagId("tag002");
		rel2.setUserId(userId);
		when(userTagRelMapper.selectList(any())).thenReturn(Arrays.asList(rel1, rel2));

		// when
		memberTagService.tagUser(userId, tagIds);

		// then
		verify(userTagRelMapper, never()).insert(anyList());
	}

	@Test
	@DisplayName("去标签 - 正常删除用户标签关联")
	void untagUser_success() {
		// given
		String userId = "user001";
		List<String> tagIds = Arrays.asList("tag001", "tag002");
		when(userTagRelMapper.delete(any())).thenReturn(2);

		// when
		memberTagService.untagUser(userId, tagIds);

		// then
		verify(userTagRelMapper).delete(any());
	}

	@Test
	@DisplayName("获取用户标签 - 用户有标签时返回标签列表")
	void getUserTags_hasTags() {
		// given
		String userId = "user001";

		UserTagRel rel1 = new UserTagRel();
		rel1.setTagId("tag001");
		UserTagRel rel2 = new UserTagRel();
		rel2.setTagId("tag002");
		when(userTagRelMapper.selectList(any())).thenReturn(Arrays.asList(rel1, rel2));

		MemberTag tag1 = new MemberTag();
		tag1.setId("tag001");
		tag1.setTagName("VIP");
		MemberTag tag2 = new MemberTag();
		tag2.setId("tag002");
		tag2.setTagName("活跃");
		when(memberTagMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(tag1, tag2));

		// when
		List<MemberTag> result = memberTagService.getUserTags(userId);

		// then
		assertEquals(2, result.size());
	}

	@Test
	@DisplayName("获取用户标签 - 用户无标签时返回空列表")
	void getUserTags_noTags() {
		// given
		String userId = "user001";
		when(userTagRelMapper.selectList(any())).thenReturn(Collections.emptyList());

		// when
		List<MemberTag> result = memberTagService.getUserTags(userId);

		// then
		assertTrue(result.isEmpty());
		verify(memberTagMapper, never()).selectBatchIds(anyCollection());
	}

}
