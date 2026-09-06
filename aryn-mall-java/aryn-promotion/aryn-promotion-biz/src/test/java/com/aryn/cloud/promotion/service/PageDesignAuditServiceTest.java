package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.core.security.UserSupplier;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.PageDesignAuditLog;
import com.aryn.cloud.promotion.api.vo.PageDesignAuditLogVO;
import com.aryn.cloud.promotion.mapper.PageDesignAuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageDesignAuditServiceTest {

	@Mock
	private PageDesignAuditLogMapper auditLogMapper;

	@Mock
	private UserSupplier userSupplier;

	private PageDesignAuditService service;

	@BeforeEach
	void setUp() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		service = new PageDesignAuditService(auditLogMapper, userSupplier);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void recordPersistsAuditEventWithTenantAndOperator() {
		when(userSupplier.getCurrentUserName()).thenReturn("reviewer");
		PageDesignAuditService.PageDesignAuditEvent event = new PageDesignAuditService.PageDesignAuditEvent(
				PageDesignAuditLog.ACTION_RELEASE_APPROVE, "page-1", "release-1", "version-1", "version-2", 4L, 4L,
				"审批通过");

		service.record(event);

		ArgumentCaptor<PageDesignAuditLog> captor = ArgumentCaptor.forClass(PageDesignAuditLog.class);
		verify(auditLogMapper).insert(captor.capture());
		PageDesignAuditLog log = captor.getValue();
		assertEquals("page-1", log.getPageDesignId());
		assertEquals("release-1", log.getReleaseId());
		assertEquals(PageDesignAuditLog.ACTION_RELEASE_APPROVE, log.getAction());
		assertEquals("reviewer", log.getOperator());
		assertEquals(PageDesignAuditLog.RESULT_SUCCESS, log.getResult());
		assertEquals("version-2", log.getAfterVersionId());
		assertEquals("tenant-1", log.getTenantId());
	}

	@Test
	void listLogsMapsEntitiesToVos() {
		PageDesignAuditLog log = new PageDesignAuditLog();
		log.setId("log-1");
		log.setPageDesignId("page-1");
		log.setAction(PageDesignAuditLog.ACTION_PUBLISH);
		when(auditLogMapper.selectList(any())).thenReturn(List.of(log));

		List<PageDesignAuditLogVO> logs = service.listLogs("page-1");

		assertEquals(1, logs.size());
		assertEquals(PageDesignAuditLog.ACTION_PUBLISH, logs.get(0).getAction());
	}

}
