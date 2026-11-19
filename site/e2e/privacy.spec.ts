import { test, expect } from '@playwright/test'

test('privacy page loads', async ({ page }) => {
  await page.goto('/privacy')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Privacy')
  await expect(page.getByRole('heading', { name: 'This site' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'The app' })).toBeVisible()
})