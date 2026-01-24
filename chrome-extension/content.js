const API_BASE_URL = 'http://localhost:61616';

const LOG_PREFIX = '[twitch-adblock]';

function log(...args) {
    console.log(LOG_PREFIX, ...args);
}

function warn(...args) {
    console.warn(LOG_PREFIX, ...args);
}

function error(...args) {
    console.error(LOG_PREFIX, ...args);
}

function onLocationChange(callback) {
    const pushState = history.pushState;
    const replaceState = history.replaceState;

    history.pushState = function () {
        pushState.apply(this, arguments);
        callback();
    };

    history.replaceState = function () {
        replaceState.apply(this, arguments);
        callback();
    };

    window.addEventListener('popstate', callback);

    // Poll as fallback for Twitch SPA routing
    let lastUrl = location.href;
    setInterval(() => {
        const currentUrl = location.href;
        if (currentUrl !== lastUrl) {
            log('URL change detected via polling:', currentUrl);
            lastUrl = currentUrl;
            callback();
        }
    }, 500);
}

function getUsernameFromURL() {
    const path = window.location.pathname;
    return path.split('/').filter(segment => segment.length > 0)[0];
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

async function getLiveData() {
    const username = getUsernameFromURL();

    if (!username) {
        error('No username found in URL');
        return null;
    }

    log(`Fetching live data for username: ${username}`);
    const liveData = await getFromAPI(`/live/${username}`);

    return liveData;
}

let currentHlsInstance = null;
let latencyMonitor = null;

// Ultra low-latency HLS player with aggressive optimizations
async function modifyVideoElements(liveData) {
    if (!liveData || !liveData.live || !liveData.playlist) {
        log('User is not live or no playlist available');
        return;
    }

    log('Replacing player with low-latency source:', liveData.playlist);

    const playerRoot = document.querySelector('[data-a-player-state]');

    if (!playerRoot) {
        error('Player root not found');
        return;
    }

    if (typeof Hls === 'undefined') {
        error('HLS.js is not loaded. Make sure hls.js is included in the extension.');
        return;
    }

    log('Removing data-a-player-state attribute to disable React control...');
    playerRoot.removeAttribute('data-a-player-state');

    const existingVideos = playerRoot.querySelectorAll('video');
    if (!existingVideos.length) {
        error('Error with video elements');
        return;
    }

    const video = existingVideos[0];
    const parent = video.parentNode;

    log('Removing existing video element...');
    video.remove();

    const ref = document.querySelector('[data-a-target="video-ref"]');

    if (ref) {
        // Find child elements whose class contains the string "video"
        const videoChild = ref.querySelector('[class*="video"]');

        if (videoChild) {
            videoChild.style.display = 'none';
        }
    }

    // Cleanup previous instances
    if (currentHlsInstance) {
        log('Destroying previous HLS instance...');
        currentHlsInstance.destroy();
        currentHlsInstance = null;
    }
    if (latencyMonitor) {
        clearInterval(latencyMonitor);
        latencyMonitor = null;
    }

    const newVideo = document.createElement('video');

    newVideo.controls = true;
    newVideo.autoplay = true;
    newVideo.playsInline = true;
    newVideo.muted = false;

    if (Hls.isSupported()) {
        const hls = new Hls({
            // MODERATE LOW LATENCY - balance between latency and stability
            debug: false,
            maxBufferLength: 4,
            maxMaxBufferLength: 10,
            maxBufferSize: 30 * 1000 * 1000,
            maxBufferHole: 0.3,
            liveSyncDuration: 2,
            liveMaxLatencyDuration: 8,
            maxLiveSyncPlaybackRate: 1.2,
            liveDurationInfinity: true,

            // Fragment loading - moderate tolerance
            manifestLoadingTimeOut: 8000,
            manifestLoadingMaxRetry: 3,
            manifestLoadingRetryDelay: 750,
            levelLoadingTimeOut: 8000,
            levelLoadingMaxRetry: 3,
            levelLoadingRetryDelay: 750,
            fragLoadingTimeOut: 15000,
            fragLoadingMaxRetry: 4,
            fragLoadingRetryDelay: 750,
            startPosition: -1,
            lowLatencyMode: true,
            backBufferLength: 10,
            enableWorker: true,
            enableSoftwareAES: true,
            highBufferWatchdogPeriod: 1.5,
            nudgeMaxRetry: 8,
            abrEwmaDefaultEstimate: 800000,
            abrBandWidthFactor: 0.85,
            abrBandWidthUpFactor: 0.6,
            abrMaxWithRealBitrate: true,

            // Fragment prefetch
            progressive: true
        });

        currentHlsInstance = hls;

        hls.loadSource(liveData.playlist);
        hls.attachMedia(newVideo);

        // Aggressive live edge seeking
        let manifestParsed = false;

        hls.on(Hls.Events.MANIFEST_PARSED, () => {
            log('HLS manifest parsed, jumping to live edge');
            manifestParsed = true;

            // Force immediate playback at live edge
            newVideo.play().catch(err => {
                error('Error playing video:', err);
            });
        });

        // Monitor and enforce live edge position - moderate thresholds
        hls.on(Hls.Events.LEVEL_UPDATED, (event, data) => {
            if (!manifestParsed || !newVideo.duration) return;

            const latency = data.edge - newVideo.currentTime;

            if (latency > 12) {
                log(`Latency too high (${latency.toFixed(2)}s), jumping to live edge`);
                newVideo.currentTime = data.edge - 3;
            } else if (latency > 8) {
                if (newVideo.playbackRate < 1.1) {
                    log(`Latency high (${latency.toFixed(2)}s), speeding up playback`);
                    newVideo.playbackRate = 1.1;
                }
            } else if (newVideo.playbackRate !== 1.0) {
                newVideo.playbackRate = 1.0;
            }
        });

        // Latency monitoring - moderate intervention
        let lastLogTime = 0;
        let lastPruneTime = 0;
        latencyMonitor = setInterval(() => {
            if (!newVideo.paused && newVideo.duration && manifestParsed) {
                const bufferEnd = newVideo.buffered.length > 0
                    ? newVideo.buffered.end(newVideo.buffered.length - 1)
                    : 0;
                const currentLatency = bufferEnd - newVideo.currentTime;

                // Log every 15 seconds
                const now = Date.now();
                if (now - lastLogTime > 15000) {
                    log(`Latency: ${currentLatency.toFixed(2)}s | Buffer: ${bufferEnd.toFixed(2)}s | Current: ${newVideo.currentTime.toFixed(2)}s | Rate: ${newVideo.playbackRate.toFixed(2)}x`);
                    lastLogTime = now;
                }

                // Intervene if buffer gets too large
                if (currentLatency > 8 && now - lastPruneTime > 5000) {
                    log('Buffer large, seeking closer to live edge');
                    newVideo.currentTime = bufferEnd - 3;
                    lastPruneTime = now;
                }

                if (currentLatency > 15) {
                    warn('Emergency catch-up: jumping forward');
                    newVideo.currentTime = bufferEnd - 2;
                }
            }
        }, 3000);

        // Handle buffering/stalling
        let stallCount = 0;
        newVideo.addEventListener('waiting', () => {
            log('Video buffering...');
            stallCount++;

            // If stalling too much, try to recover
            if (stallCount > 3) {
                warn('Too many stalls, attempting recovery');
                hls.startLoad();
                stallCount = 0;
            }
        });

        newVideo.addEventListener('playing', () => {
            log('Video playing');
            stallCount = 0; // Reset stall counter
        });

        // Error handling
        hls.on(Hls.Events.ERROR, (event, data) => {
            // Non-fatal errors - handle gracefully without logging as errors
            if (!data.fatal) {
                // bufferStalledError is common during live streaming, HLS.js handles it automatically
                if (data.details === 'bufferStalledError') {
                    // Silent - these are expected and auto-recover
                    return;
                }
                // Log other non-fatal errors as warnings for debugging
                warn('HLS warning:', data.type, data.details);
                return;
            }

            // Fatal errors - log with details and attempt recovery
            error('HLS fatal error:', data.type, data.details);

            switch (data.type) {
                case Hls.ErrorTypes.NETWORK_ERROR:
                    log('Network error, attempting recovery...');
                    hls.startLoad();
                    break;
                case Hls.ErrorTypes.MEDIA_ERROR:
                    log('Media error, attempting recovery...');
                    hls.recoverMediaError();
                    break;
                default:
                    error('Unrecoverable error, destroying HLS instance');
                    hls.destroy();
                    currentHlsInstance = null;
                    if (latencyMonitor) {
                        clearInterval(latencyMonitor);
                        latencyMonitor = null;
                    }
                    break;
            }
        });

        // Fragment loading - resume playback if paused
        hls.on(Hls.Events.FRAG_LOADED, () => {
            // Immediately play new fragments
            if (newVideo.paused) {
                newVideo.play().catch(() => {});
            }
        });

    } else if (newVideo.canPlayType('application/vnd.apple.mpegurl')) {
        log('Using native HLS support (Safari)');
        newVideo.src = liveData.playlist;
    } else {
        error('HLS is not supported in this browser');
        return;
    }

    newVideo.addEventListener('play', () => {
        log('Video started playing');
    });

    newVideo.addEventListener('pause', () => {
        log('Video paused');
    });

    newVideo.addEventListener('error', (e) => {
        error('Video error:', e, newVideo.error);
    });

    newVideo.addEventListener('loadeddata', () => {
        log('Video loaded successfully');
    });

    log('Adding new video element to player container...');
    parent.appendChild(newVideo);

    log('Ultra low-latency video player initialized');
}

let replaceButtonClicked = false;

function insertReplacementButton() {
    if (replaceButtonClicked) return;

    const button = document.querySelector('button[data-a-target="subscribe-button"]');
    if (!button) return;

    // Prevent duplicates
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
        modifyVideoElements(window.extensionLiveData);

        // Convert to Revert button
        clone.setAttribute('aria-label', 'Revert');
        clone.setAttribute('data-extension-revert-button', 'true');
        clone.removeAttribute('data-extension-replace-button');

        // Update icon to a refresh/revert icon
        const revertSVG = `
          <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true">
            <path fill-rule="evenodd"
              d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8Z"
              clip-rule="evenodd">
            </path>
          </svg>
        `;

        const iconWrapper = clone.querySelector('.tw-core-button-icon svg');
        if (iconWrapper) {
            iconWrapper.outerHTML = revertSVG;
        }

        const revertLabel = clone.querySelector('[data-a-target="tw-core-button-label-text"]');
        if (revertLabel) {
            revertLabel.textContent = 'Revert';
        }

        // Replace click handler with revert functionality
        const newClone = clone.cloneNode(true);
        clone.parentNode.replaceChild(newClone, clone);

        newClone.addEventListener('click', e => {
            e.preventDefault();
            e.stopPropagation();
            log('Reverting to original Twitch player...');
            location.reload();
        });
    });

    button.parentNode.insertBefore(clone, button.nextSibling);
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

