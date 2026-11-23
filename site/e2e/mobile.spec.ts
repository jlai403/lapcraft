import { test, expect, devices } from '@playwright/test'

test.use({ ...devices['Pixel 7'] })

test('mobile: no horizontal overflow and scrub is live', async ({ page }) => {
  await page.goto('/')
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
  expect(overflow).toBe(false)
  await expect(page.locator('.scrub')).toBeVisible()
})

test('mobile: guide link stays in the header', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('nav a[href="/guide"]')).toBeVisible()
  await expect(page.locator('nav .btn')).toBeVisible()
})

test('mobile: scrub video exists and is portrait', async ({ page }) => {
  await page.goto('/')
  const video = page.locator('.scrub video').first()
  await expect(video).toBeVisible()
  const box = await video.boundingBox()
  expect(box).not.toBeNull()
  if (box) expect(box.height).toBeGreaterThan(box.width)
})

test('mobile: walkthrough pins the phone in the top half', async ({ page }) => {
  await page.goto('/')
  const stage = page.locator('[data-stage]')
  await expect(stage).toBeVisible()
  await expect(stage).toHaveCSS('position', 'sticky')
  await expect(page.locator('[data-inline-media]').first()).toBeHidden()

  const box = await stage.boundingBox()
  expect(box).not.toBeNull()
  if (box) expect(box.height).toBeLessThanOrEqual(844 * 0.47)

  await page.evaluate(() => {
    const section = document.querySelector('#showcase') as HTMLElement
    const y = section.getBoundingClientRect().top + window.scrollY
    window.scrollTo({ top: y + 600, behavior: 'instant' as ScrollBehavior })
  })
  await page.waitForTimeout(700)
  const top = await stage.evaluate((e) => Math.round(e.getBoundingClientRect().top))
  expect(top).toBe(64)
})

test('mobile: scrolling the walkthrough swaps the pinned shot', async ({ page }) => {
  await page.goto('/')
  await page.locator('[data-step="2"]').scrollIntoViewIfNeeded()
  await page.waitForTimeout(900)
  await expect(page.locator('[data-shot="2"]')).toHaveClass(/active/)
  await expect(page.locator('[data-step="2"]')).toHaveClass(/active/)
  await expect(page.locator('[data-device]')).toHaveAttribute('data-orient', 'landscape')
})