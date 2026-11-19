import { test, expect } from '@playwright/test'

test('closing cta is a grid with a gap', async ({ page }) => {
  await page.goto('/')
  const cta = page.locator('.closing .cta')
  await expect(cta).toHaveCSS('display', 'grid')
  await expect(cta).toHaveCSS('gap', '12px')
})

test('desktop: no horizontal overflow', async ({ page }) => {
  await page.goto('/')
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
  expect(overflow).toBe(false)
})

test('mobile: download grid stacks to one column', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.locator('#download').scrollIntoViewIfNeeded()
  const grid = page.locator('.download-grid')
  const cols = await grid.evaluate((e) => getComputedStyle(e).gridTemplateColumns.split(' ').length)
  expect(cols).toBe(1)
})