import { createPublicKey, verify } from "node:crypto";

const baseUrl = "https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/web-domain-list/dev";
const publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEoTJncb+tUn3p8KtQtXENfRH1Z56HjESILP+k1LsMXVen4YJzKjm7t/Wj3wBvxoahiEsYTT9RkJ1u6VqHqGJrA==";

export type DomainListPayload = {
  source: "UT1"; version: number; sourceDate: string; generatedAt: string; categories: string[];
  countByCategory: { adult: number; porn?: number; mixed_adult: number }; educationalExceptionCount: number;
  totalCount: number; sizeBytes: number; sha256: string; signatureStatus: string;
  lastSuccessfulRun: string; lastError: string | null; devCanary: string; canaryIncluded: boolean;
  environment: "DEV"; nextScheduledAt: string;
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
