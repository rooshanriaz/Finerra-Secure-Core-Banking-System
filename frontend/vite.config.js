import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    open: true,
    // Avoid stale UI during development (browser caching index/module graph).
    headers: { 'Cache-Control': 'no-store' },
    // Proxy Keycloak so the SPA can call /realms/... same-origin (avoids browser CORS on token endpoint).
    proxy: {
      '/realms': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
})
