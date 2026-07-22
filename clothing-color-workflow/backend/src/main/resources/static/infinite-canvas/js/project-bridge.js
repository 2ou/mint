(function () {
    'use strict';

    var TOKEN_KEY = 'user_token';
    var TEXT_MODELS = ['gpt-5.6-sol', 'gpt-5.6-terra', 'gpt-5.6-luna'];
    var IMAGE_MODELS = ['project-image'];
    var VIDEO_MODELS = ['project-video'];

    function token() {
        try {
            return localStorage.getItem(TOKEN_KEY) || '';
        } catch (error) {
            return '';
        }
    }

    function ensureLogin() {
        if (token()) return;
        window.location.href = '/login.html';
    }

    function patchFetchAuth() {
        var rawFetch = window.fetch ? window.fetch.bind(window) : null;
        if (!rawFetch || rawFetch.__aiProjectPatched) return;

        function shouldAttach(url) {
            try {
                var parsed = new URL(url, window.location.origin);
                return parsed.origin === window.location.origin && parsed.pathname.indexOf('/api/') === 0;
            } catch (error) {
                return false;
            }
        }

        var patched = function (input, init) {
            var requestUrl = typeof input === 'string' ? input : (input && input.url) || '';
            var authToken = token();
            if (!authToken || !shouldAttach(requestUrl)) {
                return rawFetch(input, init);
            }

            var nextInit = Object.assign({}, init || {});
            var headers = new Headers(
                nextInit.headers || (input instanceof Request ? input.headers : undefined) || undefined
            );
            if (!headers.has('X-User-Token')) headers.set('X-User-Token', authToken);
            nextInit.headers = headers;

            if (input instanceof Request) {
                return rawFetch(new Request(input, nextInit));
            }
            return rawFetch(input, nextInit);
        };
        patched.__aiProjectPatched = true;
        window.fetch = patched;
    }

    function writeJson(key, value) {
        try {
            localStorage.setItem(key, JSON.stringify(value));
        } catch (error) {
            // Local storage can be full after large canvas imports; the backend config still works.
        }
    }

    function bootstrapModels() {
        writeJson('canvas_chat_models_ordered', TEXT_MODELS);
        writeJson('canvas_image_models_ordered', IMAGE_MODELS);
        writeJson('canvas_video_models_ordered', VIDEO_MODELS);
        writeJson('studio_api_providers', [{
            id: 'ai-project-kie',
            name: '项目 KIE 代理',
            base_url: '/api/canvas/kie/v1',
            enabled: true,
            has_key: false,
            key_preview: '后端托管',
            image_models: IMAGE_MODELS,
            chat_models: TEXT_MODELS,
            video_models: VIDEO_MODELS
        }]);
    }

    function hideApiSettingsEntrypoints() {
        var candidates = Array.prototype.slice.call(document.querySelectorAll('a,button,[onclick],[data-src]'));
        candidates.forEach(function (el) {
            var text = (el.textContent || '') + ' ' + (el.getAttribute('title') || '') + ' ' +
                (el.getAttribute('onclick') || '') + ' ' + (el.getAttribute('data-src') || '') + ' ' +
                (el.getAttribute('href') || '');
            if (/api-settings|API\s*设置|API设置/i.test(text)) {
                el.style.display = 'none';
                el.setAttribute('aria-hidden', 'true');
            }
        });
    }

    function canvasIdFromLocation() {
        try {
            return new URLSearchParams(window.location.search).get('id') || '';
        } catch (error) {
            return '';
        }
    }

    function firstMediaUrl(value) {
        if (!value) return '';
        if (typeof value === 'string') {
            return /^https?:\/\//i.test(value) || value.indexOf('data:image/') === 0 ? value : '';
        }
        if (Array.isArray(value)) {
            for (var i = 0; i < value.length; i += 1) {
                var fromArray = firstMediaUrl(value[i]);
                if (fromArray) return fromArray;
            }
            return '';
        }
        if (typeof value === 'object') {
            var keys = ['url', 'imageUrl', 'image_url', 'src', 'output', 'video_url', 'videoUrl'];
            for (var j = 0; j < keys.length; j += 1) {
                var direct = firstMediaUrl(value[keys[j]]);
                if (direct) return direct;
            }
            var values = Object.keys(value).map(function (key) { return value[key]; });
            for (var k = 0; k < values.length; k += 1) {
                var nested = firstMediaUrl(values[k]);
                if (nested) return nested;
            }
        }
        return '';
    }

    async function saveCurrentCanvasAsTemplate() {
        var canvasId = canvasIdFromLocation();
        if (!canvasId) {
            alert('请先打开一个画布再保存模板');
            return;
        }
        var name = window.prompt('模板名称', (document.getElementById('currentCanvasTitle') || {}).textContent || 'AI 画布模板');
        if (!name) return;
        var category = window.prompt('模板分类', '未分类') || '未分类';
        var response = await fetch('/api/canvases/' + encodeURIComponent(canvasId));
        if (!response.ok) {
            alert('读取当前画布失败');
            return;
        }
        var data = await response.json();
        var canvas = data.canvas || data;
        var payload = {
            templateName: name.trim(),
            category: category.trim(),
            tags: ['Infinite-Canvas'],
            coverImageUrl: firstMediaUrl(canvas),
            description: '来自 Infinite-Canvas 画布保存',
            snapshot: {
                infinite_canvas: JSON.stringify(canvas || {})
            },
            meta: {
                source: 'infinite-canvas',
                canvasId: canvasId,
                kind: canvas.kind || 'classic'
            }
        };
        var save = await fetch('/api/canvas/templates', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!save.ok) {
            alert('保存模板失败');
            return;
        }
        alert('模板已保存');
    }

    function goTemplateLibrary() {
        window.location.href = '/ai-canvas-templates.html';
    }

    function appendButton(parent, className, text, onClick) {
        if (!parent || parent.querySelector('[data-ai-project-template-action="' + text + '"]')) return;
        var button = document.createElement('button');
        button.type = 'button';
        button.className = className;
        button.textContent = text;
        button.setAttribute('data-ai-project-template-action', text);
        button.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            onClick();
        });
        parent.appendChild(button);
    }

    function injectTemplateActions() {
        var editorNav = document.querySelector('.canvas-nav');
        if (editorNav) {
            appendButton(editorNav, 'tool-btn', '保存为模板', function () {
                saveCurrentCanvasAsTemplate().catch(function (error) {
                    alert('保存模板失败：' + (error && error.message ? error.message : error));
                });
            });
            appendButton(editorNav, 'tool-btn', '模板库', goTemplateLibrary);
        }

        var listActions = document.querySelector('.ws-topbar-right');
        if (listActions) {
            appendButton(listActions, 'ws-icon-btn', '模板库', goTemplateLibrary);
        }
    }

    ensureLogin();
    patchFetchAuth();
    bootstrapModels();

    document.addEventListener('DOMContentLoaded', function () {
        hideApiSettingsEntrypoints();
        injectTemplateActions();
        window.setTimeout(hideApiSettingsEntrypoints, 300);
    });
})();
