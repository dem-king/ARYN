import { alovaInstance } from "@/api/core/instance";

export function getList() {
  return alovaInstance.Get<any>("/product/app/goodsbrand/list", {
    headers: {
      skipToken: true,
    },
  });
}
