#pragma once

#include <mutex>
#include <string>

namespace androidsa::network {

class ClientState {
public:
    std::string summary();
    bool dispatchCommand(const std::string& command);

private:
    std::mutex mutex_;
    std::string transport_ = "RakNet-compatible UDP";
    std::string state_ = "initializing";
    std::string diagnostics_ = "NDK bootstrap complete";
};

}  // namespace androidsa::network
