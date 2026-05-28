/* ===== AI 时尚视觉设计平台 - 全局侧边栏（支持子菜单折叠） ===== */

(function () {
    'use strict';

    function init() {
        if (!window.AppAuth) { setTimeout(init, 100); return; }
        var layout = document.querySelector('.app-layout');
        if (!layout) return;

        var userName = window.AppAuth.getUserName();
        var shopName = window.AppAuth.getShopName();
        var isAdmin = window.AppAuth.isAdmin();

        var onModelLib = isActive('model-library.html');
        var currentView = getViewParam();

        var sidebar = document.createElement('div');
        sidebar.className = 'sidebar-global';
        sidebar.innerHTML = [
            '<div class="sidebar-logo">✨ AI 时尚视觉</div>',
            '<div class="sidebar-user">',
            '  <div class="sidebar-user-avatar">' + (userName.charAt(0) || 'U') + '</div>',
            '  <div class="sidebar-user-info">',
            '    <div class="sidebar-user-name">' + esc(userName) + '</div>',
            '    <div class="sidebar-user-shop">' + esc(shopName) + '</div>',
            '  </div>',
            '</div>',
            '<nav class="sidebar-nav">',
            '  <a href="index.html" class="sidebar-link' + (isActive('index.html') ? ' active' : '') + '">',
            '    <span class="sidebar-link-icon">🎨</span><span>批量换色</span>',
            '  </a>',
            '  <a href="scene.html" class="sidebar-link' + (isActive('scene.html') ? ' active' : '') + '">',
            '    <span class="sidebar-link-icon">🖼️</span><span>批量场景</span>',
            '  </a>',
            '  <a href="scene-generator.html" class="sidebar-link' + (isActive('scene-generator.html') ? ' active' : '') + '">',
            '    <span class="sidebar-link-icon">🌄</span><span>AI 场景生成</span>',
            '  </a>',
            '  <a href="video.html" class="sidebar-link' + (isActive('video.html') ? ' active' : '') + '">',
            '    <span class="sidebar-link-icon">🎬</span><span>视频生成</span>',
            '  </a>',
            // 模特库 - 可折叠父级
            '  <div class="sidebar-parent' + (onModelLib ? ' expanded' : '') + '">',
            '    <div class="sidebar-link sidebar-parent-toggle' + (onModelLib ? ' active-parent' : '') + '" onclick="this.parentElement.classList.toggle(\'expanded\')">',
            '      <span class="sidebar-link-icon">👤</span>',
            '      <span>AI 模特库</span>',
            '      <span class="sidebar-arrow">▾</span>',
            '    </div>',
            '    <div class="sidebar-submenu">',
            '      <a href="model-library.html?view=generate" class="sidebar-link sidebar-sub' + (onModelLib && currentView === 'generate' ? ' active' : '') + '" data-view="generate">',
            '        <span class="sidebar-link-icon">🎨</span><span>生成模特</span>',
            '      </a>',
            '      <a href="model-library.html?view=library" class="sidebar-link sidebar-sub' + (onModelLib && currentView === 'library' ? ' active' : '') + '" data-view="library">',
            '        <span class="sidebar-link-icon">📚</span><span>模特库</span>',
            '      </a>',
            '      <a href="model-library.html?view=history" class="sidebar-link sidebar-sub' + (onModelLib && currentView === 'history' ? ' active' : '') + '" data-view="history">',
            '        <span class="sidebar-link-icon">📜</span><span>生成历史</span>',
            '      </a>',
            '    </div>',
            '  </div>',
            '  <a href="template-manage.html" class="sidebar-link' + (isActive('template-manage.html') ? ' active' : '') + '">',
            '    <span class="sidebar-link-icon">🎭</span><span>场景库</span>',
            '  </a>',
            '  <a href="colorCard.html" class="sidebar-link' + (isActive('colorCard.html') ? ' active' : '') + '">',
            '    <span class="sidebar-link-icon">📚</span><span>色卡库</span>',
            '  </a>',
            '  <a href="list.html" class="sidebar-link' + (isActive('list.html') ? ' active' : '') + '">',
            '    <span class="sidebar-link-icon">📊</span><span>任务大盘</span>',
            '  </a>',
            isAdmin ? (
            '  <a href="admin.html" class="sidebar-link' + (isActive('admin.html') ? ' active' : '') + '">' +
            '    <span class="sidebar-link-icon">⚙️</span><span>用户管理</span></a>'
            ) : '',
            '</nav>',
            '<div class="sidebar-footer">',
            '  <button class="sidebar-logout-btn" onclick="window.AppAuth.logout()">🚪 退出登录</button>',
            '</div>'
        ].join('');

        var firstChild = layout.firstChild;
        if (firstChild) { layout.insertBefore(sidebar, firstChild); }
        else { layout.appendChild(sidebar); }

        injectStyles();

        // 模特库子菜单：当前页内切换 view，不刷新页面
        if (onModelLib) {
            sidebar.addEventListener('click', function (e) {
                var link = e.target.closest('[data-view]');
                if (!link) return;
                e.preventDefault();
                var view = link.getAttribute('data-view');

                // 更新 URL
                history.replaceState(null, '', '?view=' + view);

                // 更新侧边栏高亮
                sidebar.querySelectorAll('.sidebar-sub').forEach(function (el) {
                    el.classList.toggle('active', el.getAttribute('data-view') === view);
                });

                // 调用 Vue 的 go() 切换视图
                var app = document.querySelector('#app');
                if (app && app.__vue_app__) {
                    var vm = app.__vue_app__._instance.proxy;
                    if (typeof vm.go === 'function') {
                        vm.go(view);
                        return;
                    }
                }
                // 兜底：如果 Vue 实例拿不到，刷新页面
                window.location.href = 'model-library.html?view=' + view;
            });
        }
    }

    function esc(str) {
        if (!str) return '';
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function isActive(page) {
        return window.location.pathname.endsWith(page);
    }

    function getViewParam() {
        var m = window.location.search.match(/[?&]view=([^&]+)/);
        return m ? m[1] : 'generate';
    }

    function injectStyles() {
        if (document.getElementById('sidebar-global-styles')) return;
        var style = document.createElement('style');
        style.id = 'sidebar-global-styles';
        style.textContent = [
            '.sidebar-global {',
            '  width: 220px; min-width: 220px;',
            '  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);',
            '  color: #fff; display: flex; flex-direction: column;',
            '  position: fixed; top: 0; left: 0; bottom: 0; z-index: 100; overflow-y: auto;',
            '}',
            '.sidebar-logo {',
            '  padding: 22px 18px; font-size: 18px; font-weight: 700;',
            '  background: linear-gradient(135deg, #667eea, #764ba2);',
            '  -webkit-background-clip: text; -webkit-text-fill-color: transparent;',
            '  background-clip: text; border-bottom: 1px solid rgba(255,255,255,0.08);',
            '}',
            '.sidebar-user {',
            '  display: flex; align-items: center; gap: 10px; padding: 16px 18px;',
            '  border-bottom: 1px solid rgba(255,255,255,0.06);',
            '}',
            '.sidebar-user-avatar {',
            '  width: 36px; height: 36px; border-radius: 50%;',
            '  background: linear-gradient(135deg, #667eea, #764ba2);',
            '  display: flex; align-items: center; justify-content: center;',
            '  font-weight: 700; font-size: 14px; flex-shrink: 0;',
            '}',
            '.sidebar-user-name { font-size: 13px; font-weight: 600; }',
            '.sidebar-user-shop { font-size: 11px; color: rgba(255,255,255,0.4); margin-top: 2px; }',
            '.sidebar-nav { flex: 1; padding: 10px; display: flex; flex-direction: column; gap: 2px; }',
            '.sidebar-link {',
            '  display: flex; align-items: center; gap: 10px; padding: 10px 14px;',
            '  border-radius: 8px; color: rgba(255,255,255,0.6);',
            '  text-decoration: none; font-size: 14px; transition: all 0.2s; cursor: pointer;',
            '}',
            '.sidebar-link:hover { background: rgba(255,255,255,0.06); color: #fff; }',
            '.sidebar-link.active, .sidebar-link.active-parent {',
            '  background: linear-gradient(135deg, #667eea, #764ba2);',
            '  color: #fff; font-weight: 600;',
            '}',
            '.sidebar-link-icon { font-size: 16px; width: 20px; text-align: center; flex-shrink: 0; }',

            /* 箭头 */
            '.sidebar-arrow {',
            '  margin-left: auto; font-size: 10px; transition: transform 0.25s;',
            '  opacity: 0.5;',
            '}',
            '.sidebar-parent.expanded .sidebar-arrow { transform: rotate(180deg); opacity: 1; }',

            /* 子菜单容器 */
            '.sidebar-submenu {',
            '  max-height: 0; overflow: hidden; transition: max-height 0.3s ease;',
            '}',
            '.sidebar-parent.expanded .sidebar-submenu { max-height: 300px; }',

            /* 子菜单项 */
            '.sidebar-sub {',
            '  padding-left: 44px; font-size: 13px;',
            '}',

            '.sidebar-footer {',
            '  padding: 12px; border-top: 1px solid rgba(255,255,255,0.06);',
            '}',
            '.sidebar-logout-btn {',
            '  width: 100%; padding: 10px; border: 1px solid rgba(255,255,255,0.12);',
            '  border-radius: 8px; background: transparent;',
            '  color: rgba(255,255,255,0.5); font-size: 13px; cursor: pointer; transition: all 0.2s;',
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
