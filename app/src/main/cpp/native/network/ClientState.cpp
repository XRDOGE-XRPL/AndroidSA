#include "ClientState.h"

#include <algorithm>
#include <charconv>
#include <cctype>
#include <sstream>
#include <string_view>

#include "native/logging/Logger.h"

namespace androidsa::network {
namespace {
constexpr std::size_t kMaxCommandLength = 64;
constexpr std::size_t kMaxEventCount = 12;

std::string trim(std::string value) {
    value.erase(value.begin(), std::find_if(value.begin(), value.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
    value.erase(std::find_if(value.rbegin(), value.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), value.end());
    return value;
}

std::string lower(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

bool startsWith(const std::string& value, std::string_view prefix) {
    return value.rfind(prefix, 0) == 0;
}

bool containsInvalidSummaryCharacters(std::string_view value) {
    return std::any_of(value.begin(), value.end(), [](unsigned char ch) {
        return ch < 0x20 || ch == 0x7F || ch == '|';
    });
}

bool extractExactCommandValue(
    const std::string& sanitized,
    const std::string& normalized,
    std::string_view keyword,
    std::string* outValue
) {
    const auto separator = normalized.find(':');
    if (separator == std::string::npos || normalized.substr(0, separator) != keyword) {
        return false;
    }

    *outValue = trim(sanitized.substr(separator + 1));
    return !outValue->empty();
}

bool parseNonNegativeInteger(const std::string& value, int* result) {
    const auto* begin = value.data();
    const auto* end = value.data() + value.size();
    const auto parsed = std::from_chars(begin, end, *result);
    return parsed.ec == std::errc() && parsed.ptr == end && *result >= 0;
}
}  // namespace

std::string ClientState::summary() {
    std::lock_guard lock(mutex_);
    std::ostringstream stream;
    stream << "AndroidSA"
           << '|'
           << transport_
           << '|'
           << state_
           << '|'
           << diagnostics_
           << '|'
           << serverAddress_
           << '|'
           << playerName_
           << '|'
           << latencyMs_
           << '|'
           << packetsSent_
           << '|'
           << packetsReceived_
           << '|'
           << connectionAttempts_
           << '|'
           << lastCommand_;
    return stream.str();
}

std::vector<std::string> ClientState::recentEvents() {
    std::lock_guard lock(mutex_);
    return eventLog_;
}

void ClientState::resetLocked() {
    transport_ = "RakNet-compatible UDP";
    state_ = "initializing";
    diagnostics_ = "NDK bootstrap complete";
    serverAddress_ = "demo.sa-mp.local:7777";
    playerName_ = "Guest";
    latencyMs_ = 0;
    packetsSent_ = 0;
    packetsReceived_ = 0;
    connectionAttempts_ = 0;
    lastCommand_ = "startup";
    eventLog_.clear();
}

void ClientState::recordEventLocked(const std::string& event) {
    eventLog_.push_back(event);
    if (eventLog_.size() > kMaxEventCount) {
        eventLog_.erase(eventLog_.begin(), eventLog_.begin() + static_cast<long>(eventLog_.size() - kMaxEventCount));
    }
}

bool ClientState::dispatchCommand(const std::string& command) {
    if (containsInvalidSummaryCharacters(command)) {
        return false;
    }

    const auto sanitized = trim(command);
    if (sanitized.empty()) {
        return false;
    }
    if (sanitized.size() > kMaxCommandLength) {
        return false;
    }

    const auto normalized = lower(sanitized);

    std::lock_guard lock(mutex_);
    std::string eventMessage;
    if (normalized == "ping") {
        state_ = "ready";
        diagnostics_ = "Ping acknowledged";
        latencyMs_ = std::max(latencyMs_, 24);
        eventMessage = "Ping acknowledged by native runtime";
    } else if (normalized == "connect") {
        ++connectionAttempts_;
        state_ = "connected";
        diagnostics_ = "Connection established to " + serverAddress_;
        latencyMs_ = latencyMs_ > 0 ? latencyMs_ : 48;
        packetsSent_ += 3;
        packetsReceived_ += 2;
        eventMessage = "Connected to " + serverAddress_;
    } else if (startsWith(normalized, "connect:")) {
        std::string serverAddress;
        if (!extractExactCommandValue(sanitized, normalized, "connect", &serverAddress)) {
            return false;
        }
        serverAddress_ = serverAddress;
        ++connectionAttempts_;
        state_ = "connected";
        diagnostics_ = "Connection established to " + serverAddress_;
        latencyMs_ = latencyMs_ > 0 ? latencyMs_ : 48;
        packetsSent_ += 3;
        packetsReceived_ += 2;
        eventMessage = "Connected to configured server " + serverAddress_;
    } else if (normalized == "reconnect") {
        ++connectionAttempts_;
        state_ = "connected";
        diagnostics_ = "Reconnected to " + serverAddress_;
        latencyMs_ = latencyMs_ > 0 ? latencyMs_ : 36;
        packetsSent_ += 1;
        packetsReceived_ += 1;
        eventMessage = "Reconnect flow completed for " + serverAddress_;
    } else if (normalized == "disconnect") {
        state_ = "disconnected";
        diagnostics_ = "Connection closed";
        latencyMs_ = 0;
        eventMessage = "Disconnected from server session";
    } else if (normalized == "reset") {
        resetLocked();
        diagnostics_ = "Native state reset";
        eventMessage = "Session reset to initial state";
    } else if (normalized == "status") {
        diagnostics_ = "Status snapshot ready for " + playerName_ + " on " + serverAddress_;
        eventMessage = "Status snapshot refreshed";
    } else if (startsWith(normalized, "transport")) {
        std::string transport;
        if (!extractExactCommandValue(sanitized, normalized, "transport", &transport)) {
            return false;
        }
        transport_ = transport;
        if (state_ != "connected") {
            state_ = "ready";
        }
        diagnostics_ = "Transport switched to " + transport;
        eventMessage = "Transport profile changed to " + transport;
    } else if (normalized == "diagnostics" || startsWith(normalized, "diagnostics:")) {
        std::string diagnosticsValue;
        if (!extractExactCommandValue(sanitized, normalized, "diagnostics", &diagnosticsValue)) {
            return false;
        }
        diagnostics_ = "Manual diagnostics: " + diagnosticsValue;
        eventMessage = "Manual diagnostics updated";
    } else if (normalized == "player" || startsWith(normalized, "player:")) {
        std::string playerName;
        if (!extractExactCommandValue(sanitized, normalized, "player", &playerName)) {
            return false;
        }
        playerName_ = playerName;
        diagnostics_ = "Player profile updated";
        eventMessage = "Player identity set to " + playerName_;
    } else if (normalized == "latency" || startsWith(normalized, "latency:")) {
        std::string latencyValue;
        int parsedLatency = 0;
        if (!extractExactCommandValue(sanitized, normalized, "latency", &latencyValue) ||
            !parseNonNegativeInteger(latencyValue, &parsedLatency)) {
            return false;
        }
        latencyMs_ = parsedLatency;
        diagnostics_ = "Latency overridden to " + std::to_string(latencyMs_) + " ms";
        eventMessage = "Latency override applied";
    } else if (normalized == "fail" || startsWith(normalized, "fail:")) {
        std::string reason;
        if (!extractExactCommandValue(sanitized, normalized, "fail", &reason)) {
            return false;
        }
        state_ = "error";
        diagnostics_ = "Failure simulated: " + reason;
        latencyMs_ = 0;
        eventMessage = "Failure state entered: " + reason;
    } else if (normalized == "simulate" || startsWith(normalized, "simulate:")) {
        std::string trafficDirection;
        if (!extractExactCommandValue(sanitized, normalized, "simulate", &trafficDirection)) {
            return false;
        }
        const auto normalizedDirection = lower(trafficDirection);
        if (normalizedDirection == "rx") {
            packetsReceived_ += 5;
            diagnostics_ = "Simulated inbound traffic";
            eventMessage = "Inbound traffic simulation recorded";
        } else if (normalizedDirection == "tx") {
            packetsSent_ += 5;
            diagnostics_ = "Simulated outbound traffic";
            eventMessage = "Outbound traffic simulation recorded";
        } else {
            return false;
        }
    } else {
        state_ = "command:" + sanitized;
        diagnostics_ = "Last JNI command: " + sanitized;
        eventMessage = "Generic command dispatched: " + sanitized;
    }

    lastCommand_ = sanitized;
    recordEventLocked(eventMessage);
    logging::Logger::info("AndroidSA", diagnostics_);
    return true;
}

}  // namespace androidsa::network
