import { modifyVideoElement } from './player';

const THUNDER_SVG = `
  <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true">
    <path fill-rule="evenodd"
      d="M13 2L3 14h7l-1 8 12-14h-7l-1-6Z"
      clip-rule="evenodd">
    </path>
  </svg>
`;

export function insertReplacementButton(
  onReplaceClicked: () => void
): boolean {
  const button = document.querySelector('button[data-a-target="subscribe-button"]');
  if (!button) {
    return false;
  }

  if (button.parentNode?.querySelector('[data-extension-replace-button]')) {
    return true; // Already inserted
  }


  const clone = button.cloneNode(true) as HTMLElement;
  clone.style.marginLeft = '7px';
  clone.setAttribute('aria-label', 'Replace');
  clone.setAttribute('data-extension-replace-button', 'true');

  const firstIconWrapper = clone.querySelector('.tw-core-button-icon svg');
  if (firstIconWrapper) {
    firstIconWrapper.outerHTML = THUNDER_SVG;
  }

  const label = clone.querySelector('[data-a-target="tw-core-button-label-text"]');
  if (label) {
    label.textContent = 'Replace';
    let next = label.nextSibling;
    while (next) {
      const toRemove = next;
      next = next.nextSibling;
      toRemove.remove();
    }
  }

  clone.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    onReplaceClicked();
    modifyVideoElement();
    clone.remove();
  });

  button.parentNode?.insertBefore(clone, button.nextSibling);
  return true;
}

export function removeReplaceButton(): void {
  document.querySelector('[data-extension-replace-button="true"]')?.remove();
}
