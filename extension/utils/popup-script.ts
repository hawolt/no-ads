import { getAutoReplaceEnabled, setAutoReplaceEnabled } from './storage';

async function initPopup() {
  const autoReplaceCheckbox = document.getElementById('autoReplace') as HTMLInputElement;
  const statusDiv = document.getElementById('status') as HTMLDivElement;

  async function loadSettings() {
    const autoReplace = await getAutoReplaceEnabled();
    if (autoReplaceCheckbox) {
      autoReplaceCheckbox.checked = autoReplace;
    }
  }

  async function saveSettings() {
    if (autoReplaceCheckbox) {
      await setAutoReplaceEnabled(autoReplaceCheckbox.checked);
    }
  }

  await loadSettings();

  autoReplaceCheckbox?.addEventListener('change', saveSettings);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initPopup);
} else {
  initPopup();
}
