#pragma once

#include <string_view>

namespace androidsa::logging {

class Logger {
public:
    static void info(std::string_view tag, std::string_view message);
};

}  // namespace androidsa::logging
