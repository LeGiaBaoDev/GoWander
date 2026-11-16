// GoWander Admin: tiny behaviours that need a script (CSP allows only same-origin files, no inline code).
// A select with data-go navigates to the chosen option's URL (the pager's page list).
document.addEventListener("change", (e) => {
  const s = e.target;
  if (s instanceof HTMLSelectElement && s.dataset.go !== undefined && s.value) location.assign(s.value);
});
