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
        log('User is not live or no playlist available - no modifications made');
        return;
    }

    log('User is live, replacing player with ultra low-latency source:', liveData.playlist);

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
            // ULTRA LOW LATENCY SETTINGS
            debug: false,

            // Buffer management - ABSOLUTE MINIMUM
            maxBufferLength: 1,              // Only 1 second of buffer
            maxMaxBufferLength: 2,           // Max 2 seconds total
            maxBufferSize: 10 * 1000 * 1000, // 10MB max buffer size
            maxBufferHole: 0.1,              // Jump over small gaps quickly

            // Live sync - STAY AT LIVE EDGE (use duration-based, not count-based)
            liveSyncDuration: 0.5,           // Stay 0.5s from live edge
            liveMaxLatencyDuration: 2,       // Catch up if >2s behind

            // Playback rate adjustment for catch-up
            maxLiveSyncPlaybackRate: 1.3,    // Speed up to 1.3x to catch up
            liveDurationInfinity: true,

            // Fragment loading - AGGRESSIVE
            manifestLoadingTimeOut: 5000,
            manifestLoadingMaxRetry: 2,
            manifestLoadingRetryDelay: 500,
            levelLoadingTimeOut: 5000,
            levelLoadingMaxRetry: 2,
            levelLoadingRetryDelay: 500,
            fragLoadingTimeOut: 10000,
            fragLoadingMaxRetry: 3,
            fragLoadingRetryDelay: 500,

            // Start immediately at live edge
            startPosition: -1,               // -1 = live edge

            // Low latency mode
            lowLatencyMode: true,
            backBufferLength: 0,             // Don't keep old buffer

            // Enable features for performance
            enableWorker: true,
            enableSoftwareAES: true,

            // Aggressive buffer watching
            highBufferWatchdogPeriod: 0.5,   // Check every 0.5s
            nudgeMaxRetry: 5,

            // ABR (Adaptive Bitrate) - prefer speed over quality
            abrEwmaDefaultEstimate: 500000,  // Start with moderate quality
            abrBandWidthFactor: 0.8,         // More conservative bandwidth usage
            abrBandWidthUpFactor: 0.5,       // Slower quality increase
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

        // Monitor and enforce live edge position
        hls.on(Hls.Events.LEVEL_UPDATED, (event, data) => {
            if (!manifestParsed || !newVideo.duration) return;

            const latency = data.edge - newVideo.currentTime;

            if (latency > 3) {
                // More than 3 seconds behind - jump to live edge
                log(`Latency too high (${latency.toFixed(2)}s), jumping to live edge`);
                newVideo.currentTime = data.edge - 0.5;
            } else if (latency > 1.5) {
                // 1.5-3 seconds behind - speed up playback
                if (newVideo.playbackRate < 1.2) {
                    log(`Latency high (${latency.toFixed(2)}s), speeding up playback`);
                    newVideo.playbackRate = 1.2;
                }
            } else if (latency < 0.3) {
                // Too close to edge - slow down slightly
                if (newVideo.playbackRate > 1.0) {
                    newVideo.playbackRate = 1.0;
                }
            } else {
                // In acceptable range - normalize speed
                if (newVideo.playbackRate !== 1.0) {
                    newVideo.playbackRate = 1.0;
                }
            }
        });

        // Additional latency monitoring with aggressive buffer control
        let lastLogTime = 0;
        let lastPruneTime = 0;
        latencyMonitor = setInterval(() => {
            if (!newVideo.paused && newVideo.duration && manifestParsed) {
                const bufferEnd = newVideo.buffered.length > 0
                    ? newVideo.buffered.end(newVideo.buffered.length - 1)
                    : 0;
                const currentLatency = bufferEnd - newVideo.currentTime;

                // Log every 5 seconds
                const now = Date.now();
                if (now - lastLogTime > 5000) {
                    log(`Latency: ${currentLatency.toFixed(2)}s | Buffer: ${bufferEnd.toFixed(2)}s | Current: ${newVideo.currentTime.toFixed(2)}s | Rate: ${newVideo.playbackRate.toFixed(2)}x`);
                    lastLogTime = now;
                }

                // AGGRESSIVE BUFFER PRUNING - Force HLS.js to trim buffer
                if (currentLatency > 2 && now - lastPruneTime > 1000) {
                    log('Forcing buffer flush - too much buffered ahead');
                    // Jump closer to live edge
                    newVideo.currentTime = bufferEnd - 1;
                    lastPruneTime = now;
                }

                // Emergency catch-up if we fall too far behind
                if (currentLatency > 4) {
                    warn('Emergency catch-up: jumping forward');
                    newVideo.currentTime = bufferEnd - 0.5;
                }
            }
        }, 500);

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
            error('HLS error:', data);

            if (data.fatal) {
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
                        error('Fatal error, destroying HLS instance');
                        hls.destroy();
                        currentHlsInstance = null;
                        if (latencyMonitor) {
                            clearInterval(latencyMonitor);
                            latencyMonitor = null;
                        }
                        break;
                }
            }
        });

        // Fragment loading optimization with buffer control
        hls.on(Hls.Events.FRAG_LOADED, () => {
            // Immediately play new fragments
            if (newVideo.paused) {
                newVideo.play().catch(() => {});
            }

            // Keep enforcing buffer limits
            if (newVideo.buffered.length > 0) {
                const bufferEnd = newVideo.buffered.end(newVideo.buffered.length - 1);
                const bufferStart = newVideo.buffered.start(0);
                const totalBuffered = bufferEnd - newVideo.currentTime;

                // If we have more than 2s buffered ahead, seek closer to live
                if (totalBuffered > 2.5) {
                    log(`Buffer too large (${totalBuffered.toFixed(2)}s), seeking to live edge`);
                    newVideo.currentTime = bufferEnd - 1;
                }
            }
        });

        hls.on(Hls.Events.BUFFER_FLUSHED, () => {
            log('Buffer flushed');
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

function insertReplacementButton() {
    const button = document.querySelector('button[data-a-target="subscribe-button"]');
    if (!button) {
        error('Subscribe button not found');
        return;
    }

    const clone = button.cloneNode(true);
    clone.style.marginLeft = '7px';
    clone.setAttribute('aria-label', 'Replace');

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
        e.stopPropagation();
        e.preventDefault();
        modifyVideoElements(window.extensionLiveData);
        clone.remove();
    });

    button.parentNode.insertBefore(clone, button.nextSibling);
}

// Cleanup on page unload
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

(async function init() {
    const username = getUsernameFromURL();
    log('Extension loaded for username:', username);

    const liveData = await getLiveData();

    if (liveData) {
        log('Live data received:', liveData);
        window.extensionLiveData = liveData;
    }

    const waitForButton = setInterval(() => {
        const button = document.querySelector('button[data-a-target="subscribe-button"]');
        log("Waiting for subscribe button...")
        if (!button) return;
        log("Creating custom button...")
        clearInterval(waitForButton);
        insertReplacementButton();
    }, 300);
})();

log('Video DOM Editor extension loaded for:', window.location.href);