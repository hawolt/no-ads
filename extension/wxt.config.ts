import { defineConfig } from 'wxt';

export default defineConfig({
  manifest: {
    name: 'Twitch Adblock',
    description: 'Creates a new player which serves no ads',
    version: '1.0.0',
    permissions: ['activeTab', 'storage'],
    host_permissions: [
      'http://localhost/*',
      'http://127.0.0.1/*',
      '*://*.ttvnw.net/*',
      '*://*.twitch.tv/*',
    ],
    icons: {
      16: 'icon/icon16.png',
      48: 'icon/icon48.png',
      128: 'icon/icon128.png',
    },
    web_accessible_resources: [{
      resources: ['inject-history.js'],
      matches: ['*://www.twitch.tv/*'],
    }],
  },
});
