#include "ClientState.h"

#include <algorithm>
#include <cctype>
#include <sstream>

#include "native/logging/Logger.h"

namespace androidsa::network {
namespace {
std::string trim(std::string value) {
    value.erase(value.begin(), std::find_if(value.begin(), value.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
    value.erase(std::find_if(value.rbegin(), value.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), value.end());
    return value;
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

    std::lock_guard lock(mutex_);
    state_ = sanitized == "ping" ? "ready" : "command:" + sanitized;
    diagnostics_ = "Last JNI command: " + sanitized;
    logging::Logger::info("AndroidSA", diagnostics_);
    return true;
}

}  // namespace androidsa::network
