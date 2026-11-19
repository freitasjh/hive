import { fileURLToPath, URL } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },

  plugins: [tailwindcss(), react()],
  // H3-only: quando VITE_HIVE_API_PROXY aponta para um mock local, o dev server repassa /hive/v1.
  // Sem a variável, não há proxy — produção e testes não são afetados.
  server: process.env.VITE_HIVE_API_PROXY
    ? {
        proxy: {
          "/hive": {
            target: process.env.VITE_HIVE_API_PROXY,
            changeOrigin: true,
          },
        },
      }
    : undefined,
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
  },
})
