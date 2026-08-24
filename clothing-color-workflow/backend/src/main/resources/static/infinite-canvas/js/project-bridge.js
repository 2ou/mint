(function () {
    'use strict';

    var TOKEN_KEY = 'user_token';
    var TEXT_MODELS = ['gpt-5.6-sol', 'gpt-5.6-terra', 'gpt-5.6-luna'];
    var IMAGE_MODELS = ['project-image'];
    var VIDEO_MODELS = [
        'bytedance/seedance-2-5',
        'bytedance/seedance-2',
        'minimax-h3/text-to-video',
        'minimax-h3/image-to-video',
        'minimax-h3/reference-to-video'
    ];

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

    var KIE_SUBMISSION_PATHS = [
        '/api/canvas/kie/v1/chat/completions',
        '/api/canvas/kie/v1/images/generations',
        '/api/canvas/kie/v1/videos/generations',
        '/api/canvas-image-tasks',
        '/api/online-image',
        '/api/canvas-video',
        '/api/canvas-video-tasks',
        '/api/canvas-llm'
    ];
    var kieActionSequence = 0;
    var activeKieActionId = 0;
    var kieActionConfirmations = {};

    function isKieConfirmationElement(target) {
        return !!(target && target.closest && target.closest('#ai-project-kie-confirmation'));
    }

    function startKieUserAction(event) {
        if (event && event.isTrusted === false) return;
        if (isKieConfirmationElement(event && event.target)) return;
        activeKieActionId = ++kieActionSequence;
        Object.keys(kieActionConfirmations).forEach(function (key) {
            if (Number(key.replace('action-', '')) < activeKieActionId - 8) {
                delete kieActionConfirmations[key];
            }
        });
    }

    document.addEventListener('pointerdown', startKieUserAction, true);
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' || event.key === ' ') startKieUserAction(event);
    }, true);

    function kieActionKey() {
        if (!activeKieActionId) activeKieActionId = ++kieActionSequence;
        return 'action-' + activeKieActionId;
    }

    function isKieSubmissionRequest(input, init) {
        var method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
        if (method !== 'POST') return false;

        var url = typeof input === 'string' ? input : (input && input.url) || '';
        try {
            var pathname = new URL(url, window.location.origin).pathname;
            if (/^\/api\/canvas-tasks\/[^/]+\/retry$/.test(pathname)) return true;
            return KIE_SUBMISSION_PATHS.indexOf(pathname) !== -1;
        } catch (error) {
            return false;
        }
    }

    function kieSubmissionType(input) {
        var url = typeof input === 'string' ? input : (input && input.url) || '';
        var path = '';
        try {
            path = new URL(url, window.location.origin).pathname;
        } catch (error) {
            path = url;
        }
        if (path.indexOf('video') !== -1) return '视频生成';
        if (path.indexOf('chat') !== -1 || path.indexOf('llm') !== -1) return '文本处理';
        if (path.indexOf('/retry') !== -1) return '任务重试';
        return '图片生成';
    }

    function ensureKieConfirmationStyles() {
        if (document.getElementById('aiProjectKieConfirmationStyles')) return;
        var style = document.createElement('style');
        style.id = 'aiProjectKieConfirmationStyles';
        style.textContent = '' +
            '#ai-project-kie-confirmation{position:fixed;inset:0;z-index:2147483647;display:flex;align-items:center;justify-content:center;padding:20px;background:rgba(15,23,42,.58);}' +
            '.ai-project-kie-confirm-panel{width:min(440px,100%);box-sizing:border-box;border:1px solid #d8e0ea;border-radius:16px;background:#fff;color:#172033;box-shadow:0 24px 80px rgba(15,23,42,.3);padding:24px;}' +
            '.ai-project-kie-confirm-kicker{margin:0 0 8px;color:#b45309;font-size:12px;font-weight:800;letter-spacing:.04em;}' +
            '.ai-project-kie-confirm-title{margin:0;color:#172033;font-size:20px;line-height:1.35;}' +
            '.ai-project-kie-confirm-copy{margin:12px 0 0;color:#475569;font-size:14px;line-height:1.65;}' +
            '.ai-project-kie-confirm-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:22px;}' +
            '.ai-project-kie-confirm-actions button{min-height:40px;border-radius:9px;padding:0 15px;font:inherit;font-size:14px;font-weight:700;cursor:pointer;transition:background-color .16s ease,border-color .16s ease,box-shadow .16s ease;}' +
            '.ai-project-kie-confirm-cancel{border:1px solid #cbd5e1;background:#fff;color:#334155;}' +
            '.ai-project-kie-confirm-cancel:hover{background:#f8fafc;}' +
            '.ai-project-kie-confirm-submit{border:1px solid #b45309;background:#b45309;color:#fff;}' +
            '.ai-project-kie-confirm-submit:hover{background:#92400e;border-color:#92400e;}' +
            '.ai-project-kie-confirm-actions button:focus-visible{outline:3px solid rgba(37,99,235,.42);outline-offset:2px;}' +
            'html.studio-theme-dark #ai-project-kie-confirmation,html.theme-dark #ai-project-kie-confirmation{background:rgba(2,6,23,.72);}' +
            'html.studio-theme-dark .ai-project-kie-confirm-panel,html.theme-dark .ai-project-kie-confirm-panel{border-color:#334155;background:#182235;color:#f8fafc;}' +
            'html.studio-theme-dark .ai-project-kie-confirm-title,html.theme-dark .ai-project-kie-confirm-title{color:#f8fafc;}' +
            'html.studio-theme-dark .ai-project-kie-confirm-copy,html.theme-dark .ai-project-kie-confirm-copy{color:#cbd5e1;}' +
            'html.studio-theme-dark .ai-project-kie-confirm-cancel,html.theme-dark .ai-project-kie-confirm-cancel{border-color:#475569;background:#243044;color:#e2e8f0;}' +
            'html.studio-theme-dark .ai-project-kie-confirm-cancel:hover,html.theme-dark .ai-project-kie-confirm-cancel:hover{background:#334155;}' +
            '@media(max-width:480px){#ai-project-kie-confirmation{padding:16px}.ai-project-kie-confirm-panel{padding:20px}.ai-project-kie-confirm-actions{flex-direction:column-reverse}.ai-project-kie-confirm-actions button{width:100%;}}';
        (document.head || document.documentElement).appendChild(style);
    }

    function showKieSubmissionConfirmation(type) {
        return new Promise(function (resolve) {
            ensureKieConfirmationStyles();
            var previousFocus = document.activeElement;
            var overlay = document.createElement('div');
            overlay.id = 'ai-project-kie-confirmation';
            overlay.innerHTML = '<div class="ai-project-kie-confirm-panel" role="dialog" aria-modal="true" aria-labelledby="ai-project-kie-confirm-title" aria-describedby="ai-project-kie-confirm-copy">' +
                '<p class="ai-project-kie-confirm-kicker">KIE 额度操作</p>' +
                '<h2 id="ai-project-kie-confirm-title" class="ai-project-kie-confirm-title">确认提交到 Kie？</h2>' +
                '<p id="ai-project-kie-confirm-copy" class="ai-project-kie-confirm-copy">本次将提交' + type + '请求，可能消耗 Kie 额度。确认后任务会立即创建，已提交的任务无法撤回。</p>' +
                '<div class="ai-project-kie-confirm-actions"><button class="ai-project-kie-confirm-cancel" type="button">取消</button><button class="ai-project-kie-confirm-submit" type="button">确认提交</button></div>' +
                '</div>';
            document.body.appendChild(overlay);

            var cancelButton = overlay.querySelector('.ai-project-kie-confirm-cancel');
            var submitButton = overlay.querySelector('.ai-project-kie-confirm-submit');
            var closed = false;

            function close(approved) {
                if (closed) return;
                closed = true;
                document.removeEventListener('keydown', onKeydown, true);
                overlay.remove();
                if (previousFocus && previousFocus.isConnected && previousFocus.focus) previousFocus.focus();
                resolve(approved);
            }

            function onKeydown(event) {
                if (event.key === 'Escape') {
                    event.preventDefault();
                    close(false);
                    return;
                }
                if (event.key !== 'Tab') return;
                event.preventDefault();
                if (event.shiftKey) cancelButton.focus();
                else submitButton.focus();
            }

            cancelButton.addEventListener('click', function () { close(false); });
            submitButton.addEventListener('click', function () { close(true); });
            overlay.addEventListener('click', function (event) {
                if (event.target === overlay) close(false);
            });
            document.addEventListener('keydown', onKeydown, true);
            window.setTimeout(function () { submitButton.focus(); }, 0);
        });
    }

    function confirmKieSubmission(details) {
        var key = kieActionKey();
        var current = kieActionConfirmations[key];
        if (current) {
            return current.state === 'pending' ? current.promise : Promise.resolve(current.state === 'approved');
        }

        current = {state: 'pending', promise: null};
        kieActionConfirmations[key] = current;
        current.promise = showKieSubmissionConfirmation((details && details.type) || 'AI').then(function (approved) {
            current.state = approved ? 'approved' : 'rejected';
            return approved;
        });
        return current.promise;
    }

    function patchKieSubmissionConfirmation() {
        var rawFetch = window.fetch ? window.fetch.bind(window) : null;
        if (!rawFetch || rawFetch.__aiProjectKieConfirmationPatched) return;

        var patched = function (input, init) {
            if (!isKieSubmissionRequest(input, init)) return rawFetch(input, init);
            return confirmKieSubmission({type: kieSubmissionType(input)}).then(function (approved) {
                if (approved) return rawFetch(input, init);
                var error = new Error('已取消 Kie 提交');
                error.name = 'KieSubmissionCancelledError';
                error.kieSubmissionCancelled = true;
                throw error;
            });
        };
        patched.__aiProjectKieConfirmationPatched = true;
        window.fetch = patched;
    }

    window.confirmKieSubmission = confirmKieSubmission;

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
    patchKieSubmissionConfirmation();
    bootstrapModels();

    document.addEventListener('DOMContentLoaded', function () {
        hideApiSettingsEntrypoints();
        injectTemplateActions();
        window.setTimeout(hideApiSettingsEntrypoints, 300);
    });
})();
