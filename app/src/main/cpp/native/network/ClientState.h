#pragma once

#include <mutex>
#include <netinet/in.h>
#include <string>
#include <vector>

namespace androidsa::network {

class ClientState {
public:
    ~ClientState();

    std::string summary();
    std::vector<std::string> recentEvents();
    bool dispatchCommand(const std::string& command);

private:
    bool ensureUdpRuntimeLocked(const std::string& serverAddress);
    bool resolveSocketTargetLocked(const std::string& serverAddress, sockaddr_in* destination) const;
    int sendUdpProbeLocked(const std::vector<unsigned char>& payload);
    int receiveUdpProbeLocked();
    void closeUdpRuntimeLocked();
    std::string parseRakNetLikePacket(const std::vector<unsigned char>& payload) const;
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
    std::string streamState_ = "idle";
    bool gtaRuntimeAvailable_ = true;
    std::string gtaRuntimePackage_ = "com.rockstargames.gtasager";
    std::vector<std::string> eventLog_ = {"Native runtime bootstrapped"};
    int udpSocketFd_ = -1;
    bool udpRuntimeReady_ = false;
};

}  // namespace androidsa::network
