import { defineConfig } from "vite";

export default defineConfig({
  base: "/",
  build: {
    outDir: "dist",
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (id.includes("leaflet")) return "leaflet";
          if (id.includes("react")) return "react";
          return "vendor";
        }
      }
    }
  }
});
