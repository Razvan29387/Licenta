import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    global: 'window', // Fix for sockjs-client 'global is not defined' error
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      // Proxiază cererile de WebSocket
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
})
