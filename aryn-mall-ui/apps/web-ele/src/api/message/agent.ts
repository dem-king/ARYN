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
