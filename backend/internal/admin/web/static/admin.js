// GoWander Admin: tiny behaviours that need a script (CSP allows only same-origin files, no inline code).
// A select with data-go navigates to the chosen option's URL (the pager's page list).
document.addEventListener("change", (e) => {
  const s = e.target;
  if (s instanceof HTMLSelectElement && s.dataset.go !== undefined && s.value) location.assign(s.value);
  if (s instanceof HTMLSelectElement && s.dataset.submit !== undefined) s.form?.requestSubmit();
});

// Small screens: ☰ opens the menu under the bar.
document.addEventListener("click", (e) => {
  const t = e.target instanceof Element ? e.target.closest(".menu-toggle") : null;
  if (!t) return;
  const side = t.closest(".side");
  const open = side.classList.toggle("nav-open");
  t.setAttribute("aria-expanded", String(open));
  t.setAttribute("aria-label", open ? "Đóng menu" : "Mở menu");
});

// The bell and the account menu close on a click outside them or on Escape.
document.addEventListener("click", (e) => {
  document.querySelectorAll("details.bell[open], details.me-pop[open]").forEach((d) => { if (!d.contains(e.target)) d.open = false; });
});
document.addEventListener("keydown", (e) => {
  if (e.key !== "Escape") return;
  document.querySelectorAll("details.bell[open], details.me-pop[open]").forEach((d) => { d.open = false; d.querySelector("summary")?.focus(); });
  const side = document.querySelector(".side.nav-open");
  if (side) { side.classList.remove("nav-open"); side.querySelector(".menu-toggle")?.setAttribute("aria-expanded", "false"); }
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
