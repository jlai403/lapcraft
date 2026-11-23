import { test, expect } from '@playwright/test'

test('home loads without console errors', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
  const failed: string[] = []
  page.on('requestfailed', (r) => {
    if (!r.url().match(/\.(mp4|webm|jpg|png|webp)$/)) failed.push(r.url())
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Find the tenth.')
  expect(errors, errors.join('\n')).toHaveLength(0)
  expect(failed, failed.join('\n')).toHaveLength(0)
})

test('every reveal resolves to visible after scroll', async ({ page }) => {
  await page.goto('/')
  await page.evaluate(async () => {
    const step = 400
    for (let y = 0; y < document.body.scrollHeight; y += step) {
      window.scrollTo(0, y)
      await new Promise((r) => requestAnimationFrame(() => r(null)))
    }
    window.scrollTo(0, document.body.scrollHeight)
    await new Promise((r) => setTimeout(r, 2000))
  })
  const hidden = await page.$$eval('.reveal', (els) =>
    els.filter((e) => getComputedStyle(e).opacity === '0').length
  )
  expect(hidden).toBe(0)
})

test('primary ctas and sections present', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('link', { name: /Download APK/i }).first()).toBeVisible()
  await expect(page.locator('#download a[href*="releases/latest"]')).toBeVisible()
  await expect(page.getByRole('link', { name: /Source on GitHub/i }).first()).toBeVisible()
  for (const h of ['The replay shows the lap.', 'Capture, import, compare.', 'Two laps, one clock.', 'Your laps stay on your phone.', 'Upload a lap. Compare against the field.']) {
    await expect(page.getByRole('heading', { name: h })).toBeVisible()
  }
})