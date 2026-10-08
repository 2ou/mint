(function () {
    'use strict';

    var CUSTOM_PROPS = [
        'labType', 'frameId', 'frameName', 'frameBounds', 'frameShape', 'coverScale',
        'cropZoom', 'assetUrl', 'assetName', 'isTemplateText', 'fieldId', 'fieldLabel',
        'lockedByUser', 'excludeFromExport', 'elementId', 'originalAssetUrl', 'originalAssetName',
        'cutoutUrl', 'activeImageVersion', 'templateRole', 'cutoutTaskId', 'cutoutTaskStatus',
        'cutoutTaskError', 'cutoutSourceUrl', 'cutoutEstimatedCost', 'cutoutActualCost',
        'backgroundAssetUrl', 'backgroundAssetName', 'backgroundOpacity',
        'backgroundZoom', 'backgroundCropX', 'backgroundCropY', 'subjectElementId', 'baseWidth', 'maskId',
        'shapeId', 'shapeLayer',
        'baseHeight', 'isTemplateImage', 'sourceImageWidth', 'sourceImageHeight',
        'subjectCropX', 'subjectCropY', 'subjectCropWidth', 'subjectCropHeight'
    ];
    var SAVE_DELAY_MS = 1800;
    var PREVIEW_DELAY_MS = 12000;
    var SAVE_RETRY_DELAY_MS = 5000;
    // 导出体积压缩：只在质量维度逼近上限，导出分辨率始终等于用户设定的倍数
    var EXPORT_QUALITY_MIN = 0.3;
    var EXPORT_QUALITY_MAX = 0.94;
    var EXPORT_BINARY_STEPS = 9;

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
        previewTimer: null,
        saveRetryTimer: null,
        historyTimer: null,
        saveQueue: Promise.resolve(),
        dirty: false,
        previewDirty: false,
        pendingFrameId: null,
        guideLines: [],
        fieldSequence: 0,
        assetPickMode: '',
        backgroundTargetId: '',
        backgroundPickPreviousMode: '',
        pendingPlacement: null,
        backgroundEdit: null,
        backgroundMode: 'solid',
        solidColor: '#f5f7fa',
        solidOpacity: 100,
        cutoutQuote: null,
        cutoutTargetId: null,
        cutoutPollers: {},
        frameLayout: { added: [], removed: [] },
        frameTargetSelections: {},
        frameAssignmentTokens: {},
        frameFillBusy: false,
        frameEditMode: false,
        frameEditTarget: null,
        frameEditOverlay: null,
        dragFrame: null,
        clipboard: null,
        clipboardReady: null,
        clipboardCopyToken: 0,
        clipboardPasteCount: 0,
        canvasDragDepth: 0,
        assetSelectMode: false,
        assetSelected: [],
        assetRemoving: false
    };

    var els = {
        projectName: document.getElementById('editor-project-name'),
        saveState: document.getElementById('editor-save-state'),
        loading: document.getElementById('editor-loading'),
        viewport: document.getElementById('canvas-viewport'),
        stage: document.getElementById('editor-stage'),
        workspace: document.getElementById('editor-workspace'),
        toggleLeftPanel: document.getElementById('toggle-left-panel'),
        toggleRightPanel: document.getElementById('toggle-right-panel'),
        stageTip: document.getElementById('stage-tip'),
        dropIndicator: document.getElementById('canvas-drop-indicator'),
        dropIndicatorTitle: document.getElementById('canvas-drop-indicator-title'),
        dropIndicatorHint: document.getElementById('canvas-drop-indicator-hint'),
        assetInput: document.getElementById('asset-file-input'),
        uploadAssets: document.getElementById('upload-assets'),
        assetGrid: document.getElementById('asset-grid'),
        backgroundPickBanner: document.getElementById('background-pick-banner'),
        backgroundPickTarget: document.getElementById('background-pick-target'),
        cancelBackgroundPick: document.getElementById('cancel-background-pick'),
        assetToolbar: document.getElementById('asset-toolbar'),
        assetSelectToggle: document.getElementById('asset-select-toggle'),
        assetBulk: document.getElementById('asset-bulk'),
        assetSelectAll: document.getElementById('asset-select-all'),
        assetSelectedCount: document.getElementById('asset-selected-count'),
        assetInvertSelect: document.getElementById('asset-invert-select'),
        assetRemoveSelected: document.getElementById('asset-remove-selected'),
        undo: document.getElementById('undo-button'),
        redo: document.getElementById('redo-button'),
        zoomOut: document.getElementById('zoom-out'),
        zoomIn: document.getElementById('zoom-in'),
        zoomValue: document.getElementById('zoom-value'),
        exportFormat: document.getElementById('export-format'),
        exportScale: document.getElementById('export-scale'),
        exportSizePreview: document.getElementById('export-size-preview'),
        exportMaxSize: document.getElementById('export-max-size'),
        openExportSettings: document.getElementById('open-export-settings'),
        exportSettingsDialog: document.getElementById('export-settings-dialog'),
        exportFromSettings: document.getElementById('export-from-settings'),
        exportButton: document.getElementById('export-button'),
        selectionKind: document.getElementById('selection-kind'),
        propsEmpty: document.getElementById('properties-empty'),
        propsFrame: document.getElementById('properties-frame'),
        propsText: document.getElementById('properties-text'),
        propsBackground: document.getElementById('properties-background'),
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
        frameCenterHorizontal: document.getElementById('frame-center-horizontal'),
        frameCenterVertical: document.getElementById('frame-center-vertical'),
        frameRotate: document.getElementById('frame-rotate'),
        deleteFrame: document.getElementById('delete-frame'),
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
        backgroundName: document.getElementById('background-name'),
        backgroundHelp: document.getElementById('background-help'),
        backgroundControls: document.getElementById('background-controls'),
        backgroundModeSolid: document.getElementById('background-mode-solid'),
        backgroundModeImage: document.getElementById('background-mode-image'),
        backgroundSolidControls: document.getElementById('background-solid-controls'),
        backgroundSolidColor: document.getElementById('background-solid-color'),
        backgroundSolidHex: document.getElementById('background-solid-hex'),
        backgroundSolidSwatches: document.getElementById('background-solid-swatches'),
        backgroundSolidOpacity: document.getElementById('background-solid-opacity'),
        backgroundSolidOpacityValue: document.getElementById('background-solid-opacity-value'),
        quickBackgroundModeSolid: document.getElementById('quick-background-mode-solid'),
        quickBackgroundModeImage: document.getElementById('quick-background-mode-image'),
        quickSolidControls: document.getElementById('quick-solid-controls'),
        quickSolidColor: document.getElementById('quick-solid-color'),
        quickSolidHex: document.getElementById('quick-solid-hex'),
        quickSolidSwatches: document.getElementById('quick-solid-swatches'),
        quickSolidOpacity: document.getElementById('quick-solid-opacity'),
        quickSolidOpacityValue: document.getElementById('quick-solid-opacity-value'),
        quickImageControls: document.getElementById('quick-image-controls'),
        backgroundOpacity: document.getElementById('background-opacity'),
        backgroundOpacityValue: document.getElementById('background-opacity-value'),
        backgroundZoom: document.getElementById('background-zoom'),
        backgroundZoomValue: document.getElementById('background-zoom-value'),
        editBackgroundPosition: document.getElementById('edit-background-position'),
        changeBackground: document.getElementById('change-background'),
        removeBackground: document.getElementById('remove-background'),
        freeImageFrameTarget: document.getElementById('free-image-frame-target'),
        freeImageFrameHint: document.getElementById('free-image-frame-hint'),
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

    function effectiveFrames() {
        var base = (state.template.frames || []).filter(function (f) {
            return state.frameLayout.removed.indexOf(f.id) === -1;
        });
        var moved = base.map(function (f) {
            var controller = getFrameController(f.id);
            return controller ? Object.assign({}, f, {
                x: controller.left,
                y: controller.top,
                rotation: controller.angle || 0,
                scaleX: Math.abs(Number(controller.scaleX) || 1),
                scaleY: Math.abs(Number(controller.scaleY) || 1)
            }) : f;
        });
        var added = (state.frameLayout.added || []).map(function (f) {
            var controller = getFrameController(f.id);
            return controller ? Object.assign({}, f, {
                x: controller.left,
                y: controller.top,
                rotation: controller.angle || 0,
                scaleX: Math.abs(Number(controller.scaleX) || 1),
                scaleY: Math.abs(Number(controller.scaleY) || 1)
            }) : f;
        });
        return moved.concat(added);
    }

    function frameConfig(frameId) {
        return effectiveFrames().find(function (item) { return item.id === frameId; });
    }

    function uniqueFieldId(prefix) {
        state.fieldSequence += 1;
        return (prefix || 'field') + '-' + Date.now() + '-' + state.fieldSequence;
    }

    function uniqueElementId(prefix) {
        return uniqueFieldId(prefix || 'image');
    }

    function uniqueFrameId() {
        return 'uf-' + Date.now() + '-' + (++state.fieldSequence);
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

    function frameContainingCanvasPoint(x, y) {
        return effectiveFrames().find(function (frame) {
            var bounds = frameBounds(frame);
            var radians = -(Number(bounds.rotation) || 0) * Math.PI / 180;
            var dx = Number(x) - bounds.centerX;
            var dy = Number(y) - bounds.centerY;
            var localX = dx * Math.cos(radians) - dy * Math.sin(radians);
            var localY = dx * Math.sin(radians) + dy * Math.cos(radians);
            if (frame.shape === 'circle') {
                var radiusX = Math.max(1, bounds.width / 2);
                var radiusY = Math.max(1, bounds.height / 2);
                return (localX * localX) / (radiusX * radiusX) + (localY * localY) / (radiusY * radiusY) <= 1;
            }
            return Math.abs(localX) <= bounds.width / 2 && Math.abs(localY) <= bounds.height / 2;
        }) || null;
    }

    function suggestedFrameForVisual(visual) {
        if (!visual || typeof visual.getCenterPoint !== 'function') return null;
        var center = visual.getCenterPoint();
        return frameContainingCanvasPoint(center.x, center.y);
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
        var frames = effectiveFrames();
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
            scaleX: frame.scaleX == null ? 1 : Number(frame.scaleX),
            scaleY: frame.scaleY == null ? 1 : Number(frame.scaleY),
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

    function frameBounds(frame, shape) {
        var scaleX = Math.abs(Number(shape ? shape.scaleX : frame.scaleX) || 1);
        var scaleY = Math.abs(Number(shape ? shape.scaleY : frame.scaleY) || 1);
        var width = Number(frame.width) * scaleX;
        var height = Number(frame.height) * scaleY;
        var angle = Number(shape ? shape.angle : frame.rotation) || 0;
        var left = Number(shape ? shape.left : frame.x) || 0;
        var top = Number(shape ? shape.top : frame.y) || 0;
        var radians = angle * Math.PI / 180;
        var halfX = width / 2;
        var halfY = height / 2;
        return {
            x: left,
            y: top,
            width: width,
            height: height,
            rotation: angle,
            centerX: left + halfX * Math.cos(radians) - halfY * Math.sin(radians),
            centerY: top + halfX * Math.sin(radians) + halfY * Math.cos(radians)
        };
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

    function styleFrameController(object) {
        if (!object) return;
        object.set({
            selectable: true,
            evented: true,
            hasControls: true,
            hasBorders: true,
            lockMovementX: false,
            lockMovementY: false,
            lockScalingX: false,
            lockScalingY: false,
            lockRotation: false,
            lockScalingFlip: true,
            minScaleLimit: .08,
            hoverCursor: 'move',
            moveCursor: 'move',
            objectCaching: false
        });
        styleInteractiveObject(object);
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
            selectable: false,
            evented: false,
            hasControls: false,
            hasBorders: false,
            objectCaching: false
        });
        var border = makeFrameShape(frame, {
            labType: 'frameBorder',
            frameId: frame.id,
            frameBounds: frameBounds(frame),
            fill: 'rgba(255,255,255,0)',
            stroke: 'rgba(23,32,51,.16)',
            strokeWidth: 2,
            selectable: true,
            evented: true,
            excludeFromExport: false
        });
        styleFrameController(border);
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

    function addBackgroundMask(definition, index) {
        var radius = Math.max(0, Number(definition.radius) || 0);
        var mask = new fabric.Rect({
            left: Number(definition.x) || 0,
            top: Number(definition.y) || 0,
            width: Math.max(1, Number(definition.width) || 1),
            height: Math.max(1, Number(definition.height) || 1),
            rx: radius,
            ry: radius,
            angle: Number(definition.angle) || 0,
            fill: definition.fill || state.template.background || '#f5f7fa',
            strokeWidth: 0,
            selectable: false,
            evented: false,
            hasControls: false,
            hasBorders: false,
            objectCaching: false,
            excludeFromExport: false,
            labType: 'backgroundMask',
            maskId: definition.id || ('background-mask-' + (index + 1))
        });
        var baseIndex = getCanvasBackground() ? 1 : 0;
        state.canvas.insertAt(mask, Math.min(baseIndex + index, state.canvas.getObjects().length), false);
    }

    // 装饰形状：模板自带的纯视觉层（椭圆 / 色块 / 圆角条），不可选中，随画布导出。
    // layer='back'  → 压在相框之下（如标题椭圆底色）
    // layer='front' → 浮在相框图片之上、文字之下（如图片上的说明色条）
    function addDecorShape(definition, index) {
        var type = definition.type || 'rect';
        var radius = Math.max(0, Number(definition.radius) || 0);
        var width = Math.max(1, Number(definition.width) || 1);
        var height = Math.max(1, Number(definition.height) || 1);
        var opacity = definition.opacity == null ? 1 : Math.max(0, Math.min(1, Number(definition.opacity)));
        var common = {
            left: Number(definition.x) || 0,
            top: Number(definition.y) || 0,
            originX: 'left',
            originY: 'top',
            angle: Number(definition.angle) || 0,
            fill: definition.fill || '#e5e7eb',
            opacity: opacity,
            strokeWidth: 0,
            selectable: false,
            evented: false,
            hasControls: false,
            hasBorders: false,
            objectCaching: false,
            excludeFromExport: false,
            labType: 'decorShape',
            shapeId: definition.id || ('decor-shape-' + (index + 1)),
            shapeLayer: definition.layer === 'front' ? 'front' : 'back'
        };
        var shape;
        if (type === 'ellipse' || type === 'circle') {
            shape = new fabric.Ellipse(Object.assign({}, common, { rx: width / 2, ry: height / 2 }));
        } else {
            shape = new fabric.Rect(Object.assign({}, common, {
                width: width,
                height: height,
                rx: type === 'rounded' ? radius : 0,
                ry: type === 'rounded' ? radius : 0
            }));
        }
        state.canvas.add(shape);
        return shape;
    }

    // 前景装饰始终压在相框图片之上、文字之下
    function bringDecorToFront() {
        state.canvas.getObjects().filter(function (object) {
            return object.labType === 'decorShape' && object.shapeLayer === 'front';
        }).forEach(function (object) { object.bringToFront(); });
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
        });
    }

    var SOLID_COLOR_PRESETS = [
        { label: '白色', value: '#ffffff' },
        { label: '浅暖灰', value: '#f5f7fa' },
        { label: '浅灰', value: '#e5e7eb' },
        { label: '米色', value: '#faf3e8' },
        { label: '品牌深蓝', value: '#172033' },
        { label: '透明', value: 'transparent' }
    ];

    function isTransparentSolid() {
        return state.solidColor === 'transparent' || Number(state.solidOpacity) <= 0;
    }

    // 纯色模式下才把底色画到画布上；图片模式互斥，底色置透明。
    function solidBackgroundValue() {
        if (state.backgroundMode !== 'solid') return '';
        if (isTransparentSolid()) return '';
        var alpha = Math.max(0, Math.min(100, Number(state.solidOpacity))) / 100;
        if (alpha >= 1) return state.solidColor;
        return 'rgba(' + hexToRgb(state.solidColor).join(', ') + ', ' + alpha.toFixed(3) + ')';
    }

    function hexToRgb(hex) {
        var match = /^#?([0-9a-f]{6})$/i.exec(String(hex || ''));
        if (!match) return [245, 247, 250];
        var value = parseInt(match[1], 16);
        return [(value >> 16) & 255, (value >> 8) & 255, value & 255];
    }

    function applySolidBackground(commit) {
        if (!state.canvas) return;
        state.canvas.backgroundColor = solidBackgroundValue();
        state.canvas.requestRenderAll();
        if (commit) queueMutation(true);
    }

    function syncBackgroundModeFromDefinition() {
        var background = (state.template && state.template.background) || '#f5f7fa';
        var preset = SOLID_COLOR_PRESETS.some(function (item) { return item.value === background; })
            ? background
            : (/^#[0-9a-f]{6}$/i.test(background) ? background : '#f5f7fa');
        var savedOpacity = state.template ? Number(state.template.backgroundOpacity) : NaN;
        state.solidColor = background === 'transparent' ? 'transparent' : preset;
        state.solidOpacity = isNaN(savedOpacity) ? 100 : Math.max(0, Math.min(100, savedOpacity * 100));
        state.backgroundMode = state.template && state.template.canvasBackground ? 'image' : 'solid';
    }

    function backgroundStateSnapshot() {
        return {
            mode: state.backgroundMode === 'image' ? 'image' : 'solid',
            solidColor: state.solidColor || '#f5f7fa',
            solidOpacity: Math.max(0, Math.min(100, Number(state.solidOpacity) || 0))
        };
    }

    function rgbToHex(red, green, blue) {
        return '#' + [red, green, blue].map(function (value) {
            return Math.max(0, Math.min(255, Number(value) || 0)).toString(16).padStart(2, '0');
        }).join('');
    }

    function inferBackgroundState(fabricJson) {
        var objects = fabricJson && Array.isArray(fabricJson.objects) ? fabricJson.objects : [];
        var hasImage = objects.some(function (object) { return object && object.labType === 'canvasBackground'; });
        var value = fabricJson && typeof fabricJson.background === 'string' ? fabricJson.background.trim() : '';
        var inferred = {
            mode: hasImage ? 'image' : 'solid',
            solidColor: state.solidColor || '#f5f7fa',
            solidOpacity: Number.isFinite(Number(state.solidOpacity)) ? Number(state.solidOpacity) : 100
        };
        if (!value || value === 'transparent') {
            if (!hasImage) {
                inferred.solidColor = 'transparent';
                inferred.solidOpacity = 0;
            }
            return inferred;
        }
        if (/^#[0-9a-f]{6}$/i.test(value)) {
            inferred.solidColor = value.toLowerCase();
            inferred.solidOpacity = 100;
            return inferred;
        }
        var rgba = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i.exec(value);
        if (rgba) {
            inferred.solidColor = rgbToHex(rgba[1], rgba[2], rgba[3]);
            inferred.solidOpacity = rgba[4] == null ? 100 : Math.round(Math.max(0, Math.min(1, Number(rgba[4]))) * 100);
        }
        return inferred;
    }

    function restoreBackgroundState(saved, fabricJson) {
        var next = saved && typeof saved === 'object' ? saved : inferBackgroundState(fabricJson);
        state.backgroundMode = next.mode === 'image' ? 'image' : 'solid';
        state.solidColor = next.solidColor === 'transparent' || /^#[0-9a-f]{6}$/i.test(String(next.solidColor || ''))
            ? String(next.solidColor).toLowerCase()
            : '#f5f7fa';
        var opacity = Number(next.solidOpacity);
        state.solidOpacity = Number.isFinite(opacity) ? Math.max(0, Math.min(100, opacity)) : 100;
    }

    function detachCanvasBackground() {
        var background = getCanvasBackground();
        if (!background) return false;
        stopBackgroundEditing();
        state.canvas.remove(background);
        state.canvas.discardActiveObject();
        return true;
    }

    function setBackgroundMode(mode, commit) {
        var next = mode === 'image' ? 'image' : 'solid';
        if (next === state.backgroundMode) {
            if (next === 'image' && !getCanvasBackground()) openCanvasBackgroundPicker();
            return;
        }
        if (next === 'solid') {
            clearBackgroundPick();
            detachCanvasBackground();
            state.backgroundMode = 'solid';
            applySolidBackground(false);
            if (commit !== false) queueMutation(true);
        } else {
            state.backgroundPickPreviousMode = state.backgroundMode;
            state.backgroundMode = 'image';
            applySolidBackground(false);
            if (getCanvasBackground()) {
                if (commit !== false) queueMutation(true);
            } else {
                openCanvasBackgroundPicker();
            }
        }
        renderBackgroundModeControls();
        renderProperties();
    }

    function setSolidColor(value, commit) {
        if (!value) return;
        var normalized = String(value).trim().toLowerCase();
        state.solidColor = normalized === 'transparent' ? 'transparent' : normalized;
        applySolidBackground(commit !== false);
        renderSolidControls();
    }

    function setSolidOpacity(value, commit) {
        state.solidOpacity = Math.max(0, Math.min(100, Number(value)));
        applySolidBackground(commit !== false);
        renderSolidControls();
    }

    function renderSolidControls() {
        var hex = state.solidColor === 'transparent' ? '#f5f7fa' : state.solidColor;
        [
            [els.backgroundSolidColor, els.backgroundSolidHex],
            [els.quickSolidColor, els.quickSolidHex]
        ].forEach(function (pair) {
            if (pair[0]) pair[0].value = hex;
            if (pair[1]) pair[1].value = state.solidColor === 'transparent' ? 'transparent' : hex.toUpperCase();
        });
        [[els.backgroundSolidOpacity, els.backgroundSolidOpacityValue], [els.quickSolidOpacity, els.quickSolidOpacityValue]]
            .forEach(function (pair) {
                if (!pair[0]) return;
                pair[0].value = state.solidOpacity;
                if (pair[1]) { pair[1].value = state.solidOpacity + '%'; pair[1].textContent = state.solidOpacity + '%'; }
            });
        document.querySelectorAll('.editor-swatch').forEach(function (button) {
            button.setAttribute('aria-pressed', button.dataset.value === state.solidColor ? 'true' : 'false');
        });
    }

    function renderBackgroundModeControls() {
        var isSolid = state.backgroundMode === 'solid';
        var hasImage = Boolean(getCanvasBackground());
        if (els.backgroundSolidControls) els.backgroundSolidControls.hidden = !isSolid;
        if (els.backgroundControls) els.backgroundControls.hidden = isSolid;
        if (els.backgroundModeSolid) els.backgroundModeSolid.setAttribute('aria-pressed', isSolid ? 'true' : 'false');
        if (els.backgroundModeImage) els.backgroundModeImage.setAttribute('aria-pressed', isSolid ? 'false' : 'true');
        if (els.quickSolidControls) els.quickSolidControls.hidden = !isSolid;
        if (els.quickImageControls) els.quickImageControls.hidden = isSolid;
        if (els.quickBackgroundModeSolid) els.quickBackgroundModeSolid.setAttribute('aria-pressed', isSolid ? 'true' : 'false');
        if (els.quickBackgroundModeImage) els.quickBackgroundModeImage.setAttribute('aria-pressed', isSolid ? 'false' : 'true');
        if (els.backgroundName) {
            els.backgroundName.textContent = isSolid
                ? (state.solidColor === 'transparent' ? '透明底色' : state.solidColor.toUpperCase() + ' 纯色')
                : (hasImage ? '图片背景' : '画布背景');
        }
        if (els.backgroundHelp && !isSolid) {
            els.backgroundHelp.textContent = '点击背景即可选中并直接拖动位置；使用缩放滑杆放大或缩小。背景始终位于所有内容下方。';
        }
        renderSolidControls();
    }

    function buildSolidSwatches() {
        [els.backgroundSolidSwatches, els.quickSolidSwatches].forEach(function (container) {
            if (!container) return;
            container.innerHTML = '';
            SOLID_COLOR_PRESETS.forEach(function (preset) {
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'editor-swatch';
                button.dataset.value = preset.value;
                button.title = preset.label;
                button.setAttribute('aria-label', preset.label);
                button.setAttribute('aria-pressed', 'false');
                if (preset.value === 'transparent') {
                    button.dataset.transparent = 'true';
                } else {
                    button.style.backgroundColor = preset.value;
                }
                button.addEventListener('click', function () {
                    if (state.backgroundMode !== 'solid') setBackgroundMode('solid', true);
                    setSolidColor(preset.value, true);
                });
                container.appendChild(button);
            });
        });
    }

    async function buildTemplateCanvas() {
        state.suppressHistory = true;
        state.canvas.clear();
        syncBackgroundModeFromDefinition();
        state.canvas.backgroundColor = solidBackgroundValue();
        var decorShapes = state.template.shapes || [];
        decorShapes.filter(function (shape) { return shape.layer !== 'front'; }).forEach(addDecorShape);
        effectiveFrames().forEach(addTemplateFrame);
        decorShapes.filter(function (shape) { return shape.layer === 'front'; }).forEach(addDecorShape);
        (state.template.texts || []).forEach(addTemplateText);
        (state.template.images || []).forEach(addTemplateImage);
        if (state.template.canvasBackground) {
            await rebuildCanvasBackground(state.template.canvasBackground);
        }
        (state.template.backgroundMasks || []).forEach(addBackgroundMask);
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
                object.set({ selectable: false, evented: false, hasControls: false, hasBorders: false, lockScalingX: true, lockScalingY: true, lockRotation: true, objectCaching: false });
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
                if (subject) { subject.crossOrigin = 'anonymous'; subject.set({ selectable: false, evented: false, objectCaching: false }); }
            } else if (object.labType === 'imagePlaceholder') {
                object.set({ selectable: true, evented: true, hoverCursor: 'pointer' });
                styleInteractiveObject(object);
            } else if (object.labType === 'canvasBackground') {
                object.crossOrigin = 'anonymous';
                object.set({
                    elementId: 'canvas-background',
                    backgroundZoom: Number(object.backgroundZoom) || 1,
                    backgroundCropX: object.backgroundCropX == null ? Number(object.cropX) || 0 : Number(object.backgroundCropX),
                    backgroundCropY: object.backgroundCropY == null ? Number(object.cropY) || 0 : Number(object.backgroundCropY)
                });
                styleCanvasBackground(object);
            } else if (object.labType === 'backgroundMask') {
                object.set({
                    selectable: false,
                    evented: false,
                    hasControls: false,
                    hasBorders: false,
                    objectCaching: false,
                    excludeFromExport: false
                });
            } else if (object.labType === 'framePlaceholder') {
                object.set({ selectable: false, evented: false, hasControls: false, hasBorders: false, lockMovementX: true, lockMovementY: true });
            } else if (object.labType === 'frameBorder') {
                object.set({ excludeFromExport: false });
                styleFrameController(object);
            } else if (object.labType === 'decorShape') {
                object.set({
                    selectable: false,
                    evented: false,
                    hasControls: false,
                    hasBorders: false,
                    strokeWidth: 0,
                    excludeFromExport: false,
                    objectCaching: false,
                    shapeLayer: object.shapeLayer === 'front' ? 'front' : 'back'
                });
            } else if (object.labType === 'guide') {
                state.canvas.remove(object);
            }
        });
        state.canvas.getObjects().filter(function (object) { return object.labType === 'frameBorder'; }).forEach(function (object) {
            object.bringToFront();
        });
        bringDecorToFront();
        state.canvas.getObjects().filter(function (object) { return object.labType === 'text'; }).forEach(function (object) {
            object.bringToFront();
        });
        state.canvas.renderAll();
    }

    function parseSavedDesign() {
        if (!state.project.designJson) return null;
        try {
            var parsed = JSON.parse(state.project.designJson);
            state.assets = Array.isArray(parsed.assets) ? parsed.assets : [];
            state.frameLayout = parsed.frameLayout && Array.isArray(parsed.frameLayout.added)
                ? { added: parsed.frameLayout.added, removed: parsed.frameLayout.removed || [] }
                : { added: [], removed: [] };
            restoreBackgroundState(parsed.backgroundState, parsed.fabric || null);
            return parsed.fabric || null;
        } catch (error) {
            showToast('项目数据无法读取，已恢复模板初始状态', true);
            return null;
        }
    }

    function canvasSnapshot() {
        var fabricJson = JSON.parse(JSON.stringify(state.canvas.toDatalessJSON(CUSTOM_PROPS)));
        if (fabricJson && Array.isArray(fabricJson.objects)) {
            fabricJson.objects = fabricJson.objects.filter(function (o) { return o.labType !== 'frameEditOverlay'; });
        }
        return JSON.stringify({
            schemaVersion: 2,
            fabric: fabricJson,
            frameLayout: JSON.parse(JSON.stringify(state.frameLayout || { added: [], removed: [] })),
            backgroundState: backgroundStateSnapshot()
        });
    }

    function parsedHistorySnapshot(snapshot) {
        var parsed = typeof snapshot === 'string' ? JSON.parse(snapshot) : snapshot;
        if (parsed && parsed.fabric) return parsed;
        return { fabric: parsed, frameLayout: null, backgroundState: null };
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
        stopBackgroundEditing();
        clearBackgroundPick();
        state.canvas.discardActiveObject();
        var snapshot = parsedHistorySnapshot(state.history[index]);
        if (snapshot.frameLayout && Array.isArray(snapshot.frameLayout.added)) {
            state.frameLayout = {
                added: JSON.parse(JSON.stringify(snapshot.frameLayout.added)),
                removed: Array.isArray(snapshot.frameLayout.removed) ? snapshot.frameLayout.removed.slice() : []
            };
        }
        restoreBackgroundState(snapshot.backgroundState, snapshot.fabric);
        state.canvas.loadFromJSON(snapshot.fabric, function () {
            state.historyIndex = index;
            rehydrateCanvasObjects();
            applyViewportScale();
            state.suppressHistory = false;
            updateHistoryButtons();
            renderContentPanel();
            renderLayerPanel();
            renderBackgroundModeControls();
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

    function getFrameController(frameId) {
        return getFrameBorder(frameId) || getFramePlaceholder(frameId) || null;
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
        var centerX = bounds.centerX == null ? bounds.x + bounds.width / 2 : Number(bounds.centerX);
        var centerY = bounds.centerY == null ? bounds.y + bounds.height / 2 : Number(bounds.centerY);
        var radians = -(Number(bounds.rotation) || 0) * Math.PI / 180;
        var dx = Number(image.left) - centerX;
        var dy = Number(image.top) - centerY;
        var localX = dx * Math.cos(radians) - dy * Math.sin(radians);
        var localY = dx * Math.sin(radians) + dy * Math.cos(radians);
        var maxOffsetX = Math.max(0, (width - Number(bounds.width)) / 2);
        var maxOffsetY = Math.max(0, (height - Number(bounds.height)) / 2);
        localX = Math.max(-maxOffsetX, Math.min(maxOffsetX, localX));
        localY = Math.max(-maxOffsetY, Math.min(maxOffsetY, localY));
        var forward = -radians;
        image.left = centerX + localX * Math.cos(forward) - localY * Math.sin(forward);
        image.top = centerY + localX * Math.sin(forward) + localY * Math.cos(forward);
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
            if (!bounds) return false;
            var centerX = bounds.centerX == null ? bounds.x + bounds.width / 2 : Number(bounds.centerX);
            var centerY = bounds.centerY == null ? bounds.y + bounds.height / 2 : Number(bounds.centerY);
            var radians = -(Number(bounds.rotation) || 0) * Math.PI / 180;
            var dx = logicalX - centerX;
            var dy = logicalY - centerY;
            var localX = dx * Math.cos(radians) - dy * Math.sin(radians);
            var localY = dx * Math.sin(radians) + dy * Math.cos(radians);
            return Math.abs(localX) <= Number(bounds.width) / 2
                && Math.abs(localY) <= Number(bounds.height) / 2;
        };
    }

    function restoreDefaultHitTest(image) {
        if (image && Object.prototype.hasOwnProperty.call(image, 'containsPoint')) {
            delete image.containsPoint;
        }
    }

    function setPlaceholderState(frameId, hasImage) {
        var placeholder = getFramePlaceholder(frameId);
        if (!placeholder) return;
        placeholder.set({
            selectable: false,
            evented: false,
            fill: hasImage ? 'rgba(255,255,255,0)' : 'rgba(255,255,255,.52)',
            stroke: hasImage ? 'rgba(37,99,235,0)' : 'rgba(37,99,235,.50)'
        });
    }

    function cloneLoadedVisualForFrame(visual) {
        var source = visual && visual.labType === 'imageComposition' ? compositionSubject(visual) : visual;
        var element = source && (typeof source.getElement === 'function' ? source.getElement() : source._element);
        if (!source || !element || !(element.naturalWidth || element.width)) return null;
        return new fabric.Image(element, {
            crossOrigin: 'anonymous',
            cropX: Number(source.cropX) || 0,
            cropY: Number(source.cropY) || 0,
            width: Number(source.width) || Number(element.naturalWidth) || Number(element.width),
            height: Number(source.height) || Number(element.naturalHeight) || Number(element.height)
        });
    }

    async function assignImageToFrame(frameId, asset, options) {
        var requested = typeof options === 'function' ? { onReady: options } : (options || {});
        var frame = frameConfig(frameId);
        if (!frame) {
            showToast('目标相框已不存在，请重新选择', true);
            return null;
        }
        var sourceVisual = requested.sourceVisual || null;
        var sourceUrl = asset && asset.url ? asset.url : activeVisualUrl(sourceVisual);
        var image = cloneLoadedVisualForFrame(sourceVisual);
        if (!image && !sourceUrl) {
            showToast('当前图片没有可用的图像来源，请重新上传后再试', true);
            return null;
        }
        var assignmentToken = uniqueElementId('frame-assignment');
        state.frameAssignmentTokens[frameId] = assignmentToken;
        try {
            if (!image) image = await loadFabricImage(sourceUrl);
            if (state.frameAssignmentTokens[frameId] !== assignmentToken) return null;
            frame = frameConfig(frameId);
            if (!frame) throw new Error('目标相框已被删除');
            var oldImage = getFrameImage(frameId);
            if (oldImage) state.canvas.remove(oldImage);
            var clip = makeFrameShape(frame, { absolutePositioned: true, fill: '#ffffff', strokeWidth: 0 });
            clip.setCoords();
            var bounds = frameBounds(frame, clip);
            var cover = Math.max(bounds.width / image.width, bounds.height / image.height);
            image.set({
                left: bounds.centerX,
                top: bounds.centerY,
                originX: 'center',
                originY: 'center',
                scaleX: cover,
                scaleY: cover,
                angle: bounds.rotation || 0,
                clipPath: clip,
                crossOrigin: 'anonymous',
                labType: 'frameImage',
                frameId: frameId,
                frameName: frameLabel(frameId),
                frameBounds: bounds,
                coverScale: cover,
                cropZoom: 1,
                assetUrl: sourceUrl,
                assetName: asset && asset.name ? asset.name : '图片',
                selectable: false,
                evented: false,
                hasControls: false,
                hasBorders: false,
                lockScalingX: true,
                lockScalingY: true,
                lockRotation: true,
                objectCaching: false,
                hoverCursor: 'default'
            });
            bindFrameHitTest(image);
            setPlaceholderState(frameId, true);
            state.canvas.add(image);
            var border = getFrameBorder(frameId);
            if (border) border.bringToFront();
            bringDecorToFront();
            state.canvas.getObjects().filter(function (item) { return item.labType === 'text'; }).forEach(function (item) { item.bringToFront(); });
            if (border) state.canvas.setActiveObject(border);
            constrainFrameImage(image);
            if (typeof requested.onReady === 'function') requested.onReady(image);
            state.canvas.requestRenderAll();
            if (requested.commit !== false) queueMutation(true);
            renderProperties();
            return image;
        } catch (error) {
            showToast(error && error.message ? error.message : '图片填入相框失败，请重试', true);
            return null;
        } finally {
            if (state.frameAssignmentTokens[frameId] === assignmentToken) delete state.frameAssignmentTokens[frameId];
        }
    }

    function removeFrameImage() {
        var image = selectedFrameImage();
        if (!image) return;
        var frameId = image.frameId;
        if (state.frameEditMode) exitFrameEdit();
        state.canvas.remove(image);
        setPlaceholderState(frameId, false);
        var controller = getFrameController(frameId);
        if (controller) state.canvas.setActiveObject(controller);
        state.canvas.renderAll();
        queueMutation(true);
        renderProperties();
    }

    function convertSelectedFrameToFreeImage() {
        var image = selectedFrameImage();
        if (!image) return;
        var frameId = image.frameId;
        if (state.frameEditMode) exitFrameEdit();
        image.clipPath = null;
        restoreDefaultHitTest(image);
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
            lockMovementX: false,
            lockMovementY: false,
            lockScalingX: false,
            lockScalingY: false,
            lockRotation: false,
            hoverCursor: 'move'
        });
        setPlaceholderState(frameId, false);
        styleFreeVisual(image);
        image.setCoords();
        state.canvas.setActiveObject(image);
        state.canvas.requestRenderAll();
        queueMutation(true);
        renderProperties();
        showToast('已转为自由图片，可在整个画布中移动');
    }

    function beginFrameDrag(object) {
        var frameId = object && object.frameId;
        var controller = getFrameController(frameId);
        if (!controller) return;
        var ph = getFramePlaceholder(frameId);
        var img = getFrameImage(frameId);
        var center = controller.getCenterPoint();
        var startAngle = Number(controller.angle) || 0;
        var radians = -startAngle * Math.PI / 180;
        var imageDx = img ? Number(img.left) - center.x : 0;
        var imageDy = img ? Number(img.top) - center.y : 0;
        state.dragFrame = {
            frameId: frameId,
            controller: controller,
            startWidth: Math.max(1, Math.abs(controller.getScaledWidth())),
            startHeight: Math.max(1, Math.abs(controller.getScaledHeight())),
            startAngle: startAngle,
            image: img ? {
                angle: Number(img.angle) || 0,
                localX: imageDx * Math.cos(radians) - imageDy * Math.sin(radians),
                localY: imageDx * Math.sin(radians) + imageDy * Math.cos(radians),
                cropZoom: Number(img.cropZoom) || 1
            } : null,
            placeholder: ph || null
        };
    }

    function syncFrameTransform(controller) {
        var df = state.dragFrame;
        if (!controller || !df || df.frameId !== controller.frameId) return;
        var minSize = 64;
        var maxWidth = state.logicalWidth * 2;
        var maxHeight = state.logicalHeight * 2;
        var baseWidth = Math.max(1, Number(controller.width) || 1);
        var baseHeight = Math.max(1, Number(controller.height) || 1);
        controller.scaleX = Math.max(minSize / baseWidth, Math.min(maxWidth / baseWidth, Math.abs(Number(controller.scaleX) || 1)));
        controller.scaleY = Math.max(minSize / baseHeight, Math.min(maxHeight / baseHeight, Math.abs(Number(controller.scaleY) || 1)));
        controller.setCoords();

        var ph = getFramePlaceholder(df.frameId);
        var img = getFrameImage(df.frameId);
        var clip = img && img.clipPath;
        var transform = {
            left: controller.left,
            top: controller.top,
            scaleX: controller.scaleX,
            scaleY: controller.scaleY,
            angle: controller.angle || 0
        };
        if (ph) ph.set(transform);
        if (clip) clip.set(transform);
        if (img) {
            var currentWidth = Math.max(1, Math.abs(controller.getScaledWidth()));
            var currentHeight = Math.max(1, Math.abs(controller.getScaledHeight()));
            var center = controller.getCenterPoint();
            var angle = Number(controller.angle) || 0;
            var radians = angle * Math.PI / 180;
            var localX = df.image.localX * currentWidth / df.startWidth;
            var localY = df.image.localY * currentHeight / df.startHeight;
            var offsetX = localX * Math.cos(radians) - localY * Math.sin(radians);
            var offsetY = localX * Math.sin(radians) + localY * Math.cos(radians);
            var cover = Math.max(currentWidth / Math.max(1, Number(img.width)), currentHeight / Math.max(1, Number(img.height)));
            var bounds = frameBounds({
                x: controller.left,
                y: controller.top,
                width: baseWidth,
                height: baseHeight,
                scaleX: controller.scaleX,
                scaleY: controller.scaleY,
                rotation: angle
            }, controller);
            img.set({
                left: center.x + offsetX,
                top: center.y + offsetY,
                scaleX: cover * df.image.cropZoom,
                scaleY: cover * df.image.cropZoom,
                angle: df.image.angle + angle - df.startAngle,
                coverScale: cover,
                frameBounds: bounds
            });
            constrainFrameImage(img);
        }
        if (ph) ph.setCoords();
        if (clip) clip.setCoords();
        if (img) img.setCoords();
        state.canvas.requestRenderAll();
    }

    function modifySelectedFrame(mode) {
        var frameId = selectedFrameId();
        if (state.frameEditMode) exitFrameEdit();
        var controller = getFrameController(frameId);
        if (!controller) return;
        beginFrameDrag(controller);
        var center = controller.getCenterPoint();
        if (mode === 'center-horizontal') center.x = state.logicalWidth / 2;
        if (mode === 'center-vertical') center.y = state.logicalHeight / 2;
        if (mode === 'rotate') controller.rotate(((Number(controller.angle) || 0) + 90) % 360);
        controller.setPositionByOrigin(center, 'center', 'center');
        controller.setCoords();
        syncFrameTransform(controller);
        state.dragFrame = null;
        queueMutation(true);
        renderProperties();
    }

    function addFrame(shape) {
        var id = uniqueFrameId();
        var size = Math.min(420, Math.min(state.logicalWidth, state.logicalHeight) - 80);
        var x = Math.max(20, state.logicalWidth / 2 - size / 2);
        var y = Math.max(20, state.logicalHeight / 2 - size / 2);
        var def = {
            id: id, shape: shape, x: x, y: y, width: size, height: size, rotation: 0,
            label: '自定义相框', radius: shape === 'rounded' ? 28 : 0
        };
        state.frameLayout.added.push(def);
        addTemplateFrame(def);
        var border = getFrameBorder(id);
        if (border) border.bringToFront();
        bringDecorToFront();
        state.canvas.getObjects().filter(function (o) { return o.labType === 'text'; }).forEach(function (o) { o.bringToFront(); });
        if (border) state.canvas.setActiveObject(border);
        state.canvas.renderAll();
        queueMutation(true);
        renderProperties();
        showToast('已添加' + (shape === 'circle' ? '圆形' : (shape === 'rounded' ? '圆角' : '矩形')) + '相框，可直接移动、缩放和旋转');
    }

    function deleteFrame(frameId) {
        if (!frameId) return;
        var ph = getFramePlaceholder(frameId), b = getFrameBorder(frameId), img = getFrameImage(frameId);
        [ph, b, img].forEach(function (o) { if (o) state.canvas.remove(o); });
        var addedIndex = (state.frameLayout.added || []).findIndex(function (f) { return f.id === frameId; });
        if (addedIndex >= 0) state.frameLayout.added.splice(addedIndex, 1);
        else if (state.frameLayout.removed.indexOf(frameId) === -1) state.frameLayout.removed.push(frameId);
        if (state.frameEditMode && state.frameEditTarget === frameId) exitFrameEdit();
        state.canvas.discardActiveObject();
        state.canvas.renderAll();
        queueMutation(true);
        renderProperties();
        showToast('已删除相框');
    }

    function enterFrameEdit(frameId) {
        if (state.frameEditMode) exitFrameEdit();
        var ph = getFramePlaceholder(frameId), b = getFrameBorder(frameId), img = getFrameImage(frameId);
        if (!img) {
            if (b) state.canvas.setActiveObject(b);
            document.querySelector('[data-panel-tab="assets"]').click();
            showToast('请从左侧选择图片填入这个相框');
            return;
        }
        state.frameEditMode = true;
        state.frameEditTarget = frameId;
        var overlay = new fabric.Rect({
            left: 0, top: 0, width: state.logicalWidth, height: state.logicalHeight,
            fill: 'rgba(15,20,33,0.55)', selectable: false, evented: true, excludeFromExport: true, hoverCursor: 'default'
        });
        overlay.labType = 'frameEditOverlay';
        state.frameEditOverlay = overlay;
        state.canvas.add(overlay);
        if (ph) ph.bringToFront();
        img.set({ selectable: true, evented: true, hasControls: false, hasBorders: false, lockMovementX: false, lockMovementY: false, hoverCursor: 'move' });
        img.bringToFront();
        if (b) {
            b.set({ selectable: false, evented: false, hasControls: false, hasBorders: false });
            b.bringToFront();
        }
        state.canvas.setActiveObject(img);
        state.canvas.renderAll();
        renderProperties();
        positionCutoutFab();
        showToast('正在编辑框内图片：拖动调整裁剪，点击深色区域退出', false);
    }

    function exitFrameEdit() {
        if (!state.frameEditMode) return;
        var frameId = state.frameEditTarget;
        var image = getFrameImage(frameId);
        var controller = getFrameController(frameId);
        state.frameEditMode = false;
        state.frameEditTarget = null;
        if (state.frameEditOverlay) { state.canvas.remove(state.frameEditOverlay); state.frameEditOverlay = null; }
        if (image) image.set({ selectable: false, evented: false, hasControls: false, hasBorders: false, lockMovementX: true, lockMovementY: true, hoverCursor: 'default' });
        if (controller) {
            styleFrameController(controller);
            controller.bringToFront();
            state.canvas.setActiveObject(controller);
        } else {
            state.canvas.discardActiveObject();
        }
        bringDecorToFront();
        state.canvas.getObjects().filter(function (object) { return object.labType === 'text'; }).forEach(function (object) { object.bringToFront(); });
        state.canvas.renderAll();
        renderProperties();
        positionCutoutFab();
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

    function isCopyableCanvasObject(object) {
        return Boolean(object) && ['text', 'freeImage', 'imageComposition', 'imagePlaceholder'].includes(object.labType);
    }

    function selectedCopyTarget() {
        var active = state.canvas && state.canvas.getActiveObject();
        if (!active) return null;
        if (isCopyableCanvasObject(active)) return active;
        if (active.type === 'activeSelection' && typeof active.getObjects === 'function') {
            var objects = active.getObjects();
            return objects.length && objects.every(isCopyableCanvasObject) ? active : null;
        }
        return null;
    }

    function copySelectedElements() {
        var active = selectedCopyTarget();
        if (!active) {
            showToast('请选择可复制的图片或文字；相框和画布背景不能直接复制', true);
            return;
        }
        state.clipboard = null;
        var copyToken = ++state.clipboardCopyToken;
        state.clipboardReady = new Promise(function (resolve) {
            active.clone(function (copy) {
                if (copyToken !== state.clipboardCopyToken) {
                    resolve(null);
                    return;
                }
                state.clipboard = copy;
                state.clipboardPasteCount = 0;
                var count = copy.type === 'activeSelection' && typeof copy.getObjects === 'function'
                    ? copy.getObjects().length : 1;
                showToast('已复制 ' + count + ' 个画布元素，按 Ctrl + V 粘贴');
                resolve(copy);
            }, CUSTOM_PROPS);
        });
    }

    function preparePastedElement(object) {
        if (!object) return;
        if (object.type === 'activeSelection' && typeof object.getObjects === 'function') {
            object.getObjects().forEach(preparePastedElement);
            return;
        }
        if (object.labType === 'text') {
            object.set({
                isTemplateText: false,
                fieldId: uniqueFieldId('free'),
                fieldLabel: (object.fieldLabel || '自由文字') + ' 副本'
            });
            styleInteractiveObject(object);
            return;
        }
        if (object.labType === 'freeImage' || object.labType === 'imageComposition') {
            object.set({
                elementId: uniqueElementId('image'),
                cutoutTaskId: '',
                cutoutTaskStatus: '',
                cutoutTaskError: ''
            });
            styleFreeVisual(object);
            return;
        }
        if (object.labType === 'imagePlaceholder') {
            object.set({ elementId: uniqueElementId('slot') });
            styleInteractiveObject(object);
        }
    }

    function pasteCopiedElements() {
        if (!state.clipboard && state.clipboardReady) {
            state.clipboardReady.then(function (copy) { if (copy) pasteCopiedElements(); });
            return;
        }
        if (!state.canvas || !state.clipboard) {
            showToast('请先选中画布中的图片或文字并按 Ctrl + C', true);
            return;
        }
        state.clipboard.clone(function (copy) {
            state.clipboardPasteCount += 1;
            var offset = 28 * state.clipboardPasteCount;
            state.canvas.discardActiveObject();
            preparePastedElement(copy);
            if (copy.type === 'activeSelection' && typeof copy.forEachObject === 'function') {
                copy.canvas = state.canvas;
                copy.forEachObject(function (object) { state.canvas.add(object); });
            } else {
                state.canvas.add(copy);
            }
            copy.set({
                left: (Number(copy.left) || 0) + offset,
                top: (Number(copy.top) || 0) + offset
            });
            copy.setCoords();
            state.canvas.setActiveObject(copy);
            state.canvas.requestRenderAll();
            queueMutation(true);
            renderProperties();
            positionCutoutFab();
            showToast('已粘贴画布元素');
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

    function getCanvasBackground() {
        if (!state.canvas) return null;
        return state.canvas.getObjects().find(function (object) { return object.labType === 'canvasBackground'; }) || null;
    }

    function styleCanvasBackground(image) {
        if (!image) return;
        image.set({
            selectable: true,
            evented: true,
            hasControls: false,
            hasBorders: true,
            lockMovementX: true,
            lockMovementY: true,
            lockScalingX: true,
            lockScalingY: true,
            lockRotation: true,
            objectCaching: false,
            excludeFromExport: false,
            hoverCursor: 'grab',
            borderColor: '#2563eb',
            borderScaleFactor: 2
        });
    }

    function configureCanvasBackground(image, zoom, cropX, cropY) {
        configureBackgroundImage(image, state.logicalWidth, state.logicalHeight, zoom, cropX, cropY);
        image.set({
            left: state.logicalWidth / 2,
            top: state.logicalHeight / 2,
            originX: 'center',
            originY: 'center',
            labType: 'canvasBackground'
        });
        styleCanvasBackground(image);
        image.setCoords();
    }

    function backgroundCropRatio(image) {
        if (!image) return { x: .5, y: .5 };
        var sourceWidth = Number(image._element && (image._element.naturalWidth || image._element.width)) || Number(image.sourceImageWidth) || 1;
        var sourceHeight = Number(image._element && (image._element.naturalHeight || image._element.height)) || Number(image.sourceImageHeight) || 1;
        var maxX = Math.max(0, sourceWidth - (Number(image.width) || sourceWidth));
        var maxY = Math.max(0, sourceHeight - (Number(image.height) || sourceHeight));
        return {
            x: maxX > 0 ? Math.max(0, Math.min(1, (Number(image.cropX) || 0) / maxX)) : .5,
            y: maxY > 0 ? Math.max(0, Math.min(1, (Number(image.cropY) || 0) / maxY)) : .5
        };
    }

    function canvasBackgroundCropAtRatio(image, zoom, ratio) {
        var sourceWidth = Number(image._element && (image._element.naturalWidth || image._element.width)) || Number(image.width) || 1;
        var sourceHeight = Number(image._element && (image._element.naturalHeight || image._element.height)) || Number(image.height) || 1;
        var safeZoom = Math.max(1, Math.min(3, Number(zoom) || 1));
        var cover = Math.max(state.logicalWidth / sourceWidth, state.logicalHeight / sourceHeight);
        var maxX = Math.max(0, sourceWidth - state.logicalWidth / (cover * safeZoom));
        var maxY = Math.max(0, sourceHeight - state.logicalHeight / (cover * safeZoom));
        return { x: maxX * ratio.x, y: maxY * ratio.y };
    }

    function ensureCanvasBackgroundAtBack() {
        var background = getCanvasBackground();
        if (background && state.canvas.getObjects().indexOf(background) !== 0) background.moveTo(0);
    }

    async function applyCanvasBackgroundAsset(asset, options) {
        if (!asset || !asset.url || !state.canvas) return null;
        var existing = getCanvasBackground();
        var requested = options || {};
        var opacity = existing && existing.opacity != null
            ? existing.opacity
            : (requested.opacity == null ? 1 : Number(requested.opacity));
        var zoom = existing ? (Number(existing.backgroundZoom) || 1) : (Number(requested.zoom) || 1);
        var cropRatio = existing ? backgroundCropRatio(existing) : { x: .5, y: .5 };
        try {
            var image = await loadFabricImage(asset.url);
            var crop = canvasBackgroundCropAtRatio(image, zoom, cropRatio);
            configureCanvasBackground(image, zoom, crop.x, crop.y);
            image.set({
                elementId: 'canvas-background',
                assetUrl: asset.url,
                assetName: asset.name || '画布背景',
                originalAssetUrl: asset.url,
                originalAssetName: asset.name || '画布背景',
                backgroundZoom: zoom,
                backgroundCropX: image.cropX,
                backgroundCropY: image.cropY,
                sourceImageWidth: Number(image._element && (image._element.naturalWidth || image._element.width)) || image.width,
                sourceImageHeight: Number(image._element && (image._element.naturalHeight || image._element.height)) || image.height,
                opacity: Math.max(0, Math.min(1, opacity))
            });
            if (existing) state.canvas.remove(existing);
            state.canvas.insertAt(image, 0, false);
            ensureCanvasBackgroundAtBack();
            state.backgroundMode = 'image';
            state.canvas.backgroundColor = '';
            if (requested.select !== false) state.canvas.setActiveObject(image);
            clearBackgroundPick();
            state.canvas.requestRenderAll();
            if (!requested.silent) queueMutation(true);
            renderProperties();
            if (!requested.silent) showToast(existing ? '画布背景已更换' : '画布背景已添加');
            return image;
        } catch (error) {
            showToast(error && error.message ? error.message : '画布背景读取失败', true);
            return null;
        }
    }

    async function rebuildCanvasBackground(def) {
        if (!def || !def.url) return null;
        try {
            var image = await loadFabricImage(def.url);
            var zoom = Number(def.zoom) || 1;
            var cropX = def.cropX == null ? null : Number(def.cropX);
            var cropY = def.cropY == null ? null : Number(def.cropY);
            configureCanvasBackground(image, zoom, cropX, cropY);
            image.set({
                elementId: 'canvas-background',
                assetUrl: def.url,
                assetName: def.name || '画布背景',
                originalAssetUrl: def.url,
                originalAssetName: def.name || '画布背景',
                backgroundZoom: zoom,
                backgroundCropX: image.cropX,
                backgroundCropY: image.cropY,
                sourceImageWidth: Number(image._element && (image._element.naturalWidth || image._element.width)) || image.width,
                sourceImageHeight: Number(image._element && (image._element.naturalHeight || image._element.height)) || image.height,
                opacity: Math.max(0, Math.min(1, def.opacity == null ? 1 : Number(def.opacity)))
            });
            state.canvas.insertAt(image, 0, false);
            ensureCanvasBackgroundAtBack();
            state.canvas.requestRenderAll();
            return image;
        } catch (error) {
            showToast(error && error.message ? error.message : '画布背景读取失败', true);
            return null;
        }
    }

    function removeCanvasBackground() {
        var removed = detachCanvasBackground();
        if (!removed && state.backgroundMode === 'solid') return;
        clearBackgroundPick();
        state.backgroundMode = 'solid';
        applySolidBackground(false);
        state.canvas.requestRenderAll();
        queueMutation(true);
        renderProperties();
        showToast('画布背景已移除');
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



    function setBackgroundOpacity(value, commit) {
        var background = getCanvasBackground();
        if (!background) return;
        var opacity = Math.max(0, Math.min(1, Number(value) / 100));
        background.set('opacity', opacity);
        state.canvas.requestRenderAll();
        if (commit) queueMutation(true);
    }

    function setBackgroundZoom(value, commit) {
        var background = getCanvasBackground();
        if (!background) return;
        var zoom = Math.max(1, Math.min(3, Number(value) / 100));
        var ratio = backgroundCropRatio(background);
        var crop = canvasBackgroundCropAtRatio(background, zoom, ratio);
        configureCanvasBackground(background, zoom, crop.x, crop.y);
        state.canvas.requestRenderAll();
        if (commit) queueMutation(true);
    }

    function startBackgroundEditing() {
        var background = getCanvasBackground();
        if (!background) return;
        state.canvas.setActiveObject(background);
        if (state.backgroundEdit && state.backgroundEdit.elementId === 'canvas-background') {
            renderProperties();
            return;
        }
        stopBackgroundEditing();
        state.backgroundEdit = { elementId: 'canvas-background', dragging: false };
        background.set({ lockMovementX: true, lockMovementY: true, hoverCursor: 'grab' });
        els.editBackgroundPosition.setAttribute('aria-pressed', 'true');
        els.editBackgroundPosition.textContent = '完成背景位置调整';
        els.stageTip.textContent = '拖动背景调整位置，使用右侧滑杆缩放；其他图片和文字保持不变';
        els.stageTip.hidden = false;
        state.canvas.requestRenderAll();
    }

    function stopBackgroundEditing() {
        if (!state.backgroundEdit) return;
        var background = getCanvasBackground();
        if (background) background.set({ lockMovementX: true, lockMovementY: true, hoverCursor: 'grab' });
        state.backgroundEdit = null;
        if (els.editBackgroundPosition) {
            els.editBackgroundPosition.setAttribute('aria-pressed', 'false');
            els.editBackgroundPosition.textContent = '开始调整背景';
        }
        if (els.stageTip) els.stageTip.hidden = true;
        if (state.canvas) state.canvas.requestRenderAll();
    }

    function canvasPointAtClientPoint(event) {
        var rect = state.canvas.upperCanvasEl.getBoundingClientRect();
        var zoom = Math.max(state.canvas.getZoom(), .01);
        var x = (event.clientX - rect.left) / zoom;
        var y = (event.clientY - rect.top) / zoom;
        return new fabric.Point(
            Math.max(0, Math.min(state.logicalWidth, x)),
            Math.max(0, Math.min(state.logicalHeight, y))
        );
    }

    function groupLocalPointer(group, nativeEvent) {
        var pointer = state.canvas.getPointer(nativeEvent);
        return fabric.util.transformPoint(pointer, fabric.util.invertTransform(group.calcTransformMatrix()));
    }

    function beginBackgroundDrag(event) {
        if (!state.backgroundEdit || !event.target || event.target.labType !== 'canvasBackground') return false;
        var background = getCanvasBackground();
        if (!background) return false;
        var pointer = state.canvas.getPointer(event.e);
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
        var background = getCanvasBackground();
        if (!background) return;
        var pointer = state.canvas.getPointer(event.e);
        var dx = pointer.x - state.backgroundEdit.startX;
        var dy = pointer.y - state.backgroundEdit.startY;
        if (Math.abs(dx) + Math.abs(dy) > 1) state.backgroundEdit.moved = true;
        var sourceScale = Math.max(Number(background.scaleX) || 1, .0001);
        configureCanvasBackground(background,
            background.backgroundZoom || 1,
            state.backgroundEdit.cropX - dx / sourceScale,
            state.backgroundEdit.cropY - dy / sourceScale);
        background.set({
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
        var background = getCanvasBackground();
        if (background) background.set('hoverCursor', 'grab');
        if (moved) queueMutation(true);
        return true;
    }

    function selectedFrameId() {
        var active = state.canvas && state.canvas.getActiveObject();
        return active && (active.labType === 'frameImage' || active.labType === 'framePlaceholder' || active.labType === 'frameBorder')
            ? active.frameId
            : null;
    }

    function selectedFrameImage() {
        var active = state.canvas && state.canvas.getActiveObject();
        if (!active || !active.frameId) return null;
        return active.labType === 'frameImage' ? active : getFrameImage(active.frameId);
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
        if (typeof bounds === 'string') {
            try { bounds = JSON.parse(bounds); } catch (ignore) { return; }
        }
        var centerX = bounds.centerX == null ? bounds.x + bounds.width / 2 : Number(bounds.centerX);
        var centerY = bounds.centerY == null ? bounds.y + bounds.height / 2 : Number(bounds.centerY);
        image.set({
            left: centerX,
            top: centerY,
            scaleX: image.coverScale,
            scaleY: image.coverScale,
            angle: Number(bounds.rotation) || 0,
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
            : '任务已提交；撤销画布不会取消任务，完成后会自动保存并替换内容';
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

        var frames = effectiveFrames();
        var suggestedFrame = suggestedFrameForVisual(visual);
        var rememberedFrameId = state.frameTargetSelections[visual.elementId];
        var targetFrameId = suggestedFrame
            ? suggestedFrame.id
            : (frames.some(function (frame) { return frame.id === rememberedFrameId; }) ? rememberedFrameId : (frames[0] && frames[0].id));
        if (visual.elementId && targetFrameId) state.frameTargetSelections[visual.elementId] = targetFrameId;
        els.freeImageFrameTarget.innerHTML = frames.map(function (frame) {
            var suffix = suggestedFrame && suggestedFrame.id === frame.id ? '（图片所在）' : '';
            return '<option value="' + escapeHtml(frame.id) + '">' + escapeHtml((frame.label || frameLabel(frame.id)) + suffix) + '</option>';
        }).join('');
        if (targetFrameId) els.freeImageFrameTarget.value = targetFrameId;
        if (els.freeImageFrameHint) {
            els.freeImageFrameHint.textContent = suggestedFrame
                ? '已根据图片当前位置自动选择“' + (suggestedFrame.label || frameLabel(suggestedFrame.id)) + '”'
                : (frames.length ? '也可以手动选择目标相框' : '当前画布没有可用相框');
        }
        els.placeImageInFrame.disabled = !frames.length || state.frameFillBusy;
        els.placeImageInFrame.textContent = state.frameFillBusy ? '正在填入...' : '填入所选相框';
        positionCutoutFab();
    }

    function renderBackgroundProperties() {
        var background = getCanvasBackground();
        if (background) {
            var opacity = Math.round((background.opacity == null ? 1 : background.opacity) * 100);
            var zoom = Math.round((background.backgroundZoom || 1) * 100);
            els.backgroundOpacity.value = opacity;
            els.backgroundOpacityValue.value = opacity + '%';
            els.backgroundOpacityValue.textContent = opacity + '%';
            els.backgroundZoom.value = zoom;
            els.backgroundZoomValue.value = zoom + '%';
            els.backgroundZoomValue.textContent = zoom + '%';
            els.backgroundName.textContent = state.backgroundMode === 'image'
                ? (background.backgroundAssetName || background.assetName || '画布背景')
                : '画布背景';
            els.backgroundHelp.textContent = '点击背景即可选中并直接拖动位置；使用缩放滑杆放大或缩小。背景始终位于所有内容下方。';
        }
        renderBackgroundModeControls();
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
        els.propsBackground.hidden = true;
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
            renderBackgroundModeControls();
            renderLayerPanel();
            return;
        }
        if (active.labType === 'framePlaceholder' || active.labType === 'frameImage' || active.labType === 'frameBorder') {
            els.propsFrame.hidden = false;
            var frameImage = getFrameImage(active.frameId);
            els.selectionKind.textContent = state.frameEditMode ? '框内图片裁剪' : (frameImage ? '相框' : '空相框');
            els.frameName.textContent = active.frameName || frameLabel(active.frameId);
            var image = active.labType === 'frameImage' ? active : frameImage;
            els.cropControls.hidden = !image;
            els.frameToFreeImage.hidden = !image;
            els.frameHelp.textContent = state.frameEditMode
                ? '当前正在编辑框内图片。拖动画面调整裁剪位置，点击深色区域退出。'
                : (image
                    ? '拖动相框移动，拖动控制点缩放或旋转；双击相框进入图片裁剪。'
                    : '拖动相框移动，拖动控制点缩放或旋转；双击后从左侧填入图片。');
            if (image) {
                var zoomValue = Math.round((image.cropZoom || 1) * 100);
                els.imageZoom.value = zoomValue;
                els.imageZoomValue.value = zoomValue + '%';
                els.imageZoomValue.textContent = zoomValue + '%';
            }
            renderLayerPanel();
            return;
        }
        if (active.labType === 'canvasBackground') {
            els.propsBackground.hidden = false;
            els.selectionKind.textContent = '画布背景';
            renderBackgroundProperties();
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

    function showAlignmentGuides(object) {
        if (!object || object.labType === 'guide' || object.labType === 'frameEditOverlay') return;
        removeGuides();
        var zoom = Math.max(state.canvas.getZoom(), 0.25);
        var threshold = 8 / zoom;
        var rect = object.getBoundingRect(true, true);
        var mL = rect.left, mR = rect.left + rect.width, mCX = (mL + mR) / 2;
        var mT = rect.top, mB = rect.top + rect.height, mCY = (mT + mB) / 2;
        var W = state.logicalWidth, H = state.logicalHeight;
        var candX = [0, W / 2, W];
        var candY = [0, H / 2, H];
        state.canvas.getObjects().forEach(function (o) {
            if (o === object || o.labType === 'guide' || o.labType === 'frameEditOverlay') return;
            var r = o.getBoundingRect(true, true);
            candX.push(r.left, r.left + r.width / 2, r.left + r.width);
            candY.push(r.top, r.top + r.height / 2, r.top + r.height);
        });
        var bestX = null, bestXAbs = Infinity, snapX = null;
        candX.forEach(function (tx) {
            [[mL, tx], [mCX, tx], [mR, tx]].forEach(function (p) {
                var d = p[1] - p[0];
                if (Math.abs(d) <= threshold && Math.abs(d) < bestXAbs) { bestXAbs = Math.abs(d); bestX = d; snapX = tx; }
            });
        });
        var bestY = null, bestYAbs = Infinity, snapY = null;
        candY.forEach(function (ty) {
            [[mT, ty], [mCY, ty], [mB, ty]].forEach(function (p) {
                var d = p[1] - p[0];
                if (Math.abs(d) <= threshold && Math.abs(d) < bestYAbs) { bestYAbs = Math.abs(d); bestY = d; snapY = ty; }
            });
        });
        if (bestX !== null) {
            object.left += bestX;
            addGuide(snapX, 0, snapX, H);
        }
        if (bestY !== null) {
            object.top += bestY;
            addGuide(0, snapY, W, snapY);
        }
        if (bestX !== null || bestY !== null) object.setCoords();
    }

    function assetKey(asset) {
        return asset ? String(asset.url || asset.thumbnailUrl || '') : '';
    }

    // 汇总画布与模板定义里对素材的引用，用于提示"哪些素材还在用、不能删"。
    function assetUsageLabels() {
        var used = {};
        function mark(url, label) {
            var key = String(url || '');
            if (!key) return;
            if (!used[key]) used[key] = [];
            if (used[key].indexOf(label) < 0) used[key].push(label);
        }
        function markSerializedObject(object, label) {
            if (!object || typeof object !== 'object') return;
            mark(object.assetUrl, label);
            mark(object.originalAssetUrl, label);
            mark(object.cutoutUrl, label + '·抠图');
            mark(object.cutoutSourceUrl, label + '·原图');
            mark(object.backgroundAssetUrl, label + '·背景');
            if (Array.isArray(object.objects)) {
                object.objects.forEach(function (child) { markSerializedObject(child, label); });
            }
        }
        if (state.canvas) {
            state.canvas.getObjects().forEach(function (object) {
                if (!object || object.labType === 'guide' || object.labType === 'frameEditOverlay') return;
                var label = object.labType === 'canvasBackground' ? '画布背景'
                    : object.labType === 'frameImage' ? '相框图片'
                        : object.labType === 'imagePlaceholder' ? '相框背景层'
                            : object.labType === 'imageComposition' ? '图片组合'
                                : object.labType === 'freeImage' ? '自由图片' : '画布元素';
                mark(object.assetUrl, label);
                mark(object.originalAssetUrl, label);
                mark(object.cutoutUrl, label + '·抠图');
                mark(object.cutoutSourceUrl, label + '·原图');
                mark(object.backgroundAssetUrl, label + '·背景');
            });
        }
        ((state.template && state.template.images) || []).forEach(function (definition) {
            mark(definition.url, '模板固定素材');
            mark(definition.originalAssetUrl, '模板固定素材');
            mark(definition.backgroundAssetUrl, '模板固定素材背景');
        });
        state.history.forEach(function (snapshot) {
            try {
                var parsed = parsedHistorySnapshot(snapshot);
                var objects = parsed.fabric && Array.isArray(parsed.fabric.objects) ? parsed.fabric.objects : [];
                objects.forEach(function (object) { markSerializedObject(object, '撤销记录'); });
            } catch (ignore) {
                // 损坏的旧撤销记录不应阻断素材面板。
            }
        });
        return used;
    }

    function syncAssetToolbar() {
        var hasAssets = state.assets.length > 0;
        if (!els.assetToolbar || !els.assetSelectToggle) return;
        els.assetToolbar.hidden = !hasAssets;
        if (!hasAssets) return;
        var selectMode = Boolean(state.assetSelectMode);
        els.assetSelectToggle.setAttribute('aria-pressed', selectMode ? 'true' : 'false');
        els.assetSelectToggle.textContent = selectMode ? '完成选择' : '批量选择';
        els.assetBulk.hidden = !selectMode;
        if (!selectMode) return;
        var total = state.assets.length;
        var chosen = state.assetSelected.length;
        els.assetSelectAll.checked = chosen > 0 && chosen === total;
        els.assetSelectAll.indeterminate = chosen > 0 && chosen < total;
        els.assetSelectedCount.textContent = '已选 ' + chosen + ' / ' + total + ' 张';
        els.assetRemoveSelected.disabled = chosen === 0 || state.assetRemoving;
    }

    function renderAssets() {
        if (!state.assets.length) {
            state.assetSelectMode = false;
            state.assetSelected = [];
        } else if (state.assetSelectMode) {
            state.assetSelected = state.assetSelected.filter(function (index) {
                return index >= 0 && index < state.assets.length;
            });
        }
        syncAssetToolbar();
        if (!state.assets.length) {
            els.assetGrid.classList.remove('is-selecting');
            els.assetGrid.innerHTML = '<div class="editor-asset-empty">尚未上传素材。上传后，素材会随项目保存并可重复使用。</div>';
            return;
        }
        var selectMode = Boolean(state.assetSelectMode);
        var usage = selectMode ? assetUsageLabels() : {};
        els.assetGrid.classList.toggle('is-selecting', selectMode);
        els.assetGrid.innerHTML = state.assets.map(function (asset, index) {
            var checked = state.assetSelected.indexOf(index) >= 0;
            var reasons = usage[assetKey(asset)];
            var inUse = selectMode && Boolean(reasons && reasons.length);
            var name = escapeHtml(asset.name || '素材');
            var aria = selectMode
                ? ' aria-pressed="' + (checked ? 'true' : 'false') + '" aria-label="选择素材 ' + name + '"'
                : ' aria-label="使用图片 ' + name + '"';
            return '<button class="editor-asset-card' + (checked ? ' is-checked' : '') + (inUse ? ' is-in-use' : '')
                + '" type="button" draggable="' + (selectMode ? 'false' : 'true') + '" data-asset-index="' + index + '"' + aria + '>'
                + (selectMode ? '<span class="editor-asset-card__check" aria-hidden="true"></span>' : '')
                + '<img src="' + escapeHtml(asset.thumbnailUrl || asset.url)
                + '" alt="" loading="lazy" decoding="async"><span>'
                + name + '</span></button>';
        }).join('');
    }

    function setAssetSelectMode(next) {
        var target = Boolean(next);
        if (state.assetSelectMode === target) return;
        state.assetSelectMode = target;
        if (!target) state.assetSelected = [];
        renderAssets();
    }

    function toggleAssetSelection(index) {
        if (!Number.isFinite(index)) return;
        var at = state.assetSelected.indexOf(index);
        if (at >= 0) state.assetSelected.splice(at, 1);
        else state.assetSelected.push(index);
        renderAssets();
    }

    function invertAssetSelection() {
        state.assetSelected = state.assets.map(function (asset, index) { return index; })
            .filter(function (index) { return state.assetSelected.indexOf(index) < 0; });
        renderAssets();
    }

    async function removeSelectedAssets() {
        if (state.assetRemoving || !state.assetSelected.length) return;
        var usage = assetUsageLabels();
        var blocked = [];
        var removable = [];
        state.assetSelected.slice().sort(function (a, b) { return a - b; }).forEach(function (index) {
            var asset = state.assets[index];
            if (!asset) return;
            var reasons = usage[assetKey(asset)];
            if (reasons && reasons.length) blocked.push({ asset: asset, reasons: reasons });
            else removable.push(asset);
        });
        if (!removable.length) {
            showToast('选中的 ' + blocked.length + ' 张素材仍被画布、模板或撤销记录引用，已全部跳过', true);
            return;
        }
        var lines = ['确定移除选中的 ' + removable.length + ' 张素材？',
            '服务器上的图片文件会一并删除，此操作不可撤销。'];
        if (blocked.length) {
            lines.push('', '以下 ' + blocked.length + ' 张正在使用中，将跳过：');
            blocked.slice(0, 4).forEach(function (item) {
                lines.push('· ' + (item.asset.name || '素材') + '（' + item.reasons.join('、') + '）');
            });
            if (blocked.length > 4) lines.push('· 其余 ' + (blocked.length - 4) + ' 张…');
        }
        if (!window.confirm(lines.join('\n'))) return;

        state.assetRemoving = true;
        syncAssetToolbar();
        var payload = { urls: [], thumbnailUrls: [] };
        removable.forEach(function (asset) {
            if (asset.url) payload.urls.push(asset.url);
            if (asset.thumbnailUrl && asset.thumbnailUrl !== asset.url) payload.thumbnailUrls.push(asset.thumbnailUrl);
        });
        var removedKeys = removable.map(assetKey);
        var report = {};
        try {
            var response = await axios.delete('/api/template-lab/projects/' + state.project.id + '/assets', { data: payload });
            report = apiData(response) || {};
        } catch (error) {
            state.assetRemoving = false;
            syncAssetToolbar();
            showToast(error.response && error.response.data ? error.response.data.message : '素材文件删除失败，请稍后重试', true);
            return;
        }
        state.assets = state.assets.filter(function (asset) { return removedKeys.indexOf(assetKey(asset)) < 0; });
        state.assetSelected = [];
        state.assetRemoving = false;
        state.dirty = true;
        renderAssets();
        saveNow();
        var kept = Array.isArray(report.skippedUrls) ? report.skippedUrls.length : 0;
        var message = '已移除 ' + removable.length + ' 张素材';
        if (blocked.length) message += '，跳过使用中的 ' + blocked.length + ' 张';
        if (kept) message += '（' + kept + ' 个非本项目文件已保留）';
        showToast(message, false);
    }

    function findTextByFieldId(fieldId) {
        return state.canvas && state.canvas.getObjects().find(function (object) {
            return object.labType === 'text' && String(object.fieldId) === String(fieldId);
        });
    }

    function renderContentPanel() {
        if (!state.canvas || !state.template || !els.contentList) return;
        var frames = effectiveFrames();
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
        var canvasBackground = getCanvasBackground();
        var backgroundRow = canvasBackground
            ? '<section class="editor-layer-item editor-layer-item--background' + (!canvasBackground.visible ? ' is-hidden' : '')
                + (activeVisual === canvasBackground ? ' is-selected' : '')
                + '" data-layer-background="true"><div class="editor-layer-item__head"><strong>'
                + escapeHtml(canvasBackground.backgroundAssetName || canvasBackground.assetName || '画布背景')
                + '</strong><span class="editor-content-status is-ready">最底层</span></div>'
                + '<div class="editor-layer-item__meta">整个画布背景' + (canvasBackground.visible === false ? ' · 已隐藏' : '') + '</div>'
                + '<div class="editor-layer-actions"><button type="button" data-layer-action="select-background">选择</button>'
                + '<button type="button" data-layer-action="visibility">' + (canvasBackground.visible ? '隐藏' : '显示')
                + '</button><button type="button" data-layer-action="change-background">更换</button></div></section>'
            : '';
        var images = state.canvas.getObjects().filter(function (object) {
            return object.labType === 'freeImage' || object.labType === 'imageComposition' || object.labType === 'imagePlaceholder';
        }).reverse();
        var imageRows = images.map(function (object, index) {
            var selected = activeVisual === object;
            var hasBackground = object.labType === 'imageComposition';
            var typeLabel = hasBackground ? '主体 + 背景' : (object.labType === 'imagePlaceholder' ? '图片占位' : '自由图片');
            var roleLabel = object.templateRole === 'fixed' ? '固定素材' : '可替换';
            var taskLabel = String(object.cutoutTaskStatus || '').toLowerCase() === 'processing' ? ' · 抠图中' : '';
            var backgroundLabel = hasBackground && object.backgroundAssetName
                ? ' · 背景：' + escapeHtml(object.backgroundAssetName)
                : '';
            return '<section class="editor-layer-item' + (!object.visible ? ' is-hidden' : '') + (selected ? ' is-selected' : '')
                + '" data-layer-image="' + escapeHtml(object.elementId) + '"><div class="editor-layer-item__head"><strong>'
                + escapeHtml(object.originalAssetName || object.assetName || ('图片 ' + (index + 1))) + '</strong><span class="editor-content-status is-ready">'
                + typeLabel + '</span></div><div class="editor-layer-item__meta">' + roleLabel + taskLabel
                + backgroundLabel + (object.lockedByUser ? ' · 已锁定' : '') + (object.visible === false ? ' · 已隐藏' : '') + '</div>'
                + '<div class="editor-layer-actions">'
                + '<button type="button" data-layer-action="select-image">选择</button>'
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
        var frameRows = effectiveFrames().map(function (frame) {
            return '<section class="editor-layer-item" data-layer-frame="' + escapeHtml(frame.id) + '"><div class="editor-layer-item__head"><strong>'
                + escapeHtml(frame.label || frameLabel(frame.id)) + '</strong><span class="editor-content-status' + (getFrameImage(frame.id) ? ' is-ready' : '') + '">相框</span></div>'
                + '<div class="editor-layer-actions"><button type="button" data-layer-action="select-frame">定位</button></div></section>';
        }).join('');
        els.layerList.innerHTML = imageRows + textRows + frameRows + backgroundRow;
    }

    function selectFrame(frameId) {
        var object = getFrameController(frameId);
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
        var bgObject = getCanvasBackground();
        var backgroundMasks = state.canvas.getObjects().filter(function (object) {
            return object.labType === 'backgroundMask' && object.visible !== false;
        }).map(function (object, index) {
            return {
                id: object.maskId || ('background-mask-' + (index + 1)),
                x: Math.round(object.left || 0),
                y: Math.round(object.top || 0),
                width: Math.round((object.width || 1) * (object.scaleX || 1)),
                height: Math.round((object.height || 1) * (object.scaleY || 1)),
                fill: normalizeColor(object.fill),
                radius: Math.round((object.rx || 0) * (object.scaleX || 1)),
                angle: object.angle || 0
            };
        });
        var decorShapes = state.canvas.getObjects().filter(function (object) {
            return object.labType === 'decorShape' && object.visible !== false;
        }).map(function (object, index) {
            var scaleX = object.scaleX || 1;
            var scaleY = object.scaleY || 1;
            return {
                id: object.shapeId || ('decor-shape-' + (index + 1)),
                type: object.type === 'ellipse' ? 'ellipse' : ((object.rx || 0) > 0 ? 'rounded' : 'rect'),
                layer: object.shapeLayer === 'front' ? 'front' : 'back',
                x: Math.round(object.left || 0),
                y: Math.round(object.top || 0),
                width: Math.round((object.width || ((object.rx || 0) * 2) || 1) * scaleX),
                height: Math.round((object.height || ((object.ry || 0) * 2) || 1) * scaleY),
                radius: Math.round((object.rx || 0) * scaleX),
                angle: object.angle || 0,
                fill: normalizeColor(object.fill),
                opacity: object.opacity == null ? 1 : object.opacity
            };
        });
        var canvasBackground = null;
        if (bgObject) {
            canvasBackground = {
                url: bgObject.backgroundAssetUrl || '',
                name: bgObject.backgroundAssetName || '画布背景',
                opacity: bgObject.opacity == null ? 1 : bgObject.opacity,
                zoom: bgObject.backgroundZoom || 1,
                cropX: bgObject.backgroundCropX == null ? null : bgObject.backgroundCropX,
                cropY: bgObject.backgroundCropY == null ? null : bgObject.backgroundCropY
            };
        }
        return {
            schemaVersion: 1,
            usageType: state.template.usageType,
            ratioGroup: state.template.ratioGroup,
            width: state.logicalWidth,
            height: state.logicalHeight,
            background: isTransparentSolid() && state.backgroundMode === 'solid'
                ? 'transparent'
                : (state.solidColor || '#f5f7fa'),
            backgroundMode: state.backgroundMode,
            backgroundOpacity: Math.max(0, Math.min(100, Number(state.solidOpacity))) / 100,
            canvasBackground: canvasBackground,
            backgroundMasks: backgroundMasks,
            shapes: decorShapes,
            accent: state.template.accent || '#172033',
            tags: Array.from(new Set([state.template.usageType, state.template.ratioGroup, '个人模板'])),
            frames: JSON.parse(JSON.stringify(effectiveFrames())),
            texts: texts,
            images: images
        };
    }

    function openSaveTemplateDialog() {
        els.saveTemplateName.value = (els.projectName.value.trim() || state.project.projectName) + ' 模板';
        els.saveTemplateCategory.value = state.template.category || '个人模板';
        els.saveTemplateDescription.value = '复用当前相框、自由图片和文字布局。';
        els.saveTemplateSpec.textContent = state.template.usageType + ' · ' + state.template.ratioGroup + ' · '
            + state.logicalWidth + ' × ' + state.logicalHeight + ' · ' + effectiveFrames().length + ' 个相框 · '
            + state.canvas.getObjects().filter(function (object) { return object.labType === 'freeImage' || object.labType === 'imageComposition'; }).length + ' 个自由图片';
        els.saveTemplateDialog.showModal();
        setTimeout(function () { els.saveTemplateName.focus(); els.saveTemplateName.select(); }, 0);
    }

    async function savePersonalTemplate() {
        var name = els.saveTemplateName.value.trim();
        if (!name) return showToast('请输入模板名称', true);
        var ratioW = state.logicalWidth, ratioH = state.logicalHeight, ratioGroup = state.template.ratioGroup;
        var ratioOk = (ratioGroup === '1:1' && ratioW === ratioH)
            || (ratioGroup === '3:4' && ratioW * 4 === ratioH * 3)
            || (ratioGroup === '16:9' && ratioW * 9 === ratioH * 16)
            || (ratioGroup === '2928:1200' && ratioW === 2928 && ratioH === 1200)
            || (ratioGroup === '1200:900' && ratioW === 1200 && ratioH === 900);
        if (!ratioOk) return showToast('当前画布尺寸不符合模板比例 ' + ratioGroup + '，无法保存为模板', true);
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

    function clearBackgroundPick() {
        state.assetPickMode = '';
        state.backgroundTargetId = '';
        state.backgroundPickPreviousMode = '';
        renderBackgroundPickState();
    }

    function renderBackgroundPickState() {
        if (!els.backgroundPickBanner) return;
        var active = state.assetPickMode === 'canvasBackground';
        els.backgroundPickBanner.hidden = !active;
        if (els.backgroundPickTarget) {
            els.backgroundPickTarget.textContent = getCanvasBackground() ? '选择后将替换当前画布背景' : '选择或上传一张图片作为整个画布背景';
        }
    }

    function cancelCanvasBackgroundPick(notify) {
        var previousMode = state.backgroundPickPreviousMode;
        state.assetPickMode = '';
        state.backgroundTargetId = '';
        state.backgroundPickPreviousMode = '';
        if (!getCanvasBackground() && previousMode === 'solid') {
            state.backgroundMode = 'solid';
            applySolidBackground(false);
        }
        renderBackgroundPickState();
        renderBackgroundModeControls();
        renderProperties();
        if (notify !== false) showToast('已取消选择画布背景');
    }

    function openCanvasBackgroundPicker() {
        if (state.assetPickMode !== 'canvasBackground' && !state.backgroundPickPreviousMode) {
            state.backgroundPickPreviousMode = state.backgroundMode;
        }
        state.assetPickMode = 'canvasBackground';
        state.backgroundTargetId = '';
        var assetsTab = document.querySelector('[data-panel-tab="assets"]');
        if (assetsTab) assetsTab.click();
        renderBackgroundPickState();
        showToast('请从素材库选择或上传一张画布背景图');
    }

    async function useAsset(asset, point, targetFrameId) {
        if (!asset) return;
        if (state.assetPickMode === 'canvasBackground') {
            await applyCanvasBackgroundAsset(asset);
            return;
        }
        if (targetFrameId) {
            await assignImageToFrame(targetFrameId, asset);
            return;
        }
        if (selectedImagePlaceholder()) {
            fillImagePlaceholder(asset);
            return;
        }
        await addFreeImage(asset, point || { x: state.logicalWidth / 2, y: state.logicalHeight / 2 });
    }

    function hasCanvasDropPayload(event) {
        var transfer = event && event.dataTransfer;
        if (!transfer) return false;
        var types = Array.from(transfer.types || []);
        return types.includes('Files') || types.includes('application/x-template-lab-asset');
    }

    function hasFileDropPayload(event) {
        var transfer = event && event.dataTransfer;
        return Boolean(transfer) && Array.from(transfer.types || []).includes('Files');
    }

    function supportedDroppedImageFiles(files) {
        var allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
        var list = Array.from(files || []);
        var supported = list.filter(function (file) {
            return allowedTypes.includes(String(file.type || '').toLowerCase())
                || /\.(jpe?g|png|webp)$/i.test(file.name || '');
        });
        if (supported.length !== list.length) {
            showToast('已忽略不支持的文件，仅支持 JPG、PNG、WebP 图片', true);
        }
        return supported;
    }

    function setCanvasDropState(mode) {
        if (!els.dropIndicator || !els.stage) return;
        if (!mode) {
            els.stage.classList.remove('is-drop-active');
            els.dropIndicator.hidden = true;
            els.dropIndicator.removeAttribute('data-state');
            return;
        }
        els.stage.classList.add('is-drop-active');
        els.dropIndicator.hidden = false;
        els.dropIndicator.dataset.state = mode;
        if (mode === 'uploading') {
            els.dropIndicatorTitle.textContent = '正在上传并添加图片...';
            els.dropIndicatorHint.textContent = '完成后会自动放到刚才的拖放位置';
        } else {
            els.dropIndicatorTitle.textContent = '松开即可添加图片';
            els.dropIndicatorHint.textContent = '拖到相框内自动填充，拖到空白处作为自由图片';
        }
    }

    async function uploadFiles(files, targetFrameId, placementPoint, purpose) {
        var list = Array.from(files || []);
        if (!list.length) return [];
        if (els.uploadAssets.disabled) {
            showToast('图片正在上传，请等待当前上传完成', true);
            return [];
        }
        els.uploadAssets.classList.add('is-uploading');
        els.uploadAssets.disabled = true;
        els.uploadAssets.setAttribute('aria-busy', 'true');
        var uploadTitle = els.uploadAssets.querySelector('strong');
        var uploadHint = els.uploadAssets.querySelector('span');
        var defaultUploadHint = uploadHint ? uploadHint.textContent : '';
        var uploaded = [];
        try {
            for (var i = 0; i < list.length; i += 1) {
                if (uploadTitle) uploadTitle.textContent = '正在上传 ' + (i + 1) + ' / ' + list.length;
                if (uploadHint) uploadHint.textContent = list[i].name;
                var form = new FormData();
                form.append('file', list[i]);
                var asset = apiData(await axios.post('/api/template-lab/projects/' + state.project.id + '/assets', form, {
                    headers: { 'Content-Type': 'multipart/form-data' },
                    onUploadProgress: function (progressEvent) {
                        if (!uploadHint || !progressEvent.total) return;
                        uploadHint.textContent = Math.min(100, Math.round(progressEvent.loaded * 100 / progressEvent.total)) + '%';
                    }
                }));
                state.assets.push(asset);
                uploaded.push(asset);
            }
            renderAssets();
            scheduleSave();
            if (uploaded[0] && purpose === 'canvasBackground') {
                await applyCanvasBackgroundAsset(uploaded[0]);
            } else if (targetFrameId && uploaded[0]) {
                await assignImageToFrame(targetFrameId, uploaded[0]);
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
            els.uploadAssets.removeAttribute('aria-busy');
            if (uploadTitle) uploadTitle.textContent = '上传图片';
            if (uploadHint) uploadHint.textContent = defaultUploadHint;
            els.assetInput.value = '';
            state.pendingFrameId = null;
            state.pendingPlacement = null;
        }
    }

    function serializedDesign() {
        var fabricJson = state.canvas.toDatalessJSON(CUSTOM_PROPS);
        if (fabricJson && Array.isArray(fabricJson.objects)) {
            fabricJson.objects = fabricJson.objects.filter(function (o) { return o.labType !== 'frameEditOverlay'; });
        }
        return JSON.stringify({
            schemaVersion: 4,
            templateId: state.template.id,
            assets: state.assets,
            frameLayout: state.frameLayout,
            backgroundState: backgroundStateSnapshot(),
            fabric: fabricJson
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
            return canvasDataUrl('jpeg', Math.min(1, 360 / state.logicalWidth), .72, true);
        } catch (error) {
            return state.project.thumbnailDataUrl || '';
        }
    }

    function scheduleSave() {
        state.dirty = true;
        state.previewDirty = true;
        setSaveState('有未保存修改', 'saving');
        clearTimeout(state.saveTimer);
        state.saveTimer = setTimeout(saveNow, SAVE_DELAY_MS);
        clearTimeout(state.previewTimer);
        state.previewTimer = setTimeout(savePreviewNow, PREVIEW_DELAY_MS);
    }

    function saveNow() {
        if (!state.canvas || !state.project || !state.dirty) return state.saveQueue;
        clearTimeout(state.saveTimer);
        var payload = {
            projectName: els.projectName.value.trim() || state.project.projectName,
            canvasWidth: state.logicalWidth,
            canvasHeight: state.logicalHeight,
            designJson: serializedDesign()
        };
        state.dirty = false;
        setSaveState('正在保存...', 'saving');
        state.saveQueue = state.saveQueue.then(function () {
            return axios.put('/api/template-lab/projects/' + state.project.id, payload);
        }).then(function (response) {
            state.project = apiData(response);
            clearTimeout(state.saveRetryTimer);
            state.saveRetryTimer = null;
            setSaveState(state.dirty ? '有未保存修改' : '已自动保存', state.dirty ? 'saving' : 'saved');
        }).catch(function (error) {
            state.dirty = true;
            var saveStatus = error.response && Number(error.response.status);
            var saveMessage = saveStatus === 409
                ? '项目已被其他会话修改，请刷新后重新加载'
                : (error.response && error.response.data ? error.response.data.message : error.message);
            setSaveState(saveStatus === 409 ? '保存冲突，请刷新' : '保存失败，稍后将重试', 'error');
            showToast(saveMessage, true);
            if (saveStatus !== 409) {
                clearTimeout(state.saveRetryTimer);
                state.saveRetryTimer = setTimeout(function () {
                    state.saveRetryTimer = null;
                    saveNow();
                }, SAVE_RETRY_DELAY_MS);
            }
        });
        return state.saveQueue;
    }

    function savePreviewNow() {
        if (!state.canvas || !state.project || !state.previewDirty) return state.saveQueue;
        clearTimeout(state.previewTimer);
        var payload = { thumbnailDataUrl: createThumbnail() };
        state.previewDirty = false;
        state.saveQueue = state.saveQueue.then(function () {
            return axios.put('/api/template-lab/projects/' + state.project.id, payload);
        }).then(function (response) {
            state.project = apiData(response);
            if (!state.dirty) setSaveState('已自动保存', 'saved');
        }).catch(function () {
            state.previewDirty = true;
            clearTimeout(state.previewTimer);
            state.previewTimer = setTimeout(savePreviewNow, PREVIEW_DELAY_MS);
            if (!state.dirty) setSaveState('内容已保存，预览稍后更新', 'saved');
        });
        return state.saveQueue;
    }

    function saveAllNow() {
        return saveNow().then(savePreviewNow);
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
        positionCutoutFab();
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

    function exportScaleLimit() {
        var width = Math.max(1, Number(state.logicalWidth) || 1);
        var height = Math.max(1, Number(state.logicalHeight) || 1);
        var byDimension = Math.floor(16384 / Math.max(width, height));
        var byPixels = Math.floor(Math.sqrt(100000000 / (width * height)));
        return Math.max(1, Math.min(10, byDimension, byPixels));
    }

    function updateExportSizePreview(scale) {
        var limit = exportScaleLimit();
        var requestedScale = Math.round(Number(scale) || 1);
        var safeScale = Math.max(1, Math.min(limit, requestedScale));
        var width = state.logicalWidth * safeScale;
        var height = state.logicalHeight * safeScale;
        var label = requestedScale > limit ? '上限 ' + limit + ' 倍' : width + ' × ' + height;
        els.exportSizePreview.value = label;
        els.exportSizePreview.textContent = label;
        return { scale: safeScale, width: width, height: height };
    }

    function readExportScale(showFeedback) {
        var raw = Number(els.exportScale.value);
        if (!Number.isFinite(raw)) raw = 1;
        var rounded = Math.round(raw);
        var limit = exportScaleLimit();
        var clamped = Math.max(1, Math.min(limit, rounded));
        els.exportScale.max = String(limit);
        els.exportScale.value = String(clamped);
        els.exportScale.title = '输入 1 到 ' + limit + ' 的整数倍数';
        var size = updateExportSizePreview(clamped);
        if (showFeedback && clamped !== raw) {
            showToast('当前画布支持 1 到 ' + limit + ' 倍，已调整为 ' + clamped + ' 倍');
        }
        return size;
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

    function pollCutoutTask(taskId, elementId, delay, attempts) {
        attempts = attempts || 0;
        if (attempts > 80) {
            var timeoutVisual = findVisualByElementId(elementId);
            if (timeoutVisual) {
                timeoutVisual.set({ cutoutTaskStatus: 'failed', cutoutTaskError: '抠图处理超时，请重新提交' });
                queueMutation(true);
                if (selectedVisual() === timeoutVisual) renderProperties();
            }
            showToast('抠图处理超时，请重新提交', true);
            return;
        }
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
                pollCutoutTask(taskId, elementId, 3000, attempts + 1);
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
                pollCutoutTask(taskId, elementId, 5000, attempts + 1);
            }
        }, delay || 3000);
    }

    async function applyCutoutResult(elementId, result) {
        var visual = findVisualByElementId(elementId);
        if (!visual) {
            state.assets.push({ url: result.result_url, name: 'KIE抠图结果.png', generated: true });
            renderAssets();
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
            cutoutUrl: result.result_url,
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

    function dataUrlBytes(dataUrl) {
        var comma = dataUrl.indexOf(',');
        if (comma < 0) return 0;
        var b64 = dataUrl.slice(comma + 1);
        var padding = 0;
        if (b64.charAt(b64.length - 1) === '=') padding += 1;
        if (b64.charAt(b64.length - 2) === '=') padding += 1;
        return Math.max(0, Math.floor(b64.length * 3 / 4) - padding);
    }

    function formatBytes(bytes) {
        if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
        if (bytes >= 1024) return Math.round(bytes / 1024) + ' KB';
        return bytes + ' B';
    }

    function buildOffscreenCanvas(scale) {
        var currentPixelWidth = state.canvas.getWidth();
        var targetPixelWidth = state.logicalWidth * scale;
        var multiplier = targetPixelWidth / currentPixelWidth;
        var hidden = [];
        state.canvas.getObjects().forEach(function (o) {
            if (o.labType === 'framePlaceholder' || o.labType === 'imagePlaceholder'
                    || o.labType === 'frameBorder' || o.labType === 'guide') {
                hidden.push({ o: o, v: o.visible });
                o.visible = false;
            }
        });
        state.canvas.renderAll();
        var el = state.canvas.toCanvasElement(multiplier, { enableRetinaScaling: false });
        hidden.forEach(function (h) { h.o.visible = h.v; });
        if (hidden.length) state.canvas.renderAll();
        return el;
    }

    function offscreenDataUrl(el, format, quality) {
        return el.toDataURL(format === 'jpeg' ? 'image/jpeg' : 'image/png', quality == null ? 0.94 : quality);
    }

    function readExportMaxBytes() {
        if (!els.exportMaxSize) return null;
        var raw = parseFloat(els.exportMaxSize.value);
        if (!Number.isFinite(raw) || raw <= 0) return null;
        return Math.round(raw * 1024 * 1024);
    }

    // 把带透明像素的画布铺成白底，避免转 JPEG 时透明区变黑。仅在检测到透明时才动像素。
    function flattenToOpaque(el) {
        var ctx = el.getContext && el.getContext('2d');
        if (!ctx) return el;
        var patchW = Math.min(el.width, 64);
        var patchH = Math.min(el.height, 64);
        // 4×4 网格采样覆盖整张画布，避免只扫四角时漏掉局部透明区域。
        var spots = [];
        for (var row = 0; row < 4; row += 1) {
            for (var col = 0; col < 4; col += 1) {
                spots.push([
                    Math.max(0, Math.round(col * (el.width - patchW) / 3)),
                    Math.max(0, Math.round(row * (el.height - patchH) / 3))
                ]);
            }
        }
        var transparent = false;
        for (var s = 0; s < spots.length && !transparent; s += 1) {
            var data;
            try {
                data = ctx.getImageData(spots[s][0], spots[s][1], patchW, patchH).data;
            } catch (error) {
                return el;
            }
            for (var i = 3; i < data.length; i += 4) {
                if (data[i] < 255) { transparent = true; break; }
            }
        }
        if (!transparent) return el;
        ctx.save();
        ctx.globalCompositeOperation = 'destination-over';
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, el.width, el.height);
        ctx.restore();
        return el;
    }

    // 固定分辨率下二分逼近体积上限，返回满足上限的最优质量；质量压到下限仍超限则标记 overLimit。
    function fitJpegQuality(el, maxBytes, result) {
        var highUrl = offscreenDataUrl(el, 'jpeg', EXPORT_QUALITY_MAX);
        var highBytes = dataUrlBytes(highUrl);
        if (highBytes <= maxBytes) {
            result.dataUrl = highUrl;
            result.bytes = highBytes;
            result.quality = EXPORT_QUALITY_MAX;
            return result;
        }
        var lowUrl = offscreenDataUrl(el, 'jpeg', EXPORT_QUALITY_MIN);
        var lowBytes = dataUrlBytes(lowUrl);
        if (lowBytes > maxBytes) {
            result.dataUrl = lowUrl;
            result.bytes = lowBytes;
            result.quality = EXPORT_QUALITY_MIN;
            result.overLimit = true;
            return result;
        }
        var low = EXPORT_QUALITY_MIN;
        var high = EXPORT_QUALITY_MAX;
        var fitUrl = lowUrl;
        var fitBytes = lowBytes;
        var fitQuality = EXPORT_QUALITY_MIN;
        for (var step = 0; step < EXPORT_BINARY_STEPS && high - low > 0.01; step += 1) {
            var mid = (low + high) / 2;
            var url = offscreenDataUrl(el, 'jpeg', mid);
            var bytes = dataUrlBytes(url);
            if (bytes <= maxBytes) {
                low = mid;
                fitUrl = url;
                fitBytes = bytes;
                fitQuality = mid;
            } else {
                high = mid;
            }
        }
        result.dataUrl = fitUrl;
        result.bytes = fitBytes;
        result.quality = fitQuality;
        return result;
    }

    /**
     * 压缩到指定体积上限。<b>始终使用 baseScale，绝不降低导出分辨率。</b>
     * <ul>
     *   <li>JPG：在 0.3~0.94 之间二分寻找不超过上限的最高质量。</li>
     *   <li>PNG：无损、质量参数无效；超限则保持分辨率转 JPG（先铺白底）。</li>
     *   <li>连最低质量都超限：按最低质量导出并置 overLimit，由调用方提示。</li>
     * </ul>
     */
    function compressForMaxBytes(format, baseScale, maxBytes) {
        var result = {
            dataUrl: '',
            bytes: 0,
            scale: baseScale,
            quality: 1,
            format: format,
            autoJpeg: false,
            overLimit: false
        };
        var el = buildOffscreenCanvas(baseScale);
        if (format === 'jpeg') {
            flattenToOpaque(el);
            return fitJpegQuality(el, maxBytes, result);
        }

        var pngUrl = offscreenDataUrl(el, 'png', 1);
        var pngBytes = dataUrlBytes(pngUrl);
        if (pngBytes <= maxBytes) {
            result.dataUrl = pngUrl;
            result.bytes = pngBytes;
            return result;
        }
        flattenToOpaque(el);
        result.format = 'jpeg';
        result.autoJpeg = true;
        return fitJpegQuality(el, maxBytes, result);
    }

    async function exportImage() {
        var emptyFrames = effectiveFrames().filter(function (frame) { return !getFrameImage(frame.id); });
        var emptyImageSlots = state.canvas.getObjects().filter(function (object) { return object.labType === 'imagePlaceholder'; });
        if (emptyFrames.length || emptyImageSlots.length) {
            showToast('还有 ' + (emptyFrames.length + emptyImageSlots.length) + ' 个图片位置未填入，完成后再导出。', true);
            return;
        }
        els.exportButton.disabled = true;
        els.exportButton.textContent = '正在导出...';
        try {
            await saveAllNow();
            var format = els.exportFormat.value;
            var exportSize = readExportScale(true);
            var scale = exportSize.scale;
            var maxBytes = readExportMaxBytes();
            var finalFormat = format;
            var finalScale = scale;
            var dataUrl;
            var finalBytes;
            var autoJpeg = false;
            var overLimit = false;
            if (maxBytes == null) {
                // 未限制体积时也走离屏画布，JPG 先铺白底，避免透明底色导成黑底。
                if (format === 'jpeg') {
                    var plainEl = buildOffscreenCanvas(scale);
                    flattenToOpaque(plainEl);
                    dataUrl = offscreenDataUrl(plainEl, 'jpeg', EXPORT_QUALITY_MAX);
                } else {
                    dataUrl = canvasDataUrl(format, scale, 1, true);
                }
                finalBytes = dataUrlBytes(dataUrl);
            } else {
                var compressed = compressForMaxBytes(format, scale, maxBytes);
                dataUrl = compressed.dataUrl;
                finalFormat = compressed.format;
                finalScale = compressed.scale;
                finalBytes = compressed.bytes;
                autoJpeg = compressed.autoJpeg;
                overLimit = compressed.overLimit;
            }
            var link = document.createElement('a');
            var safeName = (els.projectName.value.trim() || '拼图项目').replace(/[\\/:*?"<>|]/g, '_');
            var ext = finalFormat === 'jpeg' ? 'jpg' : 'png';
            link.download = safeName + '_' + Math.round(state.logicalWidth * finalScale) + 'x' + Math.round(state.logicalHeight * finalScale) + '.' + ext;
            link.href = dataUrl;
            document.body.appendChild(link);
            link.click();
            link.remove();
            var msg = '图片已导出：' + formatBytes(finalBytes) + ' / ' + Math.round(state.logicalWidth * finalScale) + '×' + Math.round(state.logicalHeight * finalScale);
            if (autoJpeg) msg += '（PNG 超限已转 JPG）';
            if (overLimit) msg += '；已用最低压缩质量仍超出 ' + formatBytes(maxBytes) + '，分辨率未降低，可减少导出倍数';
            showToast(msg, overLimit);
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
        return frameContainingCanvasPoint(x, y);
    }

    function createCutoutFab() {
        if (!state.canvas || !state.canvas.wrapperEl) return;
        state.canvas.wrapperEl.style.position = 'relative';
        var fab = document.createElement('button');
        fab.type = 'button';
        fab.id = 'image-cutout-fab';
        fab.className = 'image-cutout-fab';
        fab.textContent = '去除背景';
        fab.style.display = 'none';
        fab.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            openCutoutConfirmation();
        });
        state.canvas.wrapperEl.appendChild(fab);
        els.imageCutoutFab = fab;
    }

    function positionCutoutFab() {
        var fab = els.imageCutoutFab;
        if (!fab || !state.canvas) return;
        var active = state.canvas.getActiveObject();
        var cutoutable = active && !state.frameEditMode
            && (active.labType === 'freeImage' || active.labType === 'imageComposition');
        if (!cutoutable) { fab.style.display = 'none'; return; }
        var rect;
        try { rect = active.getBoundingRect(); } catch (ignore) { fab.style.display = 'none'; return; }
        if (!rect || !Number.isFinite(rect.left) || !Number.isFinite(rect.width)) { fab.style.display = 'none'; return; }
        var taskStatus = String(active.cutoutTaskStatus || '').toLowerCase();
        var processing = taskStatus === 'processing' || taskStatus === 'queued';
        var hasCutout = Boolean(active.cutoutUrl);
        fab.disabled = processing;
        fab.textContent = processing ? '抠图中…' : (hasCutout ? '重新抠图' : '去除背景');
        fab.style.display = 'inline-flex';
        var canvasW = state.canvas.getWidth();
        var w = fab.offsetWidth, h = fab.offsetHeight;
        var left = rect.left + rect.width - w / 2;
        var top = rect.top - h / 2;
        left = Math.max(4, Math.min(left, canvasW - w - 4));
        top = Math.max(4, top);
        fab.style.left = left + 'px';
        fab.style.top = top + 'px';
    }

    function setupCanvasEvents() {
        state.canvas.on('selection:created', renderProperties);
        state.canvas.on('selection:updated', renderProperties);
        state.canvas.on('selection:cleared', renderProperties);
        state.canvas.on('selection:created', positionCutoutFab);
        state.canvas.on('selection:updated', positionCutoutFab);
        state.canvas.on('selection:cleared', positionCutoutFab);
        state.canvas.on('object:moving', positionCutoutFab);
        state.canvas.on('object:scaling', positionCutoutFab);
        state.canvas.on('object:modified', positionCutoutFab);
        state.canvas.on('mouse:down', function (event) {
            var target = event.target;
            if (target && target.labType === 'canvasBackground') {
                state.dragFrame = null;
                startBackgroundEditing();
                beginBackgroundDrag(event);
                return;
            }
            if (state.frameEditMode && state.frameEditOverlay && target === state.frameEditOverlay) {
                exitFrameEdit();
                return;
            }
            if (state.frameEditMode && (!target || target.frameId !== state.frameEditTarget)) {
                exitFrameEdit();
            }
            if (!state.frameEditMode && target && target.labType === 'frameBorder') {
                beginFrameDrag(target);
            } else {
                state.dragFrame = null;
            }
            beginBackgroundDrag(event);
        });
        state.canvas.on('mouse:move', function (event) {
            moveBackgroundDrag(event);
        });
        state.canvas.on('object:moving', function (event) {
            var object = event.target;
            if (!object) return;
            if (state.frameEditMode && state.frameEditTarget && object.labType === 'frameImage' && object.frameId === state.frameEditTarget) {
                constrainFrameImage(object);
                return;
            }
            if (object.labType === 'frameBorder') {
                showAlignmentGuides(object);
                syncFrameTransform(object);
                return;
            }
            showAlignmentGuides(object);
        });
        state.canvas.on('object:scaling', function (event) {
            var object = event.target;
            if (object && object.labType === 'frameBorder') syncFrameTransform(object);
        });
        state.canvas.on('object:rotating', function (event) {
            var object = event.target;
            if (object && object.labType === 'frameBorder') syncFrameTransform(object);
        });
        state.canvas.on('object:modified', function () {
            if (state.dragFrame) {
                state.dragFrame = null;
                removeGuides();
                queueMutation(true);
                renderProperties();
                return;
            }
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
            if (event.target && (event.target.labType === 'frameBorder' || event.target.labType === 'framePlaceholder')) {
                enterFrameEdit(event.target.frameId);
            } else if (!state.frameEditMode && event.target && event.target.labType === 'frameImage') {
                enterFrameEdit(event.target.frameId);
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
        state.canvas.on('mouse:wheel', function (opt) {
            var e = opt && opt.e;
            if (!e || typeof e.preventDefault !== 'function') return;
            e.preventDefault();
            if (typeof e.stopPropagation === 'function') e.stopPropagation();
            var factor = e.deltaY > 0 ? 0.9 : 1.1;
            if (e.ctrlKey) factor = Math.pow(factor, 0.5);
            setManualZoom(state.manualZoom * factor);
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
            createCutoutFab();
            var saved = parseSavedDesign();
            if (saved) {
                state.suppressHistory = true;
                state.canvas.loadFromJSON(saved, function () {
                    rehydrateCanvasObjects();
                    finishInitialization();
                });
            } else {
                await buildTemplateCanvas();
                finishInitialization();
                scheduleSave();
            }
        } catch (error) {
            var message = error.response && error.response.data ? error.response.data.message : error.message;
            els.loading.innerHTML = '<strong>项目加载失败</strong><span>' + escapeHtml(message) + '</span>';
            setSaveState('加载失败', 'error');
        }
    }

    function setEditorPanelCollapsed(side, collapsed, persist) {
        if (!els.workspace) return;
        var isLeft = side === 'left';
        var className = isLeft ? 'is-left-collapsed' : 'is-right-collapsed';
        var button = isLeft ? els.toggleLeftPanel : els.toggleRightPanel;
        els.workspace.classList.toggle(className, Boolean(collapsed));
        if (button) {
            button.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
            button.textContent = collapsed
                ? (isLeft ? '展开素材' : '展开属性')
                : (isLeft ? '收起素材' : '收起属性');
        }
        if (persist !== false) {
            try { window.localStorage.setItem('templateLab.' + side + 'PanelCollapsed', collapsed ? '1' : '0'); } catch (ignore) { }
        }
        clearTimeout(setEditorPanelCollapsed.resizeTimer);
        setEditorPanelCollapsed.resizeTimer = setTimeout(function () {
            if (state.canvas) applyViewportScale();
        }, 190);
    }

    function setupEditorPanelToggles() {
        ['left', 'right'].forEach(function (side) {
            var button = side === 'left' ? els.toggleLeftPanel : els.toggleRightPanel;
            if (!button) return;
            var collapsed = false;
            try { collapsed = window.localStorage.getItem('templateLab.' + side + 'PanelCollapsed') === '1'; } catch (ignore) { }
            setEditorPanelCollapsed(side, collapsed, false);
            button.addEventListener('click', function () {
                var expanded = button.getAttribute('aria-expanded') === 'true';
                setEditorPanelCollapsed(side, expanded, true);
            });
        });
    }

    function finishInitialization() {
        state.suppressHistory = false;
        renderAssets();
        renderContentPanel();
        renderLayerPanel();
        applyViewportScale();
        readExportScale(false);
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
        setupEditorPanelToggles();
        renderBackgroundPickState();
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
                    cancelCanvasBackgroundPick(false);
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
            var textRow = event.target.closest('[data-layer-field]');
            var frameRow = event.target.closest('[data-layer-frame]');
            var imageRow = event.target.closest('[data-layer-image]');
            var backgroundRow = event.target.closest('[data-layer-background]');
            if (!textRow && !frameRow && !imageRow && !backgroundRow) return;
            var action = button ? button.dataset.layerAction
                : (frameRow ? 'select-frame' : (imageRow ? 'select-image' : (backgroundRow ? 'select-background' : 'select')));
            if (frameRow && action === 'select-frame') return selectFrame(frameRow.dataset.layerFrame);
            if (backgroundRow && action === 'change-background') return openCanvasBackgroundPicker();
            var object = imageRow
                ? findVisualByElementId(imageRow.dataset.layerImage)
                : (textRow ? findTextByFieldId(textRow.dataset.layerField) : (backgroundRow ? getCanvasBackground() : null));
            if (!object) return;
            if (action === 'select' || action === 'select-image' || action === 'select-background') {
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
            if (action === 'up' && object.labType !== 'canvasBackground') object.bringForward();
            if (action === 'down' && object.labType !== 'canvasBackground') object.sendBackwards();
            if (object.labType === 'canvasBackground') ensureCanvasBackgroundAtBack();
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
        document.querySelectorAll('[data-add-frame]').forEach(function (button) {
            button.addEventListener('click', function () { addFrame(button.dataset.addFrame); });
        });
        els.deleteFrame.addEventListener('click', function () {
            var id = selectedFrameId();
            if (id) deleteFrame(id);
        });
        els.uploadAssets.addEventListener('click', function () {
            state.pendingFrameId = null;
            state.pendingPlacement = null;
            els.assetInput.click();
        });
        if (els.cancelBackgroundPick) {
            els.cancelBackgroundPick.addEventListener('click', function () { cancelCanvasBackgroundPick(true); });
        }
        els.replaceFrameImage.addEventListener('click', function () {
            cancelCanvasBackgroundPick(false);
            var frameId = selectedFrameId();
            if (state.frameEditMode) exitFrameEdit();
            state.pendingFrameId = frameId;
            state.pendingPlacement = null;
            els.assetInput.click();
        });
        els.assetInput.addEventListener('change', function () {
            uploadFiles(els.assetInput.files, state.pendingFrameId, state.pendingPlacement,
                state.assetPickMode === 'canvasBackground' ? 'canvasBackground' : '');
        });
        ['dragenter', 'dragover'].forEach(function (name) {
            els.uploadAssets.addEventListener(name, function (event) { event.preventDefault(); els.uploadAssets.classList.add('is-dragover'); });
        });
        ['dragleave', 'drop'].forEach(function (name) {
            els.uploadAssets.addEventListener(name, function (event) { event.preventDefault(); els.uploadAssets.classList.remove('is-dragover'); });
        });
        els.uploadAssets.addEventListener('drop', function (event) {
            uploadFiles(event.dataTransfer.files, null, null, state.assetPickMode === 'canvasBackground' ? 'canvasBackground' : '');
        });
        els.assetGrid.addEventListener('click', async function (event) {
            var card = event.target.closest('[data-asset-index]');
            if (!card) return;
            var index = Number(card.dataset.assetIndex);
            if (state.assetSelectMode) {
                event.preventDefault();
                toggleAssetSelection(index);
                return;
            }
            await useAsset(state.assets[index], null, selectedFrameId());
        });
        els.assetGrid.addEventListener('dragstart', function (event) {
            if (state.assetSelectMode) { event.preventDefault(); return; }
            var card = event.target.closest('[data-asset-index]');
            if (!card) return;
            event.dataTransfer.setData('application/x-template-lab-asset', card.dataset.assetIndex);
            event.dataTransfer.effectAllowed = 'copy';
        });
        if (els.assetSelectToggle) {
            els.assetSelectToggle.addEventListener('click', function () {
                setAssetSelectMode(!state.assetSelectMode);
            });
            els.assetSelectAll.addEventListener('change', function () {
                if (els.assetSelectAll.checked) {
                    state.assetSelected = state.assets.map(function (asset, index) { return index; });
                    renderAssets();
                } else {
                    state.assetSelected = [];
                    renderAssets();
                }
            });
            els.assetInvertSelect.addEventListener('click', invertAssetSelection);
            els.assetRemoveSelected.addEventListener('click', function () { removeSelectedAssets(); });
        }
        els.viewport.addEventListener('dragenter', function (event) {
            if (!hasCanvasDropPayload(event)) return;
            event.preventDefault();
            state.canvasDragDepth += 1;
            setCanvasDropState('ready');
        });
        els.viewport.addEventListener('dragover', function (event) {
            if (!hasCanvasDropPayload(event)) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = 'copy';
            setCanvasDropState('ready');
        });
        els.viewport.addEventListener('dragleave', function (event) {
            if (!state.canvasDragDepth) return;
            state.canvasDragDepth = Math.max(0, state.canvasDragDepth - 1);
            if (!state.canvasDragDepth) setCanvasDropState('');
        });
        els.viewport.addEventListener('drop', async function (event) {
            if (!hasCanvasDropPayload(event)) return;
            event.preventDefault();
            event.stopPropagation();
            state.canvasDragDepth = 0;
            var frame = frameAtClientPoint(event);
            var point = canvasPointAtClientPoint(event);
            var index = event.dataTransfer.getData('application/x-template-lab-asset');
            try {
                if (index !== '') {
                    await useAsset(state.assets[Number(index)], point, frame && frame.id);
                } else if (event.dataTransfer.files && event.dataTransfer.files.length) {
                    var droppedFiles = supportedDroppedImageFiles(event.dataTransfer.files);
                    if (!droppedFiles.length) return;
                    setCanvasDropState('uploading');
                    await uploadFiles(droppedFiles, frame && frame.id, frame ? null : point,
                        state.assetPickMode === 'canvasBackground' ? 'canvasBackground' : '');
                }
            } finally {
                setCanvasDropState('');
            }
        });
        document.addEventListener('dragover', function (event) {
            if (hasFileDropPayload(event)) event.preventDefault();
        });
        document.addEventListener('drop', function (event) {
            if (!hasFileDropPayload(event)) return;
            if (els.viewport.contains(event.target) || els.uploadAssets.contains(event.target)) return;
            event.preventDefault();
            state.canvasDragDepth = 0;
            setCanvasDropState('');
            showToast('请将图片拖到中间画布，或拖到左侧上传区域', true);
        });
        document.addEventListener('dragend', function () {
            state.canvasDragDepth = 0;
            setCanvasDropState('');
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
        els.openExportSettings.addEventListener('click', function () {
            updateExportSizePreview(els.exportScale.value);
            els.exportSettingsDialog.showModal();
        });
        els.exportScale.addEventListener('input', function () { updateExportSizePreview(els.exportScale.value); });
        els.exportScale.addEventListener('change', function () { readExportScale(true); });
        els.exportScale.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                readExportScale(true);
                els.exportScale.select();
            }
        });
        els.exportFromSettings.addEventListener('click', function () {
            readExportScale(true);
            els.exportSettingsDialog.close();
            exportImage();
        });
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
        els.frameCenterHorizontal.addEventListener('click', function () { modifySelectedFrame('center-horizontal'); });
        els.frameCenterVertical.addEventListener('click', function () { modifySelectedFrame('center-vertical'); });
        els.frameRotate.addEventListener('click', function () { modifySelectedFrame('rotate'); });

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

        buildSolidSwatches();
        els.chooseBackground.addEventListener('click', openCanvasBackgroundPicker);
        els.changeBackground.addEventListener('click', openCanvasBackgroundPicker);
        els.backgroundModeSolid.addEventListener('click', function () { setBackgroundMode('solid', true); });
        els.backgroundModeImage.addEventListener('click', function () { setBackgroundMode('image', true); });
        els.quickBackgroundModeSolid.addEventListener('click', function () { setBackgroundMode('solid', true); });
        els.quickBackgroundModeImage.addEventListener('click', function () { setBackgroundMode('image', true); });
        [
            [els.backgroundSolidColor, els.backgroundSolidHex],
            [els.quickSolidColor, els.quickSolidHex]
        ].forEach(function (pair) {
            pair[0].addEventListener('input', function () { setSolidColor(pair[0].value, false); });
            pair[0].addEventListener('change', function () { setSolidColor(pair[0].value, true); });
            pair[1].addEventListener('change', function () {
                var value = pair[1].value.trim();
                if (value === 'transparent' || /^#[0-9a-f]{6}$/i.test(value)) setSolidColor(value, true);
                else { showToast('请输入 #RRGGBB 或 transparent', true); renderSolidControls(); }
            });
        });
        [[els.backgroundSolidOpacity, els.backgroundSolidOpacityValue], [els.quickSolidOpacity, els.quickSolidOpacityValue]]
            .forEach(function (pair) {
                pair[0].addEventListener('input', function () {
                    pair[1].value = pair[0].value + '%';
                    pair[1].textContent = pair[0].value + '%';
                    setSolidOpacity(pair[0].value, false);
                });
                pair[0].addEventListener('change', function () { setSolidOpacity(pair[0].value, true); });
            });
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
        els.editBackgroundPosition.addEventListener('click', function () {
            if (state.backgroundEdit) stopBackgroundEditing();
            else startBackgroundEditing();
        });
        els.removeBackground.addEventListener('click', removeCanvasBackground);
        els.freeImageFrameTarget.addEventListener('change', function () {
            var visual = selectedVisual();
            if (visual && visual.elementId) state.frameTargetSelections[visual.elementId] = els.freeImageFrameTarget.value;
        });
        els.placeImageInFrame.addEventListener('click', async function () {
            if (state.frameFillBusy) return;
            var visual = selectedVisual();
            var frameId = els.freeImageFrameTarget.value;
            if (!visual) return showToast('请先选择要填入相框的自由图片', true);
            if (!frameId || !frameConfig(frameId)) return showToast('请选择有效的目标相框', true);
            var asset = { url: activeVisualUrl(visual), name: visual.originalAssetName || visual.assetName || '图片' };
            state.frameFillBusy = true;
            els.placeImageInFrame.disabled = true;
            els.placeImageInFrame.textContent = '正在填入...';
            var filled = await assignImageToFrame(frameId, asset, {
                sourceVisual: visual,
                onReady: function () {
                    if (state.canvas.getObjects().includes(visual)) state.canvas.remove(visual);
                    stopBackgroundEditing();
                }
            });
            state.frameFillBusy = false;
            if (filled) {
                if (visual.elementId) delete state.frameTargetSelections[visual.elementId];
                showToast('图片已填入“' + frameLabel(frameId) + '”');
            } else if (selectedVisual() === visual) {
                renderImageProperties(visual);
            }
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
                event.preventDefault(); saveAllNow(); return;
            }
            if (typing) return;
            if (event.key === 'Escape') {
                if (state.assetPickMode === 'canvasBackground') { event.preventDefault(); cancelCanvasBackgroundPick(true); return; }
                if (state.frameEditMode) { event.preventDefault(); exitFrameEdit(); return; }
                if (state.assetSelectMode) { event.preventDefault(); setAssetSelectMode(false); }
                return;
            }
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'c') {
                event.preventDefault(); copySelectedElements(); return;
            }
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'v') {
                event.preventDefault(); pasteCopiedElements(); return;
            }
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
                else if (selectedFrameId()) { event.preventDefault(); deleteFrame(selectedFrameId()); }
                else if (selectedVisual()) { event.preventDefault(); deleteSelectedVisual(); }
            }
            if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
                var frameController = getFrameController(selectedFrameId());
                var object = selectedText() || selectedVisual() || frameController;
                if (!object) return;
                event.preventDefault();
                if (frameController && object === frameController) beginFrameDrag(frameController);
                var step = event.shiftKey ? 10 : 1;
                if (event.key === 'ArrowLeft') object.left -= step;
                if (event.key === 'ArrowRight') object.left += step;
                if (event.key === 'ArrowUp') object.top -= step;
                if (event.key === 'ArrowDown') object.top += step;
                if (frameController && object === frameController) {
                    syncFrameTransform(frameController);
                    state.dragFrame = null;
                }
                object.setCoords(); state.canvas.renderAll(); queueMutation(false);
            }
        });
        window.addEventListener('resize', function () { clearTimeout(setupDomEvents.resizeTimer); setupDomEvents.resizeTimer = setTimeout(applyViewportScale, 120); });
        window.addEventListener('beforeunload', function (event) { if (state.dirty) { event.preventDefault(); event.returnValue = ''; } });
        document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'hidden') saveAllNow(); });
    }

    setupDomEvents();
    initialize();
})();
