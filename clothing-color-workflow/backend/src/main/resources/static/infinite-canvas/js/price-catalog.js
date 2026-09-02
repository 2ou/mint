(function () {
    'use strict';

    const state = {
        catalogue: null,
        rules: [],
        mediaType: 'image',
        readOnly: false,
        canEdit: false,
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
    function editable() { return state.canEdit && !state.readOnly; }
    function mediaLabel(mediaType) {
        return String(mediaType).toLowerCase() === 'video' ? '视频模型' : '图片模型';
    }
    // 官方顺序：对齐本系统 listKieModels 的接入清单 (PROJECT_IMAGE_MODELS + PROJECT_VIDEO_MODELS)。
    // 清单外的模型（如 nano-banana-2 / kling-* / seedance-2-mini）统一置底，方便与 KIE 官网价目表逐行对比。
    const OFFICIAL_MODEL_ORDER = [
        'nano-banana-pro',
        'gpt-image-2-image-to-image',
        'bytedance/seedance-2-5',
        'bytedance/seedance-2',
        'minimax-h3/text-to-video',
        'minimax-h3/image-to-video',
        'minimax-h3/reference-to-video'
    ];
    function officialRank(model) {
        const idx = OFFICIAL_MODEL_ORDER.indexOf(String(model || '').trim());
        return idx >= 0 ? idx : 9999;
    }
    function rulesForActiveMedia() {
        return state.rules.map(function (rule, index) { return {rule: rule, index: index}; })
            .filter(function (entry) { return String(entry.rule.media_type || '').toLowerCase() === state.mediaType; })
            .sort(function (a, b) {
                const ra = officialRank(a.rule.model), rb = officialRank(b.rule.model);
                if (ra !== rb) return ra - rb;
                const pa = Number(a.rule.priority || 0), pb = Number(b.rule.priority || 0);
                if (pa !== pb) return pb - pa;
                return Number(a.rule.id || 0) - Number(b.rule.id || 0);
            });
    }
    function countRules(mediaType) {
        return state.rules.filter(function (rule) {
            return String(rule.media_type || '').toLowerCase() === mediaType;
        }).length;
    }
    function close() { overlay()?.remove(); }
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
            state.rules = Array.isArray(data.rules) ? data.rules.map(function (rule) { return Object.assign({}, rule); }) : [];
            state.canEdit = Boolean(data.can_edit);
        } catch (error) {
            state.error = error?.message || '读取模型价格失败';
            state.catalogue = null;
            state.rules = [];
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
    function updateRule(index, field, value) {
        const rule = state.rules[index];
        if (!rule || !editable()) return;
        rule[field] = ['unit_price_cny', 'base_price_cny', 'priority'].includes(field) ? Number(value || 0) : value;
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
        render();
    }
    function removeRule(index) {
        if (!editable()) return;
        state.rules.splice(index, 1);
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
            state.rules = Array.isArray(data.rules) ? data.rules.map(function (rule) { return Object.assign({}, rule); }) : [];
            state.notice = '模型价格已保存；之后提交的新任务会立即使用这套价格，历史任务仍保留原报价。';
        } catch (error) {
            state.error = error?.message || '保存模型价格失败';
        } finally {
            state.busy = false;
            render();
        }
    }

    function input(index, field, value, type, enabled) {
        return '<input ' + (enabled ? '' : 'readonly ') + 'type="' + type + '" value="' + escapeHtml(value) + '" data-pc-index="' + index + '" data-pc-field="' + field + '">';
    }
    function mediaSelect(index, value, enabled) {
        const current = String(value || 'image').toLowerCase();
        return '<select ' + (enabled ? '' : 'disabled ') + 'data-pc-index="' + index + '" data-pc-field="media_type">'
            + '<option value="image" ' + (current === 'image' ? 'selected' : '') + '>图片</option>'
            + '<option value="video" ' + (current === 'video' ? 'selected' : '') + '>视频</option></select>';
    }
    function ruleRows(canEdit) {
        const entries = rulesForActiveMedia();
        if (!entries.length) {
            return '<tr><td colspan="' + (canEdit ? 9 : 8) + '" class="price-catalog-empty">暂无' + mediaLabel(state.mediaType) + '价格规则。' + (canEdit ? '可点击“新增规则”添加。' : '') + '</td></tr>';
        }
        return entries.map(function (entry) {
            const rule = entry.rule;
            const index = entry.index;
            const rate = String(rule.rate_unit || 'PER_IMAGE');
            const rateSelect = '<select ' + (canEdit ? '' : 'disabled ') + 'data-pc-index="' + index + '" data-pc-field="rate_unit">'
                + '<option value="PER_IMAGE" ' + (rate === 'PER_IMAGE' ? 'selected' : '') + '>每张</option>'
                + '<option value="PER_TASK" ' + (rate === 'PER_TASK' ? 'selected' : '') + '>每任务</option>'
                + '<option value="PER_SECOND" ' + (rate === 'PER_SECOND' ? 'selected' : '') + '>每秒</option></select>';
            return '<tr><td>' + input(index, 'display_name', rule.display_name, 'text', canEdit) + '</td>'
                + '<td>' + mediaSelect(index, rule.media_type, canEdit) + '</td>'
                + '<td>' + input(index, 'model', rule.model, 'text', canEdit) + '</td>'
                + '<td>' + input(index, 'resolution', rule.resolution, 'text', canEdit) + '</td>'
                + '<td>' + input(index, 'input_mode', rule.input_mode, 'text', canEdit) + '</td>'
                + '<td>' + rateSelect + '</td><td>' + input(index, 'unit_price_cny', rule.unit_price_cny, 'number', canEdit) + '</td>'
                + '<td>' + input(index, 'priority', rule.priority, 'number', canEdit) + '</td>'
                + (canEdit ? '<td><button type="button" class="price-catalog-remove" data-pc-remove="' + index + '" aria-label="删除规则"><i data-lucide="trash-2" class="w-3.5 h-3.5"></i></button></td>' : '') + '</tr>';
        }).join('');
    }
    function render() {
        const root = overlay();
        if (!root) return;
        const canEdit = editable();
        const disabled = state.busy ? 'disabled' : '';
        const mediaTypes = ['image', 'video'].map(function (mediaType) {
            const active = state.mediaType === mediaType ? ' active' : '';
            return '<button type="button" class="price-catalog-media' + active + '" data-pc-media="' + mediaType + '"><span>' + mediaLabel(mediaType) + '</span><b>' + countRules(mediaType) + '</b></button>';
        }).join('');
        const actions = canEdit
            ? '<button type="button" class="action-btn" data-pc-add ' + disabled + '>新增规则</button><button type="button" class="action-btn primary-btn" data-pc-save ' + disabled + '>保存价格</button>'
            : '';
        const accountNotice = canEdit
            ? '<p class="price-catalog-readonly">这里维护系统唯一的一套模型价格。</p>'
            : '<p class="price-catalog-readonly">价格由系统管理员统一维护；此处仅供查看。</p>';
        root.innerHTML = '<section class="price-catalog-dialog" role="dialog" aria-modal="true" aria-labelledby="priceCatalogTitle">'
            + '<header class="price-catalog-head"><div><p>统一计价 · 人民币</p><h2 id="priceCatalogTitle">模型价格目录</h2><span>维护一套当前价格。保存后影响新任务预估；历史任务保留原报价。</span></div><button type="button" class="price-catalog-close" data-pc-close aria-label="关闭"><i data-lucide="x" class="w-4 h-4"></i></button></header>'
            + '<div class="price-catalog-layout"><aside><div class="price-catalog-side-title">计费类型</div><div class="price-catalog-media-list">' + mediaTypes + '</div>' + accountNotice + '</aside><div class="price-catalog-main">'
            + (state.catalogue ? '<div class="price-catalog-selection"><div><b>当前模型价格</b><small>1 积分 = ¥' + escapeHtml(creditToCny().toFixed(3)) + '</small></div><div class="price-catalog-actions">' + actions + '</div></div><div class="price-catalog-section-title">' + mediaLabel(state.mediaType) + '价格 <span>' + countRules(state.mediaType) + ' 条规则</span></div>'
                + (state.error ? '<div class="price-catalog-error">' + escapeHtml(state.error) + '</div>' : '')
                + (state.notice ? '<div class="price-catalog-notice">' + escapeHtml(state.notice) + '</div>' : '')
                + '<div class="price-catalog-table-wrap"><table class="price-catalog-table"><thead><tr><th>显示名</th><th>媒体</th><th>模型 ID</th><th>规格</th><th>输入方式</th><th>计费单位</th><th>单价（元）</th><th>优先级</th>' + (canEdit ? '<th></th>' : '') + '</tr></thead><tbody>' + ruleRows(canEdit) + '</tbody></table></div>'
                : '<div class="price-catalog-empty">' + (state.busy ? '正在读取模型价格…' : '未找到可用的模型价格。') + '</div>')
            + '</div></div><footer><span>' + (state.busy ? '正在处理…' : '所有金额均以人民币（¥）展示。') + '</span><button type="button" class="action-btn" data-pc-close>关闭</button></footer></section>';
        root.querySelectorAll('[data-pc-close]').forEach(function (button) { button.addEventListener('click', close); });
        root.onmousedown = function (event) { if (event.target === root) close(); };
        root.querySelectorAll('[data-pc-media]').forEach(function (button) { button.addEventListener('click', function () { chooseMedia(button.dataset.pcMedia); }); });
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
        state.canEdit = false;
        state.catalogue = null;
        state.rules = [];
        state.error = '';
        state.notice = '';
        const root = document.createElement('div');
        root.id = 'priceCatalogOverlay';
        root.className = 'price-catalog-overlay';
        // 若页面存在可见的全局侧边栏（菜单栏），让遮罩从侧边栏右侧起，避免盖住菜单
        const applySidebarOffset = function () {
            const sidebarEl = document.querySelector('.sidebar-global');
            if (sidebarEl && sidebarEl.getBoundingClientRect().width > 0) {
                root.classList.add('price-catalog-overlay--with-sidebar');
                return true;
            }
            return false;
        };
        if (!applySidebarOffset()) {
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
        document.body.appendChild(root);
        load();
    };
    document.addEventListener('keydown', function (event) { if (event.key === 'Escape' && overlay()) close(); });
}());
