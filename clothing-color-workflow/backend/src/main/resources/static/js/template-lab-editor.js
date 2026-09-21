(function () {
    'use strict';

    var CUSTOM_PROPS = [
        'labType', 'frameId', 'frameName', 'frameBounds', 'frameShape', 'coverScale',
        'cropZoom', 'assetUrl', 'assetName', 'isTemplateText', 'fieldId', 'fieldLabel',
        'lockedByUser', 'excludeFromExport', 'elementId', 'originalAssetUrl', 'originalAssetName',
        'cutoutUrl', 'activeImageVersion', 'templateRole', 'cutoutTaskId', 'cutoutTaskStatus',
        'cutoutTaskError', 'cutoutSourceUrl', 'cutoutEstimatedCost', 'cutoutActualCost',
        'backgroundAssetUrl', 'backgroundAssetName', 'backgroundOpacity',
        'backgroundZoom', 'backgroundCropX', 'backgroundCropY', 'subjectElementId', 'baseWidth',
        'baseHeight', 'isTemplateImage', 'sourceImageWidth', 'sourceImageHeight',
        'subjectCropX', 'subjectCropY', 'subjectCropWidth', 'subjectCropHeight'
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
        guideLines: [],
        fieldSequence: 0,
        assetPickMode: '',
        pendingPlacement: null,
        backgroundEdit: null,
        cutoutQuote: null,
        cutoutTargetId: null,
        cutoutPollers: {}
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
        frameToFreeImage: document.getElementById('frame-to-free-image'),
        propsImage: document.getElementById('properties-image'),
        freeImageName: document.getElementById('free-image-name'),
        freeImageStatus: document.getElementById('free-image-status'),
        showOriginalImage: document.getElementById('show-original-image'),
        showCutoutImage: document.getElementById('show-cutout-image'),
        cutoutTaskCard: document.getElementById('cutout-task-card'),
        cutoutTaskTitle: document.getElementById('cutout-task-title'),
        cutoutTaskDetail: document.getElementById('cutout-task-detail'),
        freeImageOpacity: document.getElementById('free-image-opacity'),
        freeImageOpacityValue: document.getElementById('free-image-opacity-value'),
        flipImageHorizontal: document.getElementById('flip-image-horizontal'),
        flipImageVertical: document.getElementById('flip-image-vertical'),
        rotateImage: document.getElementById('rotate-image'),
        resetFreeImage: document.getElementById('reset-free-image'),
        freeImageCropControls: document.getElementById('free-image-crop-controls'),
        freeImageCropWidth: document.getElementById('free-image-crop-width'),
        freeImageCropWidthValue: document.getElementById('free-image-crop-width-value'),
        freeImageCropHeight: document.getElementById('free-image-crop-height'),
        freeImageCropHeightValue: document.getElementById('free-image-crop-height-value'),
        freeImageCropX: document.getElementById('free-image-crop-x'),
        freeImageCropXValue: document.getElementById('free-image-crop-x-value'),
        freeImageCropY: document.getElementById('free-image-crop-y'),
        freeImageCropYValue: document.getElementById('free-image-crop-y-value'),
        resetFreeImageCrop: document.getElementById('reset-free-image-crop'),
        compositionCropNote: document.getElementById('composition-crop-note'),
        imageTemplateRole: document.getElementById('image-template-role'),
        cutoutPrice: document.getElementById('cutout-price'),
        startCutout: document.getElementById('start-cutout'),
        chooseBackground: document.getElementById('choose-background'),
        backgroundHelp: document.getElementById('background-help'),
        backgroundControls: document.getElementById('background-controls'),
        backgroundOpacity: document.getElementById('background-opacity'),
        backgroundOpacityValue: document.getElementById('background-opacity-value'),
        backgroundZoom: document.getElementById('background-zoom'),
        backgroundZoomValue: document.getElementById('background-zoom-value'),
        editBackgroundPosition: document.getElementById('edit-background-position'),
        changeBackground: document.getElementById('change-background'),
        removeBackground: document.getElementById('remove-background'),
        splitImageComposition: document.getElementById('split-image-composition'),
        freeImageFrameTarget: document.getElementById('free-image-frame-target'),
        placeImageInFrame: document.getElementById('place-image-in-frame'),
        duplicateFreeImage: document.getElementById('duplicate-free-image'),
        deleteFreeImage: document.getElementById('delete-free-image'),
        imageBringForward: document.getElementById('image-bring-forward'),
        imageSendBackward: document.getElementById('image-send-backward'),
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
        propsMulti: document.getElementById('properties-multi'),
        multiSelectionCount: document.getElementById('multi-selection-count'),
        contentSummary: document.getElementById('content-summary'),
        contentList: document.getElementById('content-replace-list'),
        layerList: document.getElementById('layer-list'),
        saveAsTemplate: document.getElementById('save-as-template'),
        saveTemplateDialog: document.getElementById('save-template-dialog'),
        saveTemplateName: document.getElementById('save-template-name'),
        saveTemplateCategory: document.getElementById('save-template-category'),
        saveTemplateDescription: document.getElementById('save-template-description'),
        saveTemplateSpec: document.getElementById('save-template-spec'),
        confirmSaveTemplate: document.getElementById('confirm-save-template'),
        cutoutConfirmDialog: document.getElementById('cutout-confirm-dialog'),
        cutoutConfirmCost: document.getElementById('cutout-confirm-cost'),
        confirmCutout: document.getElementById('confirm-cutout'),
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

    function uniqueFieldId(prefix) {
        state.fieldSequence += 1;
        return (prefix || 'field') + '-' + Date.now() + '-' + state.fieldSequence;
    }

    function uniqueElementId(prefix) {
        return uniqueFieldId(prefix || 'image');
    }

    function loadFabricImage(url) {
        return new Promise(function (resolve, reject) {
            fabric.Image.fromURL(url, function (image) {
                if (!image || !image.width || !image.height) return reject(new Error('图片读取失败'));
                image.crossOrigin = 'anonymous';
                resolve(image);
            }, { crossOrigin: 'anonymous' });
        });
    }

    function selectedVisual() {
        var active = state.canvas && state.canvas.getActiveObject();
        return active && (active.labType === 'freeImage' || active.labType === 'imageComposition') ? active : null;
    }

    function findVisualByElementId(elementId) {
        if (!state.canvas || !elementId) return null;
        return state.canvas.getObjects().find(function (object) {
            return (object.labType === 'freeImage' || object.labType === 'imageComposition' || object.labType === 'imagePlaceholder')
                && String(object.elementId) === String(elementId);
        }) || null;
    }

    function compositionSubject(group) {
        if (!group || group.labType !== 'imageComposition' || typeof group.getObjects !== 'function') return null;
        return group.getObjects().find(function (object) { return object.labType === 'imageSubject'; }) || null;
    }

    function compositionBackground(group) {
        if (!group || group.labType !== 'imageComposition' || typeof group.getObjects !== 'function') return null;
        return group.getObjects().find(function (object) { return object.labType === 'imageBackground'; }) || null;
    }

    function activeVisualUrl(object) {
        if (!object) return '';
        if (object.activeImageVersion === 'cutout' && object.cutoutUrl) return object.cutoutUrl;
        return object.originalAssetUrl || object.assetUrl || '';
    }

    function styleFreeVisual(object) {
        if (!object) return;
        object.set({
            selectable: !object.lockedByUser,
            evented: !object.lockedByUser,
            objectCaching: false,
            subTargetCheck: object.labType === 'imageComposition'
        });
        styleInteractiveObject(object);
    }

    async function addFreeImage(asset, point, options) {
        if (!asset || !asset.url) return null;
        try {
            var image = await loadFabricImage(asset.url);
            var maxWidth = state.logicalWidth * .42;
            var maxHeight = state.logicalHeight * .42;
            var scale = Math.min(maxWidth / image.width, maxHeight / image.height, 1);
            var config = options || {};
            var sourceWidth = Number(config.sourceImageWidth) || image.width;
            var sourceHeight = Number(config.sourceImageHeight) || image.height;
            var cropWidth = Number(config.cropWidth) || image.width;
            var cropHeight = Number(config.cropHeight) || image.height;
            var baseWidth = Number(config.baseWidth) || cropWidth;
            var baseHeight = Number(config.baseHeight) || cropHeight;
            var configuredScaleX = config.scaleX != null && Number.isFinite(Number(config.scaleX))
                ? Number(config.scaleX) * baseWidth / cropWidth
                : scale;
            var configuredScaleY = config.scaleY != null && Number.isFinite(Number(config.scaleY))
                ? Number(config.scaleY) * baseHeight / cropHeight
                : scale;
            image.set({
                left: point && Number.isFinite(point.x) ? point.x : state.logicalWidth / 2,
                top: point && Number.isFinite(point.y) ? point.y : state.logicalHeight / 2,
                originX: 'center',
                originY: 'center',
                scaleX: configuredScaleX,
                scaleY: configuredScaleY,
                angle: config.angle || 0,
                opacity: config.opacity == null ? 1 : config.opacity,
                labType: 'freeImage',
                elementId: config.elementId || uniqueElementId('image'),
                assetUrl: asset.url,
                assetName: asset.name || '图片',
                originalAssetUrl: config.originalAssetUrl || asset.url,
                originalAssetName: config.originalAssetName || asset.name || '图片',
                cutoutUrl: config.cutoutUrl || '',
                activeImageVersion: config.activeImageVersion || 'original',
                templateRole: config.templateRole || 'replaceable',
                cutoutTaskId: config.cutoutTaskId || '',
                cutoutTaskStatus: config.cutoutTaskStatus || '',
                cutoutTaskError: config.cutoutTaskError || '',
                sourceImageWidth: sourceWidth,
                sourceImageHeight: sourceHeight,
                cropX: Number(config.cropX) || 0,
                cropY: Number(config.cropY) || 0,
                width: cropWidth,
                height: cropHeight,
                baseWidth: baseWidth,
                baseHeight: baseHeight,
                isTemplateImage: Boolean(config.isTemplateImage)
            });
            styleFreeVisual(image);
            state.canvas.add(image);
            if (config.select !== false) state.canvas.setActiveObject(image);
            state.canvas.requestRenderAll();
            if (!config.silent) queueMutation(true);
            renderProperties();
            return image;
        } catch (error) {
            showToast('图片读取失败，请检查素材链接', true);
            return null;
        }
    }

    function frameLabel(frameId) {
        var frames = state.template.frames || [];
        var index = frames.findIndex(function (item) { return item.id === frameId; });
        return index >= 0 ? (frames[index].label || '相框 ' + (index + 1)) : '相框';
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
            angle: definition.angle || 0,
            opacity: definition.opacity == null ? 1 : definition.opacity,
            scaleX: definition.scaleX || 1,
            scaleY: definition.scaleY || 1,
            labType: 'text',
            isTemplateText: true,
            fieldId: definition.fieldId || definition.id || uniqueFieldId('field'),
            fieldLabel: definition.label || '文字字段',
            editable: true,
            splitByGrapheme: true
        });
        styleInteractiveObject(text);
        state.canvas.add(text);
    }

    function addTemplateImage(definition) {
        var role = definition.role || 'replaceable';
        var point = { x: Number(definition.x) || state.logicalWidth / 2, y: Number(definition.y) || state.logicalHeight / 2 };
        if (role !== 'fixed' || !definition.url) {
            var placeholder = new fabric.Rect({
                left: point.x,
                top: point.y,
                originX: 'center',
                originY: 'center',
                width: Number(definition.baseWidth) || 360,
                height: Number(definition.baseHeight) || 360,
                scaleX: Number(definition.scaleX) || 1,
                scaleY: Number(definition.scaleY) || 1,
                angle: Number(definition.angle) || 0,
                fill: 'rgba(237,244,255,.62)',
                stroke: 'rgba(37,99,235,.54)',
                strokeWidth: 2,
                strokeDashArray: [12, 9],
                labType: 'imagePlaceholder',
                elementId: definition.elementId || uniqueElementId('slot'),
                assetName: definition.name || '可替换图片',
                templateRole: 'replaceable',
                backgroundAssetUrl: definition.backgroundAssetUrl || '',
                backgroundAssetName: definition.backgroundAssetName || '',
                backgroundOpacity: definition.backgroundOpacity == null ? 1 : Number(definition.backgroundOpacity),
                backgroundZoom: Number(definition.backgroundZoom) || 1,
                backgroundCropX: definition.backgroundCropX == null ? null : Number(definition.backgroundCropX),
                backgroundCropY: definition.backgroundCropY == null ? null : Number(definition.backgroundCropY),
                baseWidth: Number(definition.baseWidth) || 360,
                baseHeight: Number(definition.baseHeight) || 360,
                hoverCursor: 'pointer'
            });
            styleInteractiveObject(placeholder);
            state.canvas.add(placeholder);
            return;
        }
        addFreeImage({ url: definition.url, name: definition.name || '固定模板素材' }, point, {
            elementId: definition.elementId,
            scaleX: Number(definition.scaleX) || 1,
            scaleY: Number(definition.scaleY) || 1,
            angle: Number(definition.angle) || 0,
            opacity: definition.opacity == null ? 1 : Number(definition.opacity),
            originalAssetUrl: definition.originalAssetUrl || definition.url,
            originalAssetName: definition.name || '固定模板素材',
            cutoutUrl: definition.cutoutUrl || '',
            activeImageVersion: definition.activeImageVersion || 'original',
            templateRole: 'fixed',
            baseWidth: Number(definition.baseWidth) || undefined,
            baseHeight: Number(definition.baseHeight) || undefined,
            sourceImageWidth: Number(definition.sourceImageWidth) || undefined,
            sourceImageHeight: Number(definition.sourceImageHeight) || undefined,
            cropX: Number(definition.subjectCropX) || 0,
            cropY: Number(definition.subjectCropY) || 0,
            cropWidth: Number(definition.subjectCropWidth) || undefined,
            cropHeight: Number(definition.subjectCropHeight) || undefined,
            isTemplateImage: true,
            select: false,
            silent: true
        }).then(function (image) {
            if (image && definition.backgroundAssetUrl) {
                applyBackgroundAsset(
                    { url: definition.backgroundAssetUrl, name: definition.backgroundAssetName || '模板背景' },
                    Object.assign({}, definition, { silent: true }),
                    image
                );
            }
        });
    }

    function buildTemplateCanvas() {
        state.suppressHistory = true;
        state.canvas.clear();
        state.canvas.backgroundColor = state.template.background || '#f5f7fa';
        (state.template.frames || []).forEach(addTemplateFrame);
        (state.template.texts || []).forEach(addTemplateText);
        (state.template.images || []).forEach(addTemplateImage);
        state.canvas.renderAll();
        state.suppressHistory = false;
    }

    function rehydrateCanvasObjects() {
        var imagesByFrame = {};
        var templateTextIndex = 0;
        state.canvas.getObjects().forEach(function (object) {
            if (object.labType === 'frameImage') imagesByFrame[object.frameId] = object;
        });
        state.canvas.getObjects().forEach(function (object) {
            if (object.labType === 'text') {
                var definition = object.isTemplateText ? (state.template.texts || [])[templateTextIndex++] : null;
                object.set({
                    fieldId: object.fieldId || (definition && (definition.fieldId || definition.id)) || uniqueFieldId('free'),
                    fieldLabel: object.fieldLabel || (definition && definition.label) || (object.isTemplateText ? '模板文字' : '自由文字'),
                    selectable: !object.lockedByUser,
                    evented: !object.lockedByUser,
                    editable: !object.lockedByUser,
                    splitByGrapheme: true
                });
                styleInteractiveObject(object);
            } else if (object.labType === 'frameImage') {
                object.set({ selectable: true, evented: true, hasControls: false, hasBorders: false, lockScalingX: true, lockScalingY: true, lockRotation: true, objectCaching: false });
                object.crossOrigin = 'anonymous';
                bindFrameHitTest(object);
            } else if (object.labType === 'freeImage') {
                object.crossOrigin = 'anonymous';
                object.set({
                    elementId: object.elementId || uniqueElementId('image'),
                    originalAssetUrl: object.originalAssetUrl || object.assetUrl || object.getSrc(),
                    originalAssetName: object.originalAssetName || object.assetName || '图片',
                    activeImageVersion: object.activeImageVersion || 'original',
                    templateRole: object.templateRole || 'replaceable',
                    sourceImageWidth: object.sourceImageWidth
                        || Number(object._element && (object._element.naturalWidth || object._element.width))
                        || object.width,
                    sourceImageHeight: object.sourceImageHeight
                        || Number(object._element && (object._element.naturalHeight || object._element.height))
                        || object.height,
                    baseWidth: object.baseWidth || object.width,
                    baseHeight: object.baseHeight || object.height
                });
                styleFreeVisual(object);
            } else if (object.labType === 'imageComposition') {
                object.set({
                    elementId: object.elementId || uniqueElementId('image'),
                    activeImageVersion: object.activeImageVersion || 'original',
                    templateRole: object.templateRole || 'replaceable'
                });
                styleFreeVisual(object);
                var subject = compositionSubject(object);
                var background = compositionBackground(object);
                if (subject) { subject.crossOrigin = 'anonymous'; subject.set({ selectable: false, evented: false, objectCaching: false }); }
                if (background) { background.crossOrigin = 'anonymous'; background.set({ selectable: false, evented: false, objectCaching: false }); }
            } else if (object.labType === 'imagePlaceholder') {
                object.set({ selectable: true, evented: true, hoverCursor: 'pointer' });
                styleInteractiveObject(object);
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

    function queueMutation(immediate, skipPanelRefresh) {
        if (state.suppressHistory) return;
        if (!skipPanelRefresh) {
            renderContentPanel();
            renderLayerPanel();
        }
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

    function assignImageToFrame(frameId, asset, onReady) {
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
            if (typeof onReady === 'function') onReady(image);
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

    function convertSelectedFrameToFreeImage() {
        var image = selectedFrameImage();
        if (!image) return;
        var frameId = image.frameId;
        image.clipPath = null;
        image.set({
            labType: 'freeImage',
            elementId: uniqueElementId('image'),
            originalAssetUrl: image.assetUrl || image.getSrc(),
            originalAssetName: image.assetName || '图片',
            cutoutUrl: '',
            activeImageVersion: 'original',
            templateRole: 'replaceable',
            cutoutTaskId: '',
            cutoutTaskStatus: '',
            cutoutTaskError: '',
            sourceImageWidth: image.width,
            sourceImageHeight: image.height,
            baseWidth: image.width,
            baseHeight: image.height,
            frameId: '',
            frameName: '',
            frameBounds: null,
            coverScale: null,
            cropZoom: null,
            hasControls: true,
            hasBorders: true,
            lockScalingX: false,
            lockScalingY: false,
            lockRotation: false,
            hoverCursor: 'move'
        });
        setPlaceholderState(frameId, false);
        styleFreeVisual(image);
        state.canvas.setActiveObject(image);
        state.canvas.requestRenderAll();
        queueMutation(true);
        renderProperties();
        showToast('已转为自由图片，可在整个画布中移动');
    }

    function deleteSelectedVisual() {
        var visual = selectedVisual();
        if (!visual) return;
        stopBackgroundEditing();
        state.canvas.remove(visual);
        state.canvas.discardActiveObject();
        state.canvas.requestRenderAll();
        queueMutation(true);
        renderProperties();
    }

    function duplicateSelectedVisual() {
        var visual = selectedVisual();
        if (!visual) return;
        visual.clone(function (copy) {
            copy.set({
                left: visual.left + 28,
                top: visual.top + 28,
                elementId: uniqueElementId('image'),
                cutoutTaskId: '',
                cutoutTaskStatus: '',
                cutoutTaskError: ''
            });
            styleFreeVisual(copy);
            state.canvas.add(copy);
            state.canvas.setActiveObject(copy);
            state.canvas.requestRenderAll();
            queueMutation(true);
            renderProperties();
        }, CUSTOM_PROPS);
    }

    function updateVisualProperty(property, value, commit) {
        var visual = selectedVisual();
        if (!visual) return;
        visual.set(property, value);
        visual.setCoords();
        state.canvas.requestRenderAll();
        if (commit) queueMutation(true);
    }

    function setVisualVersion(visual, version) {
        if (!visual) return;
        var url = version === 'cutout' ? visual.cutoutUrl : visual.originalAssetUrl;
        if (!url) return showToast(version === 'cutout' ? '尚未生成抠图结果' : '原图地址不可用', true);
        var image = visual.labType === 'imageComposition' ? compositionSubject(visual) : visual;
        if (!image) return;
        var width = visual.baseWidth || image.width;
        var height = visual.baseHeight || image.height;
        var sourceWidth = Number(visual.sourceImageWidth)
            || Number(image._element && (image._element.naturalWidth || image._element.width))
            || image.width;
        var sourceHeight = Number(visual.sourceImageHeight)
            || Number(image._element && (image._element.naturalHeight || image._element.height))
            || image.height;
        var cropWidthRatio = Math.max(.01, Math.min(1, image.width / sourceWidth));
        var cropHeightRatio = Math.max(.01, Math.min(1, image.height / sourceHeight));
        var cropPositionX = sourceWidth > image.width ? (Number(image.cropX) || 0) / (sourceWidth - image.width) : .5;
        var cropPositionY = sourceHeight > image.height ? (Number(image.cropY) || 0) / (sourceHeight - image.height) : .5;
        cropPositionX = Math.max(0, Math.min(1, cropPositionX));
        cropPositionY = Math.max(0, Math.min(1, cropPositionY));
        var displayedWidth = image.getScaledWidth();
        var displayedHeight = image.getScaledHeight();
        image.setSrc(url, function () {
            var nextSourceWidth = Number(image._element && (image._element.naturalWidth || image._element.width)) || image.width;
            var nextSourceHeight = Number(image._element && (image._element.naturalHeight || image._element.height)) || image.height;
            var nextCropWidth = nextSourceWidth * cropWidthRatio;
            var nextCropHeight = nextSourceHeight * cropHeightRatio;
            var nextCropX = (nextSourceWidth - nextCropWidth) * cropPositionX;
            var nextCropY = (nextSourceHeight - nextCropHeight) * cropPositionY;
            image.set({
                width: nextCropWidth,
                height: nextCropHeight,
                cropX: nextCropX,
                cropY: nextCropY
            });
            if (visual.labType === 'imageComposition') {
                image.set({
                    left: 0,
                    top: 0,
                    originX: 'center',
                    originY: 'center',
                    scaleX: width / nextCropWidth,
                    scaleY: height / nextCropHeight
                });
                visual.set({
                    subjectCropX: nextCropX,
                    subjectCropY: nextCropY,
                    subjectCropWidth: nextCropWidth,
                    subjectCropHeight: nextCropHeight
                });
            } else {
                image.set({
                    scaleX: displayedWidth / nextCropWidth,
                    scaleY: displayedHeight / nextCropHeight,
                    baseWidth: nextCropWidth,
                    baseHeight: nextCropHeight
                });
            }
            visual.set({
                activeImageVersion: version,
                assetUrl: url,
                sourceImageWidth: nextSourceWidth,
                sourceImageHeight: nextSourceHeight
            });
            visual.setCoords();
            state.canvas.requestRenderAll();
            queueMutation(true);
            if (selectedVisual() === visual) renderProperties();
        }, { crossOrigin: 'anonymous' });
    }

    function switchVisualVersion(version) {
        setVisualVersion(selectedVisual(), version);
    }

    function configureBackgroundImage(image, baseWidth, baseHeight, zoom, cropX, cropY) {
        var safeZoom = Math.max(1, Math.min(3, Number(zoom) || 1));
        var sourceWidth = Number(image._element && (image._element.naturalWidth || image._element.width)) || Number(image.width) || 1;
        var sourceHeight = Number(image._element && (image._element.naturalHeight || image._element.height)) || Number(image.height) || 1;
        var cover = Math.max(baseWidth / sourceWidth, baseHeight / sourceHeight);
        var effectiveScale = cover * safeZoom;
        var cropWidth = baseWidth / effectiveScale;
        var cropHeight = baseHeight / effectiveScale;
        var maxCropX = Math.max(0, sourceWidth - cropWidth);
        var maxCropY = Math.max(0, sourceHeight - cropHeight);
        var nextCropX = cropX == null ? maxCropX / 2 : Math.max(0, Math.min(maxCropX, cropX));
        var nextCropY = cropY == null ? maxCropY / 2 : Math.max(0, Math.min(maxCropY, cropY));
        image.set({
            left: 0,
            top: 0,
            originX: 'center',
            originY: 'center',
            width: cropWidth,
            height: cropHeight,
            cropX: nextCropX,
            cropY: nextCropY,
            scaleX: effectiveScale,
            scaleY: effectiveScale,
            labType: 'imageBackground',
            backgroundZoom: safeZoom,
            backgroundCropX: nextCropX,
            backgroundCropY: nextCropY,
            selectable: false,
            evented: false,
            objectCaching: false
        });
    }

    function visualMetadata(visual) {
        var subject = visual.labType === 'imageComposition' ? compositionSubject(visual) : visual;
        var sourceWidth = Number(visual.sourceImageWidth)
            || Number(subject && subject._element && (subject._element.naturalWidth || subject._element.width))
            || Number(subject && subject.width) || 1;
        var sourceHeight = Number(visual.sourceImageHeight)
            || Number(subject && subject._element && (subject._element.naturalHeight || subject._element.height))
            || Number(subject && subject.height) || 1;
        return {
            elementId: visual.elementId || uniqueElementId('image'),
            assetUrl: activeVisualUrl(visual),
            assetName: visual.assetName || visual.originalAssetName || '图片',
            originalAssetUrl: visual.originalAssetUrl || visual.assetUrl,
            originalAssetName: visual.originalAssetName || visual.assetName || '图片',
            cutoutUrl: visual.cutoutUrl || '',
            activeImageVersion: visual.activeImageVersion || 'original',
            templateRole: visual.templateRole || 'replaceable',
            cutoutTaskId: visual.cutoutTaskId || '',
            cutoutTaskStatus: visual.cutoutTaskStatus || '',
            cutoutTaskError: visual.cutoutTaskError || '',
            cutoutSourceUrl: visual.cutoutSourceUrl || '',
            cutoutEstimatedCost: visual.cutoutEstimatedCost == null ? null : visual.cutoutEstimatedCost,
            cutoutActualCost: visual.cutoutActualCost == null ? null : visual.cutoutActualCost,
            sourceImageWidth: sourceWidth,
            sourceImageHeight: sourceHeight,
            subjectCropX: visual.labType === 'imageComposition' ? (visual.subjectCropX || 0) : (subject.cropX || 0),
            subjectCropY: visual.labType === 'imageComposition' ? (visual.subjectCropY || 0) : (subject.cropY || 0),
            subjectCropWidth: visual.labType === 'imageComposition' ? (visual.subjectCropWidth || subject.width) : subject.width,
            subjectCropHeight: visual.labType === 'imageComposition' ? (visual.subjectCropHeight || subject.height) : subject.height,
            isTemplateImage: Boolean(visual.isTemplateImage)
        };
    }

    async function applyBackgroundAsset(asset, backgroundOptions, targetVisual) {
        var visual = targetVisual || selectedVisual();
        if (!visual || !asset || !asset.url) return;
        stopBackgroundEditing();
        try {
            var requestedBackground = backgroundOptions || {};
            var currentSubjectUrl = activeVisualUrl(visual);
            var images = await Promise.all([loadFabricImage(currentSubjectUrl), loadFabricImage(asset.url)]);
            var subject = images[0];
            var background = images[1];
            var existingSubject = visual.labType === 'imageComposition' ? compositionSubject(visual) : visual;
            var meta = visualMetadata(visual);
            var baseWidth = visual.labType === 'imageComposition'
                ? (visual.baseWidth || (existingSubject && existingSubject.width) || subject.width)
                : ((existingSubject && existingSubject.width) || subject.width);
            var baseHeight = visual.labType === 'imageComposition'
                ? (visual.baseHeight || (existingSubject && existingSubject.height) || subject.height)
                : ((existingSubject && existingSubject.height) || subject.height);
            var index = state.canvas.getObjects().indexOf(visual);
            var transform = {
                left: visual.left,
                top: visual.top,
                originX: visual.originX || 'center',
                originY: visual.originY || 'center',
                scaleX: visual.scaleX || 1,
                scaleY: visual.scaleY || 1,
                angle: visual.angle || 0,
                flipX: Boolean(visual.flipX),
                flipY: Boolean(visual.flipY),
                opacity: visual.opacity == null ? 1 : visual.opacity
            };
            subject.set({
                left: 0,
                top: 0,
                originX: 'center',
                originY: 'center',
                width: meta.subjectCropWidth || subject.width,
                height: meta.subjectCropHeight || subject.height,
                cropX: meta.subjectCropX || 0,
                cropY: meta.subjectCropY || 0,
                scaleX: baseWidth / (meta.subjectCropWidth || subject.width),
                scaleY: baseHeight / (meta.subjectCropHeight || subject.height),
                labType: 'imageSubject',
                subjectElementId: meta.elementId,
                selectable: false,
                evented: false,
                objectCaching: false
            });
            var previousBackground = visual.labType === 'imageComposition' ? compositionBackground(visual) : null;
            configureBackgroundImage(background, baseWidth, baseHeight,
                previousBackground ? previousBackground.backgroundZoom : (requestedBackground.backgroundZoom || 1),
                previousBackground ? previousBackground.backgroundCropX : requestedBackground.backgroundCropX,
                previousBackground ? previousBackground.backgroundCropY : requestedBackground.backgroundCropY);
            background.set({
                opacity: previousBackground && previousBackground.opacity != null
                    ? previousBackground.opacity
                    : (requestedBackground.backgroundOpacity == null ? 1 : Number(requestedBackground.backgroundOpacity)),
                assetUrl: asset.url,
                assetName: asset.name || '背景图片'
            });
            var group = new fabric.Group([background, subject], Object.assign(transform, meta, {
                labType: 'imageComposition',
                baseWidth: baseWidth,
                baseHeight: baseHeight,
                backgroundAssetUrl: asset.url,
                backgroundAssetName: asset.name || '背景图片',
                backgroundOpacity: background.opacity,
                backgroundZoom: background.backgroundZoom,
                backgroundCropX: background.backgroundCropX,
                backgroundCropY: background.backgroundCropY,
                sourceImageWidth: meta.sourceImageWidth,
                sourceImageHeight: meta.sourceImageHeight,
                subjectCropX: meta.subjectCropX,
                subjectCropY: meta.subjectCropY,
                subjectCropWidth: meta.subjectCropWidth,
                subjectCropHeight: meta.subjectCropHeight,
                objectCaching: false,
                subTargetCheck: true
            }));
            styleFreeVisual(group);
            state.canvas.remove(visual);
            state.canvas.insertAt(group, Math.max(0, index), false);
            state.canvas.setActiveObject(group);
            state.canvas.requestRenderAll();
            state.assetPickMode = '';
            if (!requestedBackground.silent) queueMutation(true);
            renderProperties();
            showToast('背景图片已添加，不会产生 KIE 费用');
        } catch (error) {
            showToast('背景图片读取失败', true);
        }
    }

    function removeCompositionBackground() {
        var group = selectedVisual();
        if (!group || group.labType !== 'imageComposition') return;
        stopBackgroundEditing();
        var meta = visualMetadata(group);
        var subject = compositionSubject(group);
        if (!subject) return;
        var center = group.getCenterPoint();
        var index = state.canvas.getObjects().indexOf(group);
        subject.clone(function (image) {
            image.set(Object.assign({
                left: center.x,
                top: center.y,
                originX: 'center',
                originY: 'center',
                scaleX: (Number(group.scaleX) || 1) * (Number(subject.scaleX) || 1),
                scaleY: (Number(group.scaleY) || 1) * (Number(subject.scaleY) || 1),
                angle: group.angle || 0,
                flipX: Boolean(group.flipX),
                flipY: Boolean(group.flipY),
                opacity: group.opacity == null ? 1 : group.opacity,
                labType: 'freeImage',
                baseWidth: subject.width,
                baseHeight: subject.height,
                cropX: subject.cropX || 0,
                cropY: subject.cropY || 0,
                sourceImageWidth: meta.sourceImageWidth,
                sourceImageHeight: meta.sourceImageHeight
            }, meta));
            styleFreeVisual(image);
            state.canvas.remove(group);
            state.canvas.insertAt(image, Math.max(0, index), false);
            state.canvas.setActiveObject(image);
            state.canvas.requestRenderAll();
            queueMutation(true);
            renderProperties();
        }, CUSTOM_PROPS);
    }

    function splitImageComposition() {
        var group = selectedVisual();
        if (!group || group.labType !== 'imageComposition') return;
        var subject = compositionSubject(group);
        var background = compositionBackground(group);
        if (!subject || !background) return;
        var center = group.getCenterPoint();
        var index = state.canvas.getObjects().indexOf(group);
        var groupScaleX = Number(group.scaleX) || 1;
        var groupScaleY = Number(group.scaleY) || 1;
        var cloneObject = function (object) {
            return new Promise(function (resolve) { object.clone(resolve, CUSTOM_PROPS); });
        };
        Promise.all([cloneObject(background), cloneObject(subject)]).then(function (copies) {
            var backgroundCopy = copies[0];
            var subjectCopy = copies[1];
            backgroundCopy.set({
                left: center.x,
                top: center.y,
                originX: 'center',
                originY: 'center',
                scaleX: groupScaleX * (Number(background.scaleX) || 1),
                scaleY: groupScaleY * (Number(background.scaleY) || 1),
                angle: Number(group.angle) || 0,
                flipX: Boolean(group.flipX),
                flipY: Boolean(group.flipY),
                opacity: (group.opacity == null ? 1 : group.opacity) * (background.opacity == null ? 1 : background.opacity),
                labType: 'freeImage',
                elementId: uniqueElementId('background'),
                assetUrl: group.backgroundAssetUrl,
                assetName: group.backgroundAssetName || '背景图片',
                originalAssetUrl: group.backgroundAssetUrl,
                originalAssetName: group.backgroundAssetName || '背景图片',
                cutoutUrl: '',
                activeImageVersion: 'original',
                templateRole: 'fixed',
                baseWidth: background.width,
                baseHeight: background.height,
                cutoutTaskId: '',
                cutoutTaskStatus: '',
                cutoutTaskError: ''
            });
            subjectCopy.set(Object.assign({
                left: center.x,
                top: center.y,
                originX: 'center',
                originY: 'center',
                scaleX: groupScaleX * (Number(subject.scaleX) || 1),
                scaleY: groupScaleY * (Number(subject.scaleY) || 1),
                angle: Number(group.angle) || 0,
                flipX: Boolean(group.flipX),
                flipY: Boolean(group.flipY),
                opacity: group.opacity == null ? 1 : group.opacity,
                labType: 'freeImage',
                baseWidth: subject.width,
                baseHeight: subject.height
            }, visualMetadata(group)));
            styleFreeVisual(backgroundCopy);
            styleFreeVisual(subjectCopy);
            state.canvas.remove(group);
            state.canvas.insertAt(backgroundCopy, Math.max(0, index), false);
            state.canvas.insertAt(subjectCopy, Math.max(0, index + 1), false);
            state.canvas.setActiveObject(subjectCopy);
            state.canvas.requestRenderAll();
            queueMutation(true);
            renderProperties();
            showToast('主体与背景已拆分为两个自由图片');
        });
    }

    function selectedBackground() {
        return compositionBackground(selectedVisual());
    }

    function setBackgroundOpacity(value, commit) {
        var group = selectedVisual();
        var background = compositionBackground(group);
        if (!group || !background) return;
        var opacity = Math.max(0, Math.min(1, Number(value) / 100));
        background.set('opacity', opacity);
        group.backgroundOpacity = opacity;
        state.canvas.requestRenderAll();
        if (commit) queueMutation(true);
    }

    function setBackgroundZoom(value, commit) {
        var group = selectedVisual();
        var background = compositionBackground(group);
        if (!group || !background) return;
        configureBackgroundImage(background, group.baseWidth || group.width, group.baseHeight || group.height,
            Number(value) / 100, background.backgroundCropX, background.backgroundCropY);
        group.set({
            backgroundZoom: background.backgroundZoom,
            backgroundCropX: background.backgroundCropX,
            backgroundCropY: background.backgroundCropY
        });
        state.canvas.requestRenderAll();
        if (commit) queueMutation(true);
    }

    function startBackgroundEditing() {
        var group = selectedVisual();
        if (!group || group.labType !== 'imageComposition') return;
        if (state.backgroundEdit && state.backgroundEdit.elementId === group.elementId) return stopBackgroundEditing();
        stopBackgroundEditing();
        state.backgroundEdit = { elementId: group.elementId, dragging: false };
        group.set({ lockMovementX: true, lockMovementY: true, hoverCursor: 'grab' });
        els.editBackgroundPosition.setAttribute('aria-pressed', 'true');
        els.editBackgroundPosition.textContent = '完成背景位置调整';
        els.stageTip.textContent = '在图片内拖动背景，主体和组合位置保持不变';
        els.stageTip.hidden = false;
        state.canvas.requestRenderAll();
    }

    function stopBackgroundEditing() {
        if (!state.backgroundEdit) return;
        var group = findVisualByElementId(state.backgroundEdit.elementId);
        if (group) group.set({ lockMovementX: false, lockMovementY: false, hoverCursor: 'move' });
        state.backgroundEdit = null;
        if (els.editBackgroundPosition) {
            els.editBackgroundPosition.setAttribute('aria-pressed', 'false');
            els.editBackgroundPosition.textContent = '拖动调整背景位置';
        }
        if (els.stageTip) els.stageTip.hidden = true;
        if (state.canvas) state.canvas.requestRenderAll();
    }

    function canvasPointAtClientPoint(event) {
        var rect = state.canvas.upperCanvasEl.getBoundingClientRect();
        var zoom = Math.max(state.canvas.getZoom(), .01);
        return new fabric.Point((event.clientX - rect.left) / zoom, (event.clientY - rect.top) / zoom);
    }

    function groupLocalPointer(group, nativeEvent) {
        var pointer = state.canvas.getPointer(nativeEvent);
        return fabric.util.transformPoint(pointer, fabric.util.invertTransform(group.calcTransformMatrix()));
    }

    function beginBackgroundDrag(event) {
        if (!state.backgroundEdit || !event.target || event.target.labType !== 'imageComposition'
                || String(event.target.elementId) !== String(state.backgroundEdit.elementId)) return false;
        var background = compositionBackground(event.target);
        if (!background) return false;
        var pointer = groupLocalPointer(event.target, event.e);
        state.backgroundEdit.dragging = true;
        state.backgroundEdit.moved = false;
        state.backgroundEdit.startX = pointer.x;
        state.backgroundEdit.startY = pointer.y;
        state.backgroundEdit.cropX = Number(background.backgroundCropX) || 0;
        state.backgroundEdit.cropY = Number(background.backgroundCropY) || 0;
        event.target.set('hoverCursor', 'grabbing');
        return true;
    }

    function moveBackgroundDrag(event) {
        if (!state.backgroundEdit || !state.backgroundEdit.dragging) return;
        var group = findVisualByElementId(state.backgroundEdit.elementId);
        var background = compositionBackground(group);
        if (!group || !background) return;
        var pointer = groupLocalPointer(group, event.e);
        var dx = pointer.x - state.backgroundEdit.startX;
        var dy = pointer.y - state.backgroundEdit.startY;
        if (Math.abs(dx) + Math.abs(dy) > 1) state.backgroundEdit.moved = true;
        var sourceScale = Math.max(Number(background.scaleX) || 1, .0001);
        configureBackgroundImage(background, group.baseWidth || group.width, group.baseHeight || group.height,
            background.backgroundZoom || 1,
            state.backgroundEdit.cropX - dx / sourceScale,
            state.backgroundEdit.cropY - dy / sourceScale);
        group.set({
            backgroundCropX: background.backgroundCropX,
            backgroundCropY: background.backgroundCropY,
            backgroundZoom: background.backgroundZoom
        });
        state.canvas.requestRenderAll();
    }

    function endBackgroundDrag() {
        if (!state.backgroundEdit || !state.backgroundEdit.dragging) return false;
        var moved = state.backgroundEdit.moved;
        state.backgroundEdit.dragging = false;
        var group = findVisualByElementId(state.backgroundEdit.elementId);
        if (group) group.set('hoverCursor', 'grab');
        if (moved) queueMutation(true);
        return true;
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

    function selectedTextObjects() {
        var active = state.canvas && state.canvas.getActiveObject();
        if (!active) return [];
        if (active.labType === 'text') return [active];
        if (active.type === 'activeSelection' && typeof active.getObjects === 'function') {
            return active.getObjects().filter(function (object) { return object.labType === 'text'; });
        }
        return [];
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
            fieldId: uniqueFieldId('free'),
            fieldLabel: preset === 'heading' ? '自由标题' : (preset === 'caption' ? '自由标注' : '自由正文'),
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
            copy.set({
                left: text.left + 28,
                top: text.top + 28,
                labType: 'text',
                isTemplateText: false,
                fieldId: uniqueFieldId('free'),
                fieldLabel: (text.fieldLabel || '自由文字') + ' 副本'
            });
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

    function selectedImagePlaceholder() {
        var active = state.canvas && state.canvas.getActiveObject();
        return active && active.labType === 'imagePlaceholder' ? active : null;
    }

    function fillImagePlaceholder(asset) {
        var placeholder = selectedImagePlaceholder();
        if (!placeholder || !asset) return;
        var point = { x: placeholder.left, y: placeholder.top };
        var index = state.canvas.getObjects().indexOf(placeholder);
        var backgroundUrl = placeholder.backgroundAssetUrl;
        var backgroundName = placeholder.backgroundAssetName;
        var backgroundOptions = {
            backgroundOpacity: placeholder.backgroundOpacity,
            backgroundZoom: placeholder.backgroundZoom,
            backgroundCropX: placeholder.backgroundCropX,
            backgroundCropY: placeholder.backgroundCropY
        };
        var config = {
            elementId: placeholder.elementId,
            scaleX: placeholder.scaleX,
            scaleY: placeholder.scaleY,
            angle: placeholder.angle,
            templateRole: 'replaceable',
            baseWidth: placeholder.baseWidth,
            baseHeight: placeholder.baseHeight,
            isTemplateImage: true
        };
        state.canvas.remove(placeholder);
        addFreeImage(asset, point, config).then(function (image) {
            if (!image) return;
            image.moveTo(Math.max(0, index));
            if (backgroundUrl) {
                state.canvas.setActiveObject(image);
                applyBackgroundAsset({ url: backgroundUrl, name: backgroundName || '模板背景' }, backgroundOptions);
            }
        });
    }

    function formatCny(value) {
        var number = Number(value);
        return Number.isFinite(number) ? '¥' + number.toFixed(number < 0.1 ? 3 : 2) : '费用待回传';
    }

    function freeImageCropState(image) {
        var sourceWidth = Number(image && image.sourceImageWidth)
            || Number(image && image._element && (image._element.naturalWidth || image._element.width))
            || Number(image && image.width) || 1;
        var sourceHeight = Number(image && image.sourceImageHeight)
            || Number(image && image._element && (image._element.naturalHeight || image._element.height))
            || Number(image && image.height) || 1;
        var width = Math.max(1, Math.min(sourceWidth, Number(image.width) || sourceWidth));
        var height = Math.max(1, Math.min(sourceHeight, Number(image.height) || sourceHeight));
        var maxX = Math.max(0, sourceWidth - width);
        var maxY = Math.max(0, sourceHeight - height);
        return {
            sourceWidth: sourceWidth,
            sourceHeight: sourceHeight,
            widthPercent: Math.round(width / sourceWidth * 100),
            heightPercent: Math.round(height / sourceHeight * 100),
            xPercent: maxX > 0 ? Math.round((Number(image.cropX) || 0) / maxX * 100) : 50,
            yPercent: maxY > 0 ? Math.round((Number(image.cropY) || 0) / maxY * 100) : 50
        };
    }

    function setFreeImageCrop(commit) {
        var image = selectedVisual();
        if (!image || image.labType !== 'freeImage') return;
        var current = freeImageCropState(image);
        var widthPercent = Math.max(10, Math.min(100, Number(els.freeImageCropWidth.value) || 100));
        var heightPercent = Math.max(10, Math.min(100, Number(els.freeImageCropHeight.value) || 100));
        var width = current.sourceWidth * widthPercent / 100;
        var height = current.sourceHeight * heightPercent / 100;
        var cropX = (current.sourceWidth - width) * Math.max(0, Math.min(100, Number(els.freeImageCropX.value) || 0)) / 100;
        var cropY = (current.sourceHeight - height) * Math.max(0, Math.min(100, Number(els.freeImageCropY.value) || 0)) / 100;
        image.set({
            width: width,
            height: height,
            cropX: cropX,
            cropY: cropY,
            baseWidth: width,
            baseHeight: height,
            sourceImageWidth: current.sourceWidth,
            sourceImageHeight: current.sourceHeight
        });
        image.setCoords();
        state.canvas.requestRenderAll();
        els.freeImageCropX.disabled = widthPercent >= 100;
        els.freeImageCropY.disabled = heightPercent >= 100;
        if (commit) queueMutation(true);
    }

    function renderImageProperties(visual) {
        els.freeImageName.textContent = visual.originalAssetName || visual.assetName || '自由图片';
        var taskStatus = String(visual.cutoutTaskStatus || '').toLowerCase();
        var hasCutout = Boolean(visual.cutoutUrl);
        var processing = taskStatus === 'processing' || taskStatus === 'queued';
        var failed = taskStatus === 'failed';
        els.freeImageStatus.className = 'editor-status-badge' + (processing ? ' is-processing' : (failed ? ' is-error' : ''));
        els.freeImageStatus.textContent = processing ? '抠图处理中' : (failed ? '抠图失败' : (hasCutout ? '已有抠图' : '可编辑'));
        els.cutoutTaskCard.hidden = !processing && !failed;
        els.cutoutTaskCard.classList.toggle('is-error', failed);
        els.cutoutTaskTitle.textContent = failed ? 'KIE 抠图失败' : 'KIE 正在抠图';
        els.cutoutTaskDetail.textContent = failed
            ? (visual.cutoutTaskError || '可点击重新抠图再次提交')
            : '正在识别主体边缘，完成后会自动替换内容';
        els.showOriginalImage.setAttribute('aria-pressed', String(visual.activeImageVersion !== 'cutout'));
        els.showCutoutImage.setAttribute('aria-pressed', String(visual.activeImageVersion === 'cutout'));
        els.showCutoutImage.disabled = !hasCutout;
        els.freeImageOpacity.value = Math.round((visual.opacity == null ? 1 : visual.opacity) * 100);
        els.freeImageOpacityValue.value = els.freeImageOpacity.value + '%';
        els.freeImageOpacityValue.textContent = els.freeImageOpacity.value + '%';
        var supportsCrop = visual.labType === 'freeImage';
        els.freeImageCropControls.hidden = !supportsCrop;
        els.compositionCropNote.hidden = supportsCrop;
        if (supportsCrop) {
            var crop = freeImageCropState(visual);
            els.freeImageCropWidth.value = crop.widthPercent;
            els.freeImageCropHeight.value = crop.heightPercent;
            els.freeImageCropX.value = crop.xPercent;
            els.freeImageCropY.value = crop.yPercent;
            els.freeImageCropWidthValue.value = crop.widthPercent + '%';
            els.freeImageCropWidthValue.textContent = crop.widthPercent + '%';
            els.freeImageCropHeightValue.value = crop.heightPercent + '%';
            els.freeImageCropHeightValue.textContent = crop.heightPercent + '%';
            els.freeImageCropXValue.value = crop.xPercent + '%';
            els.freeImageCropXValue.textContent = crop.xPercent + '%';
            els.freeImageCropYValue.value = crop.yPercent + '%';
            els.freeImageCropYValue.textContent = crop.yPercent + '%';
            els.freeImageCropX.disabled = crop.widthPercent >= 100;
            els.freeImageCropY.disabled = crop.heightPercent >= 100;
        }
        els.imageTemplateRole.value = visual.templateRole || 'replaceable';
        els.startCutout.disabled = processing;
        els.startCutout.textContent = processing ? '抠图处理中...' : (hasCutout ? '重新抠图' : '开始抠图');
        var quoteAmount = state.cutoutQuote && state.cutoutQuote.amount_cny;
        els.cutoutPrice.textContent = visual.cutoutActualCost != null
            ? '最近实际 ' + formatCny(visual.cutoutActualCost)
            : (quoteAmount == null ? '费用待回传' : '预计 ' + formatCny(quoteAmount) + '/张');

        var background = compositionBackground(visual);
        els.chooseBackground.hidden = Boolean(background);
        els.backgroundControls.hidden = !background;
        els.backgroundHelp.textContent = background
            ? '背景与主体组成一个元素；可单独调整背景，不会调用 KIE。'
            : '把素材放到当前图片下方，不会调用 KIE。';
        if (background) {
            var opacity = Math.round((background.opacity == null ? 1 : background.opacity) * 100);
            var zoom = Math.round((background.backgroundZoom || 1) * 100);
            els.backgroundOpacity.value = opacity;
            els.backgroundOpacityValue.value = opacity + '%';
            els.backgroundOpacityValue.textContent = opacity + '%';
            els.backgroundZoom.value = zoom;
            els.backgroundZoomValue.value = zoom + '%';
            els.backgroundZoomValue.textContent = zoom + '%';
        }

        var frames = state.template.frames || [];
        els.freeImageFrameTarget.innerHTML = frames.map(function (frame) {
            return '<option value="' + escapeHtml(frame.id) + '">' + escapeHtml(frame.label || frameLabel(frame.id)) + '</option>';
        }).join('');
        els.placeImageInFrame.disabled = !frames.length;
    }

    function renderProperties() {
        if (!state.canvas) return;
        var active = state.canvas.getActiveObject();
        if (state.backgroundEdit && (!active || String(active.elementId) !== String(state.backgroundEdit.elementId))) {
            stopBackgroundEditing();
        }
        els.propsEmpty.hidden = true;
        els.propsFrame.hidden = true;
        els.propsImage.hidden = true;
        els.propsText.hidden = true;
        els.propsMulti.hidden = true;
        if (!active || !active.labType) {
            var selectedTexts = selectedTextObjects();
            if (selectedTexts.length > 1) {
                els.propsMulti.hidden = false;
                els.multiSelectionCount.textContent = '已选择 ' + selectedTexts.length + ' 个文字元素';
                els.selectionKind.textContent = '多选文字';
                renderLayerPanel();
                return;
            }
            els.propsEmpty.hidden = false;
            els.selectionKind.textContent = '未选择元素';
            renderLayerPanel();
            return;
        }
        if (active.labType === 'framePlaceholder' || active.labType === 'frameImage') {
            els.propsFrame.hidden = false;
            els.selectionKind.textContent = active.labType === 'frameImage' ? '相框图片' : '空相框';
            els.frameName.textContent = active.frameName || frameLabel(active.frameId);
            var image = active.labType === 'frameImage' ? active : getFrameImage(active.frameId);
            els.cropControls.hidden = !image;
            els.frameToFreeImage.hidden = !image;
            els.frameHelp.textContent = image ? '拖动画面调整裁剪位置，缩放不会改变相框尺寸。' : '从左侧选择图片，或上传一张新图片。';
            if (image) {
                var zoomValue = Math.round((image.cropZoom || 1) * 100);
                els.imageZoom.value = zoomValue;
                els.imageZoomValue.value = zoomValue + '%';
                els.imageZoomValue.textContent = zoomValue + '%';
            }
            renderLayerPanel();
            return;
        }
        if (active.labType === 'freeImage' || active.labType === 'imageComposition') {
            els.propsImage.hidden = false;
            els.selectionKind.textContent = active.labType === 'imageComposition' ? '图片组合' : '自由图片';
            renderImageProperties(active);
            renderLayerPanel();
            return;
        }
        if (active.labType === 'imagePlaceholder') {
            els.propsEmpty.hidden = false;
            els.selectionKind.textContent = '可替换图片';
            renderLayerPanel();
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
            renderLayerPanel();
            return;
        }
        els.propsEmpty.hidden = false;
        els.selectionKind.textContent = '未选择元素';
        renderLayerPanel();
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

    function findTextByFieldId(fieldId) {
        return state.canvas && state.canvas.getObjects().find(function (object) {
            return object.labType === 'text' && String(object.fieldId) === String(fieldId);
        });
    }

    function renderContentPanel() {
        if (!state.canvas || !state.template || !els.contentList) return;
        var frames = state.template.frames || [];
        var definitions = state.template.texts || [];
        var filled = frames.filter(function (frame) { return Boolean(getFrameImage(frame.id)); }).length;
        els.contentSummary.innerHTML = '<strong>内容完成度 ' + filled + ' / ' + frames.length + ' 个相框</strong>'
            + '模板规格：' + escapeHtml(state.template.usageType) + ' · ' + escapeHtml(state.template.ratioGroup)
            + ' · ' + state.logicalWidth + ' × ' + state.logicalHeight;
        var frameRows = frames.map(function (frame) {
            var image = getFrameImage(frame.id);
            return '<section class="editor-content-item" data-content-frame="' + escapeHtml(frame.id) + '">'
                + '<div class="editor-content-item__head"><strong>' + escapeHtml(frame.label || frameLabel(frame.id)) + '</strong>'
                + '<span class="editor-content-status' + (image ? ' is-ready' : '') + '">' + (image ? '已填入' : '待替换') + '</span></div>'
                + '<div class="editor-layer-item__meta">' + escapeHtml(image ? (image.assetName || '已上传图片') : '选择图片后可在画布中调整裁剪') + '</div>'
                + '<div class="editor-content-item__actions"><button type="button" data-content-action="upload-frame">' + (image ? '替换图片' : '选择图片') + '</button>'
                + '<button type="button" data-content-action="select-frame">定位画布</button>'
                + (image ? '<button type="button" data-content-action="remove-frame">移除</button>' : '') + '</div></section>';
        }).join('');
        var textRows = definitions.map(function (definition) {
            var fieldId = definition.fieldId || definition.id;
            var object = findTextByFieldId(fieldId);
            return '<section class="editor-content-item" data-content-text-item="' + escapeHtml(fieldId) + '">'
                + '<div class="editor-content-item__head"><strong>' + escapeHtml(definition.label || '文字字段') + '</strong>'
                + '<span class="editor-content-status' + (object ? ' is-ready' : '') + '">' + (object ? '可编辑' : '已删除') + '</span></div>'
                + (object ? '<textarea data-content-text="' + escapeHtml(fieldId) + '" aria-label="编辑' + escapeHtml(definition.label || '文字字段') + '">' + escapeHtml(object.text || '') + '</textarea>'
                    : '<div class="editor-content-item__actions"><button type="button" data-content-action="restore-text">恢复文字字段</button></div>')
                + '</section>';
        }).join('');
        els.contentList.innerHTML = frameRows + textRows;
    }

    function layerTextLabel(object, index) {
        var fallback = '文字 ' + (index + 1);
        return object.fieldLabel || (object.text ? object.text.replace(/\s+/g, ' ').slice(0, 18) : fallback);
    }

    function renderLayerPanel() {
        if (!state.canvas || !els.layerList) return;
        var activeObjects = selectedTextObjects();
        var activeVisual = state.canvas.getActiveObject();
        var images = state.canvas.getObjects().filter(function (object) {
            return object.labType === 'freeImage' || object.labType === 'imageComposition' || object.labType === 'imagePlaceholder';
        }).reverse();
        var imageRows = images.map(function (object, index) {
            var selected = activeVisual === object;
            var typeLabel = object.labType === 'imageComposition' ? '图片组合' : (object.labType === 'imagePlaceholder' ? '图片占位' : '自由图片');
            var roleLabel = object.templateRole === 'fixed' ? '固定素材' : '可替换';
            var taskLabel = String(object.cutoutTaskStatus || '').toLowerCase() === 'processing' ? ' · 抠图中' : '';
            return '<section class="editor-layer-item' + (!object.visible ? ' is-hidden' : '') + (selected ? ' is-selected' : '')
                + '" data-layer-image="' + escapeHtml(object.elementId) + '"><div class="editor-layer-item__head"><strong>'
                + escapeHtml(object.originalAssetName || object.assetName || ('图片 ' + (index + 1))) + '</strong><span class="editor-content-status is-ready">'
                + typeLabel + '</span></div><div class="editor-layer-item__meta">' + roleLabel + taskLabel
                + (object.lockedByUser ? ' · 已锁定' : '') + (object.visible === false ? ' · 已隐藏' : '') + '</div>'
                + '<div class="editor-layer-actions"><button type="button" data-layer-action="select-image">选择</button>'
                + '<button type="button" data-layer-action="visibility">' + (object.visible ? '隐藏' : '显示') + '</button>'
                + '<button type="button" data-layer-action="lock">' + (object.lockedByUser ? '解锁' : '锁定') + '</button>'
                + '<button type="button" data-layer-action="up" aria-label="上移一层">上移</button>'
                + '<button type="button" data-layer-action="down" aria-label="下移一层">下移</button></div></section>';
        }).join('');
        var texts = state.canvas.getObjects().filter(function (object) { return object.labType === 'text'; }).reverse();
        var textRows = texts.map(function (object, index) {
            var selected = activeObjects.includes(object);
            return '<section class="editor-layer-item' + (!object.visible ? ' is-hidden' : '') + (selected ? ' is-selected' : '')
                + '" data-layer-field="' + escapeHtml(object.fieldId) + '"><div class="editor-layer-item__head"><strong>'
                + escapeHtml(layerTextLabel(object, index)) + '</strong><span class="editor-content-status">文字</span></div>'
                + '<div class="editor-layer-item__meta">' + (object.lockedByUser ? '已锁定' : '可编辑') + (object.visible ? '' : ' · 已隐藏') + '</div>'
                + '<div class="editor-layer-actions"><button type="button" data-layer-action="select">选择</button>'
                + '<button type="button" data-layer-action="visibility">' + (object.visible ? '隐藏' : '显示') + '</button>'
                + '<button type="button" data-layer-action="lock">' + (object.lockedByUser ? '解锁' : '锁定') + '</button>'
                + '<button type="button" data-layer-action="up" aria-label="上移一层">上移</button>'
                + '<button type="button" data-layer-action="down" aria-label="下移一层">下移</button></div></section>';
        }).join('');
        var frameRows = (state.template.frames || []).map(function (frame) {
            return '<section class="editor-layer-item" data-layer-frame="' + escapeHtml(frame.id) + '"><div class="editor-layer-item__head"><strong>'
                + escapeHtml(frame.label || frameLabel(frame.id)) + '</strong><span class="editor-content-status' + (getFrameImage(frame.id) ? ' is-ready' : '') + '">相框</span></div>'
                + '<div class="editor-layer-actions"><button type="button" data-layer-action="select-frame">定位</button></div></section>';
        }).join('');
        els.layerList.innerHTML = imageRows + textRows + frameRows;
    }

    function selectFrame(frameId) {
        var object = getFrameImage(frameId) || getFramePlaceholder(frameId);
        if (!object) return;
        state.canvas.setActiveObject(object);
        state.canvas.requestRenderAll();
        renderProperties();
    }

    function alignSelectedTexts(mode) {
        var objects = selectedTextObjects();
        if (objects.length < 2) return;
        var measurements = objects.map(function (object) {
            return { object: object, rect: object.getBoundingRect(true, true) };
        });
        var left = Math.min.apply(null, measurements.map(function (item) { return item.rect.left; }));
        var top = Math.min.apply(null, measurements.map(function (item) { return item.rect.top; }));
        var right = Math.max.apply(null, measurements.map(function (item) { return item.rect.left + item.rect.width; }));
        var bottom = Math.max.apply(null, measurements.map(function (item) { return item.rect.top + item.rect.height; }));
        if (mode.indexOf('distribute-') === 0 && objects.length > 2) {
            var horizontal = mode === 'distribute-horizontal';
            var sorted = measurements.slice().sort(function (a, b) { return horizontal ? a.rect.left - b.rect.left : a.rect.top - b.rect.top; });
            var start = horizontal ? sorted[0].rect.left : sorted[0].rect.top;
            var end = horizontal
                ? sorted[sorted.length - 1].rect.left + sorted[sorted.length - 1].rect.width
                : sorted[sorted.length - 1].rect.top + sorted[sorted.length - 1].rect.height;
            var totalSize = sorted.reduce(function (sum, item) { return sum + (horizontal ? item.rect.width : item.rect.height); }, 0);
            var gap = (end - start - totalSize) / (sorted.length - 1);
            var cursor = start;
            sorted.forEach(function (item) {
                var current = horizontal ? item.rect.left : item.rect.top;
                if (horizontal) item.object.left += cursor - current;
                else item.object.top += cursor - current;
                cursor += (horizontal ? item.rect.width : item.rect.height) + gap;
                item.object.setCoords();
            });
        } else {
            measurements.forEach(function (item) {
                var dx = 0;
                var dy = 0;
                if (mode === 'left') dx = left - item.rect.left;
                if (mode === 'center') dx = (left + right) / 2 - (item.rect.left + item.rect.width / 2);
                if (mode === 'right') dx = right - item.rect.left - item.rect.width;
                if (mode === 'top') dy = top - item.rect.top;
                if (mode === 'middle') dy = (top + bottom) / 2 - (item.rect.top + item.rect.height / 2);
                if (mode === 'bottom') dy = bottom - item.rect.top - item.rect.height;
                item.object.left += dx;
                item.object.top += dy;
                item.object.setCoords();
            });
        }
        var active = state.canvas.getActiveObject();
        if (active && typeof active.setCoords === 'function') active.setCoords();
        state.canvas.requestRenderAll();
        queueMutation(true);
        renderProperties();
    }

    function buildTemplateDefinition() {
        state.canvas.discardActiveObject();
        state.canvas.requestRenderAll();
        var texts = state.canvas.getObjects().filter(function (object) { return object.labType === 'text' && object.visible !== false; }).map(function (object, index) {
            return {
                id: object.fieldId || ('text-' + (index + 1)),
                fieldId: object.fieldId || ('text-' + (index + 1)),
                label: object.fieldLabel || ('文字 ' + (index + 1)),
                editable: true,
                text: object.text || '',
                x: Math.round(object.left || 0),
                y: Math.round(object.top || 0),
                width: Math.round(object.width || 320),
                fontSize: Math.round(object.fontSize || 32),
                fontWeight: String(object.fontWeight || '400'),
                fontStyle: object.fontStyle || 'normal',
                fontFamily: object.fontFamily || 'Arial',
                fill: normalizeColor(object.fill),
                textAlign: object.textAlign || 'left',
                lineHeight: object.lineHeight || 1.16,
                charSpacing: object.charSpacing || 0,
                angle: object.angle || 0,
                opacity: object.opacity == null ? 1 : object.opacity,
                scaleX: object.scaleX || 1,
                scaleY: object.scaleY || 1
            };
        });
        var images = state.canvas.getObjects().filter(function (object) {
            return object.visible !== false
                && (object.labType === 'freeImage' || object.labType === 'imageComposition' || object.labType === 'imagePlaceholder');
        }).map(function (object, index) {
            var role = object.templateRole || 'replaceable';
            var meta = visualMetadata(object);
            return {
                elementId: object.elementId || ('image-' + (index + 1)),
                name: object.originalAssetName || object.assetName || ('图片 ' + (index + 1)),
                role: role,
                url: role === 'fixed' ? activeVisualUrl(object) : '',
                originalAssetUrl: role === 'fixed' ? (object.originalAssetUrl || object.assetUrl || '') : '',
                cutoutUrl: role === 'fixed' ? (object.cutoutUrl || '') : '',
                activeImageVersion: role === 'fixed' ? (object.activeImageVersion || 'original') : 'original',
                x: Math.round(object.left || state.logicalWidth / 2),
                y: Math.round(object.top || state.logicalHeight / 2),
                baseWidth: Math.round(object.baseWidth || object.width || 360),
                baseHeight: Math.round(object.baseHeight || object.height || 360),
                scaleX: object.scaleX || 1,
                scaleY: object.scaleY || 1,
                angle: object.angle || 0,
                opacity: object.opacity == null ? 1 : object.opacity,
                backgroundAssetUrl: object.backgroundAssetUrl || '',
                backgroundAssetName: object.backgroundAssetName || '',
                backgroundOpacity: object.backgroundOpacity == null ? 1 : object.backgroundOpacity,
                backgroundZoom: object.backgroundZoom || 1,
                backgroundCropX: object.backgroundCropX == null ? null : object.backgroundCropX,
                backgroundCropY: object.backgroundCropY == null ? null : object.backgroundCropY,
                sourceImageWidth: meta.sourceImageWidth,
                sourceImageHeight: meta.sourceImageHeight,
                subjectCropX: meta.subjectCropX,
                subjectCropY: meta.subjectCropY,
                subjectCropWidth: meta.subjectCropWidth,
                subjectCropHeight: meta.subjectCropHeight
            };
        });
        return {
            schemaVersion: 1,
            usageType: state.template.usageType,
            ratioGroup: state.template.ratioGroup,
            width: state.logicalWidth,
            height: state.logicalHeight,
            background: state.canvas.backgroundColor || state.template.background || '#f5f7fa',
            accent: state.template.accent || '#172033',
            tags: Array.from(new Set([state.template.usageType, state.template.ratioGroup, '个人模板'])),
            frames: JSON.parse(JSON.stringify(state.template.frames || [])),
            texts: texts,
            images: images
        };
    }

    function openSaveTemplateDialog() {
        els.saveTemplateName.value = (els.projectName.value.trim() || state.project.projectName) + ' 模板';
        els.saveTemplateCategory.value = state.template.category || '个人模板';
        els.saveTemplateDescription.value = '复用当前相框、自由图片和文字布局。';
        els.saveTemplateSpec.textContent = state.template.usageType + ' · ' + state.template.ratioGroup + ' · '
            + state.logicalWidth + ' × ' + state.logicalHeight + ' · ' + (state.template.frames || []).length + ' 个相框 · '
            + state.canvas.getObjects().filter(function (object) { return object.labType === 'freeImage' || object.labType === 'imageComposition'; }).length + ' 个自由图片';
        els.saveTemplateDialog.showModal();
        setTimeout(function () { els.saveTemplateName.focus(); els.saveTemplateName.select(); }, 0);
    }

    async function savePersonalTemplate() {
        var name = els.saveTemplateName.value.trim();
        if (!name) return showToast('请输入模板名称', true);
        els.confirmSaveTemplate.disabled = true;
        els.confirmSaveTemplate.textContent = '正在保存...';
        try {
            await saveNow();
            var definition = buildTemplateDefinition();
            apiData(await axios.post('/api/template-lab/projects/' + state.project.id + '/templates', {
                name: name,
                category: els.saveTemplateCategory.value.trim() || '个人模板',
                description: els.saveTemplateDescription.value.trim(),
                definitionJson: JSON.stringify(definition),
                thumbnailDataUrl: createThumbnail()
            }));
            els.saveTemplateDialog.close();
            showToast('个人模板已保存，可在模板库的“个人”中使用');
        } catch (error) {
            showToast(error.response && error.response.data ? error.response.data.message : error.message, true);
        } finally {
            els.confirmSaveTemplate.disabled = false;
            els.confirmSaveTemplate.textContent = '保存模板';
        }
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }

    async function useAsset(asset, point, targetFrameId) {
        if (!asset) return;
        if (state.assetPickMode === 'background') {
            await applyBackgroundAsset(asset);
            return;
        }
        if (targetFrameId) {
            assignImageToFrame(targetFrameId, asset);
            return;
        }
        if (selectedImagePlaceholder()) {
            fillImagePlaceholder(asset);
            return;
        }
        await addFreeImage(asset, point || { x: state.logicalWidth / 2, y: state.logicalHeight / 2 });
    }

    async function uploadFiles(files, targetFrameId, placementPoint, purpose) {
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
            if (uploaded[0] && purpose === 'background') {
                await applyBackgroundAsset(uploaded[0]);
            } else if (targetFrameId && uploaded[0]) {
                assignImageToFrame(targetFrameId, uploaded[0]);
            } else if (placementPoint) {
                for (var placed = 0; placed < uploaded.length; placed += 1) {
                    await addFreeImage(uploaded[placed], {
                        x: placementPoint.x + placed * 24,
                        y: placementPoint.y + placed * 24
                    });
                }
            }
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
            state.pendingPlacement = null;
        }
    }

    function serializedDesign() {
        return JSON.stringify({
            schemaVersion: 3,
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
                if (object.labType === 'framePlaceholder' || object.labType === 'imagePlaceholder'
                        || object.labType === 'frameBorder' || object.labType === 'guide') {
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
        var scale = Math.max(.01, Math.min(5, state.fitScale * state.manualZoom));
        state.canvas.setDimensions({ width: Math.round(state.logicalWidth * scale), height: Math.round(state.logicalHeight * scale) });
        state.canvas.setZoom(scale);
        state.canvas.calcOffset();
        state.canvas.renderAll();
    }

    function setManualZoom(value) {
        state.manualZoom = Math.max(.1, Math.min(10, Math.round(value * 10000) / 10000));
        applyViewportScale();
    }

    function readZoomFactor(showFeedback) {
        var raw = Number(els.zoomValue.value);
        if (!Number.isFinite(raw)) {
            raw = 2;
        }
        var rounded = Math.round(raw);
        var clamped = Math.max(2, Math.min(10, rounded));
        els.zoomValue.value = String(clamped);
        els.zoomOut.setAttribute('aria-label', '缩小 ' + clamped + ' 倍');
        els.zoomIn.setAttribute('aria-label', '放大 ' + clamped + ' 倍');
        els.zoomOut.title = '缩小 ' + clamped + ' 倍';
        els.zoomIn.title = '放大 ' + clamped + ' 倍';
        if (showFeedback && clamped !== raw) showToast('缩放倍数已调整为 ' + clamped + ' 倍');
        return clamped;
    }

    function loadHtmlImage(url) {
        return new Promise(function (resolve, reject) {
            var image = new Image();
            image.crossOrigin = 'anonymous';
            image.onload = function () { resolve(image); };
            image.onerror = function () { reject(new Error('图片读取失败')); };
            image.src = url;
        });
    }

    function canvasBlob(canvas, type, quality) {
        return new Promise(function (resolve, reject) {
            canvas.toBlob(function (blob) {
                if (blob) resolve(blob);
                else reject(new Error('浏览器无法生成临时图片'));
            }, type, quality);
        });
    }

    async function prepareCutoutInput(url) {
        var image = await loadHtmlImage(url);
        var width = image.naturalWidth;
        var height = image.naturalHeight;
        var maxDimensionScale = Math.min(1, 4096 / Math.max(width, height));
        var megapixelScale = Math.min(1, Math.sqrt(16000000 / Math.max(1, width * height)));
        var scale = Math.min(maxDimensionScale, megapixelScale);
        if (Math.min(width, height) * scale < 256) {
            scale = Math.max(scale, 256 / Math.min(width, height));
        }
        var targetWidth = Math.round(width * scale);
        var targetHeight = Math.round(height * scale);
        if (Math.max(targetWidth, targetHeight) > 4096 || targetWidth * targetHeight > 16000000) {
            throw new Error('图片比例过于狭长，无法满足 KIE 的 256–4096px 输入限制');
        }
        var work = document.createElement('canvas');
        var context = work.getContext('2d', { alpha: false });
        var blob;
        var quality = .92;
        for (var resizeAttempt = 0; resizeAttempt < 6; resizeAttempt += 1) {
            work.width = targetWidth;
            work.height = targetHeight;
            context = work.getContext('2d', { alpha: false });
            context.fillStyle = '#ffffff';
            context.fillRect(0, 0, targetWidth, targetHeight);
            context.drawImage(image, 0, 0, targetWidth, targetHeight);
            for (quality = .92; quality >= .62; quality -= .1) {
                blob = await canvasBlob(work, 'image/jpeg', quality);
                if (blob.size <= 5 * 1024 * 1024) return blob;
            }
            targetWidth = Math.max(256, Math.round(targetWidth * .86));
            targetHeight = Math.max(256, Math.round(targetHeight * .86));
        }
        throw new Error('临时图片仍超过 5MB，请先压缩原图后再抠图');
    }

    async function composeOriginalWithMask(originalUrl, maskUrl) {
        var images = await Promise.all([loadHtmlImage(originalUrl), loadHtmlImage(maskUrl)]);
        var original = images[0];
        var mask = images[1];
        var width = original.naturalWidth;
        var height = original.naturalHeight;
        var output = document.createElement('canvas');
        output.width = width;
        output.height = height;
        var outputContext = output.getContext('2d', { willReadFrequently: true });
        outputContext.drawImage(original, 0, 0, width, height);
        var maskCanvas = document.createElement('canvas');
        maskCanvas.width = width;
        maskCanvas.height = height;
        var maskContext = maskCanvas.getContext('2d', { willReadFrequently: true });
        maskContext.clearRect(0, 0, width, height);
        maskContext.drawImage(mask, 0, 0, width, height);
        var sourcePixels = outputContext.getImageData(0, 0, width, height);
        var maskPixels = maskContext.getImageData(0, 0, width, height);
        for (var i = 3; i < sourcePixels.data.length; i += 4) {
            sourcePixels.data[i] = Math.round(sourcePixels.data[i] * maskPixels.data[i] / 255);
        }
        outputContext.putImageData(sourcePixels, 0, 0);
        return canvasBlob(output, 'image/png', 1);
    }

    async function uploadGeneratedCutout(blob, name) {
        if (!blob || blob.size > 25 * 1024 * 1024) return null;
        var safeName = String(name || '图片').replace(/\.[^.]+$/, '').replace(/[\\/:*?"<>|]/g, '_');
        var form = new FormData();
        form.append('file', new File([blob], safeName + '_cutout.png', { type: 'image/png' }));
        var asset = apiData(await axios.post('/api/template-lab/projects/' + state.project.id + '/assets', form, {
            headers: { 'Content-Type': 'multipart/form-data' }
        }));
        state.assets.push(asset);
        renderAssets();
        return asset;
    }

    async function fetchCutoutQuote() {
        if (state.cutoutQuote) return state.cutoutQuote;
        try {
            state.cutoutQuote = apiData(await axios.get('/api/template-lab/projects/' + state.project.id + '/cutout-quote'));
            if (selectedVisual()) renderImageProperties(selectedVisual());
        } catch (error) {
            state.cutoutQuote = { available: false, amount_cny: null, message: '价格暂不可用，实际以服务商账单为准' };
        }
        return state.cutoutQuote;
    }

    async function openCutoutConfirmation() {
        var visual = selectedVisual();
        if (!visual) return;
        if (String(visual.cutoutTaskStatus || '').toLowerCase() === 'processing') return;
        state.cutoutTargetId = visual.elementId;
        var quote = await fetchCutoutQuote();
        els.cutoutConfirmCost.textContent = quote && quote.amount_cny != null ? formatCny(quote.amount_cny) : '以实际账单为准';
        els.cutoutConfirmDialog.showModal();
        setTimeout(function () { els.confirmCutout.focus(); }, 0);
    }

    async function submitCutout() {
        var visual = findVisualByElementId(state.cutoutTargetId);
        if (!visual || (visual.labType !== 'freeImage' && visual.labType !== 'imageComposition')) {
            return showToast('目标图片已不存在，请重新选择', true);
        }
        var originalUrl = visual.originalAssetUrl || visual.assetUrl;
        if (!originalUrl) return showToast('原图地址不可用', true);
        els.confirmCutout.disabled = true;
        els.confirmCutout.textContent = '正在准备图片...';
        visual.set({
            cutoutTaskStatus: 'processing',
            cutoutTaskError: '',
            cutoutSourceUrl: originalUrl,
            cutoutEstimatedCost: state.cutoutQuote && state.cutoutQuote.amount_cny
        });
        els.cutoutConfirmDialog.close();
        renderProperties();
        queueMutation(true);
        try {
            var inputBlob = await prepareCutoutInput(originalUrl);
            var form = new FormData();
            form.append('elementId', visual.elementId);
            form.append('sourceUrl', originalUrl);
            form.append('file', new File([inputBlob], 'cutout-input.jpg', { type: 'image/jpeg' }));
            var created = apiData(await axios.post('/api/template-lab/projects/' + state.project.id + '/cutouts', form, {
                headers: { 'Content-Type': 'multipart/form-data' }
            }));
            visual.set({
                cutoutTaskId: created.task_id,
                cutoutTaskStatus: 'processing',
                cutoutEstimatedCost: created.estimated_cost == null ? visual.cutoutEstimatedCost : created.estimated_cost
            });
            queueMutation(true);
            renderProperties();
            pollCutoutTask(created.task_id, visual.elementId, 1400);
            showToast('KIE 抠图任务已提交，可继续编辑画布');
        } catch (error) {
            visual.set({
                cutoutTaskStatus: 'failed',
                cutoutTaskError: error.response && error.response.data ? error.response.data.message : error.message
            });
            queueMutation(true);
            renderProperties();
            showToast(visual.cutoutTaskError || '抠图任务提交失败', true);
        } finally {
            els.confirmCutout.disabled = false;
            els.confirmCutout.textContent = '确认并开始';
        }
    }

    function pollCutoutTask(taskId, elementId, delay) {
        if (!taskId || state.cutoutPollers[taskId]) return;
        state.cutoutPollers[taskId] = setTimeout(async function check() {
            delete state.cutoutPollers[taskId];
            try {
                var result = apiData(await axios.get('/api/template-lab/projects/' + state.project.id + '/cutouts/' + encodeURIComponent(taskId)));
                if (result.status === 'success' && result.result_url) {
                    await applyCutoutResult(elementId, result);
                    return;
                }
                if (result.status === 'failed') {
                    var failedVisual = findVisualByElementId(elementId);
                    if (failedVisual) {
                        failedVisual.set({ cutoutTaskStatus: 'failed', cutoutTaskError: result.error || 'KIE 抠图失败' });
                        queueMutation(true);
                        if (selectedVisual() === failedVisual) renderProperties();
                    }
                    showToast(result.error || 'KIE 抠图失败', true);
                    return;
                }
                pollCutoutTask(taskId, elementId, 3000);
            } catch (error) {
                var statusCode = error.response && Number(error.response.status);
                if (statusCode >= 400 && statusCode < 500 && statusCode !== 408 && statusCode !== 429) {
                    var unavailableVisual = findVisualByElementId(elementId);
                    if (unavailableVisual) {
                        unavailableVisual.set({
                            cutoutTaskStatus: 'failed',
                            cutoutTaskError: error.response && error.response.data && error.response.data.message
                                ? error.response.data.message
                                : '抠图任务无法继续查询'
                        });
                        queueMutation(true);
                        if (selectedVisual() === unavailableVisual) renderProperties();
                    }
                    showToast('抠图任务无法继续查询，请重新提交', true);
                    return;
                }
                pollCutoutTask(taskId, elementId, 5000);
            }
        }, delay || 3000);
    }

    async function applyCutoutResult(elementId, result) {
        var visual = findVisualByElementId(elementId);
        var originalUrl = visual && (visual.cutoutSourceUrl || visual.originalAssetUrl || visual.assetUrl);
        var asset = null;
        if (visual && originalUrl && originalUrl === (visual.originalAssetUrl || visual.assetUrl)) {
            try {
                var compositeBlob = await composeOriginalWithMask(originalUrl, result.result_url);
                asset = await uploadGeneratedCutout(compositeBlob, visual.originalAssetName || visual.assetName);
            } catch (error) {
                showToast('已完成抠图，但原图像素合成失败，使用 KIE 高清结果', true);
            }
        }
        if (!asset) {
            asset = {
                url: result.result_url,
                name: ((visual && (visual.originalAssetName || visual.assetName)) || '图片') + '_KIE抠图.png',
                generated: true
            };
            state.assets.push(asset);
            renderAssets();
        }
        if (!visual) {
            scheduleSave();
            showToast('原图片已删除，抠图结果已保存到项目素材库');
            return;
        }
        if (visual.cutoutSourceUrl && visual.originalAssetUrl !== visual.cutoutSourceUrl) {
            visual.set({ cutoutTaskStatus: 'stale', cutoutTaskError: '原图已更换，结果仅保存到素材库' });
            queueMutation(true);
            showToast('原图已更换，旧抠图结果未覆盖当前图片');
            return;
        }
        visual.set({
            cutoutUrl: asset.url,
            cutoutTaskStatus: 'success',
            cutoutTaskError: '',
            cutoutActualCost: result.actual_cost,
            activeImageVersion: 'cutout'
        });
        setVisualVersion(visual, 'cutout');
        queueMutation(true);
        if (selectedVisual() === visual) renderProperties();
        showToast('KIE 抠图已完成');
    }

    function resumeCutoutTasks() {
        if (!state.canvas) return;
        state.canvas.getObjects().forEach(function (object) {
            if ((object.labType === 'freeImage' || object.labType === 'imageComposition')
                    && object.cutoutTaskId
                    && String(object.cutoutTaskStatus || '').toLowerCase() === 'processing') {
                pollCutoutTask(object.cutoutTaskId, object.elementId, 400);
            }
        });
    }

    async function exportImage() {
        var emptyFrames = (state.template.frames || []).filter(function (frame) { return !getFrameImage(frame.id); });
        var emptyImageSlots = state.canvas.getObjects().filter(function (object) { return object.labType === 'imagePlaceholder'; });
        if (emptyFrames.length || emptyImageSlots.length) {
            showToast('还有 ' + (emptyFrames.length + emptyImageSlots.length) + ' 个图片位置未填入，完成后再导出。', true);
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
        state.canvas.on('mouse:down', function (event) {
            beginBackgroundDrag(event);
        });
        state.canvas.on('mouse:move', function (event) {
            moveBackgroundDrag(event);
        });
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
        state.canvas.on('mouse:up', function () {
            endBackgroundDrag();
            removeGuides();
        });
        state.canvas.on('mouse:dblclick', function (event) {
            if (event.target && event.target.labType === 'frameImage') {
                state.canvas.setActiveObject(event.target);
                els.stageTip.hidden = false;
                clearTimeout(setupCanvasEvents.tipTimer);
                setupCanvasEvents.tipTimer = setTimeout(function () { els.stageTip.hidden = true; }, 2600);
            } else if (event.target && (event.target.labType === 'freeImage' || event.target.labType === 'imageComposition')) {
                state.canvas.setActiveObject(event.target);
                renderProperties();
                els.propsImage.scrollTop = 0;
                showToast('图片编辑面板已打开');
            } else if (event.target && event.target.labType === 'imagePlaceholder') {
                state.canvas.setActiveObject(event.target);
                document.querySelector('[data-panel-tab="assets"]').click();
                showToast('从左侧选择图片填入这个位置');
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
            if (state.project.templateDefinitionJson) {
                try { state.template = JSON.parse(state.project.templateDefinitionJson); } catch (ignore) { state.template = null; }
            }
            if (!state.template) state.template = templates.find(function (item) { return item.id === state.project.templateId; });
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
        renderContentPanel();
        renderLayerPanel();
        applyViewportScale();
        pushHistory();
        renderProperties();
        els.loading.hidden = true;
        els.viewport.hidden = false;
        setSaveState('已自动保存', 'saved');
        setTimeout(applyViewportScale, 0);
        fetchCutoutQuote();
        resumeCutoutTasks();
    }

    function setupDomEvents() {
        document.querySelectorAll('[data-panel-tab]').forEach(function (button) {
            button.addEventListener('click', function () {
                document.querySelectorAll('[data-panel-tab]').forEach(function (item) { item.setAttribute('aria-selected', String(item === button)); });
                document.querySelectorAll('[data-panel-content]').forEach(function (panel) { panel.hidden = panel.dataset.panelContent !== button.dataset.panelTab; });
            });
        });
        els.contentList.addEventListener('input', function (event) {
            var textarea = event.target.closest('[data-content-text]');
            if (!textarea) return;
            var object = findTextByFieldId(textarea.dataset.contentText);
            if (!object) return;
            object.set('text', textarea.value);
            object.setCoords();
            state.canvas.requestRenderAll();
            queueMutation(false, true);
        });
        els.contentList.addEventListener('click', function (event) {
            var button = event.target.closest('[data-content-action]');
            if (!button) return;
            var frameRow = button.closest('[data-content-frame]');
            var textRow = button.closest('[data-content-text-item]');
            var action = button.dataset.contentAction;
            if (frameRow) {
                var frameId = frameRow.dataset.contentFrame;
                if (action === 'upload-frame') {
                    state.assetPickMode = '';
                    state.pendingFrameId = frameId;
                    state.pendingPlacement = null;
                    els.assetInput.click();
                }
                if (action === 'select-frame') selectFrame(frameId);
                if (action === 'remove-frame') {
                    var image = getFrameImage(frameId);
                    if (image) { state.canvas.setActiveObject(image); removeFrameImage(); }
                }
            }
            if (textRow && action === 'restore-text') {
                var fieldId = textRow.dataset.contentTextItem;
                var definition = (state.template.texts || []).find(function (item) { return String(item.fieldId || item.id) === String(fieldId); });
                if (definition) {
                    addTemplateText(definition);
                    var restored = findTextByFieldId(fieldId);
                    if (restored) { restored.bringToFront(); state.canvas.setActiveObject(restored); }
                    state.canvas.requestRenderAll();
                    queueMutation(true);
                    renderProperties();
                }
            }
        });
        els.layerList.addEventListener('click', function (event) {
            var button = event.target.closest('[data-layer-action]');
            if (!button) return;
            var textRow = button.closest('[data-layer-field]');
            var frameRow = button.closest('[data-layer-frame]');
            var imageRow = button.closest('[data-layer-image]');
            var action = button.dataset.layerAction;
            if (frameRow && action === 'select-frame') return selectFrame(frameRow.dataset.layerFrame);
            var object = imageRow
                ? findVisualByElementId(imageRow.dataset.layerImage)
                : (textRow ? findTextByFieldId(textRow.dataset.layerField) : null);
            if (!object) return;
            if (action === 'select' || action === 'select-image') {
                if (object.visible === false) object.visible = true;
                state.canvas.setActiveObject(object);
            }
            if (action === 'visibility') {
                object.visible = object.visible === false;
                if (!object.visible) state.canvas.discardActiveObject();
            }
            if (action === 'lock') {
                object.lockedByUser = !object.lockedByUser;
                object.set({
                    selectable: !object.lockedByUser,
                    evented: !object.lockedByUser,
                    editable: object.labType === 'text' && !object.lockedByUser
                });
                if (object.lockedByUser) state.canvas.discardActiveObject();
            }
            if (action === 'up') object.bringForward();
            if (action === 'down') object.sendBackwards();
            object.setCoords();
            state.canvas.requestRenderAll();
            queueMutation(true);
            renderProperties();
        });
        document.querySelectorAll('[data-align-mode]').forEach(function (button) {
            button.addEventListener('click', function () { alignSelectedTexts(button.dataset.alignMode); });
        });
        els.saveAsTemplate.addEventListener('click', function () {
            if (!state.canvas || !state.project) return showToast('项目尚未加载完成', true);
            openSaveTemplateDialog();
        });
        els.confirmSaveTemplate.addEventListener('click', savePersonalTemplate);
        document.querySelectorAll('[data-add-text]').forEach(function (button) {
            button.addEventListener('click', function () { addText(button.dataset.addText); });
        });
        els.uploadAssets.addEventListener('click', function () {
            state.pendingFrameId = null;
            state.pendingPlacement = null;
            els.assetInput.click();
        });
        els.replaceFrameImage.addEventListener('click', function () {
            state.assetPickMode = '';
            state.pendingFrameId = selectedFrameId();
            state.pendingPlacement = null;
            els.assetInput.click();
        });
        els.assetInput.addEventListener('change', function () {
            uploadFiles(els.assetInput.files, state.pendingFrameId, state.pendingPlacement,
                state.assetPickMode === 'background' ? 'background' : '');
        });
        ['dragenter', 'dragover'].forEach(function (name) {
            els.uploadAssets.addEventListener(name, function (event) { event.preventDefault(); els.uploadAssets.classList.add('is-dragover'); });
        });
        ['dragleave', 'drop'].forEach(function (name) {
            els.uploadAssets.addEventListener(name, function (event) { event.preventDefault(); els.uploadAssets.classList.remove('is-dragover'); });
        });
        els.uploadAssets.addEventListener('drop', function (event) {
            uploadFiles(event.dataTransfer.files, null, null, state.assetPickMode === 'background' ? 'background' : '');
        });
        els.assetGrid.addEventListener('click', async function (event) {
            var card = event.target.closest('[data-asset-index]');
            if (!card) return;
            await useAsset(state.assets[Number(card.dataset.assetIndex)], null, selectedFrameId());
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
            var point = canvasPointAtClientPoint(event);
            var index = event.dataTransfer.getData('application/x-template-lab-asset');
            if (index !== '') {
                await useAsset(state.assets[Number(index)], point, frame && frame.id);
            } else if (event.dataTransfer.files && event.dataTransfer.files.length) {
                await uploadFiles(event.dataTransfer.files, frame && frame.id, frame ? null : point,
                    state.assetPickMode === 'background' ? 'background' : '');
            }
        });

        els.projectName.addEventListener('input', scheduleSave);
        els.projectName.addEventListener('blur', function () {
            if (!els.projectName.value.trim()) els.projectName.value = state.project.projectName;
            saveNow();
        });
        els.undo.addEventListener('click', undo);
        els.redo.addEventListener('click', redo);
        els.zoomOut.addEventListener('click', function () { setManualZoom(state.manualZoom / readZoomFactor(true)); });
        els.zoomIn.addEventListener('click', function () { setManualZoom(state.manualZoom * readZoomFactor(true)); });
        els.zoomValue.addEventListener('change', function () { readZoomFactor(true); });
        els.zoomValue.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                readZoomFactor(true);
                els.zoomValue.select();
            }
        });
        readZoomFactor(false);
        els.exportButton.addEventListener('click', exportImage);
        els.imageZoom.addEventListener('input', function () {
            els.imageZoomValue.value = els.imageZoom.value + '%';
            els.imageZoomValue.textContent = els.imageZoom.value + '%';
            setImageZoom(els.imageZoom.value, false);
        });
        els.imageZoom.addEventListener('change', function () { setImageZoom(els.imageZoom.value, true); });
        els.resetCrop.addEventListener('click', resetCrop);
        els.removeFrameImage.addEventListener('click', removeFrameImage);
        els.frameToFreeImage.addEventListener('click', convertSelectedFrameToFreeImage);

        els.showOriginalImage.addEventListener('click', function () { switchVisualVersion('original'); });
        els.showCutoutImage.addEventListener('click', function () { switchVisualVersion('cutout'); });
        els.freeImageOpacity.addEventListener('input', function () {
            var value = Math.max(0, Math.min(100, Number(els.freeImageOpacity.value) || 0));
            els.freeImageOpacityValue.value = value + '%';
            els.freeImageOpacityValue.textContent = value + '%';
            updateVisualProperty('opacity', value / 100, false);
        });
        els.freeImageOpacity.addEventListener('change', function () {
            updateVisualProperty('opacity', Number(els.freeImageOpacity.value) / 100, true);
        });
        els.flipImageHorizontal.addEventListener('click', function () {
            var visual = selectedVisual();
            if (visual) updateVisualProperty('flipX', !visual.flipX, true);
        });
        els.flipImageVertical.addEventListener('click', function () {
            var visual = selectedVisual();
            if (visual) updateVisualProperty('flipY', !visual.flipY, true);
        });
        els.rotateImage.addEventListener('click', function () {
            var visual = selectedVisual();
            if (visual) updateVisualProperty('angle', ((Number(visual.angle) || 0) + 90) % 360, true);
        });
        els.resetFreeImage.addEventListener('click', function () {
            var visual = selectedVisual();
            if (!visual) return;
            stopBackgroundEditing();
            var naturalWidth = Math.max(Number(visual.width) || Number(visual.baseWidth) || 1, 1);
            var naturalHeight = Math.max(Number(visual.height) || Number(visual.baseHeight) || 1, 1);
            var fit = Math.min(state.logicalWidth * .42 / naturalWidth, state.logicalHeight * .42 / naturalHeight, 1);
            visual.set({ scaleX: fit, scaleY: fit, angle: 0, flipX: false, flipY: false, opacity: 1 });
            visual.setCoords();
            state.canvas.requestRenderAll();
            queueMutation(true);
            renderProperties();
        });
        [
            [els.freeImageCropWidth, els.freeImageCropWidthValue],
            [els.freeImageCropHeight, els.freeImageCropHeightValue],
            [els.freeImageCropX, els.freeImageCropXValue],
            [els.freeImageCropY, els.freeImageCropYValue]
        ].forEach(function (pair) {
            pair[0].addEventListener('input', function () {
                pair[1].value = pair[0].value + '%';
                pair[1].textContent = pair[0].value + '%';
                setFreeImageCrop(false);
            });
            pair[0].addEventListener('change', function () { setFreeImageCrop(true); });
        });
        els.resetFreeImageCrop.addEventListener('click', function () {
            els.freeImageCropWidth.value = 100;
            els.freeImageCropHeight.value = 100;
            els.freeImageCropX.value = 50;
            els.freeImageCropY.value = 50;
            setFreeImageCrop(true);
            renderProperties();
        });
        els.imageTemplateRole.addEventListener('change', function () {
            updateVisualProperty('templateRole', els.imageTemplateRole.value === 'fixed' ? 'fixed' : 'replaceable', true);
            renderProperties();
        });
        els.startCutout.addEventListener('click', openCutoutConfirmation);
        els.confirmCutout.addEventListener('click', submitCutout);

        function chooseBackgroundAsset() {
            if (!selectedVisual()) return showToast('请先选择要添加背景的图片', true);
            state.assetPickMode = 'background';
            var assetsTab = document.querySelector('[data-panel-tab="assets"]');
            if (assetsTab) assetsTab.click();
            showToast('请从左侧选择背景图片，或上传一张新背景');
        }
        els.chooseBackground.addEventListener('click', chooseBackgroundAsset);
        els.changeBackground.addEventListener('click', chooseBackgroundAsset);
        els.backgroundOpacity.addEventListener('input', function () {
            var value = Number(els.backgroundOpacity.value) || 0;
            els.backgroundOpacityValue.value = value + '%';
            els.backgroundOpacityValue.textContent = value + '%';
            setBackgroundOpacity(value, false);
        });
        els.backgroundOpacity.addEventListener('change', function () { setBackgroundOpacity(els.backgroundOpacity.value, true); });
        els.backgroundZoom.addEventListener('input', function () {
            var value = Number(els.backgroundZoom.value) || 100;
            els.backgroundZoomValue.value = value + '%';
            els.backgroundZoomValue.textContent = value + '%';
            setBackgroundZoom(value, false);
        });
        els.backgroundZoom.addEventListener('change', function () { setBackgroundZoom(els.backgroundZoom.value, true); });
        els.editBackgroundPosition.addEventListener('click', startBackgroundEditing);
        els.removeBackground.addEventListener('click', removeCompositionBackground);
        els.splitImageComposition.addEventListener('click', splitImageComposition);
        els.placeImageInFrame.addEventListener('click', function () {
            var visual = selectedVisual();
            var frameId = els.freeImageFrameTarget.value;
            if (!visual || !frameId) return;
            var asset = { url: activeVisualUrl(visual), name: visual.originalAssetName || visual.assetName || '图片' };
            assignImageToFrame(frameId, asset, function () {
                if (state.canvas.getObjects().includes(visual)) state.canvas.remove(visual);
                stopBackgroundEditing();
                state.canvas.requestRenderAll();
                queueMutation(true);
                renderProperties();
            });
        });
        els.duplicateFreeImage.addEventListener('click', duplicateSelectedVisual);
        els.deleteFreeImage.addEventListener('click', deleteSelectedVisual);
        els.imageBringForward.addEventListener('click', function () {
            var visual = selectedVisual();
            if (visual) { visual.bringForward(); state.canvas.requestRenderAll(); queueMutation(true); }
        });
        els.imageSendBackward.addEventListener('click', function () {
            var visual = selectedVisual();
            if (visual) { visual.sendBackwards(); state.canvas.requestRenderAll(); queueMutation(true); }
        });

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
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'd') {
                event.preventDefault();
                if (selectedVisual()) duplicateSelectedVisual();
                else duplicateSelectedText();
                return;
            }
            if (event.key === 'Delete' || event.key === 'Backspace') {
                if (selectedText()) { event.preventDefault(); deleteSelectedText(); }
                else if (selectedFrameImage()) { event.preventDefault(); removeFrameImage(); }
                else if (selectedVisual()) { event.preventDefault(); deleteSelectedVisual(); }
            }
            if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
                var object = selectedText() || selectedVisual();
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
