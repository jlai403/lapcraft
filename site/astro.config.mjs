import { defineConfig } from 'astro/config'
import svelte from '@astrojs/svelte'
import sitemap from '@astrojs/sitemap'

export default defineConfig({
  site: 'https://lapcraft.jlai.ca',
  output: 'static',
  integrations: [svelte(), sitemap()],
  image: {
    service: { entrypoint: 'astro/assets/services/sharp' }
  },
  build: {
    inlineStylesheets: 'auto'
  },
  vite: {
    server: {
      allowedHosts: ['dev.ts.jlai.ca']
    },
    preview: {
      port: 4321
    }
  }
})
// path-isolation proof
