import { afterEach, describe, expect, it, vi } from "vitest";

import { getCurrentPath } from "./current-page";

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
