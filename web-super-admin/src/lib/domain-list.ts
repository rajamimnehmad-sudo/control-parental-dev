import { createHash, createPublicKey, verify } from "node:crypto";

const baseUrl = "https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/web-domain-list/dev";
const publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEoTJncb+tUn3p8KtQtXENfRH1Z56HjESILP+k1LsMXVen4YJzKjm7t/Wj3wBvxoahiEsYTT9RkJ1u6VqHqGJrA==";

export type DomainListPayload = {
  source: "UT1"; version: number; sourceDate: string; generatedAt: string; categories: string[];
  countByCategory: { adult: number; mixed_adult: number }; educationalExceptionCount: number;
  totalCount: number; sizeBytes: number; sha256: string; signatureStatus: string;
  lastSuccessfulRun: string; lastError: string | null; devCanary: string; canaryIncluded: boolean;
  environment: "DEV"; nextScheduledAt: string;
  dataUrl?: string;
  testDomains?: Record<string, string[]>;
};

export type DomainListStatus = {
  payload: DomainListPayload | null;
  signatureValid: boolean;
  operational: { state?: string; lastError?: string | null; protectionActive?: boolean; checkedAt?: string } | null;
};

export async function getDomainListStatus(): Promise<DomainListStatus> {
  const [manifestResponse, statusResponse] = await Promise.all([
    fetch(`${baseUrl}/current-manifest.json`, { cache: "no-store" }),
    fetch(`${baseUrl}/status.json`, { cache: "no-store" }),
  ]);
  const operational = statusResponse.ok ? await statusResponse.json() : null;
  if (!manifestResponse.ok) return { payload: null, signatureValid: false, operational };
  const envelope = await manifestResponse.json();
  const signedPayload = Buffer.from(envelope.signedPayload, "base64");
  const publicKey = createPublicKey({ key: Buffer.from(publicKeyBase64, "base64"), format: "der", type: "spki" });
  const signatureValid = verify("sha256", signedPayload, publicKey, Buffer.from(envelope.manifestSignature, "base64"));
  return { payload: JSON.parse(signedPayload.toString("utf8")), signatureValid, operational };
}

export function protectionState(status: DomainListStatus) {
  if (!status.payload || !status.signatureValid) return "critical" as const;
  if (status.operational?.state === "error") return "error-active" as const;
  const age = Date.now() - new Date(status.payload.generatedAt).getTime();
  return age > 7 * 24 * 60 * 60 * 1000 ? ("stale" as const) : ("active" as const);
}

export type DomainLookupResult = {
  query: string;
  normalized: string;
  matchedDomain: string | null;
  category: string | null;
  source: "manual" | "canary" | "compiled" | null;
  version: number | null;
};

let cachedBundle: { url: string; sha256: string; bytes: Buffer } | null = null;

export async function lookupDomain(rawDomain: string): Promise<DomainLookupResult> {
  const normalized = normalizeDomain(rawDomain);
  if (!normalized) throw new Error("Ingresá un dominio válido, por ejemplo example.com.");
  const status = await getDomainListStatus();
  const payload = status.payload;
  if (!payload || !status.signatureValid || !payload.dataUrl) throw new Error("No hay una base DEV válida para consultar.");
  const bytes = await activeBundle(payload.dataUrl, payload.sha256);
  const parsed = parseBundle(bytes);
  const manual = await manualDomains();
  const candidates = domainCandidates(normalized);
  if (candidates.some((candidate) => parsed.exceptions.has(candidate))) {
    return { query: rawDomain, normalized, matchedDomain: null, category: null, source: null, version: payload.version };
  }
  for (const candidate of candidates) {
    const manualMatch = manual.find((entry) => entry.domain === candidate);
    if (manualMatch) return { query: rawDomain, normalized, matchedDomain: candidate, category: manualMatch.category, source: "manual", version: payload.version };
    if (parsed.canaries.has(candidate)) return { query: rawDomain, normalized, matchedDomain: candidate, category: "dev_test_blocked", source: "canary", version: payload.version };
    for (const category of parsed.categories) {
      if (exactContains(category.hashes, candidate)) {
        return { query: rawDomain, normalized, matchedDomain: candidate, category: category.name, source: "compiled", version: payload.version };
      }
    }
  }
  return { query: rawDomain, normalized, matchedDomain: null, category: null, source: null, version: payload.version };
}

