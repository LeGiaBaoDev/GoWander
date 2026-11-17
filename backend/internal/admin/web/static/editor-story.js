// Editor.js for the story form (ADR-041). Only text tools: paragraph, heading, list, quote and a divider. Images in the
// body are a later step; the story's cover image stays the separate upload below the form.
// The plain textarea stays on the page as the no-JavaScript fallback and is hidden once the editor works.
(function () {
  'use strict';
  var host = document.getElementById('story-editor');
  var body = document.getElementById('story-body');
  var hidden = document.getElementById('story-blocks');
  if (!host || !hidden || !window.EditorJS || !window.Header || !window.List || !window.Quote || !window.Delimiter) return;

  var initial = {};
  try {
    initial = JSON.parse(hidden.value || '{}');
  } catch (e) {
    initial = {};
  }

  var editor = new window.EditorJS({
    holder: host,
    placeholder: 'Viết câu chuyện: mở bài, các mục, điều nên thử…',
    data: initial.blocks ? { blocks: initial.blocks } : undefined,
    tools: {
      header: { class: window.Header, levels: [2, 3], defaultLevel: 2, inlineToolbar: true },
      list: { class: window.List, inlineToolbar: true, config: { defaultStyle: 'unordered' } },
      quote: { class: window.Quote, inlineToolbar: true },
      delimiter: window.Delimiter,
      image: window.StoryImage ? { class: window.StoryImage } : undefined
    },
    onChange: save
  });

  // An image block with no uploaded photo yet carries no data; the server would refuse it, so it is left out.
  function blocks(out) {
    return (out.blocks || []).filter(function (b) {
      return !(b.type === 'image' && !(b.data && b.data.key));
    });
  }

  function save() {
    editor.save().then(function (out) {
      hidden.value = JSON.stringify({ blocks: blocks(out) });
    });
  }

  if (body) body.hidden = true;

  var form = host.closest('form');
  if (form) {
    form.addEventListener('submit', function (event) {
      if (form.dataset.editorSaved === '1') return; // second pass, let it through
      event.preventDefault();
      editor.save().then(function (out) {
        hidden.value = JSON.stringify({ blocks: blocks(out) });
        form.dataset.editorSaved = '1';
        // Bypass this listener without losing the browser's own submit handling.
        HTMLFormElement.prototype.submit.call(form);
      });
    });
  }
})();
