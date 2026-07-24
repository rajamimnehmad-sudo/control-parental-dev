package com.contentfilter.user.dag2

internal val DagV2DocumentStartScript =
    """
    (function() {
      if (window.__dag2RuntimeInstalled === true) return;
      window.__dag2RuntimeInstalled = true;

      function dag2InstallHeadGuards() {
        if (!document.head) return false;
        if (!document.getElementById('__dag2_csp')) {
          var csp = document.createElement('meta');
          csp.id = '__dag2_csp';
          csp.httpEquiv = 'Content-Security-Policy';
          csp.content = "img-src https:; media-src 'none'; object-src 'none'; frame-src https://www.google.com/recaptcha/ https://www.recaptcha.net/recaptcha/ https://challenges.cloudflare.com/cdn-cgi/challenge-platform/ https://newassets.hcaptcha.com/captcha/";
          document.head.prepend(csp);
        }
        if (!document.getElementById('__dag2_safety')) {
          var safety = document.createElement('style');
          safety.id = '__dag2_safety';
          safety.textContent =
            'img{visibility:visible!important;object-fit:none!important;object-position:999999px 999999px!important;background:#E9EDF2!important;color:transparent!important;}' +
            'video,audio,canvas,object,embed,svg{display:none!important;}' +
            'iframe:not([data-dag2-safe-frame="true"]){display:none!important;}' +
            '[style*="url(data:" i],[style*="url(blob:" i]{background-image:none!important;}';
          document.head.appendChild(safety);
        }
        return true;
      }

      if (!dag2InstallHeadGuards()) {
        var headObserver = new MutationObserver(function() {
          if (dag2InstallHeadGuards()) headObserver.disconnect();
        });
        headObserver.observe(document, {childList:true, subtree:true});
      }

      function dag2HttpsVisualSource(node) {
        var source = (node && (node.currentSrc || node.getAttribute('src'))) || '';
        try {
          return new URL(source, document.baseURI).protocol === 'https:';
        } catch (_) {
          return false;
        }
      }

      function dag2AuthorizedFrame(node) {
        var source = (node && node.getAttribute('src')) || '';
        try {
          var url = new URL(source, document.baseURI);
          if (url.protocol !== 'https:') return false;
          return ((url.hostname === 'www.google.com' || url.hostname === 'www.recaptcha.net') &&
                    url.pathname.indexOf('/recaptcha/') === 0) ||
                 (url.hostname === 'challenges.cloudflare.com' &&
                    url.pathname.indexOf('/cdn-cgi/challenge-platform/') === 0) ||
                 (url.hostname === 'newassets.hcaptcha.com' &&
                    url.pathname.indexOf('/captcha/') === 0);
        } catch (_) {
          return false;
        }
      }

      function dag2Protect(node) {
        if (!node || node.nodeType !== 1) return;
        var tag = (node.tagName || '').toLowerCase();
        if (tag === 'img') {
          dag2HttpsVisualSource(node);
        } else if (tag === 'iframe') {
          if (dag2AuthorizedFrame(node)) node.setAttribute('data-dag2-safe-frame', 'true');
          else node.removeAttribute('data-dag2-safe-frame');
        } else if (tag === 'video' || tag === 'audio') {
          try { node.pause(); node.muted = true; } catch (_) {}
        }
      }

      document.addEventListener('load', function(event) {
        if (event.target && event.target.tagName === 'IMG') dag2HttpsVisualSource(event.target);
      }, true);
      document.addEventListener('error', function(event) {
        if (event.target && event.target.tagName === 'IMG') dag2HttpsVisualSource(event.target);
      }, true);

      function dag2InstallMediaObserver() {
        if (!document.documentElement || window.__dag2MediaObserver) return;
        window.__dag2MediaObserver = new MutationObserver(function(records) {
          records.forEach(function(record) {
            if (record.type === 'attributes') {
              dag2Protect(record.target);
              return;
            }
            record.addedNodes.forEach(function(node) {
              dag2Protect(node);
              if (node.querySelectorAll) {
                node.querySelectorAll('img,video,audio,canvas,iframe').forEach(dag2Protect);
              }
            });
          });
        });
        window.__dag2MediaObserver.observe(document.documentElement, {
          subtree:true,
          childList:true,
          attributes:true,
          attributeFilter:['src','srcset','poster']
        });
        document.querySelectorAll('img,video,audio,canvas,iframe').forEach(dag2Protect);
      }
      dag2InstallMediaObserver();
      document.addEventListener('DOMContentLoaded', dag2InstallMediaObserver, {once:true});

      if (window.HTMLMediaElement && window.HTMLMediaElement.prototype) {
        window.HTMLMediaElement.prototype.play = function() {
          return Promise.reject(new Error('DAG v2 blocks media'));
        };
      }

      function dag2ReportRoute(kind) {
        try { DagV2Bridge.onSpaLocation(String(location.href), kind); } catch (_) {}
      }
      ['pushState','replaceState'].forEach(function(method) {
        var original = history[method];
        if (typeof original !== 'function') return;
        history[method] = function() {
          var result = original.apply(this, arguments);
          dag2ReportRoute(method);
          return result;
        };
      });
      addEventListener('hashchange', function() { dag2ReportRoute('hash'); });
      addEventListener('popstate', function() { dag2ReportRoute('popstate'); });

      document.addEventListener('click', function(event) {
        var target = event.target && event.target.closest &&
          event.target.closest('button,[role="button"],details,[aria-expanded],[data-filter]');
        if (!target) return;
        var kind = target.matches('details,[aria-expanded]') ? 'accordion' :
          (target.matches('[data-filter]') ? 'filter' : 'button');
        try { DagV2Bridge.onInternalInteraction(kind); } catch (_) {}
      }, true);
    })();
    """.trimIndent()
