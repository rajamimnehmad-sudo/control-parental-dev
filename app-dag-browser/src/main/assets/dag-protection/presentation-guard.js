(() => {
  "use strict";

  const GUARD_ATTRIBUTE = "data-glosh-dag-presentation-guard";
  const GUARD_VERSION = "1";
  const denied = () => Promise.reject(new DOMException("Presentation blocked", "NotAllowedError"));
  const throwDenied = () => {
    throw new DOMException("Presentation blocked", "NotAllowedError");
  };

  const lockMethod = (owner, name, replacement) => {
    if (owner == null || typeof owner[name] !== "function") return true;
    try {
      Object.defineProperty(owner, name, {
        configurable: false,
        enumerable: false,
        value: replacement,
        writable: false,
      });
      return owner[name] === replacement;
    } catch {
      return false;
    }
  };

  let installed = true;
  installed = lockMethod(globalThis.HTMLVideoElement?.prototype, "requestPictureInPicture", denied) && installed;
  installed = lockMethod(globalThis.RemotePlayback?.prototype, "prompt", denied) && installed;
  const documentPictureInPicture = globalThis.documentPictureInPicture;
  if (documentPictureInPicture != null) {
    const owner = Object.getPrototypeOf(documentPictureInPicture) ?? documentPictureInPicture;
    installed = lockMethod(owner, "requestWindow", denied) && installed;
  }
  const videoPrototype = globalThis.HTMLVideoElement?.prototype;
  if (videoPrototype != null && typeof videoPrototype.webkitSetPresentationMode === "function") {
    const original = videoPrototype.webkitSetPresentationMode;
    installed = lockMethod(videoPrototype, "webkitSetPresentationMode", function (mode) {
      if (mode === "fullscreen" || mode === "picture-in-picture") return throwDenied();
      return Reflect.apply(original, this, [mode]);
    }) && installed;
  }

  const markInstalled = () => {
    if (!installed || document.documentElement == null) return false;
    document.documentElement.setAttribute(GUARD_ATTRIBUTE, GUARD_VERSION);
    return document.documentElement.getAttribute(GUARD_ATTRIBUTE) === GUARD_VERSION;
  };
  if (!markInstalled()) {
    const observer = new MutationObserver(() => {
      if (markInstalled()) observer.disconnect();
    });
    observer.observe(document, { childList: true, subtree: true });
  }
})();
