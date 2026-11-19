import { test, expect } from '@playwright/test'

test('banner appears with no stored consent', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('dialog', { name: /Cookie consent/i })).toBeVisible()
})

test('decline persists and hides the banner', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Decline' }).click()
  await expect(page.getByRole('dialog', { name: /Cookie consent/i })).toBeHidden()
  const stored = await page.evaluate(() => localStorage.getItem('lc-consent'))
  expect(stored).toBe('denied')
  await page.reload()
  await expect(page.getByRole('dialog', { name: /Cookie consent/i })).toBeHidden()
})

test('accept stores granted consent', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Accept' }).click({ force: true })
  await expect(page.getByRole('dialog', { name: /Cookie consent/i })).toBeHidden()
  const stored = await page.evaluate(() => localStorage.getItem('lc-consent'))
  expect(stored).toBe('granted')
})

test('no GTM request on a non-production host', async ({ page }) => {
  const gtm: string[] = []
  page.on('request', (r) => {
    if (r.url().includes('googletagmanager.com')) gtm.push(r.url())
  })
  await page.goto('/')
  await page.getByRole('button', { name: 'Accept' }).click({ force: true })
  await page.waitForTimeout(500)
  expect(gtm, gtm.join('\n')).toHaveLength(0)
})