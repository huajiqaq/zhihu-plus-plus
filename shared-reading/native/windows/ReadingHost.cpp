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

#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Storage.Streams.h>
#include <winrt/Windows.Data.Json.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Media.Core.h>
#include <winrt/Windows.Media.Playback.h>
#include <winrt/Windows.Media.SpeechSynthesis.h>
#include <algorithm>
#include <cmath>
#include <fcntl.h>
#include <io.h>
#include <iostream>
#include <memory>
#include <mutex>
#include <string>

using namespace winrt;
using namespace Windows::Foundation;
using namespace Windows::Data::Json;
using namespace Windows::Media;
using namespace Windows::Media::Core;
using namespace Windows::Media::Playback;
using namespace Windows::Media::SpeechSynthesis;

namespace {
std::mutex outputMutex;
void emit(JsonObject const& event) {
    std::lock_guard lock(outputMutex);
    std::cout << to_string(event.Stringify()) << '\n' << std::flush;
}
void emitEvent(std::wstring_view type, hstring const& id = {}, hstring const& detail = {}) {
    JsonObject value;
    value.Insert(L"event", JsonValue::CreateStringValue(type));
    value.Insert(L"id", JsonValue::CreateStringValue(id));
    if (!detail.empty()) value.Insert(L"message", JsonValue::CreateStringValue(detail));
    emit(value);
}
void command(std::wstring_view action) {
    JsonObject value;
    value.Insert(L"event", JsonValue::CreateStringValue(L"command"));
    value.Insert(L"action", JsonValue::CreateStringValue(action));
    emit(value);
}

// This process owns only one utterance. The JVM owns queue, retries and account data.
class ReadingHost : public std::enable_shared_from_this<ReadingHost> {
    std::recursive_mutex mutex;
    SpeechSynthesizer synth{nullptr};
    MediaPlayer player{nullptr};
    SystemMediaTransportControls controls{nullptr};
    IAsyncOperation<SpeechSynthesisStream> synthesis{nullptr};
    SpeechSynthesisStream stream{nullptr};
    event_token buttons{}, opened{}, ended{}, failed{}, position{}, playbackChanged{};
    hstring pauseRequest;
    uint64_t generation = 0;
    hstring id;
    bool paused = false, prepared = false, closed = false;
    double speed = 1.0;

    void stopLocked() {
        ++generation;
        id = {};
        paused = false;
        prepared = false;
        if (synthesis) { synthesis.Cancel(); synthesis = nullptr; }
        if (player) {
            player.Pause();
            if (opened.value) player.MediaOpened(opened);
            if (ended.value) player.MediaEnded(ended);
            if (failed.value) player.MediaFailed(failed);
            opened = {}; ended = {}; failed = {};
            player.Source(nullptr);
        }
        if (stream) { stream.Close(); stream = nullptr; }
    }

    void installStream(SpeechSynthesisStream const& result, uint64_t token, hstring const& utteranceId) {
        std::lock_guard lock(mutex);
        if (closed || token != generation) { result.Close(); return; }
        synthesis = nullptr;
        stream = result;
        auto weak = weak_from_this();
        opened = player.MediaOpened([weak, token, utteranceId](auto const&, auto const&) {
            if (auto self = weak.lock()) {
                std::lock_guard guard(self->mutex);
                if (self->closed || token != self->generation) return;
                self->prepared = true;
                self->updateTimeline();
                if (!self->paused) { self->player.Play(); emitEvent(L"start", utteranceId); }
            }
        });
        ended = player.MediaEnded([weak, token, utteranceId](auto const&, auto const&) {
            if (auto self = weak.lock()) {
                std::lock_guard guard(self->mutex);
                if (self->closed || token != self->generation) return;
                self->id = {};
                emitEvent(L"end", utteranceId);
            }
        });
        failed = player.MediaFailed([weak, token, utteranceId](auto const&, MediaPlayerFailedEventArgs const& args) {
            if (auto self = weak.lock()) {
                std::lock_guard guard(self->mutex);
                if (!self->closed && token == self->generation) emitEvent(L"error", utteranceId, args.ErrorMessage());
            }
        });
        player.Source(MediaSource::CreateFromStream(stream, stream.ContentType()));
    }

