#include <string>

#include "native/logging/NativeLogger.hpp"
#include "native/network/RpcClient.hpp"

namespace androidsa {
std::string runtime_status() {
    NativeLogger logger;
    RpcClient client;

    logger.log("Initializing AndroidSA native scaffold");
    return client.describe();
}
}  // namespace androidsa
