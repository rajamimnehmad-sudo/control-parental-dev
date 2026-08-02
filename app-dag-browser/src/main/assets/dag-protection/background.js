"use strict";

// GloshIA is intentionally disconnected in this DEV baseline. Ordinary images are
// owned by Gecko and never pass through a WebExtension response or DOM pipeline.
const BLOCKED_RESOURCE_TYPES = new Set(["media", "object"]);
const BLOCKED_MEDIA_MIME_PATTERN =
  /^(?:audio|video)\/|^application\/(?:dash\+xml|vnd\.apple\.mpegurl|x-mpegurl)/iu;
const PAGE_AD_HOSTS = new Set([
  "doubleclick.net",
  "googlesyndication.com",
  "googleadservices.com",
  "adservice.google.com",
  "adsystem.com",
  "adnxs.com",
  "amazon-adsystem.com",
  "criteo.com",
  "outbrain.com",
  "pubmatic.com",
  "rubiconproject.com",
  "taboola.com",
]);
const VIDEO_RESOURCE_TYPES = new Set(["media", "video", "audio"]);
const VIDEO_SITE_HOSTS = new Set([
  "youtube.com",
  "youtube-nocookie.com",
  "youtu.be",
  "googlevideo.com",
  "ytimg.com",
]);

const hostMatches = (hostname, candidate) =>
  hostname === candidate || hostname.endsWith(`.${candidate}`);

const isVideoSiteUrl = (value) => {
  try {
    const hostname = new URL(value || "").hostname.toLowerCase();
    return [...VIDEO_SITE_HOSTS].some((candidate) => hostMatches(hostname, candidate));
  } catch {
    return false;
  }
};

const isPageAdvertisementRequest = (details) => {
  if (!details || details.type === "main_frame" || VIDEO_RESOURCE_TYPES.has(details.type)) {
    return false;
  }
  if (isVideoSiteUrl(details.documentUrl) || isVideoSiteUrl(details.originUrl)) {
    return false;
  }
  try {
    const hostname = new URL(details.url).hostname.toLowerCase();
    return [...PAGE_AD_HOSTS].some((candidate) => hostMatches(hostname, candidate));
  } catch {
    return false;
  }
};

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (isPageAdvertisementRequest(details)) return { cancel: true };
    if (BLOCKED_RESOURCE_TYPES.has(details.type)) return { cancel: true };
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"],
);

browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    const contentType = details.responseHeaders
      ?.find((header) => header.name.toLowerCase() === "content-type")
      ?.value?.trim();
    return contentType && BLOCKED_MEDIA_MIME_PATTERN.test(contentType)
      ? { cancel: true }
      : {};
  },
  { urls: ["<all_urls>"] },
  ["blocking", "responseHeaders"],
);