function normalizeDomain(raw: string) {
  const withoutScheme = raw.trim().toLowerCase().replace(/^https?:\/\//, "").split("/")[0].split(":")[0].replace(/\.$/, "");
  const value = withoutScheme.startsWith("www.") ? withoutScheme.slice(4) : withoutScheme;
  return /^(?=.{3,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z0-9-]{2,}$/.test(value) ? value : null;
}

function domainCandidates(domain: string) {
  const labels = domain.split(".");
  return labels.slice(0, -1).map((_, index) => labels.slice(index).join("."));
}

async function activeBundle(url: string, sha256: string) {
  if (cachedBundle?.url === url && cachedBundle.sha256 === sha256) return cachedBundle.bytes;
  const response = await fetch(url, { cache: "no-store" });
  if (!response.ok) throw new Error("No se pudo descargar la base activa.");
  const bytes = Buffer.from(await response.arrayBuffer());
  if (createHash("sha256").update(bytes).digest("hex") !== sha256) {
    throw new Error("La base activa no coincide con su manifiesto firmado.");
  }
  cachedBundle = { url, sha256, bytes };
  return bytes;
}

function parseBundle(bytes: Buffer) {
  if (bytes.subarray(0, 8).toString("ascii") !== "CFDL0001" || bytes.readInt32BE(16) !== 3) throw new Error("Formato de base no compatible.");
  const categoryCount = bytes.readInt32BE(24);
  const exceptionLength = bytes.readInt32BE(28);
  const canaryLength = bytes.readInt32BE(32);
  let offset = 36;
  const descriptors = Array.from({ length: categoryCount }, () => {
    const descriptor = { nameLength: bytes.readInt32BE(offset), bitCount: bytes.readInt32BE(offset + 4), count: bytes.readInt32BE(offset + 8), exactLength: bytes.readInt32BE(offset + 12) };
    offset += 16;
    return descriptor;
  });
  const categories = descriptors.map((descriptor) => {
    const name = bytes.subarray(offset, offset + descriptor.nameLength).toString("ascii");
    offset += descriptor.nameLength + Math.ceil(descriptor.bitCount / 8);
    const hashes = bytes.subarray(offset, offset + descriptor.exactLength);
    offset += descriptor.exactLength;
    return { name, hashes };
  });
  const exceptions = new Set(bytes.subarray(offset, offset + exceptionLength).toString("ascii").split("\n").filter(Boolean));
  offset += exceptionLength;
  const canaries = new Set(bytes.subarray(offset, offset + canaryLength).toString("ascii").split("\n").filter(Boolean));
  return { categories, exceptions, canaries };
}

function exactContains(hashes: Buffer, domain: string) {
  const target = createHash("sha256").update(domain, "ascii").digest().subarray(0, 8);
  let low = 0;
  let high = hashes.length / 8 - 1;
  while (low <= high) {
    const middle = (low + high) >>> 1;
    const comparison = Buffer.compare(hashes.subarray(middle * 8, middle * 8 + 8), target);
    if (comparison < 0) low = middle + 1;
    else if (comparison > 0) high = middle - 1;
    else return true;
  }
  return false;
}

async function manualDomains(): Promise<Array<{ domain: string; category: string }>> {
  const response = await fetch(`${baseUrl}/manual-domains.json`, { cache: "no-store" });
  if (!response.ok) return [];
  const payload = await response.json().catch(() => []);
  return Array.isArray(payload) ? payload : [];
}
