(function () {
    'use strict';

    const PREFIX = '__mint_liblib_canvas_import_v1__:';
    const MAX_NODES = 160;

    function text(value, maxLength) {
        const clean = String(value == null ? '' : value).replace(/\s+/g, ' ').trim();
        return clean.slice(0, maxLength || 12000);
    }

    function decodeBase64Utf8(value) {
        const binary = atob(value);
        let encoded = '';
        for (let i = 0; i < binary.length; i += 1) {
            encoded += '%' + ('00' + binary.charCodeAt(i).toString(16)).slice(-2);
        }
        return decodeURIComponent(encoded);
    }

    function normalizeImportedPayload(value) {
        if (!value || typeof value !== 'object' || value.format !== 'mint-liblib-dom-v1') return null;
        const rawNodes = Array.isArray(value.nodes) ? value.nodes.slice(0, MAX_NODES) : [];
        const nodes = rawNodes.map((node, index) => {
            if (!node || typeof node !== 'object') return null;
            const type = ['image', 'prompt', 'group'].includes(node.type) ? node.type : 'prompt';
            const x = Number(node.x);
            const y = Number(node.y);
            const w = Number(node.w);
            const h = Number(node.h);
            const item = {
                id: text(node.id, 96) || `liblib-${index + 1}`,
                type,
                x: Number.isFinite(x) ? x : index * 36,
                y: Number.isFinite(y) ? y : index * 36,
                w: Number.isFinite(w) && w >= 96 ? Math.min(w, 2200) : undefined,
                h: Number.isFinite(h) && h >= 72 ? Math.min(h, 1800) : undefined
            };
            if (type === 'image') {
                item.url = text(node.url, 8000);
                item.name = text(node.name, 220) || 'Liblib 素材';
                item.mediaKind = node.mediaKind === 'video' ? 'video' : 'image';
                if (!item.url) return null;
            } else if (type === 'group') {
                item.name = text(node.name, 220) || 'Liblib 分组';
                item.items = Array.isArray(node.items) ? node.items.map(id => text(id, 96)).filter(Boolean).slice(0, MAX_NODES) : [];
                item.w = item.w || 520;
                item.h = item.h || 320;
            } else {
                item.prompt = text(node.prompt, 18000) || '来自 Liblib 的未识别节点';
            }
            return item;
        }).filter(Boolean);
        if (!nodes.length) return null;
        const knownIds = new Set(nodes.map(node => node.id));
        const connections = (Array.isArray(value.connections) ? value.connections : []).slice(0, MAX_NODES * 4)
            .map((connection, index) => ({
                id: text(connection && connection.id, 96) || `liblib-edge-${index + 1}`,
                from: text(connection && connection.from, 96),
                to: text(connection && connection.to, 96)
            }))
            .filter(connection => connection.from && connection.to && connection.from !== connection.to
                && knownIds.has(connection.from) && knownIds.has(connection.to));
        return {
            format: value.format,
            source: {
                url: text(value.source && value.source.url, 4000),
                title: text(value.source && value.source.title, 260)
            },
            targetCanvasId: text(value.targetCanvasId, 160),
            nodes,
            connections
        };
    }

    function takeIncoming() {
        const raw = String(window.name || '');
        if (!raw.startsWith(PREFIX)) return null;
        window.name = '';
        try {
            return normalizeImportedPayload(JSON.parse(decodeBase64Utf8(raw.slice(PREFIX.length))));
        } catch (error) {
            console.warn('[liblib-import] 无法读取导入数据', error);
            return null;
        }
    }

    function copyText(value) {
        if (navigator.clipboard && window.isSecureContext) {
            return navigator.clipboard.writeText(value).then(() => true);
        }
        const area = document.createElement('textarea');
        area.value = value;
        area.setAttribute('readonly', '');
        area.style.cssText = 'position:fixed;opacity:0;pointer-events:none;left:-9999px;top:-9999px;';
        document.body.appendChild(area);
        area.select();
        let copied = false;
        try { copied = document.execCommand('copy'); } catch (error) {}
        area.remove();
        return Promise.resolve(copied);
    }

    function bookmarkletFor(targetUrl) {
        const target = JSON.stringify(String(targetUrl || ''));
        const script = `(function(){try{var q=function(v){return String(v||'').replace(/\\s+/g,' ').trim()},o=function(v){try{var u=new URL(v,location.href);if(!/(^|\\.)liblib\\.(tv|art|cloud)$/i.test(u.hostname))return '';u.search='';return u.href}catch(e){return ''}},p=function(v){var m=String(v||'').match(/translate\\((-?[\\d.]+)px,\\s*(-?[\\d.]+)px\\)/);return m?{x:+m[1],y:+m[2]}:{x:0,y:0}},r=function(v,s){var m=String(s||'').match(new RegExp(v+'\\\\s*:\\s*([\\\\d.]+)px','i'));return m?+m[1]:0},a=[].slice.call(document.querySelectorAll('.react-flow__node[data-id]')),n=[],g=[];a.forEach(function(e,i){var c=String(e.className||''),d=e.getAttribute('data-id')||('liblib-'+i),b=e.querySelector('[data-nodeid]')||e,z=p(e.getAttribute('style')),w=r('width',b.getAttribute('style'))||r('width',e.getAttribute('style'))||520,h=r('height',b.getAttribute('style'))||r('height',e.getAttribute('style'))||320,t=q(e.innerText),k=/react-flow__node-group/.test(c),v=/react-flow__node-video/.test(c),m=e.querySelector('video[src],img[src],video[poster]'),s=m&&(m.getAttribute('src')||m.getAttribute('poster'))?o(m.getAttribute('src')||m.getAttribute('poster')):'';if(k){g.push({id:d,type:'group',name:t||'Liblib 分组',x:z.x,y:z.y,w:w,h:h});return}if(s){n.push({id:d,type:'image',name:t||('Liblib '+(v?'视频':'图片')),url:s,mediaKind:v?'video':'image',x:z.x,y:z.y,w:w,h:h});return}n.push({id:d,type:'prompt',prompt:t||'来自 Liblib 的未识别节点',x:z.x,y:z.y,w:w,h:h})});g.forEach(function(x){x.items=n.filter(function(y){return y.x+y.w/2>=x.x&&y.x+y.w/2<=x.x+x.w&&y.y+y.h/2>=x.y&&y.y+y.h/2<=x.y+x.h}).map(function(y){return y.id})});var z=n.concat(g),i={},e=[].slice.call(document.querySelectorAll('.react-flow__edge[aria-label]')).map(function(x,k){var m=q(x.getAttribute('aria-label')).match(/^Edge from (.+) to (.+)$/),f=m&&m[1],t=m&&m[2];return f&&t?{id:x.getAttribute('data-id')||('liblib-edge-'+k),from:f,to:t}:null}).filter(function(x){return x&&z.some(function(y){return y.id===x.from})&&z.some(function(y){return y.id===x.to})});var d={format:'mint-liblib-dom-v1',source:{url:location.href,title:document.title},nodes:z,connections:e},j=btoa(unescape(encodeURIComponent(JSON.stringify(d)))),w=window.open('about:blank','_blank');if(!w)throw new Error('浏览器拦截了新窗口，请允许弹窗后重试');w.name='${PREFIX}'+j;w.location.replace(${target});}catch(e){alert('Mint 导入失败：'+(e&&e.message||e))}})()`;
        return `javascript:${script}`;
    }

    window.MintLiblibCanvasBridge = {
        copyText,
        bookmarkletFor,
        takeIncoming
    };
}());
