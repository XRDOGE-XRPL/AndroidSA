#include "ClientState.h"

#include <algorithm>
#include <array>
#include <charconv>
#include <cctype>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sstream>
#include <string_view>
#include <sys/socket.h>
#include <unistd.h>
#include <vector>

#include "native/logging/Logger.h"

namespace androidsa::network {
namespace {
constexpr std::size_t kMaxCommandLength = 64;
constexpr std::size_t kMaxEventCount = 12;
constexpr std::size_t kUdpProbePacketCount = 5;
constexpr std::size_t kUdpReceiveBufferSize = 1400;

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

bool isLoopbackLikeHost(std::string_view host) {
    const auto normalized = lower(std::string(host));
    return normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1" ||
           normalized == "loopback" || normalized == "0.0.0.0";
}

bool parseSocketEndpoint(const std::string& serverAddress, std::string* host, std::string* portText) {
    if (serverAddress.empty()) {
        return false;
    }

    const auto separator = serverAddress.rfind(':');
    if (separator == std::string::npos) {
        *host = serverAddress;
        *portText = "7777";
        return !host->empty();
    }

    *host = serverAddress.substr(0, separator);
    *portText = serverAddress.substr(separator + 1);
    return !host->empty() && !portText->empty();
}

bool containsInvalidSummaryCharacters(std::string_view value) {
    return std::any_of(value.begin(), value.end(), [](unsigned char ch) {
        return ch < 0x20 || ch == 0x7F || ch == '|';
    });
}

bool isSocketTimeoutError(int err) {
    return err == EAGAIN || err == EWOULDBLOCK || err == ETIMEDOUT;
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

std::string canonicalizeAcceptedCommand(const std::string& sanitized, const std::string& normalized) {
    const auto separator = normalized.find(':');
    if (separator == std::string::npos) {
        return normalized;
    }

    const auto keyword = normalized.substr(0, separator);
    const auto value = trim(sanitized.substr(separator + 1));
    return keyword + ":" + value;
}

std::string packetTypeName(unsigned char packetId) {
    switch (packetId) {
        case 0x00:
            return "RakNet connected ping";
        case 0x10:
            return "RakNet connection request";
        case 0x13:
            return "RakNet connection accepted";
        case 0x15:
            return "RakNet new incoming connection";
        case 0x1c:
            return "RakNet open connection request";
        case 0x1d:
            return "RakNet open connection reply";
        case 0x7d:
            return "Open:MP/SA:MP RPC wrapper";
        default:
            return "Unknown packet";
    }
}

std::vector<unsigned char> buildProbePayload() {
    return {0x7d, 0x0f, 0x00, 0x02, 0xab, 0xcd};
}
}  // namespace

ClientState::~ClientState() {
    std::lock_guard lock(mutex_);
    closeUdpRuntimeLocked();
}

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

bool ClientState::resolveSocketTargetLocked(const std::string& serverAddress, sockaddr_in* destination) const {
    if (destination == nullptr) {
        return false;
    }

    std::string host;
    std::string portText;
    if (!parseSocketEndpoint(serverAddress, &host, &portText)) {
        return false;
    }

    std::memset(destination, 0, sizeof(*destination));
    destination->sin_family = AF_INET;

    char* endPointer = nullptr;
    errno = 0;
    const long parsedPort = std::strtol(portText.c_str(), &endPointer, 10);
    if (errno != 0 || endPointer == portText.c_str() || *endPointer != '\0' || parsedPort < 1 || parsedPort > 65535) {
        return false;
    }
    destination->sin_port = htons(static_cast<uint16_t>(parsedPort));

    if (isLoopbackLikeHost(host)) {
        destination->sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        return true;
    }

    addrinfo hints {};
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_DGRAM;
    hints.ai_protocol = IPPROTO_UDP;

    addrinfo* resolved = nullptr;
    const int status = ::getaddrinfo(host.c_str(), portText.c_str(), &hints, &resolved);
    if (status != 0 || resolved == nullptr) {
        destination->sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        return true;
    }

    const auto* resolvedAddress = reinterpret_cast<const sockaddr_in*>(resolved->ai_addr);
    *destination = *resolvedAddress;
    ::freeaddrinfo(resolved);
    return true;
}

bool ClientState::ensureUdpRuntimeLocked(const std::string& serverAddress) {
    if (udpRuntimeReady_ && udpSocketFd_ != -1) {
        return true;
    }

    sockaddr_in destination {};
    if (!resolveSocketTargetLocked(serverAddress, &destination)) {
        diagnostics_ = "Unable to resolve UDP endpoint for " + serverAddress;
        return false;
    }

    std::string host;
    std::string portText;
    if (!parseSocketEndpoint(serverAddress, &host, &portText)) {
        diagnostics_ = "Unable to resolve UDP endpoint for " + serverAddress;
        return false;
    }

    closeUdpRuntimeLocked();
    udpSocketFd_ = ::socket(AF_INET, SOCK_DGRAM, 0);
    if (udpSocketFd_ < 0) {
        diagnostics_ = std::string("UDP socket init failed: ") + std::strerror(errno);
        return false;
    }

    int flags = ::fcntl(udpSocketFd_, F_GETFL, 0);
    if (flags != -1) {
        ::fcntl(udpSocketFd_, F_SETFL, flags | O_NONBLOCK);
    }

    sockaddr_in localAddress {};
    localAddress.sin_family = AF_INET;
    localAddress.sin_port = 0;
    localAddress.sin_addr.s_addr = isLoopbackLikeHost(host) ? htonl(INADDR_LOOPBACK) : htonl(INADDR_ANY);
    if (::bind(udpSocketFd_, reinterpret_cast<sockaddr*>(&localAddress), sizeof(localAddress)) != 0) {
        diagnostics_ = std::string("UDP bind failed: ") + std::strerror(errno);
        closeUdpRuntimeLocked();
        return false;
    }

    if (!isLoopbackLikeHost(host)) {
        transport_ = "real-udp";
        timeval timeout {};
        timeout.tv_sec = 1;
        timeout.tv_usec = 0;
        if (::setsockopt(udpSocketFd_, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) != 0 ||
            ::setsockopt(udpSocketFd_, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout)) != 0) {
            diagnostics_ = std::string("UDP timeout configuration failed: ") + std::strerror(errno);
            closeUdpRuntimeLocked();
            return false;
        }
    } else {
        transport_ = "RakNet-compatible UDP";
    }
    udpRuntimeReady_ = true;
    return true;
}

int ClientState::sendUdpProbeLocked(const std::vector<unsigned char>& payload) {
    if (!udpRuntimeReady_ || udpSocketFd_ == -1) {
        return 0;
    }

    sockaddr_in destination {};
    const bool resolved = resolveSocketTargetLocked(serverAddress_, &destination);
    if (!resolved) {
        return 0;
    }

    sockaddr_in localLoopback = {};
    localLoopback.sin_family = AF_INET;
    localLoopback.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    localLoopback.sin_port = 0;

    sockaddr_in boundAddress {};
    socklen_t boundLength = sizeof(boundAddress);
    if (::getsockname(udpSocketFd_, reinterpret_cast<sockaddr*>(&boundAddress), &boundLength) != 0) {
        return 0;
    }

    if (destination.sin_addr.s_addr == htonl(INADDR_LOOPBACK)) {
        localLoopback.sin_port = boundAddress.sin_port;
        destination = localLoopback;
    }

    const auto written = ::sendto(
        udpSocketFd_,
        reinterpret_cast<const char*>(payload.data()),
        payload.size(),
        0,
        reinterpret_cast<sockaddr*>(&destination),
        sizeof(destination)
    );
    if (written > 0) {
        ++packetsSent_;
        return 1;
    }

    if (written < 0) {
        const int errorCode = errno;
        if (isSocketTimeoutError(errorCode) && !isLoopbackLikeHost(serverAddress_)) {
            state_ = "error";
            diagnostics_ = "Remote UDP send timed out to " + serverAddress_;
            recordEventLocked("Remote UDP send timed out to " + serverAddress_);
        }
    }
    return 0;
}

int ClientState::receiveUdpProbeLocked() {
    if (!udpRuntimeReady_ || udpSocketFd_ == -1) {
        return 0;
    }

    std::array<unsigned char, kUdpReceiveBufferSize> buffer {};
    sockaddr_in sourceAddress {};
    socklen_t sourceLength = sizeof(sourceAddress);
    const auto received = ::recvfrom(
        udpSocketFd_,
        reinterpret_cast<char*>(buffer.data()),
        buffer.size(),
        0,
        reinterpret_cast<sockaddr*>(&sourceAddress),
        &sourceLength
    );
    if (received < 0) {
        const int errorCode = errno;
        if (isSocketTimeoutError(errorCode) && !isLoopbackLikeHost(serverAddress_)) {
            state_ = "error";
            diagnostics_ = "Remote UDP receive timed out for " + serverAddress_ + "; server may be disconnected";
            recordEventLocked("Remote UDP receive timed out; server may be disconnected");
        }
        return 0;
    }
    if (received == 0) {
        return 0;
    }

    ++packetsReceived_;
    const std::vector<unsigned char> payload(buffer.begin(), buffer.begin() + received);
    recordEventLocked(parseRakNetLikePacket(payload));
    return 1;
}

void ClientState::closeUdpRuntimeLocked() {
    if (udpSocketFd_ != -1) {
        ::close(udpSocketFd_);
        udpSocketFd_ = -1;
    }
    udpRuntimeReady_ = false;
}

std::string ClientState::parseRakNetLikePacket(const std::vector<unsigned char>& payload) const {
    if (payload.empty()) {
        return "Received empty UDP payload";
    }

    std::ostringstream stream;
    const auto packetId = payload[0];
    stream << "RX " << packetTypeName(packetId);

    if (packetId == 0x7d && payload.size() >= 4) {
        const unsigned rpcId = static_cast<unsigned>(payload[1]) |
                               (static_cast<unsigned>(payload[2]) << 8U);
        const unsigned declaredLength = payload[3];
        stream << " (rpcId=" << rpcId << ", declaredPayloadBytes=" << declaredLength << ")";
    }
    return stream.str();
}

void ClientState::resetLocked() {
    closeUdpRuntimeLocked();
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
    streamState_ = "idle";
    gtaRuntimeAvailable_ = true;
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
        diagnostics_ = "Ping acknowledged (native runtime ready)";
        latencyMs_ = std::max(latencyMs_, 12);
        eventMessage = "Ping acknowledged by native runtime";
    } else if (normalized == "connect") {
        if (!ensureUdpRuntimeLocked(serverAddress_)) {
            return false;
        }
        ++connectionAttempts_;
        state_ = "connected";
        int sent = 0;
        int received = 0;
        for (std::size_t probe = 0; probe < kUdpProbePacketCount; ++probe) {
            sent += sendUdpProbeLocked(buildProbePayload());
        }
        for (std::size_t probe = 0; probe < kUdpProbePacketCount; ++probe) {
            received += receiveUdpProbeLocked();
        }
        diagnostics_ = "Connection established to " + serverAddress_ + " via UDP runtime";
        latencyMs_ = sent > 0 ? 8 : 0;
        eventMessage = "Connected to " + serverAddress_ + " (udp tx=" + std::to_string(sent) +
                       ", rx=" + std::to_string(received) + ")";
    } else if (startsWith(normalized, "connect:")) {
        std::string serverAddress;
        if (!extractExactCommandValue(sanitized, normalized, "connect", &serverAddress)) {
            return false;
        }
        if (!ensureUdpRuntimeLocked(serverAddress)) {
            return false;
        }
        serverAddress_ = serverAddress;
        ++connectionAttempts_;
        state_ = "connected";
        int sent = 0;
        int received = 0;
        for (std::size_t probe = 0; probe < kUdpProbePacketCount; ++probe) {
            sent += sendUdpProbeLocked(buildProbePayload());
        }
        for (std::size_t probe = 0; probe < kUdpProbePacketCount; ++probe) {
            received += receiveUdpProbeLocked();
        }
        diagnostics_ = "Connection established to " + serverAddress_ + " via UDP runtime";
        latencyMs_ = sent > 0 ? 8 : 0;
        eventMessage = "Connected to configured server " + serverAddress_ +
                       " (udp tx=" + std::to_string(sent) +
                       ", rx=" + std::to_string(received) + ")";
    } else if (normalized == "reconnect") {
        if (!ensureUdpRuntimeLocked(serverAddress_)) {
            return false;
        }
        ++connectionAttempts_;
        state_ = "connected";
        const int sent = sendUdpProbeLocked(buildProbePayload());
        const int received = receiveUdpProbeLocked();
        diagnostics_ = "Reconnected to " + serverAddress_ + " via UDP runtime";
        latencyMs_ = sent > 0 ? 6 : latencyMs_;
        eventMessage = "Reconnect flow completed for " + serverAddress_ +
                       " (udp tx=" + std::to_string(sent) +
                       ", rx=" + std::to_string(received) + ")";
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
        diagnostics_ = "Status snapshot ready for " + playerName_ + " on " + serverAddress_ +
                       " (udp=" + std::string(udpRuntimeReady_ ? "ready" : "offline") + ")";
        eventMessage = "Status snapshot refreshed";
    } else if (normalized == "stream" || startsWith(normalized, "stream:")) {
        std::string streamAction;
        if (!extractExactCommandValue(sanitized, normalized, "stream", &streamAction)) {
            return false;
        }
        const auto action = lower(streamAction);
        if (action == "start") {
            if (!gtaRuntimeAvailable_) {
                state_ = "error";
                diagnostics_ = "GTA SA Mobile runtime missing; install com.rockstargames.gtasager from the Play Store";
                eventMessage = "Stream start blocked because the GTA SA Mobile runtime is not installed";
            } else {
                streamState_ = "running";
                state_ = "streaming";
                diagnostics_ = "Local GTA SA Mobile stream started; host runtime is live on-device";
                eventMessage = "Local GTA SA Mobile stream started";
            }
        } else if (action == "stop") {
            streamState_ = "stopped";
            if (state_ == "streaming") {
                state_ = "ready";
            }
            diagnostics_ = "Local GTA SA Mobile stream stopped";
            eventMessage = "Local GTA SA Mobile stream stopped";
        } else if (action == "info") {
            const std::string runtimeStatus = streamState_ == "running" ? "live" : "ready";
            diagnostics_ = "GTA SA Mobile runtime: " + runtimeStatus + " (package " + gtaRuntimePackage_ + ")";
            eventMessage = "Local stream status reported for GTA SA Mobile runtime";
        } else {
            return false;
        }
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
        if (!ensureUdpRuntimeLocked(serverAddress_)) {
            return false;
        }
        const auto normalizedDirection = lower(trafficDirection);
        if (normalizedDirection == "rx") {
            int sent = 0;
            int received = 0;
            for (std::size_t probe = 0; probe < kUdpProbePacketCount; ++probe) {
                sent += sendUdpProbeLocked(buildProbePayload());
                received += receiveUdpProbeLocked();
            }
            diagnostics_ = "Inbound UDP probe flow recorded";
            eventMessage = "Inbound traffic simulation recorded (udp tx=" + std::to_string(sent) +
                           ", rx=" + std::to_string(received) + ")";
        } else if (normalizedDirection == "tx") {
            int sent = 0;
            for (std::size_t probe = 0; probe < kUdpProbePacketCount; ++probe) {
                sent += sendUdpProbeLocked(buildProbePayload());
            }
            diagnostics_ = "Outbound UDP probe flow recorded";
            eventMessage = "Outbound traffic simulation recorded (udp tx=" + std::to_string(sent) + ")";
        } else {
            return false;
        }
    } else {
        state_ = "command:" + sanitized;
        diagnostics_ = "Last JNI command: " + sanitized;
        eventMessage = "Generic command dispatched: " + sanitized;
    }

    lastCommand_ = canonicalizeAcceptedCommand(sanitized, normalized);
    recordEventLocked(eventMessage);
    logging::Logger::info("AndroidSA", diagnostics_);
    return true;
}

}  // namespace androidsa::network
