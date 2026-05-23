// auth.js - 全局鉴权与 Axios 拦截器配置（纯鉴权，不操作 DOM）

// --- 动态设置 API 基础路径 ---
// 如果是本地开发，则指向 8080
if (window.location.hostname === 'localhost') {
    axios.defaults.baseURL = 'http://localhost:8080';
} else {
    // 部署后，前端和后端同源，直接设为部署地址
    axios.defaults.baseURL = 'http://39.108.115.240:10010';
}
// --------------------------

(function () {
    'use strict';

    var pathname = window.location.pathname;
    var isLoginPage = pathname.endsWith('login.html');
    var token = localStorage.getItem("user_token");

    // 1. 登录鉴权拦截
    if (!token && !isLoginPage) {
        window.location.href = "login.html";
        return;
    }

    // 2. Axios 全局拦截器配置
    if (typeof axios !== 'undefined' && token) {
        axios.defaults.headers.common['X-User-Token'] = token;

        axios.interceptors.response.use(
            function (response) { return response; },
            function (error) {
                if (error.response && error.response.status === 401) {
                    alert("登录状态已失效，请重新登录！");
                    localStorage.clear();
                    window.location.href = "login.html";
                }
                return Promise.reject(error);
            }
        );
    }

    /* ===========================
       AppAuth 公开 API
       供 Sidebar 及其他模块使用
       =========================== */

    window.AppAuth = {
        /**
         * 获取当前用户名
         * @returns {string}
         */
        getUserName: function () {
            return localStorage.getItem('user_name') || '未知用户';
        },

        /**
         * 获取当前店铺名
         * @returns {string}
         */
        getShopName: function () {
            return localStorage.getItem('shop_name') || '未知店铺';
        },

        /**
         * 判断当前用户是否为管理员
         * @returns {boolean}
         */
        isAdmin: function () {
            return (localStorage.getItem('shop_name') || '').toUpperCase() === 'PINKSIR';
        },

        /**
         * 退出登录：清除所有 localStorage 并跳转到登录页
         */
        logout: function () {
            localStorage.clear();
            window.location.href = 'login.html';
        }
    };

})();
