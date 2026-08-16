"use strict";

(() => {
  if (globalThis.__gloshDagInstallVideoFixture !== undefined) return;

  globalThis.__gloshDagInstallVideoFixture = () => {
    if (document.documentElement.hasAttribute("data-glosh-dag-video-lab-fixture")) return;
    document.documentElement.setAttribute("data-glosh-dag-video-lab-fixture", "true");
    document.documentElement.style.cssText = "margin:0;min-height:100%;display:block";
    document.body.style.cssText = "margin:0;min-height:100%;display:block";
    document.body.innerHTML = `
      <main style="min-height:120vh;padding:24px 16px 240px;box-sizing:border-box">
        <h1>DAG Video Lab</h1>
        <p>El video debe permanecer detrás de la cobertura nativa.</p>
        <video id="fixture-video" muted playsinline
          style="position:fixed;left:6vw;top:280px;width:88vw;height:66vw;max-width:480px;max-height:360px;background:#000"></video>
        <canvas id="fixture-source" width="640" height="480"
          style="position:fixed;left:-10000px;top:-10000px"></canvas>
      </main>`;
    const video = document.getElementById("fixture-video");
    const canvas = document.getElementById("fixture-source");
    const context = canvas?.getContext("2d", { alpha: false });
    if (!(video instanceof HTMLVideoElement) || context === null) return;

    let frame = 0;
    const drawFrame = () => {
    const halfWidth = canvas.width / 2;
    const halfHeight = canvas.height / 2;
    context.fillStyle = "#ef2020";
    context.fillRect(0, 0, halfWidth, halfHeight);
    context.fillStyle = "#20cf40";
    context.fillRect(halfWidth, 0, halfWidth, halfHeight);
    context.fillStyle = "#204fef";
    context.fillRect(0, halfHeight, halfWidth, halfHeight);
    context.fillStyle = "#f5f5f5";
    context.fillRect(halfWidth, halfHeight, halfWidth, halfHeight);
    context.fillStyle = frame % 2 === 0 ? "#000000" : "#ffffff";
    context.fillRect(canvas.width / 2 - 20, canvas.height / 2 - 20, 40, 40);
    frame += 1;
    };

    drawFrame();
    const stream = canvas.captureStream(4);
    video.srcObject = stream;
    video.muted = true;
    setInterval(drawFrame, 250);
  };
})();
