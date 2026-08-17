"use strict";

(() => {
  if (globalThis.__gloshDagVideoPremiumOverlay !== undefined) return;

  const create = ({ documentObject, windowObject }) => {
    let activeRecord = null;
    let button = null;

    const apply = (element, property, value) =>
      element.style.setProperty(property, value, "important");

    const ensureButton = () => {
      if (button !== null) return button;
      button = documentObject.createElement("button");
      button.type = "button";
      button.textContent = "Saltar a parte segura";
      button.setAttribute("aria-label", "Saltar a la siguiente parte segura del video");
      button.setAttribute("data-glosh-dag-video-skip", "true");
      apply(button, "position", "fixed");
      apply(button, "z-index", "2147483647");
      apply(button, "display", "none");
      apply(button, "min-height", "38px");
      apply(button, "max-width", "calc(100vw - 24px)");
      apply(button, "padding", "8px 14px");
      apply(button, "border", "1px solid rgba(255,255,255,.34)");
      apply(button, "border-radius", "19px");
      apply(button, "background", "rgba(64, 8, 20, .92)");
      apply(button, "color", "white");
      apply(button, "font", "600 13px system-ui, sans-serif");
      apply(button, "box-shadow", "0 6px 22px rgba(0,0,0,.35)");
      apply(button, "pointer-events", "auto");
      documentObject.documentElement.appendChild(button);
      return button;
    };

    const rebind = (record = activeRecord) => {
      if (record === null || record !== activeRecord || button === null) return;
      const rect = record.video.getBoundingClientRect();
      const visible =
        record.video.isConnected &&
        rect.width > 0 &&
        rect.height > 0 &&
        rect.right > 0 &&
        rect.bottom > 0 &&
        rect.left < windowObject.innerWidth &&
        rect.top < windowObject.innerHeight;
      if (!visible) {
        apply(button, "display", "none");
        return;
      }
      const left = Math.max(12, Math.min(
        windowObject.innerWidth - 12,
        rect.left + rect.width / 2,
      ));
      const bottom = Math.max(12, windowObject.innerHeight - rect.bottom + 12);
      apply(button, "left", `${Math.round(left)}px`);
      apply(button, "bottom", `${Math.round(bottom)}px`);
      apply(button, "transform", "translateX(-50%)");
      apply(button, "display", "block");
    };

    const hide = () => {
      activeRecord = null;
      if (button === null) return;
      button.onclick = null;
      apply(button, "display", "none");
    };

    const show = (record, onClick) => {
      const control = ensureButton();
      activeRecord = record;
      control.onclick = (event) => {
        event.preventDefault();
        event.stopPropagation();
        hide();
        onClick();
      };
      rebind(record);
    };

    return Object.freeze({ hide, rebind, show });
  };

  globalThis.__gloshDagVideoPremiumOverlay = Object.freeze({ create });
})();
