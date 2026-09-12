#pragma once

#include <string>

namespace androidsa {
class RpcClient {
public:
    [[nodiscard]] std::string describe() const;
};
}  // namespace androidsa
