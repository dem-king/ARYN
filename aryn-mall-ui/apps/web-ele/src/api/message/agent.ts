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

export function getAgentConfig(staffId: string) {
  return requestClient.get<AgentInfo | null>(
    `/message/staff/agent/config/${encodeURIComponent(staffId)}`,
  );
}

export function saveAgentConfig(data: AgentConfigRequest) {
  return requestClient.put<AgentInfo>('/message/staff/agent/config', data);
}

export function getSelfAgent() {
  return requestClient.get<AgentInfo>('/message/staff/agent/self');
}

export function updateAgentPresence(status: AgentInfo['presenceStatus']) {
  return requestClient.post<AgentInfo>('/message/staff/agent/presence', {
    status,
  });
}

export function heartbeatAgent() {
  return requestClient.post('/message/staff/agent/heartbeat');
}
