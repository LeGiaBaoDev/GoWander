// A photo block for the story editor (ADR-041 §4). The file goes through the same upload as every other photo
// (POST /admin/blocks/image: re-encoded, credit and licence required, ADR-035); the block keeps only the key.
(function () {
  'use strict';

  function csrf() {
    var input = document.querySelector('form input[name="csrf"]');
    return input ? input.value : '';
  }

  function licences() {
    var list = document.getElementById('story-image-licenses');
    if (!list) return ['CC0', 'CC-BY 4.0'];
    return Array.prototype.map.call(list.options, function (o) { return o.value; });
  }

  function upload(file, credit, license, alt, done) {
    var body = new FormData();
    body.append('photo', file);
    body.append('credit', credit);
    body.append('license', license);
    body.append('alt', alt);
    fetch('/admin/blocks/image', { method: 'POST', body: body, headers: { 'X-CSRF-Token': csrf() } })
      .then(function (response) { return response.json().then(function (json) { return { ok: response.ok, json: json }; }); })
      .then(function (r) { done(r.ok ? null : (r.json.error || 'Chưa tải được ảnh'), r.json); })
      .catch(function () { done('Không gửi được ảnh, kiểm tra kết nối', null); });
  }

  function StoryImage(data) {
    this.data = data && data.data ? data.data : (data || {});
  }

  StoryImage.prototype.render = function () {
    var wrap = document.createElement('div');
    if (this.data.key) {
      var img = document.createElement('img');
      img.src = this.data.url || ('/media/' + this.data.key + '-1600.jpg');
      img.alt = this.data.alt || '';
      img.style.maxWidth = '100%';
      img.style.borderRadius = '10px';
      var caption = document.createElement('div');
      caption.className = 'muted small';
      caption.textContent = [this.data.credit, this.data.license].filter(Boolean).join(' · ');
      wrap.appendChild(img);
      wrap.appendChild(caption);
      return wrap;
    }

    var box = document.createElement('div');
    box.className = 'story-image-form';
    var file = document.createElement('input');
    file.type = 'file';
    file.accept = 'image/jpeg,image/png,image/webp';
    var credit = document.createElement('input');
    credit.type = 'text';
    credit.placeholder = 'Ghi công: ai chụp hoặc nguồn';
    credit.maxLength = 200;
    var license = document.createElement('select');
    licences().forEach(function (value) {
      var option = document.createElement('option');
      option.value = value;
      option.textContent = value;
      license.appendChild(option);
    });
    var alt = document.createElement('input');
    alt.type = 'text';
    alt.placeholder = 'Mô tả ảnh (không bắt buộc)';
    alt.maxLength = 300;
    var button = document.createElement('button');
    button.type = 'button';
    button.textContent = 'Tải ảnh lên';
    var message = document.createElement('p');
    message.className = 'small';
    var self = this;
    button.onclick = function () {
      if (!file.files || !file.files[0]) { message.textContent = 'Chọn một ảnh trước.'; return; }
      if (!credit.value.trim()) { message.textContent = 'Ảnh phải có ghi công.'; return; }
      button.disabled = true;
      message.textContent = 'Đang tải…';
      upload(file.files[0], credit.value.trim(), license.value, alt.value.trim(), function (problem, json) {
        button.disabled = false;
        if (problem) { message.textContent = problem; return; }
        self.data = { key: json.key, url: json.url, credit: json.credit, license: json.license, alt: json.alt };
        wrap.innerHTML = '';
        wrap.appendChild(self.render());
      });
    };
    [file, credit, license, alt, button, message].forEach(function (el) { box.appendChild(el); });
    return box;
  };

  StoryImage.prototype.save = function () {
    return {
      type: 'image',
      data: this.data.key
        ? { key: this.data.key, alt: this.data.alt || '', credit: this.data.credit || '', license: this.data.license || '' }
        : {}
    };
  };

  StoryImage.get toolbox = function () {
    return {
      title: 'Ảnh',
      icon: '<svg width="17" height="15" viewBox="0 0 17 15" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M1 3.5h3l1-2h7l1 2h3v10.5H1z"/><circle cx="8.5" cy="8" r="2.5"/></svg>'
    };
  };

  window.StoryImage = StoryImage;
})();
