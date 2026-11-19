import { test, expect } from '@playwright/test'

test.use({ reducedMotion: 'reduce' })

test('reduced motion shows posters and hides the stage', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('[data-stage]')).toBeHidden()
  await expect(page.locator('[data-inline-media]').first()).toBeVisible()
  const sticky = await page.locator('.showcase-copy').evaluate((e) => getComputedStyle(e).position)
  expect(sticky).not.toBe('sticky')
})