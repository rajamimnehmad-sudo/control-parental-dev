import assert from "node:assert/strict";
import { authorityKey, clipFromSnapshot, isCurrentAuthority } from "./extension/geometry.js";

const clip = clipFromSnapshot(
  { pageRect: { x: 20, y: 40, width: 320, height: 180 } },
  { cssVisualViewport: { pageX: 10, pageY: 30 } },
);
assert.deepEqual(clip, { x: 20, y: 40, width: 320, height: 180, scale: 1 });

const current = {
  protectionSession: "session",
  tabId: 4,
  documentId: "document-2",
  frameId: 0,
  navigationSequence: 1,
  viewportIdentity: "1080x2200@1",
  elementIdentity: "id:inline-svg",
  captureSequence: 7,
};
assert.equal(isCurrentAuthority(current, { ...current }), true);
for (const field of ["documentId", "navigationSequence", "viewportIdentity", "captureSequence"]) {
  assert.equal(isCurrentAuthority(current, { ...current, [field]: `${current[field]}-stale` }), false, field);
}
assert.equal(authorityKey(current).includes("document-2"), true);
process.stdout.write("geometry_and_stale_contract=PASS\n");
