package com.example.browser

import java.util.Locale

object BrowserScripts {

    fun buildDesktopViewportScript(
        desktopWidthPx: Int,
        fitScale: Float,
        desktopHeightPx: Int = ((desktopWidthPx * 1.6f).toInt())
    ): String {
        val clampedWidth = desktopWidthPx.coerceIn(640, 3840)
        val clampedHeight = desktopHeightPx.coerceIn(480, 6000)
        val clampedScale = String.format(Locale.US, "%.3f", fitScale.coerceIn(0.15f, 1.0f))
        return """
            (function() {
                try {
                    const targetW = $clampedWidth;
                    const targetH = $clampedHeight;
                    const targetScale = $clampedScale;
                    const desiredContent = 'width=' + targetW + ', initial-scale=' + targetScale + ', minimum-scale=0.15, maximum-scale=5.0, user-scalable=yes';
                    window.__pcDesktopActive = true;
                    window.__pcDesktopWidth = targetW;
                    window.__pcDesktopHeight = targetH;
                    window.__pcViewportContent = desiredContent;

                    const applyViewport = function() {
                        if (!window.__pcDesktopActive) return;
                        const host = document.head || document.documentElement;
                        if (!host) return;
                        const metas = Array.from(document.querySelectorAll('meta[name="viewport"]'));
                        let meta = metas[0];
                        if (!meta) {
                            meta = document.createElement('meta');
                            meta.name = 'viewport';
                            host.insertBefore(meta, host.firstChild);
                        }
                        for (let i = 1; i < metas.length; i++) {
                            metas[i].remove();
                        }
                        if (meta.getAttribute('content') !== window.__pcViewportContent) {
                            meta.setAttribute('content', window.__pcViewportContent);
                        }

                        let styleEl = document.getElementById('pc-viewport-force-style');
                        const cssRule = '@-ms-viewport{width:' + window.__pcDesktopWidth + 'px;} html{min-width:' + window.__pcDesktopWidth + 'px !important;}';
                        if (!styleEl) {
                            styleEl = document.createElement('style');
                            styleEl.id = 'pc-viewport-force-style';
                            styleEl.textContent = cssRule;
                            host.appendChild(styleEl);
                        } else if (styleEl.textContent !== cssRule) {
                            styleEl.textContent = cssRule;
                        }
                    };

                    if (!window.__pcViewportHooked) {
                        window.__pcViewportHooked = true;
                        try {
                            Object.defineProperty(window.screen, 'width', {
                                get: function() { return window.__pcDesktopWidth || targetW; },
                                configurable: true
                            });
                            Object.defineProperty(window.screen, 'availWidth', {
                                get: function() { return window.__pcDesktopWidth || targetW; },
                                configurable: true
                            });
                            Object.defineProperty(window.screen, 'height', {
                                get: function() { return window.__pcDesktopHeight || targetH; },
                                configurable: true
                            });
                            Object.defineProperty(window.screen, 'availHeight', {
                                get: function() { return window.__pcDesktopHeight || targetH; },
                                configurable: true
                            });
                            Object.defineProperty(window, 'outerWidth', {
                                get: function() { return window.__pcDesktopWidth || targetW; },
                                configurable: true
                            });
                            Object.defineProperty(window, 'outerHeight', {
                                get: function() { return window.__pcDesktopHeight || targetH; },
                                configurable: true
                            });
                        } catch (_) {}

                        document.addEventListener('DOMContentLoaded', applyViewport);
                        const observer = new MutationObserver(function() {
                            if (window.__pcDesktopActive) applyViewport();
                        });
                        if (document.documentElement) {
                            observer.observe(document.documentElement, {
                                childList: true,
                                subtree: true,
                                attributes: true,
                                attributeFilter: ['content']
                            });
                        }
                    }

                    applyViewport();
                } catch (err) {
                    console.warn('[PC ViewPort] Viewport enforcement warning:', err);
                }
            })();
        """.trimIndent()
    }

    fun buildMobileViewportRestoreScript(): String {
        return """
            (function() {
                window.__pcDesktopActive = false;
                const styleEl = document.getElementById('pc-viewport-force-style');
                if (styleEl) styleEl.remove();
                const meta = document.querySelector('meta[name="viewport"]');
                if (meta) {
                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0');
                }
            })();
        """.trimIndent()
    }

