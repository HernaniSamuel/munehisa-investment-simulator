import { describe, expect, it } from "vitest";
import { createRegistry } from "./registry";

type Fake = { id: string; value: number };

describe("createRegistry", () => {
  it("starts empty", () => {
    expect(createRegistry<Fake>().list()).toEqual([]);
  });

  it("registers and retrieves a plugin by id", () => {
    const registry = createRegistry<Fake>();
    registry.register({ id: "a", value: 1 });
    expect(registry.get("a")).toEqual({ id: "a", value: 1 });
  });

  it("returns undefined for an id that was never registered", () => {
    expect(createRegistry<Fake>().get("missing")).toBeUndefined();
  });

  it("lists every registered plugin", () => {
    const registry = createRegistry<Fake>();
    registry.register({ id: "a", value: 1 });
    registry.register({ id: "b", value: 2 });
    expect(registry.list()).toEqual([
      { id: "a", value: 1 },
      { id: "b", value: 2 },
    ]);
  });

  it("re-registering the same id replaces the previous plugin", () => {
    const registry = createRegistry<Fake>();
    registry.register({ id: "a", value: 1 });
    registry.register({ id: "a", value: 2 });
    expect(registry.list()).toEqual([{ id: "a", value: 2 }]);
  });
});
