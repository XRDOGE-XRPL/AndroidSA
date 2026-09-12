#include <array>
#include <iostream>
#include <string>
#include <vector>

#include "ClientState.h"

namespace {
std::array<std::string, 11> parseSummary(const std::string& summary) {
    std::array<std::string, 11> segments;
    std::size_t start = 0;

    for (std::size_t index = 0; index < segments.size() - 1; ++index) {
        const auto separator = summary.find('|', start);
        if (separator == std::string::npos) {
            return segments;
        }
        segments[index] = summary.substr(start, separator - start);
        start = separator + 1;
    }

    segments.back() = summary.substr(start);
    return segments;
}

bool expect(bool condition, const char* message) {
    if (!condition) {
        std::cerr << message << '\n';
        return false;
    }
    return true;
}
}  // namespace

int main() {
    androidsa::network::ClientState state;

    if (!expect(state.dispatchCommand("player:CJ"), "Expected player command to succeed")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("connect:play.example.org:7777"), "Expected connect:<server> command to succeed")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("simulate:tx"), "Expected simulate:tx command to succeed")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("simulate:rx"), "Expected simulate:rx command to succeed")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("latency:42"), "Expected latency override to succeed")) {
        return 1;
    }

    const auto connectedSummary = parseSummary(state.summary());
    if (!expect(connectedSummary[2] == "connected", "Connect command should move state to connected")) {
        return 1;
    }
    if (!expect(connectedSummary[4] == "play.example.org:7777", "Connect command should update server address")) {
        return 1;
    }
    if (!expect(connectedSummary[5] == "CJ", "Player command should update player name")) {
        return 1;
    }
    if (!expect(connectedSummary[6] == "42", "Latency override should persist in summary")) {
        return 1;
    }
    if (!expect(std::stoi(connectedSummary[7]) >= 5, "Packet send counter should reflect real UDP sends")) {
        return 1;
    }
    if (!expect(std::stoi(connectedSummary[8]) >= 5, "Packet receive counter should reflect real UDP receives")) {
        return 1;
    }
    if (!expect(connectedSummary[9] == "1", "Connect attempts should increment")) {
        return 1;
    }
    if (!expect(connectedSummary[10] == "latency:42", "Last command should track the latest accepted command")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("status"), "Status command should succeed")) {
        return 1;
    }

    const auto statusSummary = parseSummary(state.summary());
    if (!expect(statusSummary[3].find("Status snapshot ready for CJ on play.example.org:7777") == 0, "Status command should refresh diagnostics with expanded state")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("reconnect"), "Reconnect command should succeed")) {
        return 1;
    }

    const auto reconnectSummary = parseSummary(state.summary());
    if (!expect(reconnectSummary[9] == "2", "Reconnect should increment connection attempts")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("fail:timeout"), "Fail command should succeed")) {
        return 1;
    }

    const auto failureSummary = parseSummary(state.summary());
    if (!expect(failureSummary[2] == "error", "Fail command should switch state to error")) {
        return 1;
    }
    if (!expect(failureSummary[3] == "Failure simulated: timeout", "Fail command should update diagnostics")) {
        return 1;
    }

    if (!expect(!state.dispatchCommand("latency:fast"), "Invalid latency override should fail")) {
        return 1;
    }
    if (!expect(!state.dispatchCommand("simulate:loopback"), "Unsupported simulate direction should fail")) {
        return 1;
    }
    if (!expect(!state.dispatchCommand("ping\n"), "Commands with control characters should fail even if trim would remove them")) {
        return 1;
    }

    if (!expect(state.dispatchCommand("reset"), "Reset command should succeed")) {
        return 1;
    }

    const auto resetSummary = parseSummary(state.summary());
    if (!expect(resetSummary[1] == "RakNet-compatible UDP", "Reset should restore the default transport")) {
        return 1;
    }
    if (!expect(resetSummary[5] == "Guest", "Reset should restore the default player name")) {
        return 1;
    }
    if (!expect(resetSummary[7] == "0" && resetSummary[8] == "0", "Reset should clear packet counters")) {
        return 1;
    }
    if (!expect(resetSummary[10] == "reset", "Reset should become the latest command")) {
        return 1;
    }

    const auto events = state.recentEvents();
    if (!expect(!events.empty(), "Recent events should be available")) {
        return 1;
    }
    if (!expect(events.back() == "Session reset to initial state", "Reset should append a fresh event after clearing history")) {
        return 1;
    }
    if (!expect(events.size() == 1, "Reset should leave only the reset event after clearing history")) {
        return 1;
    }

    return 0;
}
