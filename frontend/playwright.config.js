import { defineConfig, devices } from '@playwright/test'

// needs the backend on :8080. the app is served from a production build,
// and vite preview proxies /api and /ws to it like the dev server does
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 8_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://localhost:4173',
    trace: 'on-first-retry',
    // PW_CHANNEL=msedge uses an installed Edge instead of downloading chromium
    channel: process.env.PW_CHANNEL,
  },
  webServer: {
    command: 'npm run build && npm run preview -- --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
