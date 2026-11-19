import { test, expect } from '@playwright/test'

test.use({ javaScriptEnabled: false })

test('content visible without JavaScript', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  const hidden = await page.$$eval('.reveal', (els) =>
    els.filter((e) => getComputedStyle(e).opacity === '0').length
  )
  expect(hidden).toBe(0)
  await expect(page.getByRole('heading', { name: 'Capture, import, compare.' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Upload a lap. Compare against the field.' })).toBeVisible()
})