    fun buildGeolocationShim(latitude: Double, longitude: Double): String {
        return """
            (function() {
                try {
                    const mockCoords = {
                        latitude: $latitude,
                        longitude: $longitude,
                        accuracy: 10,
                        altitude: null,
                        altitudeAccuracy: null,
                        heading: null,
                        speed: null
                    };
                    const makePosition = () => ({
                        coords: Object.assign({}, mockCoords),
                        timestamp: Date.now()
                    });
                    let nextWatchId = 1;
                    const activeWatches = new Map();
                    const mockGeo = {
                        getCurrentPosition: function(success, error, options) {
                            if (typeof success === 'function') {
                                setTimeout(() => success(makePosition()), 0);
                            }
                        },
                        watchPosition: function(success, error, options) {
                            const id = nextWatchId++;
                            if (typeof success === 'function') {
                                setTimeout(() => success(makePosition()), 0);
                                const timer = setInterval(() => {
                                    if (activeWatches.has(id)) {
                                        success(makePosition());
                                    }
                                }, 5000);
                                activeWatches.set(id, timer);
                            }
                            return id;
                        },
                        clearWatch: function(id) {
                            const timer = activeWatches.get(id);
                            if (timer !== undefined) {
                                clearInterval(timer);
                                activeWatches.delete(id);
                            }
                        }
                    };
                    Object.defineProperty(navigator, 'geolocation', {
                        value: mockGeo,
                        configurable: true
                    });
                } catch (err) {
                    console.warn('[PC ViewPort MockGPS] Failed to override geolocation:', err);
                }
            })();
        """.trimIndent()
    }

