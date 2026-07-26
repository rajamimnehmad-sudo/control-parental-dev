import { assert, assertEquals } from "jsr:@std/assert@1.0.15";
import {
  CollectorVersion,
  completeRegisteredSample,
  containsForbiddenPayloadFields,
  isStorageAlreadyExists,
  isTrainingExample,
  jpegDimensions,
  parseFields,
  PolicyVersion,
  sanitizeHost,
  sha256Hex,
  storagePath,
} from "./logic.ts";

function validForm(): FormData {
  const form = new FormData();
  form.set("device_id", "84a65e5b-cd25-4a0f-8bc9-7cd4cdbef82e");
  form.set("content_sha256", "a".repeat(64));
  form.set("perceptual_hash", "0123456789abcdef");
  form.set("width", "640");
  form.set("height", "480");
  form.set("mime_type", "image/jpeg");
  form.set("size_bytes", "1234");
  form.set("source_kind", "rasterimage");
  form.set("source_host", "images.example.com");
  form.set("document_host", "shop.example.com");
  form.set("source_url_hash", "b".repeat(64));
  form.set("review_decision", "unsure");
  form.set("policy_version", PolicyVersion);
  form.set("collector_version", CollectorVersion);
  return form;
}

Deno.test("valid metadata is accepted without personal payload", () => {
  const form = validForm();
  assert(parseFields(form));
  assertEquals(containsForbiddenPayloadFields(form), false);
});

Deno.test("invalid authorization identifiers and decisions fail validation", () => {
  const form = validForm();
  form.set("device_id", "not-a-device");
  assertEquals(parseFields(form), null);
  form.set("device_id", "84a65e5b-cd25-4a0f-8bc9-7cd4cdbef82e");
  form.set("review_decision", "allow");
  assertEquals(parseFields(form), null);
});

Deno.test("size and mime limits are closed", () => {
  const oversized = validForm();
  oversized.set("size_bytes", String(512 * 1024 + 1));
  assertEquals(parseFields(oversized), null);
  const html = validForm();
  html.set("mime_type", "text/html");
  assertEquals(parseFields(html), null);
});

Deno.test("forbidden private fields are rejected", () => {
  for (
    const field of [
      "url",
      "query",
      "text",
      "cookies",
      "referer",
      "headers",
      "model_version",
      "thresholds",
    ]
  ) {
    const form = validForm();
    form.set(field, "private");
    assert(containsForbiddenPayloadFields(form));
  }
});

Deno.test("unsure is never a positive or negative training example", () => {
  assert(isTrainingExample("show"));
  assert(isTrainingExample("hide"));
  assertEquals(isTrainingExample("unsure"), false);
});

Deno.test("hosts are sanitized and invalid hosts fail closed", () => {
  assertEquals(sanitizeHost("WWW.Example.COM."), "example.com");
  assertEquals(sanitizeHost("https://example.com?q=private"), "");
});

Deno.test("hash derived storage has no user supplied path", () => {
  const hash = "a".repeat(64);
  assertEquals(storagePath(hash), `samples/aa/${hash}.jpg`);
});

Deno.test("SHA-256 is recalculated", async () => {
  assertEquals(
    await sha256Hex(new TextEncoder().encode("dag-v2")),
    "72a8b68021d2b6f660852e00b7b9e79ce057007ae38b3bf0ce47498b7bec91b4",
  );
});

Deno.test("JPEG dimensions come from bytes and HTML disguise is rejected", () => {
  const jpeg = new Uint8Array([
    0xff,
    0xd8,
    0xff,
    0xc0,
    0x00,
    0x11,
    0x08,
    0x01,
    0xe0,
    0x02,
    0x80,
    0x03,
    0x01,
    0x11,
    0x00,
    0x02,
    0x11,
    0x00,
    0x03,
    0x11,
    0x00,
    0xff,
    0xd9,
  ]);
  assertEquals(jpegDimensions(jpeg), { width: 640, height: 480 });
  assertEquals(
    jpegDimensions(new TextEncoder().encode("<html>not an image</html>")),
    null,
  );
});

Deno.test("new and resumed rows upload and become ready", async () => {
  for (const matchKind of ["new", "retry_rejected", "resume_pending"]) {
    let uploads = 0;
    let readyCalls = 0;
    const result = await completeRegisteredSample(
      matchKind,
      () => {
        uploads += 1;
        return Promise.resolve(null);
      },
      () => {
        readyCalls += 1;
        return Promise.resolve(true);
      },
    );

    assertEquals(result, { accepted: true, deduplicated: false });
    assertEquals(uploads, 1);
    assertEquals(readyCalls, 1);
  }
});

Deno.test("ready exact and perceptual matches never create another object", async () => {
  for (const matchKind of ["exact_ready", "perceptual"]) {
    let callbacks = 0;
    const result = await completeRegisteredSample(
      matchKind,
      () => {
        callbacks += 1;
        return Promise.resolve(null);
      },
      () => {
        callbacks += 1;
        return Promise.resolve(true);
      },
    );

    assertEquals(result, { accepted: true, deduplicated: true });
    assertEquals(callbacks, 0);
  }
});

Deno.test("pending row with existing object tolerates storage conflict", async () => {
  assert(
    isStorageAlreadyExists({ statusCode: 409, code: "ResourceAlreadyExists" }),
  );
  const result = await completeRegisteredSample(
    "resume_pending",
    () => Promise.resolve({ statusCode: 409, code: "ResourceAlreadyExists" }),
    () => Promise.resolve(true),
  );

  assertEquals(result, { accepted: true, deduplicated: false });
});

Deno.test("failed ready mark is repaired by the next resumed submission", async () => {
  let readyAttempts = 0;
  const upload = () =>
    Promise.resolve({ statusCode: 409, code: "ResourceAlreadyExists" });
  const first = await completeRegisteredSample(
    "resume_pending",
    upload,
    () => {
      readyAttempts += 1;
      return Promise.resolve(false);
    },
  );
  const second = await completeRegisteredSample(
    "resume_pending",
    upload,
    () => {
      readyAttempts += 1;
      return Promise.resolve(true);
    },
  );

  assertEquals(first, { accepted: false, stage: "ready" });
  assertEquals(second, { accepted: true, deduplicated: false });
  assertEquals(readyAttempts, 2);
});

Deno.test("temporary storage failure leaves registration resumable", async () => {
  let readyCalls = 0;
  const result = await completeRegisteredSample(
    "resume_pending",
    () => Promise.resolve({ statusCode: 503, code: "InternalError" }),
    () => {
      readyCalls += 1;
      return Promise.resolve(true);
    },
  );

  assertEquals(result, { accepted: false, stage: "storage" });
  assertEquals(readyCalls, 0);
});

Deno.test("unknown match kinds fail closed without storage or activation side effects", async () => {
  let callbacks = 0;
  const result = await completeRegisteredSample(
    "model_approved",
    () => {
      callbacks += 1;
      return Promise.resolve(null);
    },
    () => {
      callbacks += 1;
      return Promise.resolve(true);
    },
  );

  assertEquals(result, { accepted: false, stage: "registration" });
  assertEquals(callbacks, 0);
});
