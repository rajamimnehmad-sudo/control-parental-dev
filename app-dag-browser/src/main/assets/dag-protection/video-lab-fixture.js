"use strict";

(() => {
  const video = document.getElementById("fixture-video");
  const canvas = document.getElementById("fixture-source");
  const context = canvas.getContext("2d", { alpha: false });
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
  void video.play().catch(() => {});
  setInterval(drawFrame, 250);
})();
