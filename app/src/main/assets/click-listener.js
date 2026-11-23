/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// noinspection ES6ConvertVarToLetConst

(function() {
    // 移除之前的监听器（如果存在）
    if (window.zhihuPlusClickListener) {
        document.removeEventListener('click', window.zhihuPlusClickListener, true);
    }
    
    // 创建新的监听器
    window.zhihuPlusClickListener = function(event) {
        try {
            var target = event.target;
            var clickedElement = target;
            
            // 向上查找，找到实际的可点击元素
            while (clickedElement && clickedElement !== document.body) {
                // 检查是否是视频链接
                if (clickedElement.tagName === 'A' && clickedElement.classList.contains('video-box')) {
                    // 阻止默认行为
                    event.preventDefault();
                    event.stopPropagation();
                    
                    // 获取被点击元素的 outerHTML
                    var outerHtml = clickedElement.outerHTML;
                    // 调用 Android 接口
                    if (window.AndroidInterface) {
                        AndroidInterface.onElementClick(outerHtml);
                    }
                    return false;
                }
                
                // 检查是否在视频链接内部点击
                if (clickedElement.closest && clickedElement.closest('a.video-box')) {
                    // 阻止默认行为
                    event.preventDefault();
                    event.stopPropagation();
                    
                    var videoBox = clickedElement.closest('a.video-box');
                    var outerHtml = videoBox.outerHTML;
                    // 调用 Android 接口
                    if (window.AndroidInterface) {
                        AndroidInterface.onElementClick(outerHtml);
                    }
                    return false;
                }
                
                clickedElement = clickedElement.parentElement;
            }
            
            // body.outerHTML 在原生端会被解析成正文的第一个子元素，只有点中图片才转发。
            if (target.tagName === 'IMG' && window.AndroidInterface) {
                AndroidInterface.onElementClick(target.outerHTML);
            }
        } catch (e) {
            console.error('Error in click listener:', e);
        }
    };
    
    // 添加点击事件监听器
    document.addEventListener('click', window.zhihuPlusClickListener, true);
    
    // 专门为视频链接添加点击处理
    var videoBoxes = document.querySelectorAll('a.video-box');
    videoBoxes.forEach(function(videoBox) {
        videoBox.addEventListener('click', function(event) {
            event.preventDefault();
            event.stopPropagation();
            
            console.log('Video box clicked:', videoBox.getAttribute('data-lens-id'));
            
            if (window.AndroidInterface) {
                AndroidInterface.onElementClick(videoBox.outerHTML);
            }
        }, true);
    });
    
    // 监控动态添加的视频元素
    var observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            mutation.addedNodes.forEach(function(node) {
                if (node.nodeType === 1) { // 仅处理元素节点。
                    if (node.tagName === 'A' && node.classList.contains('video-box')) {
                        // 新添加的视频链接
                        node.addEventListener('click', function(event) {
                            event.preventDefault();
                            event.stopPropagation();
                            
                            console.log('Dynamic video box clicked:', node.getAttribute('data-lens-id'));
                            
                            if (window.AndroidInterface) {
                                AndroidInterface.onElementClick(node.outerHTML);
                            }
                        }, true);
                    } else {
                        // 检查新添加元素内的视频链接
                        var videoBoxes = node.querySelectorAll && node.querySelectorAll('a.video-box');
                        if (videoBoxes) {
                            videoBoxes.forEach(function(videoBox) {
                                videoBox.addEventListener('click', function(event) {
                                    event.preventDefault();
                                    event.stopPropagation();
                                    
                                    console.log('Nested video box clicked:', videoBox.getAttribute('data-lens-id'));
                                    
                                    if (window.AndroidInterface) {
                                        AndroidInterface.onElementClick(videoBox.outerHTML);
                                    }
                                }, true);
                            });
                        }
                    }
                }
            });
        });
    });
    
    observer.observe(document.body, {
        childList: true,
        subtree: true
    });
})();
