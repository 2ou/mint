// auth.js - 全局鉴权与 Axios 拦截器配置，及全局顶部导航栏

// --- 动态设置 API 基础路径 ---
// 如果是本地开发（假设前端用 Live Server 5500 端口），则指向 8080
// 如果是部署到服务器（前端由 Spring Boot 8080 端口直接托管），则使用相对路径
if (window.location.port === '8080' || window.location.hostname === '127.0.0.1') {
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

            // 🔴 智能判断：当前系统缓存的 shop_name 或 login_username 是不是 PINKSIR
            const loginUsername = localStorage.getItem("shop_name") || '';
            const isAdmin = loginUsername.toUpperCase() === 'PINKSIR';

            const adminButtonStr = isAdmin ? `
                <button id="global-admin-btn" style="
                    border: 1px solid #E6A23C; background: rgba(230,162,60,0.15); 
                    color: #E6A23C; cursor: pointer; font-size: 13px; 
                    padding: 5px 14px; border-radius: 4px; transition: 0.3s; font-weight: bold;
                ">⚙️ 用户管理</button>
            ` : '';

// 左侧显示系统名称，右侧显示店铺名、修改资料按钮和退出按钮
            header.innerHTML = `
                <div style="font-size: 16px; font-weight: bold; letter-spacing: 1px; display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 20px;">✨</span> AI 批量跑图小工具
                </div>
                
                <div style="display: flex; align-items: center; gap: 20px;">
                    <div style="display: flex; align-items: center; gap: 6px; background: rgba(255,255,255,0.1); padding: 5px 15px; border-radius: 20px;">
                        <span style="font-size: 14px;">🏪</span>
                        <span style="font-size: 14px; font-weight: bold; letter-spacing: 0.5px;">${shopName}</span>
                    </div>
                    
                    ${adminButtonStr}
                    
                    <button id="global-profile-btn" style="
                        border: 1px solid rgba(255,255,255,0.4); 
                        background: transparent; 
                        color: white; 
                        cursor: pointer; 
                        font-size: 13px; 
                        padding: 5px 14px; 
                        border-radius: 4px; 
                        transition: 0.3s;
                    ">✏️ 修改资料</button>

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

            // 🔴 新增：向全局注入一个精美的原生 DOM 弹窗 (默认隐藏)
            const currentRealName = localStorage.getItem("user_name") || '';
            const profileModalHTML = `
                <div id="global-profile-modal" style="display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.6); z-index: 80000; justify-content: center; align-items: center; backdrop-filter: blur(3px);">
                    <div style="background: white; padding: 30px; border-radius: 12px; width: 350px; box-shadow: 0 10px 30px rgba(0,0,0,0.2); font-family: 'PingFang SC', sans-serif;">
                        <h3 style="margin-top: 0; color: #303133; font-size: 18px; display: flex; align-items: center; gap: 8px;"><span>✏️</span> 修改个人资料</h3>
                        
                        <div style="margin-bottom: 20px; text-align: left;">
                            <label style="display: block; margin-bottom: 8px; font-size: 14px; color: #606266; font-weight: bold;">真实姓名 <span style="font-weight: normal; color: #909399; font-size: 12px;"></span></label>
                            <input type="text" id="profile-realname" value="${currentRealName}" placeholder="请输入真实姓名" style="width: 100%; height: 40px; padding: 0 12px; border: 1px solid #dcdfe6; border-radius: 6px; box-sizing: border-box; outline: none; font-size: 14px; transition: 0.2s;">
                        </div>
                        
                        <div style="margin-bottom: 20px; text-align: left;">
                            <label style="display: block; margin-bottom: 8px; font-size: 14px; color: #606266; font-weight: bold;">旧密码 <span style="font-weight: normal; color: #909399; font-size: 12px;">(如需修改密码请填写)</span></label>
                            <input type="password" id="profile-old-password" placeholder="请输入当前使用的旧密码" style="width: 100%; height: 40px; padding: 0 12px; border: 1px solid #dcdfe6; border-radius: 6px; box-sizing: border-box; outline: none; font-size: 14px; transition: 0.2s;">
                        </div>
                        
                        <div style="margin-bottom: 30px; text-align: left;">
                            <label style="display: block; margin-bottom: 8px; font-size: 14px; color: #606266; font-weight: bold;">新密码 <span style="font-weight: normal; color: #909399; font-size: 12px;">(不修改请留空)</span></label>
                            <input type="password" id="profile-password" placeholder="请输入新密码" style="width: 100%; height: 40px; padding: 0 12px; border: 1px solid #dcdfe6; border-radius: 6px; box-sizing: border-box; outline: none; font-size: 14px; transition: 0.2s;">
                        </div>
                        
                        <div style="display: flex; justify-content: flex-end; gap: 12px;">
                            <button id="profile-cancel-btn" style="padding: 10px 20px; border: 1px solid #dcdfe6; background: white; border-radius: 6px; cursor: pointer; color: #606266; font-size: 14px; transition: 0.2s;">取消</button>
                            <button id="profile-save-btn" style="padding: 10px 20px; border: none; background: #409EFF; color: white; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: bold; transition: 0.2s; box-shadow: 0 2px 6px rgba(64,158,255,0.3);">保存修改</button>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', profileModalHTML);

            // 绑定输入框焦点高亮特效
            const inputs = document.querySelectorAll('#global-profile-modal input');
            inputs.forEach(inp => {
                inp.onfocus = () => inp.style.borderColor = '#409EFF';
                inp.onblur = () => inp.style.borderColor = '#dcdfe6';
            });

            // --- 绑定事件 ---
            const profileBtn = document.getElementById('global-profile-btn');
            const profileModal = document.getElementById('global-profile-modal');
            const cancelBtn = document.getElementById('profile-cancel-btn');
            const saveBtn = document.getElementById('profile-save-btn');
            const logoutBtn = document.getElementById('global-logout-btn');

            // 🔴 为超级管理员按钮绑定跳转事件
            if (isAdmin) {
                const adminBtn = document.getElementById('global-admin-btn');
                if (adminBtn) {
                    adminBtn.onmouseenter = () => adminBtn.style.background = 'rgba(230,162,60,0.3)';
                    adminBtn.onmouseleave = () => adminBtn.style.background = 'rgba(230,162,60,0.15)';
                    adminBtn.addEventListener('click', () => {
                        window.location.href = 'admin.html';
                    });
                }
            }

            // 1. 修改按钮悬浮特效
            profileBtn.onmouseenter = () => profileBtn.style.background = 'rgba(255,255,255,0.15)';
            profileBtn.onmouseleave = () => profileBtn.style.background = 'transparent';

            // 2. 点击打开弹窗（并清空两个密码框）
            profileBtn.addEventListener('click', () => {
                document.getElementById('profile-old-password').value = '';
                document.getElementById('profile-password').value = '';
                profileModal.style.display = 'flex';
            });

            // 3. 点击取消隐藏弹窗
            cancelBtn.addEventListener('click', () => {
                profileModal.style.display = 'none';
            });

            // 4. 点击保存的请求逻辑
            saveBtn.addEventListener('click', async () => {
                const msg = window.ElementPlus ? ElementPlus.ElMessage : {success: alert, warning: alert, error: alert};

                const newRealName = document.getElementById('profile-realname').value.trim();
                const oldPassword = document.getElementById('profile-old-password').value.trim();
                const newPassword = document.getElementById('profile-password').value.trim();

                if (!newRealName) {
                    msg.warning('真实姓名不能为空！');
                    return;
                }

                // 前端拦截：如果填了新密码，就必须填旧密码
                if (newPassword && !oldPassword) {
                    msg.warning('修改密码必须输入旧密码！');
                    document.getElementById('profile-old-password').focus();
                    return;
                }

                try {
                    saveBtn.innerText = '保存中...';
                    saveBtn.disabled = true;
                    saveBtn.style.opacity = '0.7';

                    const res = await axios.post('/api/auth/update-profile', {
                        realName: newRealName,
                        oldPassword: oldPassword,
                        password: newPassword
                    });

                    if (res.data.success || res.data.code === 200) {
                        localStorage.setItem("user_name", newRealName);

                        if (newPassword) {
                            msg.success('密码修改成功，请使用新密码重新登录！');
                            setTimeout(() => {
                                localStorage.clear();
                                window.location.href = 'login.html';
                            }, 1500);
                        } else {
                            msg.success('资料修改成功！');
                            profileModal.style.display = 'none';
                        }
                    } else {
                        msg.error(res.data.message || '修改失败');
                    }
                } catch (e) {
                    msg.error('网络异常，修改失败');
                } finally {
                    saveBtn.innerText = '保存修改';
                    saveBtn.disabled = false;
                    saveBtn.style.opacity = '1';
                }
            });

            // 5. 退出登录逻辑
            logoutBtn.onmouseenter = () => logoutBtn.style.opacity = '0.85';
            logoutBtn.onmouseleave = () => logoutBtn.style.opacity = '1';

            logoutBtn.addEventListener('click', () => {
                // 优先借用 ElementPlus 的弹窗
                if (window.ElementPlus && ElementPlus.ElMessageBox) {
                    ElementPlus.ElMessageBox.confirm('确定要退出当前店铺账号吗？', '提示', {type: 'warning'}).then(() => {
                        localStorage.clear();
                        window.location.href = 'login.html';
                    }).catch(() => {
                    });
                } else {
                    if (confirm('确定要退出当前店铺账号吗？')) {
                        localStorage.clear();
                        window.location.href = 'login.html';
                    }
                }
            });

        });
    }
})();