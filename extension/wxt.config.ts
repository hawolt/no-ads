import { defineConfig } from 'wxt';

export default defineConfig({
  manifest: {
    name: 'Twitch Adblock',
    description: 'Creates a new player which serves no ads',
    version: '1.0.0',
    permissions: ['activeTab'],
    host_permissions: [
      'http://localhost/*',
      'http://127.0.0.1/*',
      '*://*.ttvnw.net/*',
      '*://*.twitch.tv/*',
    ],
    web_accessible_resources: [{
      resources: ['inject-history.js'],
      matches: ['*://www.twitch.tv/*'],
    }],
  },
});
