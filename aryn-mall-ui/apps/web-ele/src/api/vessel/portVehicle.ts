import { requestClient } from '#/api/request';

/** 港口报备车辆 */
export interface PortVehicle {
  createTime?: string;
  driverName?: string;
  driverPhone: string;
  id?: string;
  pickupPoint?: string;
  plateNo: string;
  portName: string;
  remark?: string;
  seatCount?: number;
  sort?: number;
  status?: string;
  updateBy?: string;
  updateTime?: string;
  vehicleDesc?: string;
}

export async function getPortVehiclePage(query: any) {
  return requestClient.get('/vessel/admin/port-vehicles/page', {
    params: query,
  });
}

/** 港口名建议选项（已登记车辆港口 ∪ 靠港计划港口） */
export async function getPortVehiclePortOptions() {
  return requestClient.get<string[]>(
    '/vessel/admin/port-vehicles/port-options',
  );
}

export async function addPortVehicle(data: Partial<PortVehicle>) {
  return requestClient.post('/vessel/admin/port-vehicles', data);
}

export async function updatePortVehicle(
  id: string,
  data: Partial<PortVehicle>,
) {
  return requestClient.put(`/vessel/admin/port-vehicles/${id}`, data);
}

export async function deletePortVehicle(id: string) {
  return requestClient.delete(`/vessel/admin/port-vehicles/${id}`);
}
