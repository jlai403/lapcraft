import { test, expect } from '@playwright/test'

test('demo dialog opens, shows video, closes on Escape', async ({ page }) => {
  await page.goto('/')
  const button = page.getByRole('button', { name: /Watch the full demo/i })
  await expect(button).toBeVisible()
  await button.click()
  const dialog = page.locator('#demo-dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog.locator('video')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(button).toBeFocused()
})

test('demo dialog closes on backdrop click', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: /Watch the full demo/i }).click()
  const dialog = page.locator('#demo-dialog')
  await expect(dialog).toBeVisible()
  await page.mouse.click(5, 5)
  await expect(dialog).toBeHidden()
})