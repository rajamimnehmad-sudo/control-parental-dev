import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import vm from "node:vm";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const testRoot = dirname(fileURLToPath(import.meta.url));
const asset = join(testRoot, "../../main/assets/dag-protection/video-seek-state.js");

const runtime = async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readFile(asset, "utf8"), context, { filename: asset });
  return context.__gloshDagVideoSeekState;
};

const snapshot = (currentTime = 10) => ({
  sourceSignature: "source_a",
  viewportSignature: "viewport_a",
  currentTime,
});

test("seek reopens only after exact revoke, seeked and stable destination", async () => {
  const { create, Phase } = await runtime();
  const state = create();

  assert.equal(state.begin(snapshot()), Phase.Closing);
  assert.equal(state.acknowledgeRevocation(true), Phase.WaitingSeeked);
  assert.equal(state.observeSeeked(snapshot(25)), Phase.Stabilizing);
  assert.equal(state.settle(snapshot(25.03)), Phase.Ready);
  assert.deepEqual(
    JSON.parse(JSON.stringify(state.takeRearm())),
    snapshot(25.03),
  );
  assert.equal(state.phase(), Phase.Complete);
});

test("seeked may arrive before native revoke without reopening early", async () => {
  const { create, Phase } = await runtime();
  const state = create();

  assert.equal(state.begin(snapshot()), Phase.Closing);
  assert.equal(state.observeSeeked(snapshot(5)), Phase.Closing);
  assert.equal(state.acknowledgeRevocation(true), Phase.Stabilizing);
  assert.equal(state.phase(), Phase.Stabilizing);
  assert.equal(state.settle(snapshot(5.02)), Phase.Ready);
  assert.deepEqual(
    JSON.parse(JSON.stringify(state.takeRearm())),
    snapshot(5.02),
  );
  assert.equal(state.phase(), Phase.Complete);
});

test("repeated, changed, ambiguous and timed out seeks fail closed", async () => {
  const { create, Phase } = await runtime();

  const repeated = create();
  repeated.begin(snapshot());
  assert.equal(repeated.begin(snapshot(20)), Phase.Terminal);
  assert.equal(repeated.reason(), "seek_repeated");

  const changed = create();
  changed.begin(snapshot());
  changed.acknowledgeRevocation(true);
  assert.equal(
    changed.observeSeeked({ ...snapshot(20), sourceSignature: "source_b" }),
    Phase.Terminal,
  );

  const ambiguous = create();
  ambiguous.begin(snapshot());
  assert.equal(ambiguous.acknowledgeRevocation(false), Phase.Terminal);

  const timedOut = create();
  timedOut.begin(snapshot());
  assert.equal(timedOut.fail("seek_timeout"), Phase.Terminal);
  assert.equal(timedOut.reason(), "seek_timeout");
});
