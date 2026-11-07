// Chart.js owns the canvas; Jaspring supplies its data and receives point selections.
import Chart from "chart.js/auto";
import { nanoid } from "nanoid";

const charts = new WeakMap<HTMLCanvasElement, Chart<"bar", number[], string>>();

jaspring.hooks.register<HTMLCanvasElement, number[]>("sparkline", {
  mounted(el, ctx) {
    const chart = new Chart<"bar", number[], string>(el, {
      type: "bar",
      data: {
        labels: ctx.params.map((_, index) => `Point ${index + 1}`),
        datasets: [{
          label: "Value",
          data: [...ctx.params],
          backgroundColor: "#111827",
          borderRadius: 3,
        }],
      },
      options: {
        responsive: false,
        animation: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { display: false },
          y: { display: false, beginAtZero: true },
        },
        onClick(_event, elements) {
          const point = elements[0];
          if (point) ctx.pushEvent("point-picked", { index: point.index });
        },
      },
    });
    charts.set(el, chart);

    const chartId = nanoid();
    el.dataset.chartId = chartId;
    const chartIdOutput = el.parentElement?.querySelector("[data-chart-id-output]");
    if (chartIdOutput) chartIdOutput.textContent = chartId;
  },
  updated(el, ctx) {
    const chart = charts.get(el);
    if (!chart) return;
    chart.data.labels = ctx.params.map((_, index) => `Point ${index + 1}`);
    chart.data.datasets[0].data = [...ctx.params];
    chart.update();
  },
  destroyed(el) {
    charts.get(el)?.destroy();
    charts.delete(el);
  },
  size(el) {
    const { width, height } = el.getBoundingClientRect();
    return { width, height };
  },
});

jaspring.calls.register("viewport", () => ({
  width: window.innerWidth,
  height: window.innerHeight,
}));
