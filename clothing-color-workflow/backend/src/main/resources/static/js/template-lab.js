(function () {
    'use strict';

    var FAVORITES_KEY = 'templateLabFavorites';
    var RECENTS_KEY = 'templateLabRecentTemplates';
    var state = {
        templates: [],
        projects: [],
        selectedTemplate: null,
        category: '全部',
        search: '',
        templateSearch: '',
        usageType: '全部',
        ratioGroup: '全部',
        sourceMode: '全部',
        favorites: readStoredList(FAVORITES_KEY),
        recents: readStoredList(RECENTS_KEY)
    };

    var els = {
        projectGrid: document.getElementById('project-grid'),
        templateGrid: document.getElementById('template-grid'),
        projectCount: document.getElementById('project-count'),
        templateCount: document.getElementById('template-count'),
        templateFilter: document.getElementById('template-filter'),
        usageFilter: document.getElementById('template-usage-filter'),
        ratioFilter: document.getElementById('template-ratio-filter'),
        sourceFilter: document.getElementById('template-source-filter'),
        search: document.getElementById('project-search'),
        templateSearch: document.getElementById('template-search'),
        dialog: document.getElementById('create-dialog'),
        name: document.getElementById('project-name'),
        selectedSummary: document.getElementById('selected-template-summary'),
        createButton: document.getElementById('create-project'),
        toast: document.getElementById('lab-toast')
    };

    var icons = {
        edit: '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/></svg>',
        copy: '<svg aria-hidden="true" viewBox="0 0 24 24"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg>',
        trash: '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>',
        star: '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-2.9-5.6 2.9 1.1-6.2L3 9.6l6.2-.9Z"/></svg>'
    };

    function readStoredList(key) {
        try {
            var parsed = JSON.parse(localStorage.getItem(key) || '[]');
            return Array.isArray(parsed) ? parsed.map(String) : [];
        } catch (ignore) {
            return [];
        }
    }

    function storeList(key, value) {
        try { localStorage.setItem(key, JSON.stringify(value)); } catch (ignore) { /* storage is optional */ }
    }

    function apiData(response) {
        if (!response || !response.data || response.data.success !== true) {
            throw new Error(response && response.data && response.data.message ? response.data.message : '请求失败');
        }
        return response.data.data;
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }

    function showToast(message, error) {
        els.toast.textContent = message;
        els.toast.classList.toggle('is-error', Boolean(error));
        els.toast.classList.add('is-visible');
        clearTimeout(showToast.timer);
        showToast.timer = setTimeout(function () { els.toast.classList.remove('is-visible'); }, 3000);
    }

    function templateById(id) {
        return state.templates.find(function (item) { return item.id === id; });
    }

    function templatePreview(template) {
        if (template && template.thumbnailDataUrl) {
            return '<div class="lab-card__preview"><img src="' + escapeHtml(template.thumbnailDataUrl)
                + '" alt="' + escapeHtml(template.name) + ' 模板预览"></div>';
        }
        return miniCanvas(template);
    }

    function miniCanvas(template) {
        if (!template) return '<div class="lab-card__preview"></div>';
        var aspect = template.width / template.height;
        var style = aspect >= 1
            ? 'width:82%;aspect-ratio:' + template.width + '/' + template.height
            : 'height:86%;width:auto;aspect-ratio:' + template.width + '/' + template.height;
        var frames = (template.frames || []).map(function (frame) {
            var radius = frame.shape === 'circle' ? '50%' : ((frame.radius || 0) / frame.width * 100) + '%';
            var polygon = frame.shape === 'polygon' ? 'clip-path:polygon(0 0,100% 0,100% 88%,86% 100%,0 100%);' : '';
            return '<span class="lab-mini-frame" style="left:' + (frame.x / template.width * 100) + '%;top:'
                + (frame.y / template.height * 100) + '%;width:' + (frame.width / template.width * 100) + '%;height:'
                + (frame.height / template.height * 100) + '%;border-radius:' + radius + ';' + polygon + '"></span>';
        }).join('');
        var texts = (template.texts || []).map(function (text, index) {
            return '<span class="lab-mini-text" style="left:' + (text.x / template.width * 100) + '%;top:'
                + (text.y / template.height * 100) + '%;width:' + Math.min(48, text.width / template.width * 70) + '%;background:'
                + escapeHtml(text.fill || template.accent) + ';height:' + (index === 0 ? 4 : 2) + 'px"></span>';
        }).join('');
        return '<div class="lab-card__preview"><div class="lab-mini-canvas" style="' + style + ';background:'
            + escapeHtml(template.background) + '">' + frames + texts + '</div></div>';
    }

    function formatTime(value) {
        if (!value) return '刚刚更新';
        var date = new Date(value);
        if (Number.isNaN(date.getTime())) return '最近更新';
        return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date);
    }

    function renderProjectSkeletons() {
        els.projectGrid.innerHTML = '<div class="lab-skeleton"></div><div class="lab-skeleton"></div><div class="lab-skeleton"></div>';
        els.templateGrid.innerHTML = '<div class="lab-skeleton"></div><div class="lab-skeleton"></div><div class="lab-skeleton"></div>';
    }

    function renderProjects() {
        var search = state.search.trim().toLowerCase();
        var projects = state.projects.filter(function (project) {
            return !search || String(project.projectName || '').toLowerCase().includes(search);
        });
        els.projectCount.textContent = state.projects.length ? '共 ' + state.projects.length + ' 个草稿，编辑内容会自动保存。' : '还没有项目，从下方模板开始创建。';
        if (!projects.length) {
            els.projectGrid.innerHTML = '<div class="lab-empty"><div><strong>' + (search ? '没有匹配的项目' : '还没有拼图项目') + '</strong><span>'
                + (search ? '换一个关键词试试。' : '选择一个模板即可开始编辑。') + '</span></div></div>';
            return;
        }
        els.projectGrid.innerHTML = projects.map(function (project) {
            var template = templateById(project.templateId);
            var preview = project.thumbnailDataUrl
                ? '<div class="lab-card__preview"><img src="' + escapeHtml(project.thumbnailDataUrl) + '" alt="' + escapeHtml(project.projectName) + ' 项目预览"></div>'
                : miniCanvas(template);
            return '<article class="lab-card" data-project-id="' + project.id + '">' + preview
                + '<div class="lab-card__body"><div class="lab-card__title-row"><h3 class="lab-card__title">' + escapeHtml(project.projectName) + '</h3>'
                + '<span class="lab-card__badge">草稿</span></div>'
                + '<div class="lab-card__meta">' + escapeHtml(template ? template.name : project.templateId) + ' · '
                + project.canvasWidth + ' × ' + project.canvasHeight + ' · ' + formatTime(project.updatedAt) + '</div>'
                + '<div class="lab-card__actions">'
                + '<button class="lab-button lab-button--primary" type="button" data-action="edit">' + icons.edit + '继续编辑</button>'
                + '<button class="lab-icon-button" type="button" data-action="copy" aria-label="复制项目">' + icons.copy + '</button>'
                + '<button class="lab-icon-button" type="button" data-action="delete" aria-label="删除项目">' + icons.trash + '</button>'
                + '</div></div></article>';
        }).join('');
    }

    function filterButtons(values, current, attribute) {
        return values.map(function (value) {
            return '<button type="button" aria-pressed="' + (current === value) + '" ' + attribute + '="'
                + escapeHtml(value) + '">' + escapeHtml(value) + '</button>';
        }).join('');
    }

    function renderFilters() {
        var categories = ['全部'].concat(Array.from(new Set(state.templates.map(function (item) { return item.category; }).filter(Boolean))));
        var ratios = state.usageType === '副图' ? ['全部', '1:1', '3:4', '16:9']
            : state.usageType === '亚马逊 A+' ? ['全部', '2928:1200', '1200:900']
                : ['全部', '1:1', '3:4', '16:9', '2928:1200', '1200:900'];
        if (!ratios.includes(state.ratioGroup)) state.ratioGroup = '全部';
        els.usageFilter.innerHTML = filterButtons(['全部', '副图', '亚马逊 A+'], state.usageType, 'data-usage');
        els.ratioFilter.innerHTML = filterButtons(ratios, state.ratioGroup, 'data-ratio');
        els.sourceFilter.innerHTML = filterButtons(['全部', '系统', '个人', '收藏', '最近'], state.sourceMode, 'data-source');
        els.templateFilter.innerHTML = filterButtons(categories, state.category, 'data-category');
    }

    function filteredTemplates() {
        var query = state.templateSearch.trim().toLowerCase();
        var templates = state.templates.filter(function (template) {
            var searchable = [template.name, template.description, template.category, template.usageType, template.ratioGroup]
                .concat(template.tags || []).join(' ').toLowerCase();
            var sourceMatch = state.sourceMode === '全部'
                || (state.sourceMode === '系统' && template.source !== 'personal')
                || (state.sourceMode === '个人' && template.source === 'personal')
                || (state.sourceMode === '收藏' && state.favorites.includes(String(template.id)))
                || (state.sourceMode === '最近' && state.recents.includes(String(template.id)));
            return (!query || searchable.includes(query))
                && (state.category === '全部' || template.category === state.category)
                && (state.usageType === '全部' || template.usageType === state.usageType)
                && (state.ratioGroup === '全部' || template.ratioGroup === state.ratioGroup)
                && sourceMatch;
        });
        if (state.sourceMode === '最近') {
            templates.sort(function (left, right) { return state.recents.indexOf(String(left.id)) - state.recents.indexOf(String(right.id)); });
        }
        return templates;
    }

    function renderTemplates() {
        renderFilters();
        var templates = filteredTemplates();
        els.templateCount.textContent = '显示 ' + templates.length + ' / ' + state.templates.length + ' 个模板；仅支持副图 1:1、3:4、16:9 与 A+ 2928:1200、1200:900。';
        if (!templates.length) {
            els.templateGrid.innerHTML = '<div class="lab-empty"><div><strong>没有符合条件的模板</strong><span>清除搜索或切换用途、比例与来源。</span></div></div>';
            return;
        }
        els.templateGrid.innerHTML = templates.map(function (template) {
            var favorite = state.favorites.includes(String(template.id));
            var personal = template.source === 'personal';
            return '<article class="lab-card lab-template-card" data-template-id="' + escapeHtml(template.id) + '">'
                + templatePreview(template)
                + '<div class="lab-card__floating-actions"><button class="lab-icon-button lab-favorite' + (favorite ? ' is-active' : '')
                + '" type="button" data-action="favorite-template" aria-pressed="' + favorite + '" aria-label="' + (favorite ? '取消收藏' : '收藏模板') + '">' + icons.star + '</button>'
                + (personal ? '<button class="lab-icon-button" type="button" data-action="delete-template" aria-label="删除个人模板">' + icons.trash + '</button>' : '') + '</div>'
                + '<div class="lab-card__body"><div class="lab-card__title-row"><h3 class="lab-card__title">' + escapeHtml(template.name) + '</h3>'
                + '<span class="lab-card__badge ' + (personal ? 'lab-card__badge--personal' : '') + '">' + (personal ? '个人' : '系统') + '</span></div>'
                + '<div class="lab-card__meta"><strong>' + escapeHtml(template.usageType) + '</strong> · ' + escapeHtml(template.ratioGroup)
                + ' · ' + template.width + ' × ' + template.height + '</div>'
                + '<div class="lab-card__meta">' + (template.frames || []).length + ' 个相框 · ' + (template.texts || []).length + ' 个文字字段 · ' + escapeHtml(template.category) + '</div>'
                + '<p class="lab-card__description">' + escapeHtml(template.description) + '</p>'
                + '<div class="lab-card__actions"><button class="lab-button lab-button--primary" type="button" data-action="use-template">使用此模板</button></div>'
                + '</div></article>';
        }).join('');
    }

    function rememberTemplate(template) {
        state.recents = [String(template.id)].concat(state.recents.filter(function (id) { return id !== String(template.id); })).slice(0, 12);
        storeList(RECENTS_KEY, state.recents);
    }

    function openCreate(template) {
        if (!template) return;
        rememberTemplate(template);
        state.selectedTemplate = template;
        els.name.value = '';
        els.selectedSummary.innerHTML = templatePreview(template)
            + '<div><strong>' + escapeHtml(template.name) + '</strong><span>' + escapeHtml(template.usageType) + ' · '
            + escapeHtml(template.ratioGroup) + ' · ' + template.width + ' × ' + template.height + '</span><small>'
            + escapeHtml(template.description) + '</small></div>';
        els.dialog.showModal();
        setTimeout(function () { els.name.focus(); }, 0);
    }

    async function load() {
        renderProjectSkeletons();
        try {
            var responses = await Promise.all([
                axios.get('/api/template-lab/templates'),
                axios.get('/api/template-lab/projects')
            ]);
            state.templates = apiData(responses[0]) || [];
            state.projects = apiData(responses[1]) || [];
            renderProjects();
            renderTemplates();
        } catch (error) {
            var message = error.response && error.response.data ? error.response.data.message : error.message;
            els.projectGrid.innerHTML = '<div class="lab-error"><div><strong>项目读取失败</strong><span>' + escapeHtml(message) + '</span></div></div>';
            els.templateGrid.innerHTML = '<div class="lab-error"><div><strong>模板读取失败</strong><span>请刷新页面后重试。</span></div></div>';
        }
    }

    async function createProject() {
        if (!state.selectedTemplate) return;
        els.createButton.disabled = true;
        els.createButton.textContent = '正在创建...';
        try {
            var project = apiData(await axios.post('/api/template-lab/projects', {
                templateId: state.selectedTemplate.id,
                projectName: els.name.value.trim()
            }));
            window.location.href = 'template-lab-editor.html?id=' + encodeURIComponent(project.id);
        } catch (error) {
            showToast(error.response && error.response.data ? error.response.data.message : error.message, true);
            els.createButton.disabled = false;
            els.createButton.textContent = '创建并编辑';
        }
    }

    async function projectAction(action, id) {
        if (action === 'edit') {
            window.location.href = 'template-lab-editor.html?id=' + encodeURIComponent(id);
            return;
        }
        var project = state.projects.find(function (item) { return String(item.id) === String(id); });
        if (!project) return;
        if (action === 'delete' && !window.confirm('确认删除“' + project.projectName + '”？删除后无法恢复。')) return;
        try {
            if (action === 'copy') {
                var copy = apiData(await axios.post('/api/template-lab/projects/' + id + '/duplicate'));
                state.projects.unshift(copy);
                showToast('项目已复制');
            } else if (action === 'delete') {
                await axios.delete('/api/template-lab/projects/' + id);
                state.projects = state.projects.filter(function (item) { return String(item.id) !== String(id); });
                showToast('项目已删除');
            }
            renderProjects();
        } catch (error) {
            showToast(error.response && error.response.data ? error.response.data.message : error.message, true);
        }
    }

    function toggleFavorite(templateId) {
        var id = String(templateId);
        state.favorites = state.favorites.includes(id) ? state.favorites.filter(function (item) { return item !== id; }) : [id].concat(state.favorites);
        storeList(FAVORITES_KEY, state.favorites);
        renderTemplates();
    }

    async function deletePersonalTemplate(template) {
        if (!template || template.source !== 'personal') return;
        if (!window.confirm('确认删除个人模板“' + template.name + '”？已经创建的项目仍可继续编辑。')) return;
        try {
            await axios.delete('/api/template-lab/templates/personal/' + encodeURIComponent(template.personalTemplateId));
            state.templates = state.templates.filter(function (item) { return item.id !== template.id; });
            state.favorites = state.favorites.filter(function (id) { return id !== String(template.id); });
            storeList(FAVORITES_KEY, state.favorites);
            renderTemplates();
            showToast('个人模板已删除');
        } catch (error) {
            showToast(error.response && error.response.data ? error.response.data.message : error.message, true);
        }
    }

    document.getElementById('open-template-picker').addEventListener('click', function () {
        document.getElementById('templates-heading').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    els.search.addEventListener('input', function (event) { state.search = event.target.value; renderProjects(); });
    els.templateSearch.addEventListener('input', function (event) { state.templateSearch = event.target.value; renderTemplates(); });
    document.querySelector('.lab-template-tools').addEventListener('click', function (event) {
        var button = event.target.closest('button');
        if (!button) return;
        if (button.dataset.category) state.category = button.dataset.category;
        if (button.dataset.usage) { state.usageType = button.dataset.usage; state.ratioGroup = '全部'; }
        if (button.dataset.ratio) state.ratioGroup = button.dataset.ratio;
        if (button.dataset.source) state.sourceMode = button.dataset.source;
        renderTemplates();
    });
    els.templateGrid.addEventListener('click', function (event) {
        var button = event.target.closest('[data-action]');
        if (!button) return;
        var card = button.closest('[data-template-id]');
        var template = card ? templateById(card.dataset.templateId) : null;
        if (button.dataset.action === 'use-template') openCreate(template);
        if (button.dataset.action === 'favorite-template') toggleFavorite(card.dataset.templateId);
        if (button.dataset.action === 'delete-template') deletePersonalTemplate(template);
    });
    els.projectGrid.addEventListener('click', function (event) {
        var button = event.target.closest('[data-action]');
        if (!button) return;
        var card = button.closest('[data-project-id]');
        projectAction(button.dataset.action, card.dataset.projectId);
    });
    els.createButton.addEventListener('click', createProject);
    els.name.addEventListener('keydown', function (event) {
        if (event.key === 'Enter') { event.preventDefault(); createProject(); }
    });

    load();
})();
