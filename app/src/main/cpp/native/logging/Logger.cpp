#include "Logger.h"

#ifdef __ANDROID__
#include <android/log.h>
#else
#include <iostream>
#endif
#include <string>

namespace androidsa::logging {

void Logger::info(std::string_view tag, std::string_view message) {
#ifdef __ANDROID__
    __android_log_print(
        ANDROID_LOG_INFO,
        std::string(tag).c_str(),
        "%s",
        std::string(message).c_str()
    );
#else
    std::clog << '[' << tag << "] " << message << '\n';
#endif
}

}  // namespace androidsa::logging
