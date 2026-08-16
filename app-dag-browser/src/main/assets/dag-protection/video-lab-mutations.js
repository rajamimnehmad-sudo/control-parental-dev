"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabMutations !== undefined) return;

  const touchesActiveVideo = (mutation, record, SourceElement) => {
    if (mutation.target === record.video) return true;
    if (mutation.target instanceof SourceElement && mutation.target.parentElement === record.video) {
      return true;
    }
    for (const node of [...mutation.addedNodes, ...mutation.removedNodes]) {
      if (node === record.video) return true;
      if (node instanceof SourceElement && node.parentElement === record.video) return true;
    }
    return false;
  };

  const consumeExpectedPresentation = (mutation, record, capabilityAttributes) => {
    if (mutation.type !== "attributes" || mutation.target !== record.video) return false;
    const attribute = mutation.attributeName?.toLowerCase();
    if (!capabilityAttributes.has(attribute)) return false;
    const expectedIndex = record.expectedPresentationMutations.findIndex((expected) =>
      expected.attribute === attribute && expected.oldValue === mutation.oldValue);
    if (expectedIndex < 0) return false;
    record.expectedPresentationMutations.splice(expectedIndex, 1);
    return true;
  };

  const requiresTerminalClose = (mutation, record, SourceElement, capabilityAttributes) =>
    touchesActiveVideo(mutation, record, SourceElement) &&
    !consumeExpectedPresentation(mutation, record, capabilityAttributes);

  const diagnosticStages = (
    mutation,
    record,
    SourceElement,
    capabilityAttributes,
    sourceIdentity,
  ) => {
    const stages = [];
    const attribute = mutation.attributeName?.toLowerCase();
    if (mutation.type === "attributes" && mutation.target === record.video) {
      if (attribute === "src") {
        stages.push("mutation_video_attribute_src");
      } else if (capabilityAttributes.has(attribute)) {
        stages.push("mutation_video_attribute_capability", `mutation_capability_${attribute}`);
      } else {
        stages.push("mutation_video_attribute_other");
      }
    } else if (mutation.type === "attributes" && mutation.target instanceof SourceElement) {
      stages.push(attribute === "src" ? "mutation_source_attribute_src" : "mutation_source_attribute_type");
    } else if (mutation.type === "childList" && mutation.target === record.video) {
      const addedSource = [...mutation.addedNodes].some((node) => node instanceof SourceElement);
      const removedSource = [...mutation.removedNodes].some((node) => node instanceof SourceElement);
      stages.push(
        addedSource && removedSource
          ? "mutation_source_replaced"
          : addedSource
            ? "mutation_source_added"
            : removedSource
              ? "mutation_source_removed"
              : "mutation_video_children_other",
      );
    } else {
      const videoAdded = [...mutation.addedNodes].includes(record.video);
      const videoRemoved = [...mutation.removedNodes].includes(record.video);
      stages.push(videoRemoved ? "mutation_video_removed" : videoAdded ? "mutation_video_added" : "mutation_other");
    }

    const before = record.sourceIdentity;
    const after = sourceIdentity(record.video);
    if (before === null) {
      stages.push("mutation_source_identity_unknown");
      return stages;
    }
    if (before.currentSrc !== after.currentSrc) stages.push("mutation_current_src_changed");
    if (before.srcAttribute !== after.srcAttribute) stages.push("mutation_src_attribute_changed");
    if (before.hasSrcObject !== after.hasSrcObject) stages.push("mutation_src_object_changed");
    if (before.sourceChildren !== after.sourceChildren) stages.push("mutation_source_children_changed");
    if (
      before.currentSrc === after.currentSrc &&
      before.srcAttribute === after.srcAttribute &&
      before.hasSrcObject === after.hasSrcObject &&
      before.sourceChildren === after.sourceChildren
    ) stages.push("mutation_source_identity_stable");
    return stages;
  };

  globalThis.__gloshDagVideoLabMutations = Object.freeze({
    consumeExpectedPresentation,
    diagnosticStages,
    requiresTerminalClose,
    touchesActiveVideo,
  });
})();
