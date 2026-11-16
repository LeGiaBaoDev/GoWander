// GoWander Admin: tiny behaviours that need a script (CSP allows only same-origin files, no inline code).
// A select with data-go navigates to the chosen option's URL (the pager's page list).
document.addEventListener("change", (e) => {
  const s = e.target;
  if (s instanceof HTMLSelectElement && s.dataset.go !== undefined && s.value) location.assign(s.value);
});

// Every password field gets an eye button to show or hide what was typed (owner, 16-11-2026). The field goes back to
// hidden when its form is sent, so the browser never stores it as plain text.
(() => {
  const NS = "http://www.w3.org/2000/svg";
  const eye = (open) => {
    const svg = document.createElementNS(NS, "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("width", "20");
    svg.setAttribute("height", "20");
    svg.setAttribute("aria-hidden", "true");
    const add = (tag, attrs) => {
      const el = document.createElementNS(NS, tag);
      for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
      svg.appendChild(el);
    };
    add("path", { d: "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" });
    add("circle", { cx: "12", cy: "12", r: "3" });
    if (!open) add("path", { d: "M4 4l16 16" });
    return svg;
  };
  const setup = (input) => {
    if (input.dataset.eye) return;
    input.dataset.eye = "1";
    const wrap = document.createElement("span");
    wrap.className = "pw";
    input.parentNode.insertBefore(wrap, input);
    wrap.appendChild(input);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "pw-toggle";
    const show = (visible) => {
      input.type = visible ? "text" : "password";
      btn.setAttribute("aria-pressed", String(visible));
      btn.setAttribute("aria-label", visible ? "Ẩn mật khẩu" : "Hiện mật khẩu");
      btn.title = btn.getAttribute("aria-label");
      btn.replaceChildren(eye(visible)); // the icon shows the current state: open eye = password visible
    };
    btn.addEventListener("click", () => { show(input.type === "password"); input.focus(); });
    input.form?.addEventListener("submit", () => show(false));
    show(false);
    wrap.appendChild(btn);
  };
  const run = () => document.querySelectorAll('input[type="password"]').forEach(setup);
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", run); else run();
})();
