// Generic mechanism shared by the chart-type, indicator, and drawing-tool
// registries (see chart-types.ts / indicators.ts / drawing-tools.ts). This
// is the entire "how a new plugin hooks in" surface - nothing else in the
// module needs to change for a consumer to add one.
export type RegistryEntry = { id: string };

export type Registry<T extends RegistryEntry> = {
  register(plugin: T): void;
  get(id: string): T | undefined;
  list(): T[];
};

export function createRegistry<T extends RegistryEntry>(): Registry<T> {
  const plugins = new Map<string, T>();
  return {
    register(plugin) {
      plugins.set(plugin.id, plugin);
    },
    get(id) {
      return plugins.get(id);
    },
    list() {
      return [...plugins.values()];
    },
  };
}
