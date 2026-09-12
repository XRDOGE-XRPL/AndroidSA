#pragma once

#include <mutex>
#include <string>
#include <vector>

namespace androidsa::network {

class ClientState {
public:
    std::string summary();
    std::vector<std::string> recentEvents();
    bool dispatchCommand(const std::string& command);

private:
    void resetLocked();
    void recordEventLocked(const std::string& event);

    std::mutex mutex_;
    std::string transport_ = "RakNet-compatible UDP";
    std::string state_ = "initializing";
    std::string diagnostics_ = "NDK bootstrap complete";
    std::string serverAddress_ = "demo.sa-mp.local:7777";
    std::string playerName_ = "Guest";
    int latencyMs_ = 0;
    int packetsSent_ = 0;
    int packetsReceived_ = 0;
    int connectionAttempts_ = 0;
    std::string lastCommand_ = "startup";
    std::vector<std::string> eventLog_ = {"Native runtime bootstrapped"};
};

}  // namespace androidsa::network
