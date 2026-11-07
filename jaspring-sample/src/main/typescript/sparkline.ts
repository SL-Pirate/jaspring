// Client hook for <canvas data-jsp-hook="sparkline">: draws the points the server renders into
// data-jsp-hook-params, reports clicks as a custom event, and answers measurement calls.
function draw(el: HTMLCanvasElement, points: number[]): void {
  const context = el.getContext("2d");
  if (!context) return;
  const max = Math.max(...points, 1);
  const step = el.width / points.length;
  context.clearRect(0, 0, el.width, el.height);
  context.fillStyle = "#111827";
  points.forEach((value, index) => {
    const height = (value / max) * (el.height - 8);
    context.fillRect(index * step + 2, el.height - height, step - 4, height);
  });
}

jaspring.hooks.register<HTMLCanvasElement, number[]>("sparkline", {
  mounted(el, ctx) {
    draw(el, ctx.params);
    el.addEventListener("click", (event) => {
      const points = ctx.params;
      const index = Math.max(
        0,
        Math.min(
          points.length - 1,
          Math.floor(event.offsetX / (el.width / points.length)),
        ),
      );
      ctx.pushEvent("point-picked", { index });
    });
  },
  updated(el, ctx) {
    draw(el, ctx.params);
  },
  size(el) {
    return { width: el.width, height: el.height };
  },
});

jaspring.calls.register("viewport", () => ({
  width: window.innerWidth,
  height: window.innerHeight,
}));
