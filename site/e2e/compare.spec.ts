import { test, expect } from '@playwright/test'

test('compare tablist swaps the visible panel', async ({ page }) => {
  await page.goto('/')
  const tabs = page.locator('[data-compare-tab]')
  await expect(tabs).toHaveCount(2)
  await expect(page.locator('[data-compare-panel="video"]')).toBeVisible()
  await expect(page.locator('[data-compare-panel="telemetry"]')).toBeHidden()

  await page.getByRole('tab', { name: 'Telemetry' }).click()
  await expect(page.locator('[data-compare-panel="video"]')).toBeHidden()
  await expect(page.locator('[data-compare-panel="telemetry"]')).toBeVisible()
  await expect(page.getByRole('tab', { name: 'Telemetry' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('tab', { name: 'Video' })).toHaveAttribute('aria-selected', 'false')
})

test('compare tabs respond to arrow keys', async ({ page }) => {
  await page.goto('/')
  const videoTab = page.getByRole('tab', { name: 'Video' })
  await videoTab.click()
  await videoTab.press('ArrowRight')
  await expect(page.getByRole('tab', { name: 'Telemetry' })).toHaveAttribute('aria-selected', 'true')
})

test('compare images have alt text', async ({ page }) => {
  await page.goto('/')
  const imgs = page.locator('[data-compare-panel] img')
  const count = await imgs.count()
  expect(count).toBe(2)
  for (let i = 0; i < count; i++) {
    const alt = await imgs.nth(i).getAttribute('alt')
    expect(alt?.trim().length ?? 0).toBeGreaterThan(0)
  }
})