    void acknowledgePause() {
        if (pauseRequest.empty()) return;
        JsonObject acknowledgement;
        acknowledgement.Insert(L"event", JsonValue::CreateStringValue(L"ack"));
        acknowledgement.Insert(L"request", JsonValue::CreateStringValue(pauseRequest));
        pauseRequest = {};
        emit(acknowledgement);
    }

    void updateTimeline() {
        if (!controls || !player || !prepared) return;
        SystemMediaTransportControlsTimelineProperties timeline;
        timeline.StartTime(TimeSpan{0});
        timeline.MinSeekTime(TimeSpan{0});
        timeline.EndTime(player.PlaybackSession().NaturalDuration());
        timeline.MaxSeekTime(player.PlaybackSession().NaturalDuration());
        timeline.Position(player.PlaybackSession().Position());
        controls.UpdateTimelineProperties(timeline);
    }

public:
    void initialize(JsonObject const& message) {
        std::lock_guard lock(mutex);
        if (message.GetNamedNumber(L"version", 0) != 1) throw hresult_invalid_argument(L"Unsupported speech protocol");
        stopLocked();
        if (!synth) synth = SpeechSynthesizer();
        if (!player) {
            player = MediaPlayer();
            player.AutoPlay(false);
            player.CommandManager().IsEnabled(false);
            controls = player.SystemMediaTransportControls();
            controls.IsEnabled(false);
            auto weak = weak_from_this();
            buttons = controls.ButtonPressed([weak](auto const&, SystemMediaTransportControlsButtonPressedEventArgs const& args) {
                if (auto self = weak.lock()) {
                    std::lock_guard guard(self->mutex);
                    if (self->closed || !self->controls.IsEnabled()) return;
                    switch (args.Button()) {
                    case SystemMediaTransportControlsButton::Play: command(L"Play"); break;
                    case SystemMediaTransportControlsButton::Pause: command(L"Pause"); break;
                    case SystemMediaTransportControlsButton::Stop: command(L"Stop"); break;
                    case SystemMediaTransportControlsButton::Previous: command(L"Previous"); break;
                    case SystemMediaTransportControlsButton::Next: command(L"Next"); break;
                    default: break;
                    }
                }
            });
            playbackChanged = player.PlaybackSession().PlaybackStateChanged([weak](auto const&, auto const&) {
                if (auto self = weak.lock()) {
                    std::lock_guard guard(self->mutex);
                    if (!self->closed && self->paused && self->player.PlaybackSession().PlaybackState() != MediaPlaybackState::Playing) self->acknowledgePause();
                }
            });
            position = player.PlaybackSession().PositionChanged([weak](auto const&, auto const&) {
                if (auto self = weak.lock()) {
                    std::lock_guard guard(self->mutex);
                    if (!self->closed) self->updateTimeline();
                }
            });
        }
        auto language = message.GetNamedString(L"language", L"");
        VoiceInformation selected{nullptr};
        for (auto const& voice : SpeechSynthesizer::AllVoices()) {
            if (voice.Language() == language) { selected = voice; break; }
            auto requested = std::wstring_view(language);
            auto offered = std::wstring_view(voice.Language());
            if (!selected && requested.substr(0, requested.find(L'-')) == offered.substr(0, offered.find(L'-'))) selected = voice;
        }
        if (selected) synth.Voice(selected);
        speed = std::clamp(message.GetNamedNumber(L"speed", 1), 0.5, 2.0);
        JsonObject ready;
        ready.Insert(L"event", JsonValue::CreateStringValue(L"ready"));
        ready.Insert(L"label", JsonValue::CreateStringValue(synth.Voice().DisplayName()));
        emit(ready);
    }

