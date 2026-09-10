import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Everything under /api goes to the Spring backend. Proxying rather than
      // calling localhost:8080 directly keeps the browser on one origin, so the
      // httpOnly login cookie and the CSRF cookie are same-site and no CORS
      // configuration is needed.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
