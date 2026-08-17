"use strict";

(() => {
  if (globalThis.__gloshDagVideoBlockQuarantine !== undefined) return;

  const create = (sourceSignature) => {
    const generations = new WeakMap();
    const blocked = new WeakMap();

    const generationFor = (video) => generations.get(video) ?? 0;

    const noteGeneration = (video) => {
      generations.set(video, generationFor(video) + 1);
    };

    const block = (record) => {
      blocked.set(record.video, Object.freeze({
        generation: generationFor(record.video),
        sourceSignature: record.sourceSignature,
      }));
    };

    const allows = (video) => {
      const quarantine = blocked.get(video);
      if (quarantine === undefined) return true;
      const changed =
        generationFor(video) > quarantine.generation ||
        sourceSignature(video) !== quarantine.sourceSignature;
      if (changed) blocked.delete(video);
      return changed;
    };

    return Object.freeze({ allows, block, noteGeneration });
  };

  globalThis.__gloshDagVideoBlockQuarantine = Object.freeze({ create });
})();
