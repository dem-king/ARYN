import type { AgentRosterItem } from './types';

import { requestClient } from '#/api/request';

export interface AgentInfo {
  autoAccept: '0' | '1';
  currentActiveCount: number;
  enabled: '0' | '1';
  id: string;
  lastAssignedTime?: string;
  maxActiveCount: number;
  presenceStatus: 'BUSY' | 'OFFLINE' | 'ONLINE' | 'PAUSED';
  staffId: string;
}

export interface AgentCandidate {
  avatar?: string;
  deptId?: string;
  id: string;
  nickname?: string;
}

export interface AgentCandidatePage {
  hasMore: boolean;
  nextCursor?: string;
  records: AgentCandidate[];
}

export interface AgentConfigRequest {
  autoAccept: boolean;
  enabled: boolean;
  maxActiveCount: number;
  staffId: string;
}

export function getAgentCandidates() {
  return requestClient.get<AgentCandidatePage>(
    '/message/staff/agent/candidates',
  );
}

/**
 * 在线坐席名单（转交/强制分配/发起会话选人，契约 §4）。
 *
 * 含 presenceStatus / 当前在办 / 接待上限 / 是否可接，数据源为已配置坐席；
 * 权限 `message:service:agent` OR `message:service:supervisor`。
 * 与 `getAgentCandidates()` 互补：roster 失败或为空时可回退 candidates 全量搜索。
 */
export function getAgentRoster() {
  return requestClient.get<AgentRosterItem[]>('/message/staff/agent/roster');
}

export function getAgentConfig(staffId: string) {
  return requestClient.get<AgentInfo | null>(
    `/message/staff/agent/config/${encodeURIComponent(staffId)}`,
  );
}

export function saveAgentConfig(data: AgentConfigRequest) {
  return requestClient.put<AgentInfo>('/message/staff/agent/config', data);
}

/** 本人坐席配置：未配置坐席时后端返回 null（不再抛业务错误）。 */
export function getSelfAgent() {
  return requestClient.get<AgentInfo | null>('/message/staff/agent/self');
}

export function updateAgentPresence(status: AgentInfo['presenceStatus']) {
  return requestClient.post<AgentInfo>('/message/staff/agent/presence', {
    status,
  });
}

export function heartbeatAgent() {
  return requestClient.post('/message/staff/agent/heartbeat');
}
