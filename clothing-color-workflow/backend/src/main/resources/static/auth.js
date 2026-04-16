// auth.js - 全局鉴权与 Axios 拦截器配置，及全局顶部导航栏

// --- 动态设置 API 基础路径 ---
// 如果是本地开发（假设前端用 Live Server 5500 端口），则指向 8080
// 如果是部署到服务器（前端由 Spring Boot 8080 端口直接托管），则使用相对路径
if (window.location.port === '8080') {
    axios.defaults.baseURL = 'http://localhost:8080';
} else {
    // 部署后，前端和后端同源，直接设为空字符串，使用相对路径
    axios.defaults.baseURL = 'http://39.108.115.240:10010';
}
// --------------------------

(function () {
    const pathname = window.location.pathname;
    const isLoginPage = pathname.endsWith('login.html');
    const token = localStorage.getItem("user_token");

    // 1. 登录鉴权拦截
    if (!token && !isLoginPage) {
        window.location.href = "login.html";
        return;
    }

    // 2. Axios 全局拦截器配置
    if (typeof axios !== 'undefined' && token) {
        axios.defaults.headers.common['X-User-Token'] = token;

        axios.interceptors.response.use(
            response => response,
            error => {
                if (error.response && error.response.status === 401) {
                    alert("登录状态已失效，请重新登录！");
                    localStorage.clear();
                    window.location.href = "login.html";
                }
                return Promise.reject(error);
            }
        );
    }

    // 3. 🔴 动态注入“全屏顶部系统导航栏”
    if (token && !isLoginPage) {
        window.addEventListener('DOMContentLoaded', () => {
            const shopName = localStorage.getItem("shop_name") || '未知店铺';

            // 为了防止顶部导航栏遮挡原页面内容，自动给 body 增加顶部内边距
            document.body.style.paddingTop = '65px';

            // 创建一条横跨全屏的顶部黑底导航栏
            const header = document.createElement('div');
            header.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 50px;
                background: #2b2f3a; /* 深邃的高级灰蓝色 */
                color: #ffffff;
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 0 30px;
                box-sizing: border-box;
                z-index: 10000;
                font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
                box-shadow: 0 2px 10px rgba(0,0,0,0.15);
            `;

            // 左侧显示系统名称，右侧显示店铺名和退出按钮
            header.innerHTML = `
                <div style="font-size: 16px; font-weight: bold; letter-spacing: 1px; display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 20px;">✨</span> AI 批量跑图小工具
                </div>
                
                <div style="display: flex; align-items: center; gap: 20px;">
                    <div style="display: flex; align-items: center; gap: 6px; background: rgba(255,255,255,0.1); padding: 5px 15px; border-radius: 20px;">
                        <span style="font-size: 14px;">🏪</span>
                        <span style="font-size: 14px; font-weight: bold; letter-spacing: 0.5px;">${shopName}</span>
                    </div>
                    
                    <button id="global-logout-btn" style="
                        border: none; 
                        background: #f56c6c; 
                        color: white; 
                        cursor: pointer; 
                        font-size: 13px; 
                        padding: 6px 16px; 
                        border-radius: 4px; 
                        font-weight: bold;
                        box-shadow: 0 2px 4px rgba(245, 108, 108, 0.3);
                    ">退出登录</button>
                </div>
            `;

            document.body.appendChild(header);

            // 绑定退出事件，鼠标移入增加一点透明度变化
            const logoutBtn = document.getElementById('global-logout-btn');
            logoutBtn.onmouseenter = () => logoutBtn.style.opacity = '0.85';
            logoutBtn.onmouseleave = () => logoutBtn.style.opacity = '1';

            logoutBtn.addEventListener('click', () => {
                if (confirm('确定要退出当前店铺账号吗？')) {
                    localStorage.clear();
                    window.location.href = 'login.html';
                }
            });
        });
    }
})();