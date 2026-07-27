"use strict";

const BLOCKED_RESOURCE_TYPES = new Set(["image", "imageset", "media", "object"]);

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (BLOCKED_RESOURCE_TYPES.has(details.type)) {
      return { cancel: true };
    }
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"],
);
