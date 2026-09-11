import { requestClient } from '#/api/request';

/** 船舶档案 */
export interface VesselInfo {
  callSign?: string;
  crewCapacity?: number;
  dwt?: number;
  flagState?: string;
  id?: string;
  imoCode?: string;
  remark?: string;
  status?: string;
  vesselName: string;
  vesselNameEn?: string;
  vesselType?: string;
}

/** 船舶成员 */
export interface VesselMember {
  canConfirm?: string;
  canEdit?: string;
  id?: string;
  joinTime?: string;
  memberRole?: string;
  remark?: string;
  status?: string;
  userId: string;
  vesselId?: string;
}

/** 靠港计划 */
export interface VesselCall {
  berth?: string;
  deliveryWindowEnd?: string;
  deliveryWindowStart?: string;
  eta: string;
  etd: string;
  id?: string;
  portCode: string;
  portName: string;
  remark?: string;
  status?: string;
  vesselId: string;
}

export async function getVesselPage(query: any) {
  return requestClient.get('/vessel/admin/page', { params: query });
}

export async function addVessel(data: Partial<VesselInfo>) {
  return requestClient.post('/vessel/admin', data);
}

export async function updateVessel(id: string, data: Partial<VesselInfo>) {
  return requestClient.put(`/vessel/admin/${id}`, data);
}

export async function getVesselMembers(vesselId: string) {
  return requestClient.get(`/vessel/admin/${vesselId}/members`);
}

export async function addVesselMember(
  vesselId: string,
  data: Partial<VesselMember>,
) {
  return requestClient.post(`/vessel/admin/${vesselId}/members`, data);
}

export async function getVesselCalls(vesselId: string) {
  return requestClient.get(`/vessel/admin/${vesselId}/calls`);
}

export async function addVesselCall(
  vesselId: string,
  data: Partial<VesselCall>,
) {
  return requestClient.post(`/vessel/admin/${vesselId}/calls`, data);
}

export async function updateVesselCall(
  callId: string,
  data: Partial<VesselCall>,
) {
  return requestClient.put(`/vessel/admin/calls/${callId}`, data);
}
