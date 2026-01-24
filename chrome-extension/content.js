const API_BASE_URL = 'http://localhost:61616';

const LOG_PREFIX = '[twitch-adblock]';

let replaceButtonClicked = false;
let currentHlsInstance = null;

function log(...args) {
    console.log(LOG_PREFIX, ...args);
}

function warn(...args) {
    console.warn(LOG_PREFIX, ...args);
}

function error(...args) {
    console.error(LOG_PREFIX, ...args);
}

const script = document.createElement("script");
script.src = chrome.runtime.getURL("inject-history.js");
script.type = "text/javascript";
script.onload = () => script.remove();
(document.head || document.documentElement).appendChild(script)

window.addEventListener("twitch-adblock:locationchange", (e) => {
    handleNavigation(e.detail.url);
});

function getUsernameFromURL(url) {
    try {
        const {pathname} = new URL(url);
        return pathname.split("/").filter(Boolean)[0] || null;
    } catch {
        return null;
    }
}

async function getFromAPI(endpoint) {
    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
            }
        });

        if (!response.ok) {
            throw new Error(`API call failed: ${response.status}`);
        }

        const result = await response.json();
        log('API response:', result);
        return result;
    } catch (error) {
        error('Error calling localhost API:', error);
        return null;
    }
}

async function getLivestream(url) {
    const username = getUsernameFromURL(url);

    if (!username) {
        error('No username found in URL');
        return null;
    }

    log(`Fetching live data for username: ${username}`);
    return await getFromAPI(`/live/${username}`);
}

function handleNavigation(url) {
    console.log(
        "[twitch-adblock] navigated to",
        ":",
        url
    );
    replaceButtonClicked = false;
    observeSubscribeButton();
    getLivestream(url).then(source => {
        window.extensionLiveSource = source;

        const root = document.querySelector('[data-a-player-state]');
        const videos = root.querySelectorAll('video');
        if (!videos.length) {
            log('navigated with ad-free player, replacing video element')
            modifyVideoElement(source);
            log('removing replace button')
            replaceButtonClicked = true;
            document.querySelector('[data-extension-replace-button="true"]').remove();
        }
    });
}

function observeSubscribeButton() {
    const observer = new MutationObserver(() => {
        if (replaceButtonClicked) {
            observer.disconnect();
            return;
        }

        insertReplacementButton();
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true,
    });
}

function insertReplacementButton() {
    if (replaceButtonClicked) return;

    const button = document.querySelector('button[data-a-target="subscribe-button"]');
    if (!button) return;

    if (button.parentNode.querySelector('[data-extension-replace-button]')) {
        return;
    }

    const clone = button.cloneNode(true);
    clone.style.marginLeft = '7px';
    clone.setAttribute('aria-label', 'Replace');
    clone.setAttribute('data-extension-replace-button', 'true');

    const thunderSVG = `
      <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true">
        <path fill-rule="evenodd"
          d="M13 2L3 14h7l-1 8 12-14h-7l-1-6Z"
          clip-rule="evenodd">
        </path>
      </svg>
    `;

    const firstIconWrapper = clone.querySelector('.tw-core-button-icon svg');
    if (firstIconWrapper) {
        firstIconWrapper.outerHTML = thunderSVG;
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

    clone.addEventListener('click', e => {
        e.preventDefault();
        e.stopPropagation();
        replaceButtonClicked = true;
        modifyVideoElement(window.extensionLiveSource);
        clone.remove();
    });

    button.parentNode.insertBefore(clone, button.nextSibling);
}

function modifyVideoElement(source) {
    if (!source || !source.live || !source.playlist) {
        log('User is not live or no playlist available - no modifications made');
        return;
    }

    const root = document.querySelector('[data-a-player-state]');

    if (!root) {
        error('Player root not found');
        return;
    }

    if (typeof Hls === 'undefined') {
        error('HLS.js is not loaded.');
        return;

    }

    log('Removing data-a-player-state attribute to disable React control...');
    root.removeAttribute('data-a-player-state');

    let videos = root.querySelectorAll('video');

    if (!videos.length) {
        videos = document.querySelectorAll('video');
    }

    if (!videos.length) {
        error('No video elements found in the DOM');
        return;
    }

    const video = videos[0];
    const parent = video.parentNode;

    log('Removing existing video element...');
    video.remove();

    const ref = document.querySelector('[data-a-target="video-ref"]');

    if (ref) {
        const videoChild = ref.querySelector('[class*="video"]');

        if (videoChild) {
            videoChild.style.display = 'none';
        }
    }

    if (currentHlsInstance) {
        log('Destroying previous HLS instance...');
        currentHlsInstance.destroy();
        currentHlsInstance = null;
    }

    const inject = document.createElement('video');

    inject.playsInline = true;
    inject.controls = true;
    inject.autoplay = true;
    inject.muted = false;

    if (Hls.isSupported()) {
        const hls = new Hls({
            lowLatencyMode: true,

            liveSyncDurationCount: 2,
            liveMaxLatencyDurationCount: 4,

            backBufferLength: 30,
            maxBufferLength: 60,
            maxBufferSize: 30 * 1000 * 1000,

            liveDurationInfinity: true
        });

        currentHlsInstance = hls;

        hls.loadSource(source.playlist);
        hls.attachMedia(inject);
    } else {
        console.error("HLS does not seem to be supported...")
    }

    parent.appendChild(inject);
}

log('extension loaded');

handleNavigation(window.location.href);
observeSubscribeButton();
