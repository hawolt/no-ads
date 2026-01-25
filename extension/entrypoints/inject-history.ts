export default defineUnlistedScript(() => {
  const pushState = history.pushState;
  const replaceState = history.replaceState;

  function notify(type: string) {
    window.dispatchEvent(
      new CustomEvent('twitch-adblock:locationchange', {
        detail: { type, url: location.href },
      })
    );
  }

  history.pushState = function (...args) {
    pushState.apply(this, args);
    notify('pushState');
  };

  history.replaceState = function (...args) {
    replaceState.apply(this, args);
    notify('replaceState');
  };

  window.addEventListener('popstate', () => {
    notify('popstate');
  });
});
