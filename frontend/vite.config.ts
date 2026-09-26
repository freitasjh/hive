/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  // @ts-ignore vitest test prop
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
  },
  server: {
    proxy: {
      '/hive': {
        target: process.env.HIVE_API_PROXY ?? 'http://localhost:8080/hive',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/hive/, '')
      }
    }
  }
})
