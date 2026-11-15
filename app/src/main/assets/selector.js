(function () {
  if (window.__wa) return;
  var enabled = false, box = null, sel = null;

  function esc(s) {
    return (window.CSS && CSS.escape) ? CSS.escape(s) : String(s).replace(/([^\w-])/g, '\\$1');
  }
  function uniq(s) {
    try { return document.querySelectorAll(s).length === 1; } catch (e) { return false; }
  }
  function stableToken(v) {
    return !!v && v.length <= 32 && !/\d{4,}/.test(v) && !/^\d/.test(v);
  }
  var ATTRS = ['data-testid', 'data-test', 'data-qa', 'data-id', 'name', 'itemprop', 'aria-label', 'role'];

  function baseNoNth(el) {
    var t = el.tagName.toLowerCase();
    for (var i = 0; i < ATTRS.length; i++) {
      var v = el.getAttribute(ATTRS[i]);
      if (v && v.length < 60) {
        return t + '[' + ATTRS[i] + '="' + v.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"]';
      }
    }
    var cls = [];
    if (el.classList) {
      for (var j = 0; j < el.classList.length && cls.length < 2; j++) {
        if (stableToken(el.classList[j])) cls.push('.' + esc(el.classList[j]));
      }
    }
    return t + cls.join('');
  }

  function part(el) {
    if (el.id && stableToken(el.id)) {
      var s = '#' + esc(el.id);
      if (uniq(s)) return s;
    }
    var base = baseNoNth(el), p = el.parentElement;
    if (p) {
      var same = 0;
      for (var i = 0; i < p.children.length; i++) {
        try { if (p.children[i].matches(base)) same++; } catch (e) {}
      }
      if (same > 1) base += ':nth-child(' + (Array.prototype.indexOf.call(p.children, el) + 1) + ')';
    }
    return base;
  }

  function cssPath(el) {
    var parts = [], cur = el;
    while (cur && cur.nodeType === 1 && cur !== document.documentElement) {
      parts.unshift(part(cur));
      var s = parts.join(' > ');
      if (uniq(s)) return s;
      cur = cur.parentElement;
    }
    return parts.join(' > ');
  }

  function xpath(el) {
    if (el.id && el.id.indexOf('"') < 0 && document.querySelectorAll('[id="' + el.id + '"]').length === 1) {
      return '//*[@id="' + el.id + '"]';
    }
    var parts = [];
    for (; el && el.nodeType === 1; el = el.parentNode) {
      var i = 1;
      for (var s = el.previousElementSibling; s; s = s.previousElementSibling) {
        if (s.tagName === el.tagName) i++;
      }
      parts.unshift(el.tagName.toLowerCase() + '[' + i + ']');
    }
    return '/' + parts.join('/');
  }

  function describe(el) {
    var text = (el.innerText || el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 300);
    var attrs = {};
    if (el.attributes) {
      for (var i = 0; i < el.attributes.length && i < 12; i++) {
        attrs[el.attributes[i].name] = String(el.attributes[i].value).slice(0, 200);
      }
    }
    var t = el.closest ? el.closest('table') : null;
    var gen = (el.parentElement ? cssPath(el.parentElement) + ' > ' : '') + baseNoNth(el);
    var n = 0;
    try { n = document.querySelectorAll(gen).length; } catch (e) {}
    return {
      selector: cssPath(el),
      xpath: xpath(el),
      tag: el.tagName.toLowerCase(),
      text: text,
      href: el.href || '',
      src: el.src || '',
      attrs: attrs,
      table: t ? cssPath(t) : '',
      listSelector: gen,
      listCount: n
    };
  }

  function ensureBox() {
    if (box) return;
    box = document.createElement('div');
    box.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;border:2px solid #FF5722;' +
      'background:rgba(255,87,34,0.18);box-sizing:border-box;display:none;';
    (document.body || document.documentElement).appendChild(box);
  }
  function place(el) {
    ensureBox();
    if (!el || !el.getBoundingClientRect) { box.style.display = 'none'; return; }
    var r = el.getBoundingClientRect();
    box.style.left = r.left + 'px';
    box.style.top = r.top + 'px';
    box.style.width = r.width + 'px';
    box.style.height = r.height + 'px';
    box.style.display = 'block';
  }

  var hovered = null;
  function onTouch(ev) {
    if (!enabled || !ev.touches || !ev.touches.length) return;
    var t = ev.touches[0];
    var el = document.elementFromPoint(t.clientX, t.clientY);
    if (el && el !== box) { hovered = el; place(el); }
  }
  function onMouseOver(ev) {
    if (!enabled) return;
    if (ev.target && ev.target !== box) { hovered = ev.target; place(ev.target); }
  }
  function swallow(ev) {
    if (!enabled) return;
    ev.stopPropagation();
    if (ev.stopImmediatePropagation) ev.stopImmediatePropagation();
  }
  function onClick(ev) {
    if (!enabled) return;
    ev.preventDefault();
    swallow(ev);
    var el = ev.target;
    if (!el || el === box) return;
    sel = el;
    place(el);
  }
  function reposition() { if (enabled && sel) place(sel); }

  ['touchstart', 'touchmove'].forEach(function (n) { window.addEventListener(n, onTouch, true); });
  window.addEventListener('mouseover', onMouseOver, true);
  ['mousedown', 'mouseup', 'pointerdown', 'pointerup'].forEach(function (n) { window.addEventListener(n, swallow, true); });
  window.addEventListener('click', onClick, true);
  window.addEventListener('scroll', reposition, true);
  window.addEventListener('resize', reposition, true);

  window.__wa = {
    enable: function () { enabled = true; ensureBox(); },
    disable: function () { enabled = false; if (box) box.style.display = 'none'; },
    clear: function () { sel = null; if (box) box.style.display = 'none'; },
    current: function () { return sel && sel.isConnected !== false ? describe(sel) : null; }
  };
})();
