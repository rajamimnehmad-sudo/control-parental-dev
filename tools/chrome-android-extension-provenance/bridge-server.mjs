import { createServer } from "node:http";
import { appendFileSync, readFileSync, statSync } from "node:fs";
import { resolve } from "node:path";

const host = "127.0.0.1";
const port = Number(process.env.GLOSH_EXTENSION_BRIDGE_PORT ?? "8765");
const extensionId = process.env.GLOSH_EXTENSION_ID;
const nonce = process.env.GLOSH_EXTENSION_SESSION_NONCE;
const artifactDirectory = resolve(process.env.GLOSH_EXTENSION_ARTIFACT_DIR ?? ".");
const eventLog = resolve(process.env.GLOSH_EXTENSION_EVENT_LOG ?? "./extension-events.jsonl");
const accessLog = resolve(process.env.GLOSH_EXTENSION_ACCESS_LOG ?? "./extension-access.jsonl");
const expectedOrigin = `chrome-extension://${extensionId}`;

if (!/^[a-p]{32}$/.test(extensionId ?? "")) throw new Error("GLOSH_EXTENSION_ID is required");
if ((nonce ?? "").length < 32) throw new Error("GLOSH_EXTENSION_SESSION_NONCE must have at least 32 characters");
if (!Number.isInteger(port) || port < 1024 || port > 65_535) throw new Error("invalid bridge port");

function send(response, status, contentType, body) {
  response.writeHead(status, { "Content-Type": contentType, "Cache-Control": "no-store" });
  response.end(body);
}

function serveFile(response, name, contentType) {
  const file = resolve(artifactDirectory, name);
  const bytes = readFileSync(file);
  response.writeHead(200, {
    "Content-Type": contentType,
    "Content-Length": statSync(file).size,
    "Cache-Control": "no-store",
  });
  response.end(bytes);
}

function extensionOrigin(request) {
  return request.headers.origin === expectedOrigin;
}

const server = createServer((request, response) => {
  const url = new URL(request.url, `http://${host}:${port}`);
  response.once("finish", () => {
    const record = {
      timestamp: new Date().toISOString(),
      method: request.method ?? null,
      path: url.pathname,
      userAgent: request.headers["user-agent"] ?? null,
      remoteAddress: request.socket.remoteAddress ?? null,
      status: response.statusCode,
    };
    appendFileSync(accessLog, `${JSON.stringify(record)}\n`, { encoding: "utf8", mode: 0o600 });
    process.stdout.write(`access=${JSON.stringify(record)}\n`);
  });
  if (request.method === "GET" && url.pathname === "/health") return send(response, 200, "text/plain", "ready\n");
  if (request.method === "GET" && url.pathname === "/update.xml") return serveFile(response, "update.xml", "application/xml");
  if (request.method === "GET" && url.pathname === "/extension.crx") {
    return serveFile(response, "extension.crx", "application/x-chrome-extension");
  }
  if (request.method === "GET" && url.pathname === "/session") {
    if (!extensionOrigin(request)) return send(response, 403, "text/plain", "origin rejected\n");
    return send(response, 200, "application/json", JSON.stringify({ nonce }));
  }
  if (request.method === "POST" && url.pathname === "/events") {
    if (!extensionOrigin(request) || request.headers["x-glosh-lab-session"] !== nonce) {
      return send(response, 403, "text/plain", "authority rejected\n");
    }
    let size = 0;
    const chunks = [];
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size <= 65_536) chunks.push(chunk);
    });
    request.on("end", () => {
      if (size > 65_536) return send(response, 413, "text/plain", "too large\n");
      try {
        const event = JSON.parse(Buffer.concat(chunks).toString("utf8"));
        const serialized = JSON.stringify({ receivedAt: Date.now(), origin: request.headers.origin, event });
        if (/data:image|;base64,/i.test(serialized)) return send(response, 422, "text/plain", "capture payload rejected\n");
        appendFileSync(eventLog, `${serialized}\n`, { encoding: "utf8", mode: 0o600 });
        return send(response, 202, "application/json", "{\"accepted\":true}");
      } catch (_) {
        return send(response, 400, "text/plain", "invalid json\n");
      }
    });
    return;
  }
  send(response, 404, "text/plain", "not found\n");
});

server.listen(port, host, () => {
  process.stdout.write(
    `bridge=ready host=${host} port=${port} extensionId=${extensionId} origin=${expectedOrigin} accessLog=${accessLog}\n`,
  );
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
