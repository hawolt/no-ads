(() => {
    const pushState = history.pushState;
    const replaceState = history.replaceState;

    function notify(type) {
        window.dispatchEvent(
            new CustomEvent("twitch-adblock:locationchange", {
                detail: {type, url: location.href}
            })
        );
    }

    history.pushState = function () {
        pushState.apply(this, arguments);
        notify("pushState");
    };

    history.replaceState = function () {
        replaceState.apply(this, arguments);
        notify("replaceState");
    };

    window.addEventListener("popstate", () => {
        notify("popstate");
    });
})();