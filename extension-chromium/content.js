const API_BASE_URL = 'http://localhost:61616';

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
        console.log('API response:', result);
        return result;
    } catch (error) {
        console.error('Error calling localhost API:', error);
        return null;
    }
}

async function getLiveData() {
    const username = getUsernameFromURL();

    if (!username) {
        console.error('No username found in URL');
        return null;
    }

    console.log(`Fetching live data for username: ${username}`);
    const liveData = await getFromAPI(`/live/${username}`);

    return liveData;
}

function modifyVideoElements(liveData) {
    window.MediaSource = undefined;
    console.log('MediaSource API disabled');

    if (liveData && liveData.live && liveData.playlist) {
        console.log('User is live, replacing player with new source:', liveData.playlist);

        const playerRoot = document.querySelector('[data-a-player-state]');

        if (!playerRoot) {
            console.error('Player root not found');
            return;
        }

        console.log('Removing data-a-player-state attribute to disable React control...');
        playerRoot.removeAttribute('data-a-player-state');

        const existingVideos = playerRoot.querySelectorAll('video');
        if (!existingVideos.length) {
            console.error('Error with video elements');
        }

        const video = existingVideos[0];
        const parent = video.parentNode;

        console.log('Removing existing video element...');
        video.remove();

        const newVideo = document.createElement('video');

        newVideo.src = liveData.playlist;

        newVideo.controls = true;
        newVideo.autoplay = true;
        newVideo.playsInline = true;
        newVideo.muted = false;

        newVideo.style.width = '100%';
        newVideo.style.height = '100%';
        newVideo.style.objectFit = 'contain';

        newVideo.classList.add('modified-video');

        newVideo.addEventListener('play', () => {
            console.log('New video started playing');
        });

        newVideo.addEventListener('pause', () => {
            console.log('New video paused');
        });

        newVideo.addEventListener('error', (e) => {
            console.error('Video error:', e, newVideo.error);
        });

        newVideo.addEventListener('loadeddata', () => {
            console.log('New video loaded successfully');
        });

        newVideo.addEventListener('canplay', () => {
            console.log('New video can play');
            newVideo.play().then(() => {
                console.log('New video playback started');
            }).catch(error => {
                console.error('Error playing new video:', error);
            });

        });

        console.log('Adding new video element to player container...');
        parent.appendChild(newVideo);

        console.log('Video replacement complete');
    } else {
        console.log('User is not live or no playlist available - no modifications made');
    }
}

function insertReplacementButton(){
    const button = document.querySelector('button[aria-label^="Subscribe"]');
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
    const firstIconWrapper = clone.querySelector(
        '.tw-core-button-icon svg'
    );

    if (firstIconWrapper) {
        firstIconWrapper.outerHTML = thunderSVG;
    }
    const label = clone.querySelector(
        '[data-a-target="tw-core-button-label-text"]'
    );
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
        modifyVideoElements(window.extensionLiveData)
        clone.remove();
    });
    button.parentNode.insertBefore(clone, button.nextSibling);
}

(async function init() {
    const username = getUsernameFromURL();
    console.log('Extension loaded for username:', username);

    const liveData = await getLiveData();

    if (liveData) {
        console.log('Live data received:', liveData);
        window.extensionLiveData = liveData;
    }

    const waitForButton = setInterval(() => {
        const btn = document.querySelector(
            'button[data-a-target="subscribe-button"]'
        );

        if (!btn) return;

        clearInterval(waitForButton);
        insertReplacementButton();
    }, 300);
})();

console.log('no-ads extension loaded for:', window.location.href);