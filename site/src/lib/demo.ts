export function initDemo(): void {
  const dialog = document.querySelector<HTMLDialogElement>('#demo-dialog')
  const openBtn = document.querySelector<HTMLButtonElement>('[data-demo-open]')
  const closeBtn = document.querySelector<HTMLButtonElement>('[data-demo-close]')
  const video = dialog?.querySelector<HTMLVideoElement>('video')
  if (!dialog || !openBtn) return

  let lastFocus: HTMLElement | null = null

  const open = (): void => {
    lastFocus = (document.activeElement as HTMLElement) ?? null
    dialog.showModal()
    if (video) { video.preload = 'auto'; video.play().catch(() => {}) }
  }
  const close = (): void => {
    dialog.close()
    if (video) video.pause()
    lastFocus?.focus()
  }

  openBtn.addEventListener('click', open)
  closeBtn?.addEventListener('click', close)
  dialog.addEventListener('click', (e) => { if (e.target === dialog) close() })
  dialog.addEventListener('cancel', (e) => { e.preventDefault(); close() })
}