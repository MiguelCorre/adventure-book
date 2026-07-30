import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests, covering the ground the unit suite structurally cannot: what is
 * actually painted, real browser history, and real layout. jsdom has no geometry, so the
 * scroll behaviour in particular can only be proved here.
 *
 * Both halves of the application are started for the run. The backend gets an in-memory
 * database so a run can never inherit saved games from a previous one, and reads the real
 * books directory, because the state of those six files is part of what is being asserted.
 */
// The wrapper lives in the backend directory and is invoked from there, so it needs an
// explicit relative path on both platforms.
const mavenWrapper = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';

export default defineConfig({
  testDir: './e2e',
  // The backend keeps sessions in memory and serves one books directory, so the specs
  // share state by nature. Serial keeps them honest rather than merely green.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',

  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer: [
    {
      command: `${mavenWrapper} -B spring-boot:run "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:e2e"`,
      cwd: '../backend',
      url: 'http://localhost:8080/api/books',
      reuseExistingServer: !process.env.CI,
      timeout: 240_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm start',
      url: 'http://localhost:4200',
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
});
