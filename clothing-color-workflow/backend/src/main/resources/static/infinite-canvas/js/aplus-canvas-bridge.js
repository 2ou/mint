(function () {
    'use strict';

    const DEFAULT_MOBILE_INSTRUCTION = '保留主体、产品、风格和关键信息；重新构图为适合手机端的 4:3 画面。';
    const WEB_X = -700;
    const MOBILE_X = 1050;
    const WEB_GAP_Y = 330;
    const APLUS_POLL_MS = 5000;
    const MOBILE_POLL_MS = 4000;
    let aplusProjectId = new URLSearchParams(window.location.search).get('aplusProjectId') || '';
    let projectPollTimer = null;
    let mobilePollTimer = null;
    let modal = null;

    function text(value) {
        return value == null ? '' : String(value);
    }

    function requestJson(url, options) {
        return fetch(url, options).then(async response => {
            const body = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(body.message || '请求失败');
            return body;
        });
    }

    function imageUrl(task) {
        return text(task && (task.resultOssUrl || task.resultTempUrl)).trim();
    }

    function imageName(task, variant) {
        const module = text(task.moduleCode || 'A+').trim();
        const name = text(task.moduleName || '').trim();
        const suffix = variant === 'mobile' ? '手机端 4:3' : '网页端 21:9';
        return [module, name, suffix].filter(Boolean).join(' · ');
    }

    // Keep approved copy outside generated pixels so it can be reviewed and edited on canvas.
    function overlayText(task) {
        const copy = text(task && task.moduleCopy);
        const match = copy.match(/\*\*Copy Text\*\*:\s*([^\n]+)/i);
        const source = match ? match[1] : '';
        return source.split(/[;|]/).map(item => item.trim()).filter(Boolean).slice(0, 4);
    }

    function aplusNodesForTask(taskId) {
        return nodes.filter(node => node && node.aplus
            && node.aplus.source === 'aplus'
            && text(node.aplus.projectId) === text(aplusProjectId)
            && text(node.aplus.taskId) === text(taskId)
            && node.aplus.variant === 'web');
    }

    function webPlacement(task, taskIndex) {
        const versions = aplusNodesForTask(task.id);
        const versionIndex = versions.length;
        return {
            x: WEB_X + versionIndex * 610,
            y: taskIndex * WEB_GAP_Y,
            w: 520,
            h: 270
        };
    }

    function pendingWebNode(task, taskIndex) {
        const placement = webPlacement(task, taskIndex);
        return {
            id: uid('aplus_web'),
            type: 'image',
            ...placement,
            name: imageName(task, 'web'),
            mediaKind: 'image',
            aplus: {
                source: 'aplus',
                projectId: aplusProjectId,
                taskId: task.id,
                moduleCode: task.moduleCode,
                moduleName: task.moduleName,
                kieTaskId: task.kieTaskId || '',
                prompt: task.prompt || '',
                model: task.model || 'nano-banana-pro',
                resolution: task.resolution || '2K',
                versionNumber: task.versionNumber || 1,
                qualityStatus: task.qualityStatus || 'NOT_EVALUATED',
                qualityReportJson: task.qualityReportJson || '',
                referenceImagesJson: task.referenceImagesJson || '[]',
                overlayText: overlayText(task),
                variant: 'web',
                state: task.status || 'PENDING'
            }
        };
    }

    function matchingWebNode(task) {
        const taskNodes = aplusNodesForTask(task.id);
        const kieTaskId = text(task.kieTaskId).trim();
        if (kieTaskId) {
            const sameKieTask = taskNodes.find(node => text(node.aplus.kieTaskId).trim() === kieTaskId);
            if (sameKieTask) return sameKieTask;
        }
        return taskNodes.find(node => !text(node.aplus.kieTaskId).trim()
            && !node.url
            && ['PENDING', 'PROCESSING'].includes(text(node.aplus.state).toUpperCase()));
    }

    function updateWebNode(node, task) {
        const nextUrl = imageUrl(task);
        const nextState = text(task.status || 'PENDING').toUpperCase();
        const before = JSON.stringify({
            url: node.url || '',
            state: node.aplus.state || '',
            kieTaskId: node.aplus.kieTaskId || '',
            prompt: node.aplus.prompt || '',
            resolution: node.aplus.resolution || '',
            versionNumber: node.aplus.versionNumber || 1,
            qualityStatus: node.aplus.qualityStatus || '',
            overlayText: node.aplus.overlayText || []
        });
        node.name = imageName(task, 'web');
        node.aplus.moduleCode = task.moduleCode;
        node.aplus.moduleName = task.moduleName;
        node.aplus.kieTaskId = task.kieTaskId || node.aplus.kieTaskId || '';
        node.aplus.prompt = task.prompt || node.aplus.prompt || '';
        node.aplus.model = task.model || node.aplus.model || 'nano-banana-pro';
        node.aplus.resolution = task.resolution || node.aplus.resolution || '2K';
        node.aplus.versionNumber = task.versionNumber || node.aplus.versionNumber || 1;
        node.aplus.qualityStatus = task.qualityStatus || node.aplus.qualityStatus || 'NOT_EVALUATED';
        node.aplus.qualityReportJson = task.qualityReportJson || node.aplus.qualityReportJson || '';
        node.aplus.referenceImagesJson = task.referenceImagesJson || node.aplus.referenceImagesJson || '[]';
        node.aplus.overlayText = overlayText(task);
        node.aplus.state = nextState;
        if (nextUrl) node.url = nextUrl;
        const after = JSON.stringify({
            url: node.url || '',
            state: node.aplus.state || '',
            kieTaskId: node.aplus.kieTaskId || '',
            prompt: node.aplus.prompt || '',
            resolution: node.aplus.resolution || '',
            versionNumber: node.aplus.versionNumber || 1,
            qualityStatus: node.aplus.qualityStatus || '',
            overlayText: node.aplus.overlayText || []
        });
        return before !== after;
    }

    function syncAplusTasks(project) {
        if (!project || !Array.isArray(project.imageTasks) || !canvas) return false;
        let changed = false;
        project.imageTasks.forEach((task, index) => {
            let node = matchingWebNode(task);
            if (!node) {
                node = pendingWebNode(task, index);
                nodes.push(node);
                changed = true;
            }
            changed = updateWebNode(node, task) || changed;
        });
        if (changed) {
            render();
            scheduleSave();
        }
        return changed;
    }

    async function pollAplusProject() {
        if (!aplusProjectId || !canvas) return;
        try {
            const response = await requestJson(`/api/aplus/projects/${encodeURIComponent(aplusProjectId)}`);
            if (response.success && response.data) syncAplusTasks(response.data);
        } catch (error) {
            console.warn('[A+ canvas] 项目状态同步失败', error);
        }
    }

    function taskImageUrl(response) {
        const result = response && response.result;
        if (!result) return '';
        if (typeof result.url === 'string') return result.url;
        if (Array.isArray(result.images) && result.images[0]) return text(result.images[0]);
        return '';
    }

    async function pollMobileTasks() {
        const mobileNodes = nodes.filter(node => node && node.aplus
            && node.aplus.source === 'mobile-conversion'
            && node.aplus.kieTaskId
            && ['PENDING', 'PROCESSING'].includes(text(node.aplus.state).toUpperCase()));
        if (!mobileNodes.length) return;
        let changed = false;
        await Promise.all(mobileNodes.map(async node => {
            try {
                const result = await requestJson(`/api/canvas-image-tasks/${encodeURIComponent(node.aplus.kieTaskId)}`);
                const status = text(result.status).toLowerCase();
                if (status === 'succeeded') {
                    const url = taskImageUrl(result);
                    if (url) {
                        node.url = url;
                        node.aplus.state = 'SUCCESS';
                        node.aplus.error = '';
                        changed = true;
                    }
                } else if (status === 'failed' || status === 'cancelled') {
                    node.aplus.state = 'FAILED';
                    node.aplus.error = text(result.error || '生成失败');
                    changed = true;
                } else if (node.aplus.state !== 'PROCESSING') {
                    node.aplus.state = 'PROCESSING';
                    changed = true;
                }
            } catch (error) {
                console.warn('[A+ canvas] 手机端任务查询失败', error);
            }
        }));
        if (changed) {
            render();
            scheduleSave();
        }
    }

    function selectedImageNodes() {
        return [...selected]
            .map(id => nodes.find(node => node.id === id))
            .filter(node => node && node.type === 'image' && text(node.url).trim())
            .sort((left, right) => Number(left.y || 0) - Number(right.y || 0)
                || Number(left.x || 0) - Number(right.x || 0));
    }

    function replaceAspectRatio(prompt) {
        return text(prompt).replace(/\b(?:16:9|21:9|4:4|4:3)\b/g, '4:3');
    }

    function selectedAplusNodes() {
        return [...selected]
            .map(id => nodes.find(node => node.id === id))
            .filter(node => node && node.aplus && Array.isArray(node.aplus.overlayText));
    }

    function mobilePrompt(source, instruction, resolution) {
        const extra = text(instruction).trim() || DEFAULT_MOBILE_INSTRUCTION;
        if (source.aplus && source.aplus.source === 'aplus' && text(source.aplus.prompt).trim()) {
            const prompt = replaceAspectRatio(source.aplus.prompt)
                .replace(/\b(?:1K|2K|4K)\b/g, text(resolution).toUpperCase());
            return `${prompt}\n\nMobile conversion requirement: ${extra}\nOutput: 4:3 mobile image.`;
        }
        const normalImageInstruction = extra === DEFAULT_MOBILE_INSTRUCTION
            ? DEFAULT_MOBILE_INSTRUCTION
            : `${DEFAULT_MOBILE_INSTRUCTION}\n${extra}`;
        return `${normalImageInstruction}\nUse the selected image as the source of truth. Preserve its subject, product, style, and key details. Output one 4:3 mobile image.`;
    }

    function mobileSize(resolution) {
        return text(resolution).toUpperCase() === '4K' ? '4096x3072' : '2048x1536';
    }

    function mobileNode(source, index, prompt, resolution) {
        return {
            id: uid('aplus_mobile'),
            type: 'image',
            x: MOBILE_X,
            y: Number(source.y || 0) + index * 18,
            w: 360,
            h: 330,
            name: `${text(source.name || '图片')} · 手机端 4:3`,
            mediaKind: 'image',
            aplus: {
                source: 'mobile-conversion',
                variant: 'mobile',
                state: 'PENDING',
                resolution,
                sourceNodeId: source.id,
                prompt,
                model: source.aplus?.model || source.model || 'nano-banana-pro',
                overlayText: source.aplus?.overlayText || [],
                conversionPayload: {
                    prompt,
                    reference_images: [source.url],
                    size: mobileSize(resolution),
                    model: source.aplus?.model || source.model || 'nano-banana-pro'
                }
            }
        };
    }

    async function submitMobileNode(node) {
        const payload = node.aplus.conversionPayload;
        const result = await requestJson('/api/canvas-image-tasks', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        });
        const taskId = result.task_id || result.id;
        if (!taskId) throw new Error(result.message || '未收到生成任务 ID');
        node.aplus.kieTaskId = taskId;
        node.aplus.state = 'PROCESSING';
        node.aplus.error = '';
    }

    async function convertSelected(instruction, resolution) {
        const sources = selectedImageNodes();
        if (!sources.length) throw new Error('请先选中至少一张图片');
        const created = sources.map((source, index) => {
            const node = mobileNode(source, index, mobilePrompt(source, instruction, resolution), resolution);
            nodes.push(node);
            return node;
        });
        render();
        scheduleSave();
        await Promise.all(created.map(async node => {
            try {
                await submitMobileNode(node);
            } catch (error) {
                node.aplus.state = 'FAILED';
                node.aplus.error = text(error && error.message || error);
            }
        }));
        render();
        scheduleSave();
    }

    async function retry(nodeId) {
        const node = nodes.find(item => item.id === nodeId);
        if (!node || !node.aplus) return;
        try {
            if (node.aplus.source === 'aplus') {
                const moduleCode = text(node.aplus.moduleCode).trim();
                if (!aplusProjectId || !moduleCode) throw new Error('缺少 A+ 模块信息');
                await requestJson(`/api/aplus/projects/${encodeURIComponent(aplusProjectId)}/modules/${encodeURIComponent(moduleCode)}/regenerate`, {
                    method: 'POST'
                });
                node.aplus.state = 'PENDING';
                node.aplus.kieTaskId = '';
                node.aplus.error = '';
            } else if (node.aplus.source === 'mobile-conversion') {
                node.aplus.state = 'PENDING';
                node.aplus.kieTaskId = '';
                node.aplus.error = '';
                await submitMobileNode(node);
            }
            render();
            scheduleSave();
        } catch (error) {
            node.aplus.state = 'FAILED';
            node.aplus.error = text(error && error.message || error);
            render();
            scheduleSave();
        }
    }

    function ensureStyles() {
        if (document.getElementById('aplusCanvasBridgeStyles')) return;
        const style = document.createElement('style');
        style.id = 'aplusCanvasBridgeStyles';
        style.textContent = `
            .aplus-image-placeholder { height:100%; min-height:120px; border:1px dashed #93c5fd; border-radius:16px; background:linear-gradient(135deg,#eff6ff,#f8fafc); color:#2563eb; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:7px; text-align:center; }
            .aplus-image-placeholder.pending svg { animation:aplusCanvasSpin 1.2s linear infinite; }
            .aplus-image-placeholder.failed { color:#dc2626; border-color:#fca5a5; background:#fef2f2; }
            .aplus-placeholder-title { font-size:12px; font-weight:800; }
            .aplus-placeholder-detail { color:#64748b; font-size:10px; font-weight:700; }
            .aplus-placeholder-retry { border:0; border-radius:999px; background:#dc2626; color:#fff; font-size:11px; font-weight:800; padding:5px 12px; cursor:pointer; }
            .aplus-convert-mask { position:fixed; inset:0; z-index:9999; display:flex; align-items:center; justify-content:center; background:rgba(15,23,42,.45); padding:20px; }
            .aplus-convert-dialog { width:min(520px,100%); border-radius:18px; background:#fff; color:#0f172a; box-shadow:0 24px 80px rgba(15,23,42,.28); padding:22px; }
            .aplus-convert-dialog h3 { margin:0 0 8px; font-size:18px; }
            .aplus-convert-dialog p { margin:0 0 16px; color:#64748b; font-size:13px; line-height:1.6; }
            .aplus-convert-dialog textarea { width:100%; min-height:96px; resize:vertical; border:1px solid #cbd5e1; border-radius:10px; padding:10px; font:inherit; line-height:1.5; box-sizing:border-box; }
            .aplus-convert-row { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-top:14px; font-size:13px; font-weight:700; }
            .aplus-convert-row select { border:1px solid #cbd5e1; border-radius:8px; padding:7px 10px; background:#fff; }
            .aplus-convert-actions { display:flex; justify-content:flex-end; gap:10px; margin-top:20px; }
            .aplus-convert-actions button { border-radius:9px; padding:8px 14px; font-weight:800; cursor:pointer; }
            .aplus-convert-cancel { border:1px solid #cbd5e1; background:#fff; color:#475569; }
            .aplus-convert-submit { border:1px solid #2563eb; background:#2563eb; color:#fff; }
            @keyframes aplusCanvasSpin { to { transform:rotate(360deg); } }
        `;
        document.head.appendChild(style);
    }

    function closeModal() {
        if (modal) modal.remove();
        modal = null;
    }

    function openConvertDialog() {
        const count = selectedImageNodes().length;
        if (!count) {
            alert('请先在画布中选中至少一张图片');
            return;
        }
        ensureStyles();
        closeModal();
        modal = document.createElement('div');
        modal.className = 'aplus-convert-mask';
        modal.innerHTML = `
            <div class="aplus-convert-dialog" role="dialog" aria-modal="true" aria-label="转换手机端">
                <h3>转换手机端 4:3</h3>
                <p>将为已选中的 ${count} 张图片分别生成手机端版本，并追加到画布右侧，原图会保留用于对比。</p>
                <textarea aria-label="转换指令">${DEFAULT_MOBILE_INSTRUCTION}</textarea>
                <div class="aplus-convert-row"><span>输出分辨率</span><select><option value="2K" selected>2K（推荐）</option><option value="4K">4K</option></select></div>
                <div class="aplus-convert-actions"><button type="button" class="aplus-convert-cancel">取消</button><button type="button" class="aplus-convert-submit">生成手机端</button></div>
            </div>`;
        document.body.appendChild(modal);
        const dialog = modal.querySelector('.aplus-convert-dialog');
        const textarea = modal.querySelector('textarea');
        const resolution = modal.querySelector('select');
        modal.querySelector('.aplus-convert-cancel').onclick = closeModal;
        modal.onclick = event => { if (event.target === modal) closeModal(); };
        dialog.onclick = event => event.stopPropagation();
        modal.querySelector('.aplus-convert-submit').onclick = async () => {
            const submit = modal.querySelector('.aplus-convert-submit');
            submit.disabled = true;
            submit.textContent = '提交中…';
            try {
                await convertSelected(textarea.value, resolution.value);
                closeModal();
            } catch (error) {
                submit.disabled = false;
                submit.textContent = '生成手机端';
                alert(text(error && error.message || error));
            }
        };
        textarea.focus();
        textarea.setSelectionRange(textarea.value.length, textarea.value.length);
    }

    function openCopyOverlayDialog() {
        const selectedNodes = selectedAplusNodes();
        if (selectedNodes.length !== 1) {
            alert('Please select exactly one A+ image to edit its overlay copy.');
            return;
        }
        const node = selectedNodes[0];
        ensureStyles();
        closeModal();
        modal = document.createElement('div');
        modal.className = 'aplus-convert-mask';
        modal.innerHTML = `
            <div class="aplus-convert-dialog" role="dialog" aria-modal="true" aria-label="Edit A+ overlay copy">
                <h3>Edit A+ Overlay Copy</h3>
                <p>One line equals one controlled overlay. This is especially useful for AD-06 measurements, which must not be rasterized by the image model.</p>
                <textarea aria-label="A+ overlay copy"></textarea>
                <div class="aplus-convert-actions"><button type="button" class="aplus-convert-cancel">Cancel</button><button type="button" class="aplus-convert-submit">Save Copy</button></div>
            </div>`;
        document.body.appendChild(modal);
        const dialog = modal.querySelector('.aplus-convert-dialog');
        const textarea = modal.querySelector('textarea');
        textarea.value = (node.aplus.overlayText || []).join('\n');
        modal.querySelector('.aplus-convert-cancel').onclick = closeModal;
        modal.onclick = event => { if (event.target === modal) closeModal(); };
        dialog.onclick = event => event.stopPropagation();
        modal.querySelector('.aplus-convert-submit').onclick = () => {
            node.aplus.overlayText = textarea.value.split('\n').map(line => line.trim()).filter(Boolean).slice(0, 8);
            render();
            scheduleSave();
            closeModal();
        };
        textarea.focus();
    }

    function syncToolbar() {
        const button = document.getElementById('aplusMobileConvertBtn');
        const copyButton = document.getElementById('aplusCopyOverlayBtn');
        if (copyButton) copyButton.style.display = selectedAplusNodes().length ? '' : 'none';
        if (!button) return;
        const selectedCount = selectedImageNodes().length;
        button.style.display = selectedCount ? '' : 'none';
        button.querySelector('span').textContent = selectedCount ? `手机端 4:3 (${selectedCount})` : '手机端 4:3';
    }

    function start() {
        if (!canvas) return false;
        aplusProjectId = aplusProjectId || text(canvas.aplusProjectId).trim();
        if (!aplusProjectId) return true;
        ensureStyles();
        const button = document.getElementById('aplusMobileConvertBtn');
        if (button && !button.dataset.aplusBound) {
            button.dataset.aplusBound = 'true';
            button.addEventListener('click', openConvertDialog);
        }
        const copyButton = document.getElementById('aplusCopyOverlayBtn');
        if (copyButton && !copyButton.dataset.aplusBound) {
            copyButton.dataset.aplusBound = 'true';
            copyButton.addEventListener('click', openCopyOverlayDialog);
        }
        syncToolbar();
        if (!projectPollTimer) {
            pollAplusProject();
            projectPollTimer = window.setInterval(pollAplusProject, APLUS_POLL_MS);
        }
        if (!mobilePollTimer) {
            mobilePollTimer = window.setInterval(pollMobileTasks, MOBILE_POLL_MS);
        }
        window.setInterval(syncToolbar, 350);
        return true;
    }

    window.AplusCanvasBridge = { retry, refresh: pollAplusProject };
    window.addEventListener('load', () => {
        let attempts = 0;
        const waitForCanvas = window.setInterval(() => {
            attempts += 1;
            if (start() || attempts > 120) window.clearInterval(waitForCanvas);
        }, 150);
    });
})();
