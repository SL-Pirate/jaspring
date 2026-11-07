/* prebuilt */
jaspring.hooks.register("hello", {
  mounted(el, ctx) {
    el.textContent = ctx.params;
  },
});
export {};
