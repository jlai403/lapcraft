import { test, expect, devices } from '@playwright/test'

test('guide page loads with steps and images', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
  await page.goto('/guide')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('How to capture a lap')
  for (const h of [
    'Find the PS5 address',
    'Connect to GT7',
    'Record the replay',
    'Capture the lap',
    'Get the clip into the PS App',
    'Share the clip with LapCraft',
    'Set the lap start',
    'Check the finish and save',
  ]) {
    await expect(page.getByRole('heading', { name: h })).toBeVisible()
  }
  await page.evaluate(async () => {
    const step = 500
    for (let y = 0; y < document.body.scrollHeight; y += step) {
      window.scrollTo(0, y)
      await new Promise((r) => requestAnimationFrame(() => r(null)))
    }
    window.scrollTo(0, document.body.scrollHeight)
    await new Promise((r) => setTimeout(r, 1000))
  })
  await page.waitForFunction(() =>
    Array.from(document.images).every((i) => i.complete && i.naturalWidth > 0)
  )
  expect(errors, errors.join('\n')).toHaveLength(0)
})

test('guide images all have alt text', async ({ page }) => {
  await page.goto('/guide')
  const images = page.locator('main img:not([alt=""])')
  const count = await images.count()
  expect(count).toBeGreaterThan(0)
  for (let i = 0; i < count; i++) {
    await expect(images.nth(i)).toHaveAttribute('alt', /.+/)
  }
})

test('guide links to the latest stable release', async ({ page }) => {
  await page.goto('/guide')
  await expect(page.locator('a[href*="releases/latest"]').first()).toBeVisible()
})

test('nav links home and guide', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('nav a[href="/guide"]')).toBeVisible()
  await page.goto('/guide')
  await expect(page.locator('nav a[href="/guide"]')).toHaveAttribute('aria-current', 'page')
  await page.locator('nav a.brand').click()
  await expect(page).toHaveURL('/')
})

test('footer github link has an icon', async ({ page }) => {
  await page.goto('/')
  const link = page.locator('footer a.footer-link', { hasText: 'GitHub' })
  await expect(link).toHaveAttribute('href', 'https://github.com/jlai403/lapcraft')
  await expect(link.locator('svg')).toBeVisible()
})

test.describe('mobile', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('mobile: no horizontal overflow', async ({ page }) => {
    await page.goto('/guide')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
    expect(overflow).toBe(false)
  })
})

test.describe('without JavaScript', () => {
  test.use({ javaScriptEnabled: false })

  test('guide steps stay visible', async ({ page }) => {
    await page.goto('/guide')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    const hidden = await page.$$eval('.reveal', (els) =>
      els.filter((e) => getComputedStyle(e).opacity === '0').length
    )
    expect(hidden).toBe(0)
  })
})
