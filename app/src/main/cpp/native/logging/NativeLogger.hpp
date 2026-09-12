#pragma once

#include <string_view>

namespace androidsa {
class NativeLogger {
public:
    void log(std::string_view message) const;
};
}  // namespace androidsa
