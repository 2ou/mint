(function () {
    'use strict';

    var CUSTOM_PROPS = [
        'labType', 'frameId', 'frameName', 'frameBounds', 'frameShape', 'coverScale',
        'cropZoom', 'assetUrl', 'assetName', 'isTemplateText', 'excludeFromExport'
    ];

    var state = {
        project: null,
        template: null,
        canvas: null,
        assets: [],
        logicalWidth: 1200,
        logicalHeight: 1200,
        fitScale: 1,
        manualZoom: 1,
        history: [],
        historyIndex: -1,
        suppressHistory: false,
        saveTimer: null,
        historyTimer: null,
        saveQueue: Promise.resolve(),
        dirty: false,
        pendingFrameId: null,
        guideLines: []
    };

    var els = {
        projectName: document.getElementById('editor-project-name'),
        saveState: document.getElementById('editor-save-state'),
        loading: document.getElementById('editor-loading'),
        viewport: document.getElementById('canvas-viewport'),
        stage: document.getElementById('editor-stage'),
        stageTip: document.getElementById('stage-tip'),
        assetInput: document.getElementById('asset-file-input'),
        uploadAssets: document.getElementById('upload-assets'),
        assetGrid: document.getElementById('asset-grid'),
        undo: document.getElementById('undo-button'),
        redo: document.getElementById('redo-button'),
        zoomOut: document.getElementById('zoom-out'),
        zoomIn: document.getElementById('zoom-in'),
        zoomValue: document.getElementById('zoom-value'),
        exportFormat: document.getElementById('export-format'),
        exportScale: document.getElementById('export-scale'),
        exportButton: document.getElementById('export-button'),
        selectionKind: document.getElementById('selection-kind'),
        propsEmpty: document.getElementById('properties-empty'),
        propsFrame: document.getElementById('properties-frame'),
        propsText: document.getElementById('properties-text'),
        documentTemplate: document.getElementById('document-template'),
        documentSize: document.getElementById('document-size'),
        frameName: document.getElementById('frame-name'),
        frameHelp: document.getElementById('frame-help'),
        replaceFrameImage: document.getElementById('replace-frame-image'),
        cropControls: document.getElementById('crop-controls'),
        imageZoom: document.getElementById('image-zoom'),
        imageZoomValue: document.getElementById('image-zoom-value'),
        resetCrop: document.getElementById('reset-crop'),
        removeFrameImage: document.getElementById('remove-frame-image'),
        textContent: document.getElementById('text-content'),
        fontFamily: document.getElementById('font-family'),
        fontSize: document.getElementById('font-size'),
        textColor: document.getElementById('text-color'),
        lineHeight: document.getElementById('text-line-height'),
        letterSpacing: document.getElementById('text-letter-spacing'),
        textBold: document.getElementById('text-bold'),
        textItalic: document.getElementById('text-italic'),
        textOpacity: document.getElementById('text-opacity'),
        textOpacityValue: document.getElementById('text-opacity-value'),
        duplicateText: document.getElementById('duplicate-text'),
        deleteText: document.getElementById('delete-text'),
        bringForward: document.getElementById('bring-forward'),
        sendBackward: document.getElementById('send-backward'),
        toast: document.getElementById('editor-toast')
    };

    function apiData(response) {
        if (!response || !response.data || response.data.success !== true) {
            throw new Error(response && response.data && response.data.message ? response.data.message : '请求失败');
        }
        return response.data.data;
    }

    function showToast(message, error) {
        els.toast.textContent = message;
        els.toast.classList.toggle('is-error', Boolean(error));
        els.toast.classList.add('is-visible');
        clearTimeout(showToast.timer);
        showToast.timer = setTimeout(function () { els.toast.classList.remove('is-visible'); }, 3200);
    }

    function setSaveState(text, kind) {
        els.saveState.textContent = text;
        els.saveState.className = 'editor-save-state' + (kind ? ' is-' + kind : '');
    }

    function getProjectId() {
        return new URLSearchParams(window.location.search).get('id');
    }

    function frameConfig(frameId) {
        return (state.template.frames || []).find(function (item) { return item.id === frameId; });
    }

    function frameLabel(frameId) {
        var frames = state.template.frames || [];
        var index = frames.findIndex(function (item) { return item.id === frameId; });
        return index >= 0 ? '相框 ' + (index + 1) : '相框';
    }

    function makeFrameShape(frame, options) {
        var common = Object.assign({
            left: frame.x,
            top: frame.y,
            originX: 'left',
            originY: 'top',
            angle: frame.rotation || 0,
            objectCaching: false
        }, options || {});
        if (frame.shape === 'circle') {
            return new fabric.Ellipse(Object.assign(common, { rx: frame.width / 2, ry: frame.height / 2 }));
        }
        if (frame.shape === 'polygon') {
            var points = (frame.points || [[0, 0], [frame.width, 0], [frame.width, frame.height], [0, frame.height]])
                .map(function (point) { return { x: point[0], y: point[1] }; });
            return new fabric.Polygon(points, common);
        }
        return new fabric.Rect(Object.assign(common, {
            width: frame.width,
            height: frame.height,
            rx: frame.shape === 'rounded' ? (frame.radius || 20) : 0,
            ry: frame.shape === 'rounded' ? (frame.radius || 20) : 0
        }));
    }

    function frameBounds(frame) {
        return { x: frame.x, y: frame.y, width: frame.width, height: frame.height };
    }

    function styleInteractiveObject(object) {
        object.set({
            transparentCorners: false,
            cornerColor: '#2563eb',
            cornerStrokeColor: '#f5f8ff',
            borderColor: '#2563eb',
            cornerSize: 11,
            padding: 2,
            borderDashArray: [5, 4]
        });
        object.setControlsVisibility({ mt: true, mb: true, ml: true, mr: true, tl: true, tr: true, bl: true, br: true, mtr: true });
    }

    function addTemplateFrame(frame) {
        var placeholder = makeFrameShape(frame, {
            labType: 'framePlaceholder',
            frameId: frame.id,
            frameName: frameLabel(frame.id),
            frameBounds: frameBounds(frame),
            fill: 'rgba(255,255,255,.52)',
            stroke: 'rgba(37,99,235,.50)',
            strokeWidth: 2,
            strokeDashArray: [12, 9],
            selectable: true,
            hasControls: false,
            lockMovementX: true,
            lockMovementY: true,
            hoverCursor: 'pointer'
        });
        var border = makeFrameShape(frame, {
            labType: 'frameBorder',
            frameId: frame.id,
            frameBounds: frameBounds(frame),
            fill: 'rgba(255,255,255,0)',
            stroke: 'rgba(23,32,51,.16)',
            strokeWidth: 2,
            selectable: false,
            evented: false,
            excludeFromExport: false
        });
        state.canvas.add(placeholder);
        state.canvas.add(border);
    }

    function addTemplateText(definition) {
        var text = new fabric.Textbox(definition.text || '双击编辑文字', {
            left: definition.x || 80,
            top: definition.y || 80,
            width: definition.width || 520,
            fontSize: definition.fontSize || 40,
            fontWeight: definition.fontWeight || '400',
            fontFamily: definition.fontFamily || 'Arial',
            fill: definition.fill || state.template.accent || '#172033',
            textAlign: definition.textAlign || 'left',
            lineHeight: definition.lineHeight || 1.12,
            charSpacing: definition.charSpacing || 0,
            labType: 'text',
            isTemplateText: true,
            editable: true,
            splitByGrapheme: true
        });
        styleInteractiveObject(text);
        state.canvas.add(text);
    }

    function buildTemplateCanvas() {
        state.suppressHistory = true;
        state.canvas.clear();
        state.canvas.backgroundColor = state.template.background || '#f5f7fa';
        (state.template.frames || []).forEach(addTemplateFrame);
        (state.template.texts || []).forEach(addTemplateText);
        state.canvas.renderAll();
        state.suppressHistory = false;
    }

    function rehydrateCanvasObjects() {
        var imagesByFrame = {};
        state.canvas.getObjects().forEach(function (object) {
            if (object.labType === 'frameImage') imagesByFrame[object.frameId] = object;
        });
        state.canvas.getObjects().forEach(function (object) {
            if (object.labType === 'text') {
                object.set({ selectable: true, evented: true, editable: true, splitByGrapheme: true });
                styleInteractiveObject(object);
            } else if (object.labType === 'frameImage') {
                object.set({ selectable: true, evented: true, hasControls: false, hasBorders: false, lockScalingX: true, lockScalingY: true, lockRotation: true, objectCaching: false });
                object.crossOrigin = 'anonymous';
                bindFrameHitTest(object);
            } else if (object.labType === 'framePlaceholder') {
                object.set({ selectable: !imagesByFrame[object.frameId], evented: !imagesByFrame[object.frameId], hasControls: false, lockMovementX: true, lockMovementY: true });
            } else if (object.labType === 'frameBorder') {
                object.set({ selectable: false, evented: false, excludeFromExport: false });
            } else if (object.labType === 'guide') {
                state.canvas.remove(object);
            }
        });
        state.canvas.renderAll();
    }

    function parseSavedDesign() {
        if (!state.project.designJson) return null;
        try {
            var parsed = JSON.parse(state.project.designJson);
            state.assets = Array.isArray(parsed.assets) ? parsed.assets : [];
            return parsed.fabric || null;
        } catch (error) {
            showToast('项目数据无法读取，已恢复模板初始状态', true);
            return null;
        }
    }

    function canvasSnapshot() {
        return JSON.stringify(state.canvas.toDatalessJSON(CUSTOM_PROPS));
    }

    function pushHistory() {
        if (state.suppressHistory || !state.canvas) return;
        var snapshot = canvasSnapshot();
        if (state.history[state.historyIndex] === snapshot) return;
        state.history = state.history.slice(0, state.historyIndex + 1);
        state.history.push(snapshot);
        if (state.history.length > 60) state.history.shift();
        state.historyIndex = state.history.length - 1;
        updateHistoryButtons();
    }

    function queueMutation(immediate) {
        if (state.suppressHistory) return;
        clearTimeout(state.historyTimer);
        var commit = function () {
            pushHistory();
            scheduleSave();
        };
        if (immediate) commit();
        else state.historyTimer = setTimeout(commit, 220);
    }

    function updateHistoryButtons() {
        els.undo.disabled = state.historyIndex <= 0;
        els.redo.disabled = state.historyIndex < 0 || state.historyIndex >= state.history.length - 1;
    }

    function loadSnapshot(index) {
        if (index < 0 || index >= state.history.length) return;
        state.suppressHistory = true;
        state.canvas.discardActiveObject();
        state.canvas.loadFromJSON(JSON.parse(state.history[index]), function () {
            state.historyIndex = index;
            rehydrateCanvasObjects();
            applyViewportScale();
            state.suppressHistory = false;
            updateHistoryButtons();
            renderProperties();
            scheduleSave();
        });
    }

    function undo() { loadSnapshot(state.historyIndex - 1); }
    function redo() { loadSnapshot(state.historyIndex + 1); }

    function getFrameImage(frameId) {
        return state.canvas.getObjects().find(function (object) {
            return object.labType === 'frameImage' && object.frameId === frameId;
        });
    }

    function getFramePlaceholder(frameId) {
        return state.canvas.getObjects().find(function (object) {
            return object.labType === 'framePlaceholder' && object.frameId === frameId;
        });
    }

    function getFrameBorder(frameId) {
        return state.canvas.getObjects().find(function (object) {
            return object.labType === 'frameBorder' && object.frameId === frameId;
        });
    }

    function constrainFrameImage(image) {
        if (!image || image.labType !== 'frameImage') return;
        var bounds = image.frameBounds;
        if (typeof bounds === 'string') {
            try { bounds = JSON.parse(bounds); } catch (ignore) { return; }
        }
        if (!bounds) return;
        var width = image.getScaledWidth();
        var height = image.getScaledHeight();
        var minLeft = bounds.x + bounds.width - width / 2;
        var maxLeft = bounds.x + width / 2;
        var minTop = bounds.y + bounds.height - height / 2;
        var maxTop = bounds.y + height / 2;
        image.left = Math.min(maxLeft, Math.max(minLeft, image.left));
        image.top = Math.min(maxTop, Math.max(minTop, image.top));
        image.setCoords();
    }

    function bindFrameHitTest(image) {
        image.containsPoint = function (point) {
            var bounds = this.frameBounds;
            if (typeof bounds === 'string') {
                try { bounds = JSON.parse(bounds); } catch (ignore) { return false; }
            }
            var zoom = this.canvas ? this.canvas.getZoom() : 1;
            var logicalX = point.x / Math.max(zoom, .01);
            var logicalY = point.y / Math.max(zoom, .01);
            return Boolean(bounds)
                && logicalX >= bounds.x
                && logicalX <= bounds.x + bounds.width
                && logicalY >= bounds.y
                && logicalY <= bounds.y + bounds.height;
        };
    }

    function setPlaceholderState(frameId, hasImage) {
        var placeholder = getFramePlaceholder(frameId);
        if (!placeholder) return;
        placeholder.set({
            selectable: !hasImage,
            evented: !hasImage,
            fill: hasImage ? 'rgba(255,255,255,0)' : 'rgba(255,255,255,.52)',
            stroke: hasImage ? 'rgba(37,99,235,0)' : 'rgba(37,99,235,.50)'
        });
    }

    function assignImageToFrame(frameId, asset) {
        var frame = frameConfig(frameId);
        if (!frame || !asset || !asset.url) return;
        var oldImage = getFrameImage(frameId);
        var active = state.canvas.getActiveObject();
        fabric.Image.fromURL(asset.url, function (image) {
            if (!image || !image.width || !image.height) {
                showToast('图片读取失败，请检查图片链接', true);
                return;
            }
            if (oldImage) state.canvas.remove(oldImage);
            var cover = Math.max(frame.width / image.width, frame.height / image.height);
            var clip = makeFrameShape(frame, { absolutePositioned: true, fill: '#ffffff', strokeWidth: 0 });
            image.set({
                left: frame.x + frame.width / 2,
                top: frame.y + frame.height / 2,
                originX: 'center',
                originY: 'center',
                scaleX: cover,
                scaleY: cover,
                clipPath: clip,
                crossOrigin: 'anonymous',
                labType: 'frameImage',
                frameId: frameId,
                frameName: frameLabel(frameId),
                frameBounds: frameBounds(frame),
                coverScale: cover,
                cropZoom: 1,
                assetUrl: asset.url,
                assetName: asset.name || '图片',
                selectable: true,
                evented: true,
                hasControls: false,
                hasBorders: false,
                lockScalingX: true,
                lockScalingY: true,
                lockRotation: true,
                objectCaching: false,
                hoverCursor: 'move'
            });
            bindFrameHitTest(image);
            setPlaceholderState(frameId, true);
            state.canvas.add(image);
            var border = getFrameBorder(frameId);
            if (border) border.bringToFront();
            state.canvas.getObjects().filter(function (item) { return item.labType === 'text'; }).forEach(function (item) { item.bringToFront(); });
            state.canvas.setActiveObject(image);
            constrainFrameImage(image);
            state.canvas.renderAll();
            queueMutation(true);
            renderProperties();
            if (active && active.labType === 'text') active.setCoords();
        }, { crossOrigin: 'anonymous' });
    }

    function removeFrameImage() {
        var image = selectedFrameImage();
        if (!image) return;
        var frameId = image.frameId;
        state.canvas.remove(image);
        setPlaceholderState(frameId, false);
        var placeholder = getFramePlaceholder(frameId);
        if (placeholder) state.canvas.setActiveObject(placeholder);
        state.canvas.renderAll();
        queueMutation(true);
        renderProperties();
    }

    function selectedFrameId() {
        var active = state.canvas && state.canvas.getActiveObject();
        return active && (active.labType === 'frameImage' || active.labType === 'framePlaceholder') ? active.frameId : null;
    }

    function selectedFrameImage() {
        var active = state.canvas && state.canvas.getActiveObject();
        return active && active.labType === 'frameImage' ? active : null;
    }

    function selectedText() {
        var active = state.canvas && state.canvas.getActiveObject();
        return active && active.labType === 'text' ? active : null;
    }

    function resetCrop() {
        var image = selectedFrameImage();
        if (!image) return;
        var bounds = image.frameBounds;
        image.set({
            left: bounds.x + bounds.width / 2,
            top: bounds.y + bounds.height / 2,
            scaleX: image.coverScale,
            scaleY: image.coverScale,
            cropZoom: 1
        });
        image.setCoords();
        state.canvas.renderAll();
        queueMutation(true);
        renderProperties();
    }

    function setImageZoom(value, commit) {
        var image = selectedFrameImage();
        if (!image) return;
        var zoom = Number(value) / 100;
        image.set({ scaleX: image.coverScale * zoom, scaleY: image.coverScale * zoom, cropZoom: zoom });
        constrainFrameImage(image);
        state.canvas.renderAll();
        if (commit) queueMutation(true);
    }

    function addText(preset) {
        var definitions = {
            heading: { text: '输入标题', fontSize: 64, fontWeight: '700', width: 600 },
            body: { text: '输入商品说明文字', fontSize: 32, fontWeight: '400', width: 560 },
            caption: { text: '输入细节标注', fontSize: 22, fontWeight: '400', width: 420 }
        };
        var definition = definitions[preset] || definitions.body;
        var text = new fabric.Textbox(definition.text, {
            left: Math.max(40, state.logicalWidth / 2 - definition.width / 2),
            top: Math.max(40, state.logicalHeight / 2 - definition.fontSize),
            width: definition.width,
            fontSize: definition.fontSize,
            fontWeight: definition.fontWeight,
            fontFamily: 'Microsoft YaHei',
            fill: state.template.accent || '#172033',
            lineHeight: 1.15,
            splitByGrapheme: true,
            labType: 'text',
            isTemplateText: false,
            editable: true
        });
        styleInteractiveObject(text);
        state.canvas.add(text);
        text.bringToFront();
        state.canvas.setActiveObject(text);
        state.canvas.renderAll();
        queueMutation(true);
        renderProperties();
        text.enterEditing();
        text.selectAll();
    }

    function duplicateSelectedText() {
        var text = selectedText();
        if (!text) return;
        text.clone(function (copy) {
            copy.set({ left: text.left + 28, top: text.top + 28, labType: 'text', isTemplateText: false });
            styleInteractiveObject(copy);
            state.canvas.add(copy);
            state.canvas.setActiveObject(copy);
            state.canvas.renderAll();
            queueMutation(true);
            renderProperties();
        }, CUSTOM_PROPS);
    }

    function deleteSelectedText() {
        var text = selectedText();
        if (!text) return;
        state.canvas.remove(text);
        state.canvas.discardActiveObject();
        state.canvas.renderAll();
        queueMutation(true);
        renderProperties();
    }

    function updateTextProperty(property, value, commit) {
        var text = selectedText();
        if (!text) return;
        text.set(property, value);
        text.setCoords();
        state.canvas.renderAll();
        if (commit) queueMutation(false);
    }

    function renderProperties() {
        if (!state.canvas) return;
        var active = state.canvas.getActiveObject();
        els.propsEmpty.hidden = true;
        els.propsFrame.hidden = true;
        els.propsText.hidden = true;
        if (!active || !active.labType) {
            els.propsEmpty.hidden = false;
            els.selectionKind.textContent = '未选择元素';
            return;
        }
        if (active.labType === 'framePlaceholder' || active.labType === 'frameImage') {
            els.propsFrame.hidden = false;
            els.selectionKind.textContent = active.labType === 'frameImage' ? '相框图片' : '空相框';
            els.frameName.textContent = active.frameName || frameLabel(active.frameId);
            var image = active.labType === 'frameImage' ? active : getFrameImage(active.frameId);
            els.cropControls.hidden = !image;
            els.frameHelp.textContent = image ? '拖动画面调整裁剪位置，缩放不会改变相框尺寸。' : '从左侧选择图片，或上传一张新图片。';
            if (image) {
                var zoomValue = Math.round((image.cropZoom || 1) * 100);
                els.imageZoom.value = zoomValue;
                els.imageZoomValue.value = zoomValue + '%';
                els.imageZoomValue.textContent = zoomValue + '%';
            }
            return;
        }
        if (active.labType === 'text') {
            els.propsText.hidden = false;
            els.selectionKind.textContent = '文字';
            els.textContent.value = active.text || '';
            els.fontFamily.value = active.fontFamily || 'Arial';
            els.fontSize.value = Math.round(active.fontSize || 32);
            els.textColor.value = normalizeColor(active.fill);
            els.lineHeight.value = Number(active.lineHeight || 1.16).toFixed(2);
            els.letterSpacing.value = Math.round((active.charSpacing || 0) * (active.fontSize || 32) / 1000);
            els.textBold.setAttribute('aria-pressed', String(String(active.fontWeight) === '700' || String(active.fontWeight) === 'bold'));
            els.textItalic.setAttribute('aria-pressed', String(active.fontStyle === 'italic'));
            document.querySelectorAll('[data-text-align]').forEach(function (button) {
                button.setAttribute('aria-pressed', String(button.dataset.textAlign === (active.textAlign || 'left')));
            });
            var opacity = Math.round((active.opacity == null ? 1 : active.opacity) * 100);
            els.textOpacity.value = opacity;
            els.textOpacityValue.value = opacity + '%';
            els.textOpacityValue.textContent = opacity + '%';
            return;
        }
        els.propsEmpty.hidden = false;
        els.selectionKind.textContent = '未选择元素';
    }

    function normalizeColor(value) {
        if (typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value)) return value;
        return '#172033';
    }

    function removeGuides() {
        state.guideLines.forEach(function (line) { state.canvas.remove(line); });
        state.guideLines = [];
    }

    function addGuide(x1, y1, x2, y2) {
        var line = new fabric.Line([x1, y1, x2, y2], {
            labType: 'guide',
            stroke: '#2563eb',
            strokeWidth: 1 / Math.max(state.canvas.getZoom(), .25),
            strokeDashArray: [6, 6],
            selectable: false,
            evented: false,
            excludeFromExport: true
        });
        state.guideLines.push(line);
        state.canvas.add(line);
        line.bringToFront();
    }

    function snapMovingObject(object) {
        if (!object || object.labType !== 'text') return;
        removeGuides();
        var rect = object.getBoundingRect(true, true);
        var threshold = 8 / Math.max(state.canvas.getZoom(), .25);
        var centerX = rect.left + rect.width / 2;
        var centerY = rect.top + rect.height / 2;
        var canvasCenterX = state.logicalWidth / 2;
        var canvasCenterY = state.logicalHeight / 2;
        if (Math.abs(centerX - canvasCenterX) <= threshold) {
            object.left += canvasCenterX - centerX;
            addGuide(canvasCenterX, 0, canvasCenterX, state.logicalHeight);
        }
        if (Math.abs(centerY - canvasCenterY) <= threshold) {
            object.top += canvasCenterY - centerY;
            addGuide(0, canvasCenterY, state.logicalWidth, canvasCenterY);
        }
        object.setCoords();
    }

    function renderAssets() {
        if (!state.assets.length) {
            els.assetGrid.innerHTML = '<div class="editor-asset-empty">尚未上传素材。上传后，素材会随项目保存并可重复使用。</div>';
            return;
        }
        els.assetGrid.innerHTML = state.assets.map(function (asset, index) {
            return '<button class="editor-asset-card" type="button" draggable="true" data-asset-index="' + index + '" aria-label="使用图片 '
                + escapeHtml(asset.name || '素材') + '"><img src="' + escapeHtml(asset.url) + '" alt=""><span>'
                + escapeHtml(asset.name || '素材') + '</span></button>';
        }).join('');
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }

    async function uploadFiles(files, targetFrameId) {
        var list = Array.from(files || []);
        if (!list.length) return [];
        els.uploadAssets.classList.add('is-uploading');
        els.uploadAssets.disabled = true;
        var uploaded = [];
        try {
            for (var i = 0; i < list.length; i += 1) {
                var form = new FormData();
                form.append('file', list[i]);
                var asset = apiData(await axios.post('/api/template-lab/projects/' + state.project.id + '/assets', form, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                }));
                state.assets.push(asset);
                uploaded.push(asset);
            }
            renderAssets();
            scheduleSave();
            if (targetFrameId && uploaded[0]) assignImageToFrame(targetFrameId, uploaded[0]);
            showToast(uploaded.length + ' 张图片已上传');
            return uploaded;
        } catch (error) {
            showToast(error.response && error.response.data ? error.response.data.message : error.message, true);
            return uploaded;
        } finally {
            els.uploadAssets.classList.remove('is-uploading');
            els.uploadAssets.disabled = false;
            els.assetInput.value = '';
            state.pendingFrameId = null;
        }
    }

    function serializedDesign() {
        return JSON.stringify({
            schemaVersion: 1,
            templateId: state.template.id,
            assets: state.assets,
            fabric: state.canvas.toDatalessJSON(CUSTOM_PROPS)
        });
    }

    function canvasDataUrl(format, targetScale, quality, cleanOutput) {
        var currentPixelWidth = state.canvas.getWidth();
        var targetPixelWidth = state.logicalWidth * targetScale;
        var multiplier = targetPixelWidth / currentPixelWidth;
        var hiddenObjects = [];
        if (cleanOutput) {
            state.canvas.getObjects().forEach(function (object) {
                if (object.labType === 'framePlaceholder' || object.labType === 'frameBorder' || object.labType === 'guide') {
                    hiddenObjects.push({ object: object, visible: object.visible });
                    object.visible = false;
                }
            });
            state.canvas.renderAll();
        }
        try {
            return state.canvas.toDataURL({
                format: format,
                quality: quality == null ? .92 : quality,
                multiplier: multiplier,
                enableRetinaScaling: false
            });
        } finally {
            hiddenObjects.forEach(function (item) { item.object.visible = item.visible; });
            if (hiddenObjects.length) state.canvas.renderAll();
        }
    }

    function createThumbnail() {
        try {
            return canvasDataUrl('jpeg', Math.min(1, 360 / state.logicalWidth), .72, false);
        } catch (error) {
            return state.project.thumbnailDataUrl || '';
        }
    }

    function scheduleSave() {
        state.dirty = true;
        setSaveState('有未保存修改', 'saving');
        clearTimeout(state.saveTimer);
        state.saveTimer = setTimeout(saveNow, 900);
    }

    function saveNow() {
        if (!state.canvas || !state.project || !state.dirty) return state.saveQueue;
        clearTimeout(state.saveTimer);
        var payload = {
            projectName: els.projectName.value.trim() || state.project.projectName,
            canvasWidth: state.logicalWidth,
            canvasHeight: state.logicalHeight,
            designJson: serializedDesign(),
            thumbnailDataUrl: createThumbnail()
        };
        state.dirty = false;
        setSaveState('正在保存...', 'saving');
        state.saveQueue = state.saveQueue.then(function () {
            return axios.put('/api/template-lab/projects/' + state.project.id, payload);
        }).then(function (response) {
            state.project = apiData(response);
            setSaveState('已自动保存', 'saved');
        }).catch(function (error) {
            state.dirty = true;
            setSaveState('保存失败，稍后将重试', 'error');
            showToast(error.response && error.response.data ? error.response.data.message : error.message, true);
        });
        return state.saveQueue;
    }

    function applyViewportScale() {
        if (!state.canvas) return;
        var availableWidth = Math.max(320, els.stage.clientWidth - 80);
        var availableHeight = Math.max(320, els.stage.clientHeight - 80);
        state.fitScale = Math.min(availableWidth / state.logicalWidth, availableHeight / state.logicalHeight, 1);
        var scale = Math.max(.12, Math.min(2.5, state.fitScale * state.manualZoom));
        state.canvas.setDimensions({ width: Math.round(state.logicalWidth * scale), height: Math.round(state.logicalHeight * scale) });
        state.canvas.setZoom(scale);
        state.canvas.calcOffset();
        state.canvas.renderAll();
        els.zoomValue.textContent = Math.round(state.manualZoom * 100) + '%';
    }

    function setManualZoom(value) {
        state.manualZoom = Math.max(.5, Math.min(2, value));
        applyViewportScale();
    }

    async function exportImage() {
        var emptyFrames = (state.template.frames || []).filter(function (frame) { return !getFrameImage(frame.id); });
        if (emptyFrames.length) {
            showToast('还有 ' + emptyFrames.length + ' 个相框未填入图片，完成后再导出。', true);
            return;
        }
        els.exportButton.disabled = true;
        els.exportButton.textContent = '正在导出...';
        try {
            await saveNow();
            var format = els.exportFormat.value;
            var scale = Number(els.exportScale.value) || 1;
            var dataUrl = canvasDataUrl(format, scale, format === 'jpeg' ? .94 : 1, true);
            var link = document.createElement('a');
            var safeName = (els.projectName.value.trim() || '拼图项目').replace(/[\\/:*?"<>|]/g, '_');
            link.download = safeName + '_' + state.logicalWidth * scale + 'x' + state.logicalHeight * scale + '.' + (format === 'jpeg' ? 'jpg' : 'png');
            link.href = dataUrl;
            document.body.appendChild(link);
            link.click();
            link.remove();
            showToast('图片已导出');
        } catch (error) {
            showToast('导出失败。请检查素材是否允许跨域读取。', true);
        } finally {
            els.exportButton.disabled = false;
            els.exportButton.innerHTML = '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 3v12M7 10l5 5 5-5"/><path d="M5 21h14"/></svg>导出图片';
        }
    }

    function frameAtClientPoint(event) {
        var rect = state.canvas.upperCanvasEl.getBoundingClientRect();
        var zoom = state.canvas.getZoom();
        var x = (event.clientX - rect.left) / zoom;
        var y = (event.clientY - rect.top) / zoom;
        var frames = state.template.frames || [];
        return frames.find(function (frame) {
            return x >= frame.x && x <= frame.x + frame.width && y >= frame.y && y <= frame.y + frame.height;
        });
    }

    function setupCanvasEvents() {
        state.canvas.on('selection:created', renderProperties);
        state.canvas.on('selection:updated', renderProperties);
        state.canvas.on('selection:cleared', renderProperties);
        state.canvas.on('object:moving', function (event) {
            var object = event.target;
            if (object.labType === 'frameImage') constrainFrameImage(object);
            else snapMovingObject(object);
        });
        state.canvas.on('object:modified', function () {
            removeGuides();
            queueMutation(true);
            renderProperties();
        });
        state.canvas.on('text:changed', function () {
            renderProperties();
            queueMutation(false);
        });
        state.canvas.on('mouse:up', removeGuides);
        state.canvas.on('mouse:dblclick', function (event) {
            if (event.target && event.target.labType === 'frameImage') {
                els.stageTip.hidden = false;
                clearTimeout(setupCanvasEvents.tipTimer);
                setupCanvasEvents.tipTimer = setTimeout(function () { els.stageTip.hidden = true; }, 2600);
            }
        });
    }

    async function initialize() {
        var projectId = getProjectId();
        if (!projectId) {
            window.location.replace('template-lab.html');
            return;
        }
        if (!window.fabric) {
            els.loading.innerHTML = '<strong>编辑器组件加载失败</strong><span>请检查网络后刷新页面。</span>';
            return;
        }
        try {
            var responses = await Promise.all([
                axios.get('/api/template-lab/projects/' + encodeURIComponent(projectId)),
                axios.get('/api/template-lab/templates')
            ]);
            state.project = apiData(responses[0]);
            var templates = apiData(responses[1]) || [];
            state.template = templates.find(function (item) { return item.id === state.project.templateId; });
            if (!state.template) throw new Error('项目使用的模板不存在');
            state.logicalWidth = state.project.canvasWidth || state.template.width;
            state.logicalHeight = state.project.canvasHeight || state.template.height;
            els.projectName.value = state.project.projectName;
            els.documentTemplate.textContent = state.template.name;
            els.documentSize.textContent = state.logicalWidth + ' × ' + state.logicalHeight;
            state.canvas = new fabric.Canvas('template-canvas', {
                preserveObjectStacking: true,
                selection: true,
                uniScaleKey: null,
                centeredScaling: false,
                stopContextMenu: true,
                fireRightClick: true
            });
            fabric.Object.prototype.objectCaching = false;
            setupCanvasEvents();
            var saved = parseSavedDesign();
            if (saved) {
                state.suppressHistory = true;
                state.canvas.loadFromJSON(saved, function () {
                    rehydrateCanvasObjects();
                    finishInitialization();
                });
            } else {
                buildTemplateCanvas();
                finishInitialization();
                scheduleSave();
            }
        } catch (error) {
            var message = error.response && error.response.data ? error.response.data.message : error.message;
            els.loading.innerHTML = '<strong>项目加载失败</strong><span>' + escapeHtml(message) + '</span>';
            setSaveState('加载失败', 'error');
        }
    }

    function finishInitialization() {
        state.suppressHistory = false;
        renderAssets();
        applyViewportScale();
        pushHistory();
        renderProperties();
        els.loading.hidden = true;
        els.viewport.hidden = false;
        setSaveState('已自动保存', 'saved');
        setTimeout(applyViewportScale, 0);
    }

    function setupDomEvents() {
        document.querySelectorAll('[data-panel-tab]').forEach(function (button) {
            button.addEventListener('click', function () {
                document.querySelectorAll('[data-panel-tab]').forEach(function (item) { item.setAttribute('aria-selected', String(item === button)); });
                document.querySelectorAll('[data-panel-content]').forEach(function (panel) { panel.hidden = panel.dataset.panelContent !== button.dataset.panelTab; });
            });
        });
        document.querySelectorAll('[data-add-text]').forEach(function (button) {
            button.addEventListener('click', function () { addText(button.dataset.addText); });
        });
        els.uploadAssets.addEventListener('click', function () { state.pendingFrameId = null; els.assetInput.click(); });
        els.replaceFrameImage.addEventListener('click', function () { state.pendingFrameId = selectedFrameId(); els.assetInput.click(); });
        els.assetInput.addEventListener('change', function () { uploadFiles(els.assetInput.files, state.pendingFrameId); });
        ['dragenter', 'dragover'].forEach(function (name) {
            els.uploadAssets.addEventListener(name, function (event) { event.preventDefault(); els.uploadAssets.classList.add('is-dragover'); });
        });
        ['dragleave', 'drop'].forEach(function (name) {
            els.uploadAssets.addEventListener(name, function (event) { event.preventDefault(); els.uploadAssets.classList.remove('is-dragover'); });
        });
        els.uploadAssets.addEventListener('drop', function (event) { uploadFiles(event.dataTransfer.files, selectedFrameId()); });
        els.assetGrid.addEventListener('click', function (event) {
            var card = event.target.closest('[data-asset-index]');
            if (!card) return;
            var frameId = selectedFrameId();
            if (!frameId) return showToast('请先选择一个相框', true);
            assignImageToFrame(frameId, state.assets[Number(card.dataset.assetIndex)]);
        });
        els.assetGrid.addEventListener('dragstart', function (event) {
            var card = event.target.closest('[data-asset-index]');
            if (!card) return;
            event.dataTransfer.setData('application/x-template-lab-asset', card.dataset.assetIndex);
            event.dataTransfer.effectAllowed = 'copy';
        });
        els.viewport.addEventListener('dragover', function (event) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; });
        els.viewport.addEventListener('drop', async function (event) {
            event.preventDefault();
            var frame = frameAtClientPoint(event);
            if (!frame) return showToast('请把图片放到模板相框内', true);
            var index = event.dataTransfer.getData('application/x-template-lab-asset');
            if (index !== '') assignImageToFrame(frame.id, state.assets[Number(index)]);
            else if (event.dataTransfer.files && event.dataTransfer.files.length) await uploadFiles(event.dataTransfer.files, frame.id);
        });

        els.projectName.addEventListener('input', scheduleSave);
        els.projectName.addEventListener('blur', function () {
            if (!els.projectName.value.trim()) els.projectName.value = state.project.projectName;
            saveNow();
        });
        els.undo.addEventListener('click', undo);
        els.redo.addEventListener('click', redo);
        els.zoomOut.addEventListener('click', function () { setManualZoom(state.manualZoom - .1); });
        els.zoomIn.addEventListener('click', function () { setManualZoom(state.manualZoom + .1); });
        els.zoomValue.addEventListener('click', function () { setManualZoom(1); });
        els.exportButton.addEventListener('click', exportImage);
        els.imageZoom.addEventListener('input', function () {
            els.imageZoomValue.value = els.imageZoom.value + '%';
            els.imageZoomValue.textContent = els.imageZoom.value + '%';
            setImageZoom(els.imageZoom.value, false);
        });
        els.imageZoom.addEventListener('change', function () { setImageZoom(els.imageZoom.value, true); });
        els.resetCrop.addEventListener('click', resetCrop);
        els.removeFrameImage.addEventListener('click', removeFrameImage);
        els.textContent.addEventListener('input', function () { updateTextProperty('text', els.textContent.value, true); });
        els.fontFamily.addEventListener('change', function () { updateTextProperty('fontFamily', els.fontFamily.value, true); });
        els.fontSize.addEventListener('change', function () { updateTextProperty('fontSize', Math.max(10, Number(els.fontSize.value) || 32), true); });
        els.textColor.addEventListener('input', function () { updateTextProperty('fill', els.textColor.value, true); });
        els.lineHeight.addEventListener('change', function () { updateTextProperty('lineHeight', Number(els.lineHeight.value) || 1.16, true); });
        els.letterSpacing.addEventListener('change', function () {
            var text = selectedText();
            if (!text) return;
            var px = Number(els.letterSpacing.value) || 0;
            updateTextProperty('charSpacing', px * 1000 / (text.fontSize || 32), true);
        });
        els.textBold.addEventListener('click', function () {
            var pressed = els.textBold.getAttribute('aria-pressed') === 'true';
            updateTextProperty('fontWeight', pressed ? '400' : '700', true);
            renderProperties();
        });
        els.textItalic.addEventListener('click', function () {
            var pressed = els.textItalic.getAttribute('aria-pressed') === 'true';
            updateTextProperty('fontStyle', pressed ? 'normal' : 'italic', true);
            renderProperties();
        });
        document.querySelectorAll('[data-text-align]').forEach(function (button) {
            button.addEventListener('click', function () { updateTextProperty('textAlign', button.dataset.textAlign, true); renderProperties(); });
        });
        els.textOpacity.addEventListener('input', function () {
            els.textOpacityValue.value = els.textOpacity.value + '%';
            els.textOpacityValue.textContent = els.textOpacity.value + '%';
            updateTextProperty('opacity', Number(els.textOpacity.value) / 100, false);
        });
        els.textOpacity.addEventListener('change', function () { updateTextProperty('opacity', Number(els.textOpacity.value) / 100, true); });
        els.duplicateText.addEventListener('click', duplicateSelectedText);
        els.deleteText.addEventListener('click', deleteSelectedText);
        els.bringForward.addEventListener('click', function () { var text = selectedText(); if (text) { text.bringForward(); queueMutation(true); state.canvas.renderAll(); } });
        els.sendBackward.addEventListener('click', function () { var text = selectedText(); if (text) { text.sendBackwards(); queueMutation(true); state.canvas.renderAll(); } });

        document.addEventListener('keydown', function (event) {
            var target = event.target;
            var typing = target && (/INPUT|TEXTAREA|SELECT/.test(target.tagName) || target.isContentEditable);
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
                event.preventDefault(); saveNow(); return;
            }
            if (typing) return;
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') { event.preventDefault(); event.shiftKey ? redo() : undo(); return; }
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'y') { event.preventDefault(); redo(); return; }
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'd') { event.preventDefault(); duplicateSelectedText(); return; }
            if (event.key === 'Delete' || event.key === 'Backspace') {
                if (selectedText()) { event.preventDefault(); deleteSelectedText(); }
                else if (selectedFrameImage()) { event.preventDefault(); removeFrameImage(); }
            }
            if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
                var object = selectedText();
                if (!object) return;
                event.preventDefault();
                var step = event.shiftKey ? 10 : 1;
                if (event.key === 'ArrowLeft') object.left -= step;
                if (event.key === 'ArrowRight') object.left += step;
                if (event.key === 'ArrowUp') object.top -= step;
                if (event.key === 'ArrowDown') object.top += step;
                object.setCoords(); state.canvas.renderAll(); queueMutation(false);
            }
        });
        window.addEventListener('resize', function () { clearTimeout(setupDomEvents.resizeTimer); setupDomEvents.resizeTimer = setTimeout(applyViewportScale, 120); });
        window.addEventListener('beforeunload', function (event) { if (state.dirty) { event.preventDefault(); event.returnValue = ''; } });
        document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'hidden') saveNow(); });
    }

    setupDomEvents();
    initialize();
})();