    void receive(JsonObject const& message) {
        std::lock_guard lock(mutex);
        if (closed) return;
        auto name = message.GetNamedString(L"command", L"");
        if (name == L"initialize") { initialize(message); return; }
        if (!player || !synth) throw hresult_illegal_method_call(L"Speech host is not initialized");
        if (name == L"speak") {
            stopLocked();
            auto text = message.GetNamedString(L"text", L"");
            id = message.GetNamedString(L"id", L"");
            if (text.empty() || text.size() > 32768 || id.empty()) throw hresult_invalid_argument(L"Invalid speech request");
            auto requested = message.GetNamedNumber(L"speed", speed);
            if (!std::isfinite(requested)) throw hresult_invalid_argument(L"Invalid speech rate");
            speed = std::clamp(requested, 0.5, 2.0);
            synth.Options().SpeakingRate(speed);
            auto token = generation;
            auto utteranceId = id;
            auto weak = weak_from_this();
            synthesis = synth.SynthesizeTextToStreamAsync(text);
            synthesis.Completed([weak, token, utteranceId](auto const& operation, AsyncStatus status) {
                if (auto self = weak.lock()) {
                    try {
                        if (status == AsyncStatus::Canceled) return;
                        auto result = operation.GetResults();
                        self->installStream(result, token, utteranceId);
                    } catch (hresult_error const& error) {
                        std::lock_guard guard(self->mutex);
                        if (!self->closed && token == self->generation) emitEvent(L"error", utteranceId, error.message());
                    }
                }
            });
        } else if (name == L"pause") {
            paused = true;
            pauseRequest = message.GetNamedString(L"request", L"");
            player.Pause();
            if (player.PlaybackSession().PlaybackState() != MediaPlaybackState::Playing) acknowledgePause();
        } else if (name == L"resume") {
            paused = false;
            if (prepared) { player.Play(); emitEvent(L"start", id); }
        } else if (name == L"stop") {
            stopLocked();
            acknowledgePause();
        } else if (name == L"speed") {
            speed = std::clamp(message.GetNamedNumber(L"speed", 1), 0.5, 2.0);
        } else if (name == L"metadata") {
            bool active = message.GetNamedBoolean(L"active", false);
            controls.IsEnabled(active);
            controls.IsPlayEnabled(active);
            controls.IsPauseEnabled(active);
            controls.IsStopEnabled(active);
            controls.IsPreviousEnabled(active && message.GetNamedBoolean(L"previous", false));
            controls.IsNextEnabled(active && message.GetNamedBoolean(L"next", false));
            controls.PlaybackStatus(!active ? MediaPlaybackStatus::Stopped : message.GetNamedBoolean(L"playing", false) ? MediaPlaybackStatus::Playing : MediaPlaybackStatus::Paused);
            auto display = controls.DisplayUpdater();
            if (active) {
                display.Type(MediaPlaybackType::Music);
                display.MusicProperties().Title(message.GetNamedString(L"title", L""));
                display.MusicProperties().Artist(message.GetNamedString(L"author", L""));
                display.MusicProperties().AlbumTitle(message.GetNamedString(L"album", L""));
                display.Update();
            } else display.ClearAll();
        } else throw hresult_invalid_argument(L"Unknown speech command");
    }

    void close() {
        std::lock_guard lock(mutex);
        if (closed) return;
        closed = true;
        stopLocked();
        if (controls) { controls.IsEnabled(false); controls.ButtonPressed(buttons); controls.DisplayUpdater().ClearAll(); }
        if (player) { player.PlaybackSession().PositionChanged(position); player.PlaybackSession().PlaybackStateChanged(playbackChanged); player.Close(); player = nullptr; }
        if (synth) { synth.Close(); synth = nullptr; }
    }
};
}

int main() {
    _setmode(_fileno(stdin), _O_BINARY);
    _setmode(_fileno(stdout), _O_BINARY);
    try {
        init_apartment(apartment_type::multi_threaded);
        auto host = std::make_shared<ReadingHost>();
        std::string line;
        while (std::getline(std::cin, line)) {
            if (line.size() > 262144) { emitEvent(L"error", {}, L"Speech command too large"); break; }
            JsonObject message;
            try {
                message = JsonObject::Parse(to_hstring(line));
                host->receive(message);
            } catch (hresult_error const& error) {
                emitEvent(L"error", message ? message.GetNamedString(L"id", L"") : hstring{}, error.message());
            } catch (std::exception const&) {
                emitEvent(L"error", {}, L"Invalid speech command");
            }
        }
        host->close();
        return 0;
    } catch (hresult_error const& error) {
        emitEvent(L"error", {}, error.message());
        return 1;
    }
}
