export const MaximumNormalizedBytes = 512 * 1024;
export const PolicyVersion = "DAG_STRICT_MODESTY_V1";
export const CollectorVersion = "dag-v2-calibration-collector-1";
export const ValidDecisions = new Set(["show", "hide", "unsure"]);
export const ValidSourceKinds = new Set([
  "rasterimage",
  "webview_raster",
  "serviceworker_raster",
]);

export type CalibrationFields = {
  deviceId: string;
  contentSha256: string;
  existingContentSha256: string | null;
  perceptualHash: string;
  width: number;
  height: number;
  mimeType: string;
  sizeBytes: number;
  sourceKind: string;
  sourceHost: string;
  documentHost: string;
  sourceUrlHash: string;
  reviewDecision: string;
  policyVersion: string;
  collectorVersion: string;
};

export function parseFields(form: FormData): CalibrationFields | null {
  const fields: CalibrationFields = {
    deviceId: text(form, "device_id"),
    contentSha256: text(form, "content_sha256").toLowerCase(),
    existingContentSha256:
      text(form, "existing_content_sha256").toLowerCase() || null,
    perceptualHash: text(form, "perceptual_hash").toLowerCase(),
    width: Number(text(form, "width")),
    height: Number(text(form, "height")),
    mimeType: text(form, "mime_type").toLowerCase(),
    sizeBytes: Number(text(form, "size_bytes")),
    sourceKind: text(form, "source_kind").toLowerCase(),
    sourceHost: sanitizeHost(text(form, "source_host")),
    documentHost: sanitizeHost(text(form, "document_host")),
    sourceUrlHash: text(form, "source_url_hash").toLowerCase(),
    reviewDecision: text(form, "review_decision").toLowerCase(),
    policyVersion: text(form, "policy_version"),
    collectorVersion: text(form, "collector_version"),
  };
  if (
    !isUuid(fields.deviceId) ||
    !isSha256(fields.contentSha256) ||
    (fields.existingContentSha256 !== null &&
      !isSha256(fields.existingContentSha256)) ||
    !/^[0-9a-f]{16}$/.test(fields.perceptualHash) ||
    !Number.isSafeInteger(fields.width) ||
    !Number.isSafeInteger(fields.height) ||
    fields.width < 1 || fields.width > 768 ||
    fields.height < 1 || fields.height > 768 ||
    fields.mimeType !== "image/jpeg" ||
    !Number.isSafeInteger(fields.sizeBytes) ||
    fields.sizeBytes < 0 || fields.sizeBytes > MaximumNormalizedBytes ||
    !ValidSourceKinds.has(fields.sourceKind) ||
    fields.sourceHost.length < 1 ||
    fields.documentHost.length < 1 ||
    !isSha256(fields.sourceUrlHash) ||
    !ValidDecisions.has(fields.reviewDecision) ||
    fields.policyVersion !== PolicyVersion ||
    fields.collectorVersion !== CollectorVersion
  ) return null;
  return fields;
}

export function jpegDimensions(
  bytes: Uint8Array,
): { width: number; height: number } | null {
  if (
    bytes.length < 4 || bytes[0] !== 0xff || bytes[1] !== 0xd8 ||
    bytes[bytes.length - 2] !== 0xff || bytes[bytes.length - 1] !== 0xd9
  ) return null;
  let offset = 2;
  while (offset + 8 < bytes.length) {
    if (bytes[offset] !== 0xff) return null;
    const marker = bytes[offset + 1];
    offset += 2;
    if (marker === 0xd9 || marker === 0xda) break;
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
    if (offset + 2 > bytes.length) return null;
    const length = (bytes[offset] << 8) | bytes[offset + 1];
    if (length < 2 || offset + length > bytes.length) return null;
    if (
      marker >= 0xc0 && marker <= 0xcf &&
      ![0xc4, 0xc8, 0xcc].includes(marker)
    ) {
      if (length < 7) return null;
      return {
        height: (bytes[offset + 3] << 8) | bytes[offset + 4],
        width: (bytes[offset + 5] << 8) | bytes[offset + 6],
      };
    }
    offset += length;
  }
  return null;
}

export function sanitizeHost(value: string): string {
  const normalized = value.trim().toLowerCase().replace(/\.$/, "")
    .replace(/^www\./, "");
  return /^[a-z0-9.-]{1,253}$/.test(normalized) ? normalized : "";
}

export function isTrainingExample(decision: string): boolean {
  return decision === "show" || decision === "hide";
}

export function containsForbiddenPayloadFields(form: FormData): boolean {
  const forbidden = new Set([
    "url",
    "resource_url",
    "document_url",
    "query",
    "text",
    "cookie",
    "cookies",
    "referer",
    "headers",
    "form",
    "model_version",
    "threshold",
    "thresholds",
  ]);
  return [...form.keys()].some((key) => forbidden.has(key.toLowerCase()));
}

export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    Uint8Array.from(bytes).buffer,
  );
  return [...new Uint8Array(digest)]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
}

export function storagePath(contentSha256: string): string {
  return `samples/${contentSha256.slice(0, 2)}/${contentSha256}.jpg`;
}

export function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
    .test(value);
}

function isSha256(value: string): boolean {
  return /^[0-9a-f]{64}$/.test(value);
}

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.trim() : "";
}
