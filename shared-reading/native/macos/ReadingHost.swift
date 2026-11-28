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

import AppKit
import AVFoundation
import Foundation
import MediaPlayer

// Only fixed JSON commands are accepted. Article text is never evaluated as code.
@MainActor
final class ReadingHost: NSObject, @preconcurrency AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private var voice: AVSpeechSynthesisVoice?
    private var speed: Float = 1
    private var active: (AVSpeechUtterance, String)?
    private var targets: [(MPRemoteCommand, Any)] = []
    private var hasSession = false
    private var playing = false
    private var pendingPause: (utterance: AVSpeechUtterance?, request: String)?

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func emit(_ value: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: value) else { return }
        FileHandle.standardOutput.write(data)
        FileHandle.standardOutput.write(Data([10]))
    }

    func receive(line: String) {
        guard line.utf8.count <= 262144, let data = line.data(using: .utf8),
              let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            emit(["event": "error", "message": "Invalid speech protocol"])
            close()
            NSApplication.shared.terminate(nil)
            return
        }
        receive(message)
    }

    private func receive(_ message: [String: Any]) {
        let command = message["command"] as? String ?? ""
        switch command {
        case "initialize":
            guard message["version"] as? Int == 1 else { fail("Unsupported speech protocol"); return }
            stop()
            voice = AVSpeechSynthesisVoice(language: message["language"] as? String ?? "")
            speed = Float(message["speed"] as? Double ?? 1)
            emit(["event": "ready", "label": voice?.name ?? "AVSpeechSynthesizer"])
        case "speak":
            stop()
            guard let text = message["text"] as? String, !text.isEmpty, text.utf16.count <= 32768,
                  let id = message["id"] as? String, !id.isEmpty else { fail("Invalid speech request"); return }
            speed = Float(message["speed"] as? Double ?? Double(speed))
            let utterance = AVSpeechUtterance(string: text)
            utterance.voice = voice
            utterance.rate = min(AVSpeechUtteranceMaximumSpeechRate, max(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceDefaultSpeechRate * speed))
            active = (utterance, id)
            synthesizer.speak(utterance)
        case "pause":
            pendingPause = (message["request"] as? String).map { (active?.0, $0) }
            if synthesizer.isPaused || !synthesizer.isSpeaking {
                acknowledgePause()
            } else if !synthesizer.pauseSpeaking(at: .immediate) {
                fail("Speech could not be paused", id: active?.1 ?? "")
            }
        case "resume":
            if active != nil && !synthesizer.continueSpeaking() {
                fail("Speech could not be resumed", id: active?.1 ?? "")
            }
        case "speed": speed = Float(message["speed"] as? Double ?? 1)
        case "stop": stop()
        case "metadata": publish(message)
        default: fail("Unknown speech command")
        }
    }

    private func fail(_ message: String, id: String = "") {
        emit(["event": "error", "id": id, "message": message])
    }

    private func stop() {
        let stoppedId = active?.1 ?? ""
        active = nil
        let accepted = synthesizer.stopSpeaking(at: .immediate)
        if !synthesizer.isSpeaking {
            acknowledgePause()
        } else if !accepted {
            fail("Speech could not be stopped", id: stoppedId)
        }
    }

    private func acknowledgePause() {
        if let pending = pendingPause { emit(["event": "ack", "request": pending.request]); pendingPause = nil }
    }

    private func confirmSuspended(_ utterance: AVSpeechUtterance) {
        guard let pending = pendingPause, pending.utterance === utterance else { return }
        acknowledgePause()
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didPause utterance: AVSpeechUtterance) {
        confirmSuspended(utterance)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        guard let (current, id) = active, current === utterance else { return }
        emit(["event": "start", "id": id])
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        confirmSuspended(utterance)
        guard let (current, id) = active, current === utterance else { return }
        active = nil
        emit(["event": "end", "id": id])
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        confirmSuspended(utterance)
        guard let (current, id) = active, current === utterance else { return }
        active = nil
        fail("Speech was cancelled by the system", id: id)
    }

    private func publish(_ metadata: [String: Any]) {
        hasSession = metadata["active"] as? Bool ?? false
        playing = metadata["playing"] as? Bool ?? false
        guard hasSession else { clearMedia(); return }
        let commands = MPRemoteCommandCenter.shared()
        if targets.isEmpty {
            func register(_ remote: MPRemoteCommand, _ action: @escaping @MainActor @Sendable () -> String) {
                let target = remote.addTarget { [weak self] _ in
                    guard let self = self else { return .commandFailed }
                    DispatchQueue.main.async {
                        guard self.hasSession else { return }
                        self.emit(["event": "command", "action": action()])
                    }
                    return .success
                }
                targets.append((remote, target))
            }
            register(commands.playCommand) { "Play" }
            register(commands.pauseCommand) { "Pause" }
            register(commands.stopCommand) { "Stop" }
            register(commands.nextTrackCommand) { "Next" }
            register(commands.previousTrackCommand) { "Previous" }
            register(commands.togglePlayPauseCommand) { [weak self] in self?.playing == true ? "Pause" : "Play" }
        }
        commands.playCommand.isEnabled = true
        commands.pauseCommand.isEnabled = true
        commands.stopCommand.isEnabled = true
        commands.togglePlayPauseCommand.isEnabled = true
        commands.previousTrackCommand.isEnabled = metadata["previous"] as? Bool ?? false
        commands.nextTrackCommand.isEnabled = metadata["next"] as? Bool ?? false
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: metadata["title"] as? String ?? "",
            MPMediaItemPropertyArtist: metadata["author"] as? String ?? "",
            MPMediaItemPropertyAlbumTitle: metadata["album"] as? String ?? "",
            MPNowPlayingInfoPropertyPlaybackRate: playing ? speed : 0,
        ]
        MPNowPlayingInfoCenter.default().playbackState = playing ? .playing : .paused
    }

    private func clearMedia() {
        for (remote, target) in targets { remote.removeTarget(target) }
        targets.removeAll()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        MPNowPlayingInfoCenter.default().playbackState = .stopped
    }

    func close() {
        hasSession = false
        stop()
        clearMedia()
        synthesizer.delegate = nil
    }
}

MainActor.assumeIsolated {
    let application = NSApplication.shared
    application.setActivationPolicy(.prohibited)
    let host = ReadingHost()
    DispatchQueue.global(qos: .userInitiated).async {
        while let line = readLine() {
            DispatchQueue.main.async { host.receive(line: line) }
        }
        DispatchQueue.main.async { host.close(); NSApplication.shared.terminate(nil) }
    }
    application.run()
}
