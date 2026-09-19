(function () {
    'use strict';

    const state = {
        catalogue: null,
        rules: [],
        mediaType: 'image',
        readOnly: false,
        embedded: false,
        canEdit: false,
        editing: false,
        dirty: false,
        originalRules: [],
        busy: false,
        error: '',
        notice: ''
    };

    function overlay() { return document.getElementById('priceCatalogOverlay'); }
    function escapeHtml(value) {
        return String(value == null ? '' : value).replace(/[&<>"']/g, function (char) {
            return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char];
        });
    }
    function copyRules(rules) { return rules.map(function (rule) { return Object.assign({}, rule); }); }
    function canManage() { return state.canEdit && !state.readOnly; }
    function editable() { return canManage() && state.editing; }
    function mediaLabel(mediaType) {
        return String(mediaType).toLowerCase() === 'video' ? '视频模型' : '图片模型';
    }
    // 画布内的价目表与系统管理页使用同一套 KIE 目录顺序。
    // 未登记模型仍可显示，统一归入末尾的“其他手动规则”。
    const KIE_MODEL_CATALOGUE = [
        { model: 'nano-banana-pro', label: 'Nano Banana Pro', family: 'Nano Banana', vendor: 'Google', media: 'image', rank: 10 },
        { model: 'nano-banana-2', label: 'Nano Banana 2', family: 'Nano Banana', vendor: 'Google', media: 'image', rank: 20 },
        { model: 'gpt-image-2-image-to-image', label: 'GPT Image 2 · 图生图', family: 'GPT Image 2', vendor: 'OpenAI', media: 'image', rank: 30 },
        { model: 'gpt-image-2-5-sunburst-text-to-image', label: 'GPT Image 2.5 · 文生图', family: 'GPT Image 2.5', vendor: 'OpenAI', media: 'image', rank: 50 },
        { model: 'gpt-image-2-5-sunburst-image-to-image', label: 'GPT Image 2.5 · 图生图', family: 'GPT Image 2.5', vendor: 'OpenAI', media: 'image', rank: 51 },
        { model: 'bytedance/seedance-2-5', label: 'Seedance 2.5', family: 'Seedance 2.5', vendor: 'ByteDance', media: 'video', rank: 10 },
        { model: 'bytedance/seedance-2', label: 'Seedance 2', family: 'Seedance 2', vendor: 'ByteDance', media: 'video', rank: 20 },
        { model: 'bytedance/seedance-2-mini', label: 'Seedance 2 Mini', family: 'Seedance 2 Mini', vendor: 'ByteDance', media: 'video', rank: 30 },
        { model: 'minimax-h3/text-to-video', label: 'MiniMax H3 · 文生视频', family: 'MiniMax H3', vendor: 'MiniMax', media: 'video', rank: 40 },
        { model: 'minimax-h3/image-to-video', label: 'MiniMax H3 · 图生视频', family: 'MiniMax H3', vendor: 'MiniMax', media: 'video', rank: 41 },
        { model: 'minimax-h3/reference-to-video', label: 'MiniMax H3 · 参考生视频', family: 'MiniMax H3', vendor: 'MiniMax', media: 'video', rank: 42 },
        { model: 'minimax-h3/image-input', label: 'MiniMax H3 · 图片输入', family: 'MiniMax H3', vendor: 'MiniMax', media: 'video', rank: 43 },
        { model: 'kling-3.0/video', label: 'Kling 3.0', family: 'Kling 3.0', vendor: 'Kling', media: 'video', rank: 50 },
        { model: 'kling-3.0/motion-control', label: 'Kling 3.0 · Motion Control', family: 'Kling 3.0', vendor: 'Kling', media: 'video', rank: 51 },
        { model: 'kling/v3-turbo-image-to-video', label: 'Kling 3.0 · Turbo 图生视频', family: 'Kling 3.0', vendor: 'Kling', media: 'video', rank: 52 }
    ];
    function modelKey(value) { return String(value || '').trim().toLowerCase(); }
    function modelMeta(value) {
        return KIE_MODEL_CATALOGUE.find(function (item) { return item.model === modelKey(value); }) || {
            model: String(value || '').trim(),
            label: String(value || '').trim() || '未命名模型',
            family: '其他手动规则',
            vendor: '其他',
            media: '',
            rank: 9999
        };
    }
    function resolutionRank(value) {
        return ({
            '4k': 10, '2k': 20, '1k': 30,
            '1080p': 10, '768p': 20, '720p': 30, '480p': 40,
            'pro': 50, 'standard': 60
        })[modelKey(value)] ?? 90;
    }
    function inputModeRank(value) {
        return ({
            'video': 10, 'image_input': 20, 'image-to-video': 30,
            'image_to_video': 30, 'reference_to_video': 40, 'text_to_video': 50,
            'audio': 60
        })[modelKey(value)] ?? 90;
    }
    function compareKieCatalogue(left, right) {
        const a = modelMeta(left.model), b = modelMeta(right.model);
        return a.rank - b.rank
            || a.vendor.localeCompare(b.vendor)
            || a.family.localeCompare(b.family)
            || resolutionRank(left.resolution) - resolutionRank(right.resolution)
            || inputModeRank(left.input_mode) - inputModeRank(right.input_mode)
            || Number(right.priority || 0) - Number(left.priority || 0)
            || Number(left.id || 0) - Number(right.id || 0);
    }
    function rulesForActiveMedia() {
        return state.rules.map(function (rule, index) { return {rule: rule, index: index}; })
            .filter(function (entry) { return String(entry.rule.media_type || '').toLowerCase() === state.mediaType; })
            .sort(function (a, b) {
                return compareKieCatalogue(a.rule, b.rule);
            });
    }
    function countRules(mediaType) {
        return state.rules.filter(function (rule) {
            return String(rule.media_type || '').toLowerCase() === mediaType;
        }).length;
    }
    function close() {
        const root = overlay();
        // 系统管理页内嵌目录不是临时弹窗，不能被 Esc 或关闭按钮误删。
        if (root?.dataset.embedded === 'true') return;
        root?.remove();
    }
    function refreshIcons() { window.lucide?.createIcons(); }
    function creditToCny() {
        const value = state.catalogue?.credit_to_cny ?? state.catalogue?.creditToCny ?? 0.032;
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : 0.032;
    }

    async function load() {
        state.busy = true;
        state.error = '';
        render();
        try {
            const response = await fetch('/api/admin/model-prices');
            if (!response.ok) throw new Error((await response.text()) || '读取模型价格失败');
            const data = await response.json();
            state.catalogue = data.catalogue || null;
            state.rules = Array.isArray(data.rules) ? copyRules(data.rules) : [];
            state.originalRules = copyRules(state.rules);
            state.canEdit = Boolean(data.can_edit);
            state.editing = false;
            state.dirty = false;
        } catch (error) {
            state.error = error?.message || '读取模型价格失败';
            state.catalogue = null;
            state.rules = [];
            state.originalRules = [];
        } finally {
            state.busy = false;
            render();
        }
    }

    function chooseMedia(mediaType) {
        if (state.busy || !['image', 'video'].includes(mediaType)) return;
        state.mediaType = mediaType;
        state.notice = '';
        render();
    }
    function beginEdit() {
        if (!canManage() || state.busy) return;
        state.editing = true;
        state.notice = '';
        state.error = '';
        render();
    }
    function cancelEdit() {
        if (!editable() || state.busy) return;
        state.rules = copyRules(state.originalRules);
        state.editing = false;
        state.dirty = false;
        state.error = '';
        state.notice = '未保存的修改已撤销。';
        render();
    }
    function updateRule(index, field, value) {
        const rule = state.rules[index];
        if (!rule || !editable()) return;
        rule[field] = ['unit_price_cny', 'base_price_cny', 'priority'].includes(field) ? Number(value || 0) : value;
        state.dirty = true;
    }
    function addRule() {
        if (!editable()) return;
        state.rules.push({
            provider: 'kie',
            media_type: state.mediaType,
            model: '',
            resolution: '',
            input_mode: '',
            rate_unit: state.mediaType === 'video' ? 'PER_SECOND' : 'PER_IMAGE',
            unit_price_cny: 0,
            base_price_cny: 0,
            priority: 10,
            active: true,
            display_name: '新价格规则'
        });
        state.dirty = true;
        render();
    }
    function removeRule(index) {
        if (!editable()) return;
        state.rules.splice(index, 1);
        state.dirty = true;
        render();
    }
    function rulePayload(rule) {
        return {
            provider: String(rule.provider || 'kie').trim(),
            media_type: String(rule.media_type || '').trim(),
            model: String(rule.model || '').trim(),
            resolution: String(rule.resolution || '').trim(),
            input_mode: String(rule.input_mode || '').trim(),
            rate_unit: String(rule.rate_unit || 'PER_IMAGE').trim(),
            unit_price_cny: Number(rule.unit_price_cny || 0),
            base_price_cny: Number(rule.base_price_cny || 0),
            priority: Number(rule.priority || 0),
            active: rule.active !== false,
            display_name: String(rule.display_name || '').trim()
        };
    }
    async function save() {
        if (!state.catalogue || !editable() || state.busy) return;
        if (state.rules.some(function (rule) { return !String(rule.media_type || '').trim() || !String(rule.model || '').trim(); })) {
            state.error = '每条规则都需要填写媒体类型和模型 ID';
            render();
            return;
        }
        state.busy = true;
        state.error = '';
        state.notice = '';
        render();
        try {
            const response = await fetch('/api/admin/model-prices/rules', {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({rules: state.rules.map(rulePayload)})
            });
            if (!response.ok) throw new Error((await response.text()) || '保存模型价格失败');
            const data = await response.json();
            state.rules = Array.isArray(data.rules) ? copyRules(data.rules) : [];
            state.originalRules = copyRules(state.rules);
            state.editing = false;
            state.dirty = false;
            state.notice = '模型价格已保存；之后提交的新任务会立即使用这套价格，历史任务仍保留原报价。';
        } catch (error) {
            state.error = error?.message || '保存模型价格失败';
        } finally {
            state.busy = false;
            render();
        }
    }

    function valueOrDash(value) {
        const text = String(value == null ? '' : value).trim();
        return text ? escapeHtml(text) : '<span class="price-catalog-dash">—</span>';
    }
    function fieldLabel(field) {
        return ({
            display_name: '模型显示名',
            model: '模型 ID',
            resolution: '规格',
            input_mode: '输入方式',
            unit_price_cny: '单价（元）'
        })[field] || field;
    }
    function input(index, field, value, type, enabled, className) {
        const cssClass = className ? ' class="' + className + '"' : '';
        const numericAttrs = type === 'number' ? (field === 'unit_price_cny' ? ' min="0" step="0.001"' : ' min="0" step="1"') : '';
        if (!enabled) return '<span' + cssClass + '>' + valueOrDash(value) + '</span>';
        return '<input' + cssClass + ' type="' + type + '"' + numericAttrs + ' value="' + escapeHtml(value) + '" aria-label="' + fieldLabel(field) + '" data-pc-index="' + index + '" data-pc-field="' + field + '">';
    }
    function rateLabel(rate) {
        return rate === 'PER_SECOND' ? '每秒' : rate === 'PER_TASK' ? '每任务' : '每张';
    }
    function rateControl(index, rate, enabled) {
        if (!enabled) return '<span class="price-catalog-rate-unit">' + rateLabel(rate) + '</span>';
        return '<select class="price-catalog-rate-select" aria-label="计费单位" data-pc-index="' + index + '" data-pc-field="rate_unit">'
            + '<option value="PER_IMAGE" ' + (rate === 'PER_IMAGE' ? 'selected' : '') + '>每张</option>'
            + '<option value="PER_TASK" ' + (rate === 'PER_TASK' ? 'selected' : '') + '>每任务</option>'
            + '<option value="PER_SECOND" ' + (rate === 'PER_SECOND' ? 'selected' : '') + '>每秒</option></select>';
    }
    function ruleRows(canEdit) {
        const entries = rulesForActiveMedia();
        if (!entries.length) {
            return '<tr><td colspan="' + (canEdit ? 6 : 5) + '" class="price-catalog-empty">暂无' + mediaLabel(state.mediaType) + '价格规则。' + (canEdit ? '可点击“新增规则”添加。' : '') + '</td></tr>';
        }
        return entries.map(function (entry, entryPosition) {
            const rule = entry.rule;
            const index = entry.index;
            const rate = String(rule.rate_unit || 'PER_IMAGE');
            const meta = modelMeta(rule.model);
            const previousMeta = entryPosition > 0 ? modelMeta(entries[entryPosition - 1].rule.model) : null;
            const groupStart = previousMeta && (previousMeta.vendor !== meta.vendor || previousMeta.family !== meta.family) ? ' class="price-catalog-group-start"' : '';
            return '<tr' + groupStart + '><td class="price-catalog-family-cell"><div class="price-catalog-family"><strong>' + escapeHtml(meta.family) + '</strong><span>' + escapeHtml(meta.vendor) + ' · KIE</span></div></td>'
                + '<td class="price-catalog-model-cell"><div class="price-catalog-model-name">' + input(index, 'display_name', rule.display_name || meta.label, 'text', canEdit, 'price-catalog-name-input') + '</div>'
                + '<div class="price-catalog-model-id">' + input(index, 'model', rule.model, 'text', canEdit, 'price-catalog-id-input') + '</div></td>'
                + '<td class="price-catalog-spec-cell"><div><span>规格</span>' + input(index, 'resolution', rule.resolution, 'text', canEdit, 'price-catalog-compact-input') + '</div>'
                + '<div><span>输入</span>' + input(index, 'input_mode', rule.input_mode, 'text', canEdit, 'price-catalog-compact-input') + '</div></td>'
                + '<td class="price-catalog-price-cell"><span class="price-catalog-cell-label">单价</span><div class="price-catalog-price-field"><b>¥</b>' + input(index, 'unit_price_cny', rule.unit_price_cny, 'number', canEdit, 'price-catalog-price-input') + '</div></td>'
                + '<td class="price-catalog-unit-cell"><span class="price-catalog-cell-label">计费</span>' + rateControl(index, rate, canEdit) + '</td>'
                + (canEdit ? '<td><button type="button" class="price-catalog-remove" data-pc-remove="' + index + '" aria-label="删除规则"><i data-lucide="trash-2" class="w-3.5 h-3.5"></i></button></td>' : '') + '</tr>';
        }).join('');
    }
    function render() {
        const root = overlay();
        if (!root) return;
        const embedded = state.embedded;
        const canEdit = editable();
        const canManagePrices = canManage();
        const disabled = state.busy ? 'disabled' : '';
        root.classList.toggle('price-catalog--editing', state.editing);
        const mediaTypes = ['image', 'video'].map(function (mediaType) {
            const active = state.mediaType === mediaType ? ' active' : '';
            return '<button type="button" class="price-catalog-media' + active + '" aria-pressed="' + (state.mediaType === mediaType ? 'true' : 'false') + '" data-pc-media="' + mediaType + '"><span>' + mediaLabel(mediaType) + '</span><b>' + countRules(mediaType) + '</b></button>';
        }).join('');
        const actions = !canManagePrices ? '' : (state.editing
            ? '<button type="button" class="action-btn price-catalog-secondary-btn" data-pc-cancel ' + disabled + '>取消</button><button type="button" class="action-btn price-catalog-secondary-btn" data-pc-add ' + disabled + '>新增规则</button><button type="button" class="action-btn primary-btn" data-pc-save ' + disabled + '>' + (state.busy ? '保存中…' : '保存价格') + '</button>'
            : '<button type="button" class="action-btn primary-btn" data-pc-edit ' + disabled + '><i data-lucide="square-pen" class="w-3.5 h-3.5" aria-hidden="true"></i>编辑价格</button>');
        const accountNotice = canManagePrices
            ? '<div class="price-catalog-side-note"><i data-lucide="shield-check" class="w-4 h-4" aria-hidden="true"></i><span>这里维护系统唯一的一套模型价格。保存后，只影响之后提交的新任务。</span></div>'
            : '<p class="price-catalog-readonly">价格由系统管理员统一维护；此处仅供查看。</p>';
        const headingKicker = embedded
            ? '<p class="price-catalog-breadcrumb"><span>系统管理</span><b>/</b><span>模型价格</span></p>'
            : '<p>统一计价 · 人民币</p>';
        const overview = state.catalogue ? '<div class="price-catalog-overview"><div><span>积分换算</span><strong>1 积分 = ¥' + escapeHtml(creditToCny().toFixed(3)) + '</strong></div><div><span>图片规则</span><strong>' + countRules('image') + ' <small>条</small></strong></div><div><span>视频规则</span><strong>' + countRules('video') + ' <small>条</small></strong></div></div>' : '';
        const closeButton = embedded ? '' : '<button type="button" class="price-catalog-close" data-pc-close aria-label="关闭"><i data-lucide="x" class="w-4 h-4"></i></button>';
        const closeFooter = embedded ? '' : '<button type="button" class="action-btn" data-pc-close>关闭</button>';
        root.innerHTML = '<section class="price-catalog-dialog" role="' + (embedded ? 'region' : 'dialog') + '"' + (embedded ? '' : ' aria-modal="true"') + ' aria-labelledby="priceCatalogTitle">'
            + '<header class="price-catalog-head"><div>' + headingKicker + '<h2 id="priceCatalogTitle">模型价格</h2><span>维护当前生效的价格规则；保存后立即用于新任务预估，历史任务始终保留提交时的报价。</span></div>' + closeButton + '</header>'
            + overview
            + '<div class="price-catalog-layout"><aside><div class="price-catalog-side-title">模型类型</div><div class="price-catalog-media-list">' + mediaTypes + '</div>' + accountNotice + '</aside><div class="price-catalog-main">'
            + (state.catalogue ? '<div class="price-catalog-selection"><div><span class="price-catalog-selection-label">当前分类</span><b>' + mediaLabel(state.mediaType) + '</b><small>' + countRules(state.mediaType) + ' 条计费规则' + (state.editing && state.dirty ? ' · 有未保存修改' : '') + '</small></div><div class="price-catalog-actions">' + actions + '</div></div>'
                + (state.error ? '<div class="price-catalog-error" role="alert">' + escapeHtml(state.error) + '</div>' : '')
                + (state.notice ? '<div class="price-catalog-notice" role="status">' + escapeHtml(state.notice) + '</div>' : '')
                + '<p class="price-catalog-order-note">按 KIE 模型目录排序：厂商 → 模型系列 → 规格 / 输入方式。</p>'
                + '<div class="price-catalog-table-wrap"><table class="price-catalog-table"><thead><tr><th>厂商 / 模型系列</th><th>计费规则</th><th>规格与输入</th><th>价格</th><th>计费单位</th>' + (canEdit ? '<th><span class="sr-only">操作</span></th>' : '') + '</tr></thead><tbody>' + ruleRows(canEdit) + '</tbody></table></div>'
                : '<div class="price-catalog-empty">' + (state.busy ? '正在读取模型价格…' : '未找到可用的模型价格。') + '</div>')
            + '</div></div><footer><span>' + (state.busy ? '正在处理…' : '所有金额均以人民币（¥）展示。') + '</span>' + closeFooter + '</footer></section>';
        root.querySelectorAll('[data-pc-close]').forEach(function (button) { button.addEventListener('click', close); });
        root.onmousedown = function (event) { if (!embedded && event.target === root) close(); };
        root.querySelectorAll('[data-pc-media]').forEach(function (button) { button.addEventListener('click', function () { chooseMedia(button.dataset.pcMedia); }); });
        root.querySelector('[data-pc-edit]')?.addEventListener('click', beginEdit);
        root.querySelector('[data-pc-cancel]')?.addEventListener('click', cancelEdit);
        root.querySelector('[data-pc-add]')?.addEventListener('click', addRule);
        root.querySelector('[data-pc-save]')?.addEventListener('click', save);
        root.querySelectorAll('[data-pc-remove]').forEach(function (button) { button.addEventListener('click', function () { removeRule(Number(button.dataset.pcRemove)); }); });
        root.querySelectorAll('[data-pc-index][data-pc-field]').forEach(function (field) {
            const update = function () { updateRule(Number(field.dataset.pcIndex), field.dataset.pcField, field.value); };
            field.addEventListener('input', update);
            field.addEventListener('change', update);
        });
        refreshIcons();
    }

    window.openPriceCatalog = function (options) {
        if (overlay()) return;
        state.readOnly = Boolean(options && options.readOnly);
        state.embedded = Boolean(options && options.embedded);
        state.canEdit = false;
        state.editing = false;
        state.dirty = false;
        state.catalogue = null;
        state.rules = [];
        state.originalRules = [];
        state.error = '';
        state.notice = '';
        const root = document.createElement('div');
        root.id = 'priceCatalogOverlay';
        root.className = 'price-catalog-overlay' + (state.embedded ? ' price-catalog-overlay--embedded' : '');
        if (state.embedded) root.dataset.embedded = 'true';
        // 若页面存在可见的全局侧边栏（菜单栏），让遮罩从侧边栏右侧起，避免盖住菜单
        const applySidebarOffset = function () {
            const sidebarEl = document.querySelector('.sidebar-global');
            if (sidebarEl && sidebarEl.getBoundingClientRect().width > 0) {
                root.classList.add('price-catalog-overlay--with-sidebar');
                return true;
            }
            return false;
        };
        if (!state.embedded && !applySidebarOffset()) {
            // 侧栏可能尚未由 sidebar.js 注入完成（时序竞态 / auth 异步），用 rAF + 短延时 + MutationObserver 兜底，
            // 确保侧栏一旦出现在 DOM 且可见，遮罩立即让出左侧，避免盖住菜单
            requestAnimationFrame(applySidebarOffset);
            setTimeout(applySidebarOffset, 150);
            if (typeof MutationObserver !== 'undefined') {
                var mo = new MutationObserver(function () { if (applySidebarOffset()) mo.disconnect(); });
                mo.observe(document.body, { childList: true, subtree: true });
                setTimeout(function () { mo.disconnect(); }, 3000);
            }
        }
        const mount = state.embedded ? document.querySelector(options?.mount || '#priceManagementCatalog') : null;
        (mount || document.body).appendChild(root);
        load();
    };
    document.addEventListener('keydown', function (event) { if (event.key === 'Escape' && overlay()) close(); });
}());
