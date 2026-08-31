/* Global sidebar */

(function () {
    'use strict';

    function init() {
        if (!window.AppAuth) {
            setTimeout(init, 100);
            return;
        }

        var layout = document.querySelector('.app-layout');
        if (!layout) return;

        var userName = window.AppAuth.getUserName();
        var shopName = window.AppAuth.getShopName();
        var isAdmin = window.AppAuth.isAdmin();
        var onAplus = isActive('aplus.html') || isActive('aplus-templates.html') || isActive('aplus-history.html');
        var onModelLib = isActive('model-library.html');
        var currentView = getViewParam();
        var onAiCanvas = isActive('ai-canvas.html') || isActive('ai-canvas-templates.html');
        var onSystemManagement = isActive('admin.html') || isActive('price-management.html');

        var sidebar = document.createElement('div');
        sidebar.className = 'sidebar-global';
        sidebar.innerHTML = [
            '<div class="sidebar-logo">AI 时尚视觉</div>',
            '<div class="sidebar-user">',
            '  <div class="sidebar-user-avatar">' + (userName.charAt(0) || 'U') + '</div>',
            '  <div class="sidebar-user-info">',
            '    <div class="sidebar-user-name">' + esc(userName) + '</div>',
            '    <div class="sidebar-user-shop">' + esc(shopName) + '</div>',
            '  </div>',
            '</div>',
            '<nav class="sidebar-nav">',
            navLink('index.html', 'CC', '批量换色', isActive('index.html')),
            navLink('scene.html', 'SC', '批量场景', isActive('scene.html')),
            navLink('scene-generator.html', 'AI', 'AI 场景生成', isActive('scene-generator.html')),
            navLink('buyer-show.html', 'BS', 'AI 买家秀', isActive('buyer-show.html')),
            aplusMenu(onAplus),
            aiCanvasMenu(onAiCanvas),
            navLink('video.html', 'VD', '视频生成', isActive('video.html')),
            modelLibraryMenu(onModelLib, currentView),
            navLink('template-manage.html', 'TP', '场景库', isActive('template-manage.html')),
            navLink('colorCard.html', 'CL', '色卡库', isActive('colorCard.html')),
            navLink('list.html', 'DB', '任务大盘', isActive('list.html')),
            isAdmin ? systemManagementMenu(onSystemManagement) : '',
            '</nav>',
            '<div class="sidebar-footer">',
            '  <button class="sidebar-logout-btn" onclick="window.AppAuth.logout()">退出登录</button>',
            '</div>'
        ].join('');

        if (layout.firstChild) {
            layout.insertBefore(sidebar, layout.firstChild);
        } else {
            layout.appendChild(sidebar);
        }

        injectStyles();

        if (onModelLib) {
            sidebar.addEventListener('click', function (event) {
                var link = event.target.closest('[data-view]');
                if (!link) return;

                event.preventDefault();
                var view = link.getAttribute('data-view');
                history.replaceState(null, '', '?view=' + view);

                sidebar.querySelectorAll('.sidebar-sub').forEach(function (el) {
                    el.classList.toggle('active', el.getAttribute('data-view') === view);
                });

                var app = document.querySelector('#app');
                if (app && app.__vue_app__) {
                    var vm = app.__vue_app__._instance.proxy;
                    if (typeof vm.go === 'function') {
                        vm.go(view);
                        return;
                    }
                }

                window.location.href = 'model-library.html?view=' + view;
            });
        }
    }

    function navLink(href, icon, text, active) {
        return [
            '<a href="' + href + '" class="sidebar-link' + (active ? ' active' : '') + '">',
            '  <span class="sidebar-link-icon">' + icon + '</span><span>' + text + '</span>',
            '</a>'
        ].join('');
    }

    function aplusMenu(onAplus) {
        return [
            '<div class="sidebar-parent' + (onAplus ? ' expanded' : '') + '">',
            '  <div class="sidebar-link sidebar-parent-toggle' + (onAplus ? ' active-parent' : '') + '" onclick="this.parentElement.classList.toggle(\'expanded\')">',
            '    <span class="sidebar-link-icon">A+</span>',
            '    <span>A+ 套图</span>',
            '    <span class="sidebar-arrow">v</span>',
            '  </div>',
            '  <div class="sidebar-submenu">',
            subLink('aplus.html', 'P', '套图项目', isActive('aplus.html')),
            subLink('aplus-templates.html', 'T', '套图模板', isActive('aplus-templates.html')),
            subLink('aplus-history.html', 'H', '生成记录', isActive('aplus-history.html')),
            '  </div>',
            '</div>'
        ].join('');
    }

    function aiCanvasMenu(onAiCanvas) {
        return [
            '<div class="sidebar-parent' + (onAiCanvas ? ' expanded' : '') + '">',
            '  <div class="sidebar-link sidebar-parent-toggle' + (onAiCanvas ? ' active-parent' : '') + '" onclick="this.parentElement.classList.toggle(\'expanded\')">',
            '    <span class="sidebar-link-icon">CV</span>',
            '    <span>AI 画布</span>',
            '    <span class="sidebar-arrow">v</span>',
            '  </div>',
            '  <div class="sidebar-submenu">',
            subLink('ai-canvas.html', 'C', '画布工作台', isActive('ai-canvas.html')),
            subLink('ai-canvas-templates.html', 'T', '画布模板', isActive('ai-canvas-templates.html')),
            '  </div>',
            '</div>'
        ].join('');
    }

    function modelLibraryMenu(onModelLib, currentView) {
        return [
            '<div class="sidebar-parent' + (onModelLib ? ' expanded' : '') + '">',
            '  <div class="sidebar-link sidebar-parent-toggle' + (onModelLib ? ' active-parent' : '') + '" onclick="this.parentElement.classList.toggle(\'expanded\')">',
            '    <span class="sidebar-link-icon">ML</span>',
            '    <span>AI 模特库</span>',
            '    <span class="sidebar-arrow">v</span>',
            '  </div>',
            '  <div class="sidebar-submenu">',
            subLink('model-library.html?view=generate', 'G', '生成模特', onModelLib && currentView === 'generate', 'generate'),
            subLink('model-library.html?view=library', 'L', '模特库', onModelLib && currentView === 'library', 'library'),
            subLink('model-library.html?view=history', 'H', '生成历史', onModelLib && currentView === 'history', 'history'),
            '  </div>',
            '</div>'
        ].join('');
    }

    function systemManagementMenu(onSystemManagement) {
        return [
            '<div class="sidebar-parent' + (onSystemManagement ? ' expanded' : '') + '">',
            '  <div class="sidebar-link sidebar-parent-toggle' + (onSystemManagement ? ' active-parent' : '') + '" onclick="this.parentElement.classList.toggle(\'expanded\')">',
            '    <span class="sidebar-link-icon">SM</span>',
            '    <span>系统管理</span>',
            '    <span class="sidebar-arrow">v</span>',
            '  </div>',
            '  <div class="sidebar-submenu">',
            subLink('admin.html', 'UM', '用户管理', isActive('admin.html')),
            subLink('price-management.html', '￥', '模型价格', isActive('price-management.html')),
            '  </div>',
            '</div>'
        ].join('');
    }

    function subLink(href, icon, text, active, view) {
        return [
            '<a href="' + href + '" class="sidebar-link sidebar-sub' + (active ? ' active' : '') + '"' + (view ? ' data-view="' + view + '"' : '') + '>',
            '  <span class="sidebar-link-icon">' + icon + '</span><span>' + text + '</span>',
            '</a>'
        ].join('');
    }

    function esc(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function isActive(page) {
        return window.location.pathname.endsWith(page);
    }

    function getViewParam() {
        var match = window.location.search.match(/[?&]view=([^&]+)/);
        return match ? match[1] : 'generate';
    }

    function injectStyles() {
        if (document.getElementById('sidebar-global-styles')) return;

        var style = document.createElement('style');
        style.id = 'sidebar-global-styles';
        style.textContent = [
            '.sidebar-global {',
            '  width: var(--app-sidebar-width, 240px); min-width: var(--app-sidebar-width, 240px);',
            '  background: #0f172a;',
            '  color: var(--app-text-inverse, #eef4ff); display: flex; flex-direction: column;',
            '  position: fixed; top: 0; left: 0; bottom: 0; z-index: 100; overflow-y: auto;',
            '  border-right: 1px solid rgba(255,255,255,0.08);',
            '  box-shadow: 16px 0 40px rgba(15,23,42,0.18);',
            '}',
            '.sidebar-logo {',
            '  padding: 22px 18px 18px; font-size: 18px; font-weight: 800;',
            '  color: #f8fbff; letter-spacing: 0; border-bottom: 1px solid rgba(255,255,255,0.08);',
            '}',
            '.sidebar-user {',
            '  display: flex; align-items: center; gap: 10px; padding: 16px 18px;',
            '  border-bottom: 1px solid rgba(255,255,255,0.08);',
            '}',
            '.sidebar-user-avatar {',
            '  width: 36px; height: 36px; border-radius: 10px;',
            '  background: rgba(37,99,235,0.22); border: 1px solid rgba(147,197,253,0.28);',
            '  display: flex; align-items: center; justify-content: center;',
            '  font-weight: 700; font-size: 14px; flex-shrink: 0;',
            '}',
            '.sidebar-user-name { font-size: 13px; font-weight: 700; color: #f8fbff; }',
            '.sidebar-user-shop { font-size: 11px; color: rgba(226,232,240,0.62); margin-top: 2px; }',
            '.sidebar-nav { flex: 1; padding: 10px 12px; display: flex; flex-direction: column; gap: 3px; }',
            '.sidebar-link {',
            '  display: flex; align-items: center; gap: 10px; padding: 10px 12px;',
            '  border-radius: 8px; color: rgba(226,232,240,0.72);',
            '  text-decoration: none; font-size: 14px; transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;',
            '  cursor: pointer; border: 1px solid transparent;',
            '}',
            '.sidebar-link:hover { background: rgba(255,255,255,0.07); color: #f8fbff; border-color: rgba(255,255,255,0.08); }',
            '.sidebar-link.active, .sidebar-link.active-parent {',
            '  background: rgba(37,99,235,0.22);',
            '  color: #f8fbff; font-weight: 700; border-color: rgba(147,197,253,0.28);',
            '}',
            '.sidebar-link-icon {',
            '  width: 24px; height: 24px; border-radius: 6px; display: inline-flex; align-items: center; justify-content: center;',
            '  background: rgba(255,255,255,0.07); color: rgba(226,232,240,0.86); font-size: 10px; flex-shrink: 0; font-weight: 800;',
            '}',
            '.sidebar-link.active .sidebar-link-icon, .sidebar-link.active-parent .sidebar-link-icon { background: rgba(37,99,235,0.36); color: #f8fbff; }',
            '.sidebar-arrow { margin-left: auto; font-size: 10px; transition: transform 0.25s; opacity: 0.5; }',
            '.sidebar-parent.expanded .sidebar-arrow { transform: rotate(180deg); opacity: 1; }',
            '.sidebar-submenu { max-height: 0; overflow: hidden; transition: max-height 0.3s ease; }',
            '.sidebar-parent.expanded .sidebar-submenu { max-height: 300px; }',
            '.sidebar-sub { padding-left: 44px; font-size: 13px; }',
            '.sidebar-footer { padding: 12px; border-top: 1px solid rgba(255,255,255,0.08); }',
            '.sidebar-logout-btn {',
            '  width: 100%; padding: 10px; border: 1px solid rgba(255,255,255,0.12);',
            '  border-radius: 8px; background: transparent;',
            '  color: rgba(226,232,240,0.62); font-size: 13px; cursor: pointer; transition: all 0.18s ease;',
            '}',
            '.sidebar-logout-btn:hover {',
            '  background: rgba(245,108,108,0.15); border-color: rgba(245,108,108,0.4); color: #f56c6c;',
            '}',
            '@media (max-width: 900px) {',
            '  .sidebar-global { display: none; }',
            '  .app-main { margin-left: 0 !important; }',
            '}'
        ].join('\n');

        document.head.appendChild(style);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