    fun quoteJsString(value: String): String {
        val sb = StringBuilder(value.length + 16)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '<' -> sb.append("\\u003C")
                '>' -> sb.append("\\u003E")
                else -> {
                    if (ch < ' ') {
                        sb.append("\\u%04x".format(ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun buildEsModuleInjectionScript(userCode: String): String {
        val quotedCode = quoteJsString(userCode)
        return """
            (function() {
                try {
                    const script = document.createElement('script');
                    script.type = 'module';
                    script.textContent = $quotedCode;
                    script.onerror = function() {
                        console.error('[ES Module] Failed to load module script.');
                    };
                    (document.head || document.documentElement).appendChild(script);
                    return 'ES Module injected into DOM';
                } catch (err) {
                    console.error('[ES Module Error]', err && err.message ? err.message : String(err));
                    return 'Error: ' + (err && err.message ? err.message : String(err));
                }
            })();
        """.trimIndent()
    }

    val EXTRACT_PAGE_CONTENT_SCRIPT = """
        (function() {
            try {
                const title = document.title || '';
                const url = window.location.href || '';
                const metaDesc = (document.querySelector('meta[name="description"]')?.getAttribute('content'))
                    || (document.querySelector('meta[property="og:description"]')?.getAttribute('content'))
                    || '';
                const selectedText = (window.getSelection && window.getSelection().toString()) || '';
                const headings = Array.from(document.querySelectorAll('h1, h2, h3'))
                    .map(h => (h.innerText || '').trim())
                    .filter(Boolean)
                    .slice(0, 18);
                const bodyText = (document.body ? document.body.innerText : '').replace(/\s+/g, ' ').trim().slice(0, 12000);
                const linkCount = document.querySelectorAll('a[href]').length;
                const mediaCount = document.querySelectorAll('img, video, audio').length;
                return JSON.stringify({
                    title,
                    url,
                    description: metaDesc,
                    selectedText,
                    headings,
                    bodyText,
                    linkCount,
                    mediaCount
                });
            } catch (e) {
                return JSON.stringify({ title: document.title || '', url: window.location.href || '', description: '', selectedText: '', headings: [], bodyText: '', linkCount: 0, mediaCount: 0 });
            }
        })();
    """.trimIndent()

    val EXTRACT_MEDIA_SCRIPT = """
        (function() {
            try {
                const abs = (u) => {
                    try { return new URL(u, window.location.href).href; } catch (_) { return ''; }
                };
                const videos = [];
                const images = [];
                const audio = [];
                const seen = new Set();

                const pushUnique = (list, item) => {
                    if (!item.url || item.url.startsWith('data:image/svg') || seen.has(item.url)) return;
                    seen.add(item.url);
                    list.push(item);
                };

                document.querySelectorAll('video, video source').forEach(el => {
                    const src = abs(el.currentSrc || el.src || el.getAttribute('src') || '');
                    if (src && !src.startsWith('blob:')) {
                        pushUnique(videos, { url: src, label: el.getAttribute('title') || 'Video stream', type: 'VIDEO' });
                    }
                });

                document.querySelectorAll('audio, audio source').forEach(el => {
                    const src = abs(el.currentSrc || el.src || el.getAttribute('src') || '');
                    if (src && !src.startsWith('blob:')) {
                        pushUnique(audio, { url: src, label: el.getAttribute('title') || 'Audio track', type: 'AUDIO' });
                    }
                });

                document.querySelectorAll('img').forEach(img => {
                    const src = abs(img.currentSrc || img.src || img.getAttribute('data-src') || '');
                    const w = img.naturalWidth || img.width || 0;
                    const h = img.naturalHeight || img.height || 0;
                    if (src && !src.startsWith('blob:')) {
                        pushUnique(images, { url: src, label: (img.alt || 'Image') + (w > 0 ? ' (' + w + 'x' + h + ')' : ''), type: 'IMAGE' });
                    }
                });

                document.querySelectorAll('a[href]').forEach(a => {
                    const href = abs(a.getAttribute('href') || '');
                    const lower = href.toLowerCase();
                    if (/\.(mp4|webm|mov|m3u8|mkv)(\?|$)/.test(lower)) {
                        pushUnique(videos, { url: href, label: (a.innerText || 'Linked Video').trim().slice(0, 60), type: 'VIDEO' });
                    } else if (/\.(mp3|wav|m4a|ogg|flac|aac)(\?|$)/.test(lower)) {
                        pushUnique(audio, { url: href, label: (a.innerText || 'Linked Audio').trim().slice(0, 60), type: 'AUDIO' });
                    } else if (/\.(png|jpe?g|webp|gif)(\?|$)/.test(lower)) {
                        pushUnique(images, { url: href, label: (a.innerText || 'Linked Image').trim().slice(0, 60), type: 'IMAGE' });
                    }
                });

                return JSON.stringify({
                    videos: videos.slice(0, 40),
                    images: images.slice(0, 60),
                    audio: audio.slice(0, 40)
                });
            } catch (e) {
                return JSON.stringify({ videos: [], images: [], audio: [] });
            }
        })();
    """.trimIndent()

    val EXTRACT_PWA_MANIFEST_SCRIPT = """
        (function() {
            try {
                const manifestLink = document.querySelector('link[rel="manifest"]')?.getAttribute('href') || '';
                const appName = document.querySelector('meta[name="application-name"]')?.getAttribute('content')
                    || document.querySelector('meta[property="og:site_name"]')?.getAttribute('content')
                    || document.title
                    || window.location.hostname;
                return JSON.stringify({
                    hasManifest: Boolean(manifestLink),
                    manifestUrl: manifestLink,
                    appName: (appName || '').trim(),
                    startUrl: window.location.href
                });
            } catch (_) {
                return JSON.stringify({ hasManifest: false, manifestUrl: '', appName: document.title || '', startUrl: window.location.href });
            }
        })();
    """.trimIndent()

    fun buildAutofillPasswordScript(username: String, password: String): String {
        val qUser = quoteJsString(username)
        val qPass = quoteJsString(password)
        return """
            (function() {
                try {
                    const passFields = Array.from(document.querySelectorAll('input[type="password"]'));
                    const userFields = Array.from(document.querySelectorAll('input[type="email"], input[type="text"], input[name*="user" i], input[name*="mail" i], input[autocomplete="username"]'));
                    const setNativeValue = (el, val) => {
                        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
                        if (setter) setter.call(el, val); else el.value = val;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                    };
                    let filled = 0;
                    if (userFields.length > 0) {
                        setNativeValue(userFields[0], $qUser);
                        filled++;
                    }
                    if (passFields.length > 0) {
                        setNativeValue(passFields[0], $qPass);
                        filled++;
                    }
                    return filled > 0 ? 'FILLED_' + filled : 'NO_FIELDS';
                } catch (e) {
                    return 'ERROR';
                }
            })();
        """.trimIndent()
    }
}
