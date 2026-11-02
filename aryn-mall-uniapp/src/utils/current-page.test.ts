import { afterEach, describe, expect, it, vi } from "vitest";

import { getCurrentFullPath, getCurrentPath } from "./current-page";

describe("getCurrentPath", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns the route of the active page", () => {
    vi.stubGlobal("getCurrentPages", () => [
      { route: "pages/home/index" },
      { route: "sub-pages/product/goods-detail/index" },
    ]);

    expect(getCurrentPath()).toBe("sub-pages/product/goods-detail/index");
  });

  it("returns an empty route when the page stack is empty", () => {
    vi.stubGlobal("getCurrentPages", () => []);

    expect(getCurrentPath()).toBe("");
  });
});

/**
 * 登录回跳（redirectPath）必须保留页面参数，否则商详/订单详情回跳后丢 id 打不开。
 */
describe("getCurrentFullPath", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns the leading-slash route with encoded query (wechat/alipay options)", () => {
    vi.stubGlobal("getCurrentPages", () => [
      { route: "pages/home/index" },
      {
        route: "sub-pages/product/goods-detail/index",
        options: { id: "42", from: "home list", empty: "" },
      },
    ]);

    expect(getCurrentFullPath()).toBe(
      "/sub-pages/product/goods-detail/index?id=42&from=home%20list",
    );
  });

  it("falls back to $page.options on h5/app", () => {
    vi.stubGlobal("getCurrentPages", () => [
      {
        route: "pages/user/shopping-cart/index",
        $page: { options: { scene: "share" } },
      },
    ]);

    expect(getCurrentFullPath()).toBe("/pages/user/shopping-cart/index?scene=share");
  });

  it("returns the bare route when there is no query, and empty when the stack is empty", () => {
    vi.stubGlobal("getCurrentPages", () => [
      { route: "/pages/delivery/task-detail" },
    ]);
    expect(getCurrentFullPath()).toBe("/pages/delivery/task-detail");

    vi.stubGlobal("getCurrentPages", () => []);
    expect(getCurrentFullPath()).toBe("");
  });
});
