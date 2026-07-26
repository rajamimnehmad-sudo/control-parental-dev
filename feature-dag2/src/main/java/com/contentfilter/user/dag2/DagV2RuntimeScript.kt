package com.contentfilter.user.dag2

internal fun dagV2DocumentStartScript(context: DagV2DocumentRequestContext): String {
    require(context.sessionId.matches(SafeIdentifier))
    require(context.navigationToken.matches(SafeIdentifier))
    return DagV2DocumentStartScriptTemplate
        .replace("__DAG2_SESSION_ID__", context.sessionId)
        .replace("__DAG2_NAVIGATION_TOKEN__", context.navigationToken)
}

internal val DagV2DocumentStartScript: String
    get() = DagV2DocumentStartScriptTemplate

private val SafeIdentifier = Regex("[A-Za-z0-9-]{1,128}")

private val DagV2DocumentStartScriptTemplate =
    """
    (function() {
      if (window.__dag2RuntimeInstalled === true) return;
      window.__dag2RuntimeInstalled = true;
      window.__dag2Context = Object.freeze({
        sessionId:'__DAG2_SESSION_ID__',
        navigationToken:'__DAG2_NAVIGATION_TOKEN__'
      });

      function dag2Post(type, payload) {
        if (window.top !== window.self) return;
        try {
          DagV2Bridge.postMessage(JSON.stringify({
            type:type,
            sessionId:window.__dag2Context.sessionId,
            navigationToken:window.__dag2Context.navigationToken,
            payload:payload || {}
          }));
        } catch (_) {}
      }

      function dag2InstallHeadGuards() {
        if (!document.head) return;
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
            'iframe:not([data-dag2-safe-frame="true"]){display:none!important;}';
          document.head.appendChild(safety);
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
        if (tag === 'iframe') {
          if (dag2AuthorizedFrame(node)) node.setAttribute('data-dag2-safe-frame', 'true');
          else node.removeAttribute('data-dag2-safe-frame');
        } else if (tag === 'video' || tag === 'audio') {
          try { node.pause(); node.muted = true; } catch (_) {}
        }
      }

      function dag2ProtectTree(root) {
        dag2Protect(root);
        if (root && root.querySelectorAll) {
          root.querySelectorAll('img,picture,source,video,audio,canvas,iframe').forEach(dag2Protect);
        }
      }

      var dag2Observer = new MutationObserver(function(records) {
        dag2InstallHeadGuards();
        records.forEach(function(record) {
          if (record.type === 'attributes') {
            dag2Protect(record.target);
          } else {
            record.addedNodes.forEach(dag2ProtectTree);
          }
        });
      });
      dag2Observer.observe(document, {
        subtree:true,
        childList:true,
        attributes:true,
        attributeFilter:['src','srcset','poster']
      });
      dag2InstallHeadGuards();
      dag2ProtectTree(document.documentElement);

      if (window.HTMLMediaElement && window.HTMLMediaElement.prototype) {
        window.HTMLMediaElement.prototype.play = function() {
          return Promise.reject(new Error('DAG v2 blocks media'));
        };
      }

      var dag2LastHref = String(location.href);
      function dag2ReportRoute(kind) {
        var current = String(location.href);
        if (current === dag2LastHref) return;
        dag2LastHref = current;
        dag2Post('spa_location', {url:current, kind:kind});
      }

      var dag2RouteTimer = null;
      if (window.navigation && typeof window.navigation.addEventListener === 'function') {
        window.navigation.addEventListener('navigate', function(event) {
          var kind = String(event.navigationType || 'navigation');
          setTimeout(function() { dag2ReportRoute(kind); }, 0);
        });
      } else {
        dag2RouteTimer = setInterval(function() { dag2ReportRoute('poll'); }, 500);
      }
      addEventListener('hashchange', function() { dag2ReportRoute('hash'); });
      addEventListener('popstate', function() { dag2ReportRoute('popstate'); });

      var dag2ReadyReported = false;
      function dag2ReportReady() {
        if (dag2ReadyReported || window.top !== window.self) return;
        dag2ReadyReported = true;
        dag2Post('document_ready', {url:String(location.href)});
      }
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', dag2ReportReady, {once:true});
      } else {
        setTimeout(dag2ReportReady, 0);
      }

      document.addEventListener('click', function(event) {
        if (window.top !== window.self) return;
        var target = event.target && event.target.closest &&
          event.target.closest('button,[role="button"],details,[aria-expanded],[data-filter]');
        if (!target) return;
        var kind = target.matches('details,[aria-expanded]') ? 'accordion' :
          (target.matches('[data-filter]') ? 'filter' : 'button');
        dag2Post('internal_interaction', {kind:kind});
      }, true);

      addEventListener('pagehide', function() {
        if (dag2RouteTimer !== null) clearInterval(dag2RouteTimer);
        dag2Observer.disconnect();
      }, {once:true});
    })();
    """.trimIndent()
