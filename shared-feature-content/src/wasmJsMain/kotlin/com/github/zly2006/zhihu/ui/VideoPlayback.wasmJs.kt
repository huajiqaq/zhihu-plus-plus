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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.github.zly2006.zhihu.i18n.ContentStrings
import com.github.zly2006.zhihu.platform.AudioActivityCoordinator

@Composable
internal actual fun PlatformVideoPlayer(url: String, position: VideoPlaybackPosition, active: Boolean, onFailure: () -> Unit, modifier: Modifier) {
    val failure = rememberUpdatedState(onFailure)
    val density = LocalDensity.current.density
    val video = remember(url) {
        createVideoElement(url, ContentStrings.videoPlayerTitle, position.seconds, position.playing, position.rate, position.muted,
            { seconds, playing, rate, muted -> position.seconds = seconds; position.playing = playing; position.rate = rate; position.muted = muted },
            { failure.value() },
        )
    }
    DisposableEffect(video) {
        val lease = AudioActivityCoordinator.acquirePlayback(onInterrupted = { video.setActive(false); position.playing = false })
        if (lease == null) failure.value()
        onDispose { video.close(); lease?.close() }
    }
    LaunchedEffect(active) { video.setActive(active) }
    Spacer(modifier.onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInWindow()
        video.layout(rect.left / density, rect.top / density, rect.width / density, rect.height / density)
    })
}

private external interface BrowserVideo : JsAny {
    fun layout(left: Float, top: Float, width: Float, height: Float)
    fun setActive(active: Boolean)
    fun close()
}

@JsFun("""(url, label, start, playing, rate, muted, changed, failed) => {
    const video = document.createElement('video');
    video.controls = true; video.playsInline = true; video.preload = 'metadata';
    video.setAttribute('aria-label', label); video.setAttribute('referrerpolicy', 'no-referrer');
    video.style.cssText = 'position:fixed;z-index:10000;background:black;object-fit:contain;';
    let closed = false, active = true, wanted = playing, applying = false;
    const publish = () => { if(!closed) changed(Number.isFinite(video.currentTime)?video.currentTime:0,wanted,video.playbackRate,video.muted); };
    const play = () => {
        if(!closed && active && wanted) video.play().catch(error => {
            if(closed) return;
            if(error.name === 'NotAllowedError') { wanted=false;publish(); } else if(error.name !== 'AbortError') failed();
        });
    };
    const loaded = () => {
        video.currentTime = Math.min(start,Number.isFinite(video.duration)?video.duration:start);
        video.playbackRate=rate;video.muted=muted;play();
    };
    const pause = () => { if(!applying && active && !document.hidden) wanted=false;publish(); };
    const started = () => { wanted=true;publish(); };
    const visibility = () => {
        applying=true;
        if(document.hidden) video.pause(); else play();
        queueMicrotask(()=>applying=false);
    };
    video.addEventListener('loadedmetadata',loaded);
    video.addEventListener('timeupdate',publish);
    video.addEventListener('ratechange',publish);
    video.addEventListener('volumechange',publish);
    video.addEventListener('play',started);
    video.addEventListener('pause',pause);
    video.addEventListener('ended',()=>{wanted=false;publish()});
    video.addEventListener('error',()=>{if(!closed)failed()});
    document.addEventListener('visibilitychange',visibility);
    video.src=url;document.body.appendChild(video);
    return {
        layout(left,top,width,height){
            const root=document.getElementById('app')?.getBoundingClientRect();
            video.style.left=(left+(root?.left||0))+'px';video.style.top=(top+(root?.top||0))+'px';
            video.style.width=Math.max(0,width)+'px';video.style.height=Math.max(0,height)+'px';
        },
        setActive(value){active=value;applying=true;if(active&&!document.hidden)play();else video.pause();queueMicrotask(()=>applying=false)},
        close(){if(closed)return;publish();closed=true;document.removeEventListener('visibilitychange',visibility);video.pause();video.removeAttribute('src');video.load();video.remove()}
    };
}""")
private external fun createVideoElement(url: String, label: String, start: Double, playing: Boolean, rate: Double, muted: Boolean, changed: (Double, Boolean, Double, Boolean) -> Unit, failed: () -> Unit): BrowserVideo
