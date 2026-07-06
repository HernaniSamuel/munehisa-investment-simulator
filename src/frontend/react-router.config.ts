import type { Config } from "@react-router/dev/config";

// Must match the Vite `base` in vite.config.ts.
const basePath = process.env.BASE_PATH ?? "/";

export default {
  // GitHub Pages is a static host - no runtime server rendering available.
  ssr: false,
  basename: basePath,
} satisfies Config;