window.addEventListener('beforeunload', () => {
    if (currentHlsInstance) {
        log('Cleaning up HLS instance on page unload');
        currentHlsInstance.destroy();
        currentHlsInstance = null;
    }
    if (latencyMonitor) {
        clearInterval(latencyMonitor);
        latencyMonitor = null;
    }
});

let lastHandledUrl = null;  // Start as null so first call always runs

function handleNavigation() {
    const url = location.href;

    // Check if this is a URL change (not initial load)
    const isUrlChange = lastHandledUrl !== null && url !== lastHandledUrl;

    if (isUrlChange) {
        log('SPA navigation detected:', url);

        // If we had replaced the player, reload for clean state
        if (currentHlsInstance || replaceButtonClicked) {
            log('Reloading page to restore clean player state');
            if (currentHlsInstance) {
                currentHlsInstance.destroy();
                currentHlsInstance = null;
            }
            if (latencyMonitor) {
                clearInterval(latencyMonitor);
                latencyMonitor = null;
            }
            location.reload();
            return;
        }
    }

    // Skip if URL hasn't changed (for polling calls)
    if (lastHandledUrl === url) {
        return;
    }

    lastHandledUrl = url;

    replaceButtonClicked = false;

    log('Fetching live data for:', url);
    getLiveData().then(data => {
        window.extensionLiveData = data;
        log('Live data loaded:', data);
    });
}

(async function init() {
    handleNavigation();
    observeSubscribeButton();
    onLocationChange(handleNavigation);
})();

log('Video DOM Editor extension loaded for:', window.location.href);