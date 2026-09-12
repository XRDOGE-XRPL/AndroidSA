#include "ClientState.h"

#include <algorithm>
#include <cctype>
#include <sstream>
#include <string_view>

#include "native/logging/Logger.h"

namespace androidsa::network {
namespace {
constexpr std::size_t kMaxCommandLength = 64;

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
}  // namespace

std::string ClientState::summary() {
    std::lock_guard lock(mutex_);
    std::ostringstream stream;
    stream << "AndroidSA" << '|' << transport_ << '|' << state_ << '|' << diagnostics_;
    return stream.str();
}

bool ClientState::dispatchCommand(const std::string& command) {
    const auto sanitized = trim(command);
    if (sanitized.empty()) {
        return false;
    }
    if (sanitized.size() > kMaxCommandLength) {
        return false;
    }
    if (containsInvalidSummaryCharacters(sanitized)) {
        return false;
    }

    const auto normalized = lower(sanitized);
    const auto transportSeparator = normalized.find(':');

    std::lock_guard lock(mutex_);
    if (normalized == "ping") {
        state_ = "ready";
        diagnostics_ = "Ping acknowledged";
    } else if (normalized == "connect") {
        state_ = "connected";
        diagnostics_ = "Connection established";
    } else if (normalized == "disconnect") {
        state_ = "disconnected";
        diagnostics_ = "Connection closed";
    } else if (normalized == "reset") {
        transport_ = "RakNet-compatible UDP";
        state_ = "initializing";
        diagnostics_ = "Native state reset";
    } else if (normalized == "status") {
        diagnostics_ = "Status snapshot requested";
    } else if (startsWith(normalized, "transport")) {
        if (transportSeparator == std::string::npos) {
            return false;
        }

        const auto transportKeyword = normalized.substr(0, transportSeparator);
        if (transportKeyword != "transport") {
            return false;
        }

        const auto transport = trim(sanitized.substr(transportSeparator + 1));
        if (transport.empty()) {
            return false;
        }
        transport_ = transport;
        state_ = "ready";
        diagnostics_ = "Transport switched to " + transport;
    } else if (normalized == "diagnostics" || startsWith(normalized, "diagnostics:")) {
        if (transportSeparator == std::string::npos) {
            return false;
        }

        const auto diagnosticsKeyword = normalized.substr(0, transportSeparator);
        if (diagnosticsKeyword != "diagnostics") {
            return false;
        }

        const auto diagnosticsValue = trim(sanitized.substr(transportSeparator + 1));
        if (diagnosticsValue.empty()) {
            return false;
        }
        diagnostics_ = "Manual diagnostics: " + diagnosticsValue;
    } else {
        state_ = "command:" + sanitized;
        diagnostics_ = "Last JNI command: " + sanitized;
    }
    logging::Logger::info("AndroidSA", diagnostics_);
    return true;
}

}  // namespace androidsa::network
