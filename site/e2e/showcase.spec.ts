import { test, expect } from '@playwright/test'

test.describe('showcase', () => {
  test.skip(({ isMobile }) => isMobile, 'showcase is desktop-only')

  test('scrolling activates the right shot and only one video plays', async ({ page }) => {
    await page.goto('/')
    const steps = page.locator('[data-step]')
    const count = await steps.count()
    expect(count).toBe(3)
    for (let i = 0; i < count; i++) {
      await steps.nth(i).scrollIntoViewIfNeeded()
      await page.waitForTimeout(700)
      const activeShot = await page.locator('[data-shot].active').getAttribute('data-shot')
      expect(activeShot).toBe(String(i))
      const orient = await page.locator('[data-device]').getAttribute('data-orient')
      expect(orient).toBe(i >= 2 ? 'landscape' : 'portrait')
      const media = await page.locator('.device-media').boundingBox()
      expect(media).not.toBeNull()
      if (media) {
        if (orient === 'landscape') expect(media.width).toBeGreaterThan(media.height)
        else expect(media.height).toBeGreaterThan(media.width)
      }
      const playing = await page.$$eval('video', (vs) => vs.filter((v) => !v.paused).length)
      expect(playing).toBeLessThanOrEqual(1)
    }
  })
})