/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.github.zly2006.zhihu.account

// 官方 unhuman 页只在该接口返回 code=0 或 block_level=0 后跳离；只观察已有请求，不主动探测。
internal val verificationResponseObserver =
    """
    (() => {
        if (!/(?:^|\/)(?:unhuman|risk_control)(?:\/|${'$'})/.test(location.pathname) || window.__zhppVerificationObserver) return;
        window.__zhppVerificationObserver = true;
        let completed = false;
        const observe = (url, response, status) => {
            try {
                const target = new URL(url, location.href);
                if (completed || target.origin !== 'https://www.zhihu.com' ||
                    target.pathname !== '/api/v4/anticrawl/new_captcha_appeal' || status < 200 || status >= 300) return;
                const result = JSON.parse(response);
                if (result.error || (result.code !== 0 && result.block_level !== 0)) return;
                completed = true;
                window.ZhihuVerification?.postMessage('verified');
            } catch (_) {}
        };
        const open = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            this.__zhppVerificationUrl = url;
            return open.apply(this, arguments);
        };
        const send = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function() {
            this.addEventListener('load', () => {
                if (!this.responseType || this.responseType === 'text') observe(this.responseURL || this.__zhppVerificationUrl, this.responseText, this.status);
                else if (this.responseType === 'json') observe(this.responseURL || this.__zhppVerificationUrl, JSON.stringify(this.response), this.status);
            }, { once: true });
            return send.apply(this, arguments);
        };
        const fetch = window.fetch;
        if (fetch) window.fetch = function() {
            return fetch.apply(this, arguments).then(response => {
                try {
                    const target = new URL(response.url, location.href);
                    if (target.hostname === 'www.zhihu.com' && target.pathname === '/api/v4/anticrawl/new_captcha_appeal')
                        response.clone().text().then(body => observe(response.url, body, response.status)).catch(() => {});
                } catch (_) {}
                return response;
            });
        };
    })();
    """.trimIndent()
