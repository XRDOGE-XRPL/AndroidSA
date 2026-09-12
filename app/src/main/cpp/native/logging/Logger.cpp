#include "Logger.h"

#include <android/log.h>
#include <string>

namespace androidsa::logging {

void Logger::info(std::string_view tag, std::string_view message) {
    __android_log_print(
        ANDROID_LOG_INFO,
        std::string(tag).c_str(),
        "%s",
        std::string(message).c_str()
    );
}

}  // namespace androidsa::logging
