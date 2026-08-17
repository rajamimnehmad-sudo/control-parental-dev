"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabConfiguration !== undefined) return;

  const parse = (message) => Object.freeze({
    version: message?.version,
    diagnostics: message?.diagnostics === true,
    enabled: message?.enabled === true,
    fixture: message?.fixture === true,
  });

  globalThis.__gloshDagVideoLabConfiguration = Object.freeze({ parse });
})();
