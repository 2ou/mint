(function () {
    'use strict';

    var API_BASE = '/api/canvas';
    var PROJECT_PROVIDER = 'project-kie';
    var PROJECT_KEY = 'project-kie-local';
    var PROJECT_TEXT_MODELS = [
        { id: 'gpt-5.6-sol', displayName: 'GPT 5.6 Sol（高质量）' },
        { id: 'gpt-5.6-terra', displayName: 'GPT 5.6 Terra（均衡）' },
        { id: 'gpt-5.6-luna', displayName: 'GPT 5.6 Luna（快速）' }
    ];
    var PROJECT_DEFAULT_TEXT_MODEL = 'gpt-5.6-terra';
    var PROJECT_IMAGE_MODEL = 'project-image';
    var PROJECT_VIDEO_MODEL = 'project-video';
    var CURRENT_PROJECT_KEY = 'ai_canvas_project_id';
    var AUTOSAVE_INTERVAL_MS = 8000;
    var frameElement = null;
    var autoSaveTimer = null;
    var saving = false;
    var lastSnapshotHash = '';

    function getToken() {
        return localStorage.getItem('user_token') || '';
    }

    function getProjectId() {
        var raw = localStorage.getItem(CURRENT_PROJECT_KEY) || '';
        return raw ? Number(raw) : null;
    }

    function setProjectId(id) {
        if (id) {
            localStorage.setItem(CURRENT_PROJECT_KEY, String(id));
        } else {
            localStorage.removeItem(CURRENT_PROJECT_KEY);
        }
    }

    function readJson(key, fallback) {
        try {
            var raw = localStorage.getItem(key);
            if (!raw) return fallback;
            return JSON.parse(raw);
        } catch (error) {
            return fallback;
        }
    }

    function writeJson(key, value) {
        localStorage.setItem(key, JSON.stringify(value));
    }

    function updateStatus(text, type) {
        var el = document.getElementById('canvasSaveStatus');
        if (!el) return;
        el.textContent = text || '';
        el.dataset.type = type || '';
    }

    function apiFetch(path, options) {
        var token = getToken();
        var opts = Object.assign({ method: 'GET' }, options || {});
        var headers = Object.assign({}, opts.headers || {});
        if (opts.body && !headers['Content-Type'] && !headers['content-type']) {
            headers['Content-Type'] = 'application/json';
        }
        if (token) headers['X-User-Token'] = token;
        opts.headers = headers;
        return fetch(API_BASE + path, opts).then(function (response) {
            return response.text().then(function (text) {
                var payload = null;
                try {
                    payload = text ? JSON.parse(text) : null;
                } catch (error) {
                    payload = text;
                }
                if (response.status === 401) {
                    updateStatus('登录已失效', 'error');
                    throw new Error('登录已失效，请重新登录');
                }
                if (!response.ok) {
                    throw new Error((payload && payload.message) || text || ('HTTP ' + response.status));
                }
                if (payload && payload.success === false) {
                    throw new Error(payload.message || '请求失败');
                }
                return payload && Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload;
            });
        });
    }

    function commonHeaders() {
        var token = getToken();
        var headers = {
            'Content-Type': 'application/json'
        };
        if (token) headers['X-User-Token'] = token;
        return headers;
    }

    function chatTemplate() {
        return {
            enabled: true,
            endpoint: '/v1/chat/completions',
            method: 'POST',
            bodyType: 'json',
            headers: commonHeaders(),
            query: {},
            files: {},
            timeoutMs: 180000,
            responseParser: '',
            body: {
                model: '{{modelName}}',
                messages: '{{messages}}',
                stream: false
            }
        };
    }

    function imageAsyncConfig() {
        return {
            enabled: true,
            requestIdPaths: ['data.taskId', 'taskId', 'task_id', 'id'],
            pollIntervalMs: 3000,
            maxAttempts: 300,
            statusRequest: {
                endpoint: '/v1/images/tasks/{{requestId}}',
                method: 'GET',
                headers: commonHeaders(),
                query: {},
                bodyType: 'json',
                body: {}
            },
            statusPath: 'status',
            successValues: ['SUCCESS'],
            failureValues: ['FAILED'],
            outputsRequest: null,
            outputsPath: 'data.outputs',
            outputsUrlField: 'url',
            errorPath: 'message'
        };
    }

    function imageTemplate() {
        return {
            enabled: true,
            endpoint: '/v1/images/generations',
            method: 'POST',
            bodyType: 'json',
            headers: commonHeaders(),
            query: {},
            files: {},
            timeoutMs: 180000,
            responseParser: '',
            body: {
                model: '{{modelName}}',
                prompt: '{{prompt}}',
                n: '{{n:number}}',
                size: '{{size}}',
                resolution: '{{resolution}}',
                ratio: '{{ratio}}',
                aspect_ratio: '{{ratio}}',
                imageUrl: '{{imageUrl}}',
                imageUrls: '{{imageUrls}}',
                imagesUrl: '{{imagesUrl}}',
                imagesUrls: '{{imagesUrls}}'
            }
        };
    }

    function videoTemplate() {
        return {
            enabled: true,
            endpoint: '/v1/videos/generations',
            method: 'POST',
            bodyType: 'json',
            headers: commonHeaders(),
            query: {},
            files: {},
            timeoutMs: 180000,
            responseParser: '',
            body: {
                model: '{{modelName}}',
                prompt: '{{prompt}}',
                duration: '{{duration:number}}',
                ratio: '{{ratio}}',
                aspect_ratio: '{{ratio}}',
                resolution: '{{resolution}}',
                imageUrl: '{{imageUrl}}',
                imageUrls: '{{imageUrls}}',
                imagesUrl: '{{imagesUrl}}',
                imagesUrls: '{{imagesUrls}}',
                firstFrameUrl: '{{firstFrameUrl}}',
                lastFrameUrl: '{{lastFrameUrl}}'
            }
        };
    }

    function projectEntries() {
        var textEntries = PROJECT_TEXT_MODELS.map(function (model) {
            return {
                id: model.id,
                modelName: model.id,
                displayName: model.displayName,
                provider: PROJECT_PROVIDER,
                type: 'Chat',
                apiType: 'openai',
                requestTemplate: chatTemplate(),
                asyncConfig: null,
                disabled: false
            };
        });

        return textEntries.concat([
            {
                id: PROJECT_IMAGE_MODEL,
                modelName: PROJECT_IMAGE_MODEL,
                displayName: '项目图片模型',
                provider: PROJECT_PROVIDER,
                type: 'Image',
                apiType: 'openai',
                requestTemplate: imageTemplate(),
                asyncConfig: imageAsyncConfig(),
                disabled: false,
                imageRouteMode: 'auto',
                imageBatchMode: 'parallel_aggregate',
                nativeMultiImageMode: 'auto',
                ratioLimits: ['Auto', '1:1', '16:9', '9:16', '4:3', '3:4', '21:9', '3:2', '2:3'],
                defaultRatio: 'Auto',
                resolutionLimits: ['Auto', '1K', '2K', '4K'],
                defaultResolution: '2K',
                defaultImageConcurrency: 1
            },
            {
                id: PROJECT_VIDEO_MODEL,
                modelName: PROJECT_VIDEO_MODEL,
                displayName: '项目视频模型',
                provider: PROJECT_PROVIDER,
                type: 'Video',
                apiType: 'openai',
                requestTemplate: videoTemplate(),
                asyncConfig: null,
                disabled: false,
                durations: ['5s', '10s'],
                defaultDuration: '5s',
                videoResolutions: ['720P', '1080P'],
                defaultVideoResolution: '720P',
                ratioLimits: ['Auto', '1:1', '16:9', '9:16'],
                defaultRatio: '16:9',
                supportsHD: true,
                supportsFirstLastFrame: true
            }
        ]);
    }

    function mergeById(existing, entries) {
        var source = Array.isArray(existing) ? existing.slice() : [];
        var ids = entries.map(function (item) { return item.id; });
        var legacyIds = ['project-text'];
        var textIds = PROJECT_TEXT_MODELS.map(function (item) { return item.id; });
        source = source.filter(function (item) { return item && ids.indexOf(item.id) === -1; });
        source = source.filter(function (item) { return legacyIds.indexOf(item.id) === -1; });
        source = source.filter(function (item) { return !isLegacyGptChatModel(item, textIds); });
        return entries.concat(source);
    }

    function isLegacyGptChatModel(item, allowedIds) {
        var id = String((item && (item.id || item.modelName)) || '').toLowerCase();
        var type = String((item && item.type) || '').toLowerCase();
        return type === 'chat' && id.indexOf('gpt') === 0 && allowedIds.indexOf(id) === -1;
    }

    function injectProjectModels() {
        var providers = readJson('tapnow_providers', {});
        providers[PROJECT_PROVIDER] = Object.assign({}, providers[PROJECT_PROVIDER] || {}, {
            key: PROJECT_KEY,
            url: window.location.origin + API_BASE + '/kie',
            apiType: 'openai',
            useProxy: false,
            forceAsync: false,
            enabled: true
        });
        writeJson('tapnow_providers', providers);
        localStorage.setItem('tapnow_global_key', PROJECT_KEY);

        var entries = projectEntries();
        var apiConfigs = readJson('tapnow_api_configs', []);
        writeJson('tapnow_api_configs', mergeById(apiConfigs, entries));

        var libraryEntries = entries.map(function (entry) {
            var clone = Object.assign({}, entry);
            delete clone.provider;
            return clone;
        });
        var library = readJson('tapnow_model_library', []);
        writeJson('tapnow_model_library', mergeById(library, libraryEntries));

        localStorage.setItem('tapnow_chat_model', PROJECT_DEFAULT_TEXT_MODEL);
        localStorage.setItem('tapnow_last_extract_model', PROJECT_DEFAULT_TEXT_MODEL);
        localStorage.setItem('tapnow_last_analyze_model', PROJECT_DEFAULT_TEXT_MODEL);
        localStorage.setItem('tapnow_last_image_model', PROJECT_IMAGE_MODEL);
        localStorage.setItem('tapnow_last_video_model', PROJECT_VIDEO_MODEL);
        localStorage.setItem('tapnow_last_image_res', '2K');
        localStorage.setItem('tapnow_last_video_res', '720P');
    }

    function takeSnapshot() {
        var snapshot = {};
        for (var i = 0; i < localStorage.length; i += 1) {
            var key = localStorage.key(i);
            if (!key) continue;
            if (key.indexOf('tapnow_') === 0) {
                snapshot[key] = localStorage.getItem(key) || '';
            }
        }
        return snapshot;
    }

    function snapshotHash(snapshot) {
        try {
            return JSON.stringify(snapshot);
        } catch (error) {
            return String(Date.now());
        }
    }

    function currentProjectName() {
        return localStorage.getItem('tapnow_project_name') || 'AI 画布';
    }

    function restoreSnapshot(snapshot) {
        if (!snapshot || typeof snapshot !== 'object') return;
        Object.keys(snapshot).forEach(function (key) {
            if (key.indexOf('tapnow_') === 0) {
                localStorage.setItem(key, snapshot[key] || '');
            }
        });
        injectProjectModels();
    }

    function saveProject(options) {
        var opts = Object.assign({ autosave: false, force: false }, options || {});
        if (saving) return Promise.resolve(null);
        var snapshot = takeSnapshot();
        var hash = snapshotHash(snapshot);
        if (opts.autosave && !opts.force && hash === lastSnapshotHash) {
            return Promise.resolve(null);
        }
        saving = true;
        updateStatus(opts.autosave ? '自动保存中...' : '保存中...', 'saving');
        return apiFetch(opts.autosave ? '/projects/autosave' : '/projects', {
            method: 'POST',
            body: JSON.stringify({
                id: getProjectId(),
                projectName: currentProjectName(),
                snapshot: snapshot,
                meta: {
                    savedAt: new Date().toISOString(),
                    source: opts.autosave ? 'autosave' : 'manual',
                    userAgent: navigator.userAgent
                }
            })
        }).then(function (project) {
            if (project && project.id) setProjectId(project.id);
            lastSnapshotHash = hash;
            updateStatus(opts.autosave ? '已自动保存' : '已保存', 'ok');
            return project;
        }).catch(function (error) {
            console.error('[AI Canvas] save failed', error);
            updateStatus('保存失败：' + (error.message || '未知错误'), 'error');
            throw error;
        }).finally(function () {
            saving = false;
        });
    }

    function bootstrapStorage() {
        injectProjectModels();
        var currentId = getProjectId();
        var loader = currentId
            ? apiFetch('/projects/' + currentId).catch(function () { return null; })
            : apiFetch('/projects/latest').catch(function () { return null; });
        return loader.then(function (project) {
            if (project && project.id && project.snapshot) {
                setProjectId(project.id);
                restoreSnapshot(project.snapshot);
                updateStatus('已载入：' + (project.projectName || 'AI 画布'), 'ok');
            } else {
                injectProjectModels();
                updateStatus('新画布', 'ok');
            }
            lastSnapshotHash = snapshotHash(takeSnapshot());
            return project;
        });
    }

    function loadFrame() {
        if (!frameElement) return;
        var src = frameElement.getAttribute('data-src') || 'ai-canvas/index.html';
        var separator = src.indexOf('?') === -1 ? '?' : '&';
        frameElement.src = src + separator + 't=' + Date.now();
    }

    function patchFrameFetch() {
        if (!frameElement || !frameElement.contentWindow) return;
        var win = frameElement.contentWindow;
        if (win.__aiCanvasFetchPatched || !win.fetch) return;
        var nativeFetch = win.fetch.bind(win);
        win.fetch = function (input, init) {
            var opts = init || {};
            var rawUrl = '';
            try {
                rawUrl = typeof input === 'string' ? input : (input && input.url) || '';
                var absolute = new URL(rawUrl, win.location.href);
                if (absolute.origin === win.location.origin && absolute.pathname.indexOf('/api/canvas/kie') === 0) {
                    var headers = new win.Headers((opts && opts.headers) || (input && input.headers) || {});
                    var token = getToken();
                    if (token) headers.set('X-User-Token', token);
                    opts = Object.assign({}, opts, { headers: headers });
                    if (typeof input !== 'string' && input instanceof win.Request) {
                        return nativeFetch(new win.Request(input, opts));
                    }
                }
            } catch (error) {
                return nativeFetch(input, init);
            }
            return nativeFetch(input, opts);
        };
        win.__aiCanvasFetchPatched = true;
    }

    function isApiSettingsLabel(label) {
        var text = String(label || '').replace(/\s+/g, ' ').trim();
        if (!text) return false;
        return /(模型接口配置|接口配置|API\s*(设置|配置|Key|Keys)|API配置|API设置|Provider|供应商|密钥)/i.test(text);
    }

    function controlLabel(el) {
        if (!el) return '';
        return [
            el.getAttribute('title') || '',
            el.getAttribute('aria-label') || '',
            el.textContent || ''
        ].join(' ').replace(/\s+/g, ' ').trim();
    }

    function hideApiSettingsControls(doc) {
        if (!doc || !doc.body) return;
        var candidates = doc.querySelectorAll('button, [role="button"], a, [title], [aria-label]');
        Array.prototype.forEach.call(candidates, function (el) {
            if (!el || el.getAttribute('data-ai-canvas-hidden-api-settings') === '1') return;
            if (!isApiSettingsLabel(controlLabel(el))) return;
            el.setAttribute('data-ai-canvas-hidden-api-settings', '1');
            el.style.display = 'none';
            el.style.pointerEvents = 'none';
        });
    }

    function patchFrameUi() {
        if (!frameElement || !frameElement.contentWindow) return;
        var win = frameElement.contentWindow;
        var doc = win.document;
        if (!doc || !doc.body) return;
        if (win.__aiCanvasUiPatchedDoc === doc) return;
        if (win.__aiCanvasUiObserver && typeof win.__aiCanvasUiObserver.disconnect === 'function') {
            win.__aiCanvasUiObserver.disconnect();
        }

        var scanTimer = null;
        var runScan = function () {
            if (scanTimer) win.clearTimeout(scanTimer);
            scanTimer = win.setTimeout(function () {
                hideApiSettingsControls(doc);
            }, 40);
        };

        hideApiSettingsControls(doc);
        if (win.MutationObserver) {
            var observer = new win.MutationObserver(runScan);
            observer.observe(doc.body, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['title', 'aria-label', 'class']
            });
            win.__aiCanvasUiObserver = observer;
        }
        for (var i = 1; i <= 8; i += 1) {
            win.setTimeout(function () {
                hideApiSettingsControls(doc);
            }, i * 500);
        }
        win.__aiCanvasUiPatchedDoc = doc;
    }

    function startAutosave() {
        if (autoSaveTimer) clearInterval(autoSaveTimer);
        autoSaveTimer = setInterval(function () {
            saveProject({ autosave: true }).catch(function () {});
        }, AUTOSAVE_INTERVAL_MS);
        document.addEventListener('visibilitychange', function () {
            if (document.visibilityState === 'hidden') {
                saveProject({ autosave: true }).catch(function () {});
            }
        });
        window.addEventListener('beforeunload', function () {
            saveProject({ autosave: true }).catch(function () {});
        });
    }

    function renderRecords(records) {
        var list = document.getElementById('canvasRecordsList');
        if (!list) return;
        if (!records || records.length === 0) {
            list.innerHTML = '<div class="canvas-record-empty">暂无保存记录</div>';
            return;
        }
        list.innerHTML = records.map(function (item) {
            var updatedAt = item.updatedAt ? new Date(item.updatedAt).toLocaleString('zh-CN') : '';
            return [
                '<div class="canvas-record-item" data-id="' + item.id + '">',
                '<div class="canvas-record-info">',
                '<div class="canvas-record-title">' + escapeHtml(item.projectName || 'AI 画布') + '</div>',
                '<div class="canvas-record-meta">' + escapeHtml(updatedAt) + '</div>',
                '</div>',
                '<div class="canvas-record-actions">',
                '<button type="button" data-action="open" data-id="' + item.id + '">打开</button>',
                '<button type="button" data-action="delete" data-id="' + item.id + '">删除</button>',
                '</div>',
                '</div>'
            ].join('');
        }).join('');
    }

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function openRecordsModal() {
        var modal = document.getElementById('canvasRecordsModal');
        if (modal) modal.hidden = false;
        updateStatus('读取保存记录...', 'saving');
        apiFetch('/projects')
            .then(function (records) {
                renderRecords(records || []);
                updateStatus('保存记录已更新', 'ok');
            })
            .catch(function (error) {
                renderRecords([]);
                updateStatus('读取失败：' + error.message, 'error');
            });
    }

    function closeRecordsModal() {
        var modal = document.getElementById('canvasRecordsModal');
        if (modal) modal.hidden = true;
    }

    function openTemplateModal() {
        var modal = document.getElementById('canvasTemplateModal');
        var nameInput = document.getElementById('canvasTemplateName');
        var categoryInput = document.getElementById('canvasTemplateCategory');
        var tagsInput = document.getElementById('canvasTemplateTags');
        var coverInput = document.getElementById('canvasTemplateCover');
        var descriptionInput = document.getElementById('canvasTemplateDescription');
        if (!modal) return;
        if (nameInput && !nameInput.value) nameInput.value = currentProjectName();
        if (categoryInput && !categoryInput.value) categoryInput.value = '未分类';
        if (tagsInput && !tagsInput.value) tagsInput.value = '';
        if (coverInput && !coverInput.value) coverInput.value = '';
        if (descriptionInput && !descriptionInput.value) descriptionInput.value = '';
        modal.hidden = false;
        if (nameInput) nameInput.focus();
    }

    function closeTemplateModal() {
        var modal = document.getElementById('canvasTemplateModal');
        if (modal) modal.hidden = true;
    }

    function parseTags(value) {
        return String(value || '')
            .split(/[,，]/)
            .map(function (item) { return item.trim(); })
            .filter(Boolean)
            .filter(function (item, index, arr) { return arr.indexOf(item) === index; });
    }

    function saveCurrentAsTemplate() {
        var nameInput = document.getElementById('canvasTemplateName');
        var categoryInput = document.getElementById('canvasTemplateCategory');
        var tagsInput = document.getElementById('canvasTemplateTags');
        var coverInput = document.getElementById('canvasTemplateCover');
        var descriptionInput = document.getElementById('canvasTemplateDescription');
        var templateName = nameInput ? nameInput.value.trim() : '';
        if (!templateName) {
            updateStatus('请填写模板名称', 'error');
            if (nameInput) nameInput.focus();
            return;
        }
        updateStatus('保存模板中...', 'saving');
        apiFetch('/templates', {
            method: 'POST',
            body: JSON.stringify({
                templateName: templateName,
                category: categoryInput ? categoryInput.value.trim() : '',
                tags: parseTags(tagsInput ? tagsInput.value : ''),
                coverImageUrl: coverInput ? coverInput.value.trim() : '',
                description: descriptionInput ? descriptionInput.value.trim() : '',
                snapshot: takeSnapshot(),
                meta: {
                    savedAt: new Date().toISOString(),
                    source: 'canvas-template',
                    projectId: getProjectId(),
                    userAgent: navigator.userAgent
                }
            })
        }).then(function () {
            closeTemplateModal();
            updateStatus('模板已保存', 'ok');
        }).catch(function (error) {
            updateStatus('模板保存失败：' + error.message, 'error');
        });
    }

    function openProject(id) {
        apiFetch('/projects/' + id).then(function (project) {
            if (!project || !project.snapshot) return;
            setProjectId(project.id);
            restoreSnapshot(project.snapshot);
            closeRecordsModal();
            loadFrame();
            updateStatus('已打开：' + (project.projectName || 'AI 画布'), 'ok');
        }).catch(function (error) {
            updateStatus('打开失败：' + error.message, 'error');
        });
    }

    function deleteProject(id) {
        if (!window.confirm('确定删除这条画布记录吗？')) return;
        apiFetch('/projects/' + id, { method: 'DELETE' }).then(function () {
            if (String(getProjectId()) === String(id)) setProjectId(null);
            openRecordsModal();
            updateStatus('已删除', 'ok');
        }).catch(function (error) {
            updateStatus('删除失败：' + error.message, 'error');
        });
    }

    function clearCanvasState() {
        var keys = [];
        for (var i = 0; i < localStorage.length; i += 1) {
            var key = localStorage.key(i);
            if (key && key.indexOf('tapnow_') === 0) keys.push(key);
        }
        keys.forEach(function (key) { localStorage.removeItem(key); });
        setProjectId(null);
        injectProjectModels();
        localStorage.setItem('tapnow_project_name', 'AI 画布 ' + new Date().toLocaleString('zh-CN'));
        lastSnapshotHash = snapshotHash(takeSnapshot());
        loadFrame();
        updateStatus('已新建画布', 'ok');
    }

    function bindUi() {
        var saveBtn = document.getElementById('canvasSaveBtn');
        var saveTemplateBtn = document.getElementById('canvasSaveTemplateBtn');
        var recordsBtn = document.getElementById('canvasRecordsBtn');
        var newBtn = document.getElementById('canvasNewBtn');
        var closeBtn = document.getElementById('canvasRecordsCloseBtn');
        var templateCloseBtn = document.getElementById('canvasTemplateCloseBtn');
        var templateCancelBtn = document.getElementById('canvasTemplateCancelBtn');
        var templateSaveBtn = document.getElementById('canvasTemplateSaveBtn');
        var modal = document.getElementById('canvasRecordsModal');
        var templateModal = document.getElementById('canvasTemplateModal');
        var list = document.getElementById('canvasRecordsList');

        if (saveBtn) saveBtn.addEventListener('click', function () {
            saveProject({ autosave: false, force: true }).catch(function () {});
        });
        if (saveTemplateBtn) saveTemplateBtn.addEventListener('click', function () {
            saveProject({ autosave: true, force: true }).finally(openTemplateModal);
        });
        if (recordsBtn) recordsBtn.addEventListener('click', openRecordsModal);
        if (newBtn) newBtn.addEventListener('click', function () {
            if (window.confirm('当前画布会先自动保存，然后新建空白画布。继续吗？')) {
                saveProject({ autosave: true, force: true }).finally(clearCanvasState);
            }
        });
        if (closeBtn) closeBtn.addEventListener('click', closeRecordsModal);
        if (templateCloseBtn) templateCloseBtn.addEventListener('click', closeTemplateModal);
        if (templateCancelBtn) templateCancelBtn.addEventListener('click', closeTemplateModal);
        if (templateSaveBtn) templateSaveBtn.addEventListener('click', saveCurrentAsTemplate);
        if (modal) modal.addEventListener('click', function (event) {
            if (event.target === modal) closeRecordsModal();
        });
        if (templateModal) templateModal.addEventListener('click', function (event) {
            if (event.target === templateModal) closeTemplateModal();
        });
        if (list) list.addEventListener('click', function (event) {
            var target = event.target;
            if (!target || !target.dataset) return;
            var id = target.dataset.id;
            if (!id) return;
            if (target.dataset.action === 'open') openProject(id);
            if (target.dataset.action === 'delete') deleteProject(id);
        });
    }

    function init(options) {
        frameElement = document.getElementById((options && options.frameId) || 'canvasFrame');
        bindUi();
        if (frameElement) {
            frameElement.addEventListener('load', function () {
                patchFrameFetch();
                patchFrameUi();
                updateStatus('画布已就绪', 'ok');
            });
        }
        bootstrapStorage()
            .then(function () {
                loadFrame();
                startAutosave();
            })
            .catch(function (error) {
                console.error('[AI Canvas] bootstrap failed', error);
                injectProjectModels();
                loadFrame();
                startAutosave();
                updateStatus('载入记录失败，已进入新画布', 'error');
            });
    }

    window.AiCanvasBridge = {
        init: init,
        reload: loadFrame,
        save: saveProject,
        records: openRecordsModal,
        template: openTemplateModal,
        injectProjectModels: injectProjectModels
    };
})();
