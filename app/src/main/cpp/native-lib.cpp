#include <jni.h>

#include <sstream>
#include <string>

#include "native/network/ClientState.h"

namespace {
androidsa::network::ClientState& clientState() {
    static androidsa::network::ClientState instance;
    return instance;
}

std::string joinEvents(const std::vector<std::string>& events) {
    std::ostringstream stream;
    for (std::size_t index = 0; index < events.size(); ++index) {
        if (index > 0) {
            stream << '\n';
        }
        stream << events[index];
    }
    return stream.str();
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xrdoge_xrpl_androidsa_NativeBridge_nativeGetClientSummary(JNIEnv* env, jobject /* this */) {
    const auto summary = clientState().summary();
    return env->NewStringUTF(summary.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xrdoge_xrpl_androidsa_NativeBridge_nativeGetRecentEvents(JNIEnv* env, jobject /* this */) {
    const auto events = joinEvents(clientState().recentEvents());
    return env->NewStringUTF(events.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_xrdoge_xrpl_androidsa_NativeBridge_nativeDispatchCommand(JNIEnv* env, jobject /* this */, jstring command) {
    if (command == nullptr) {
        return false;
    }

    const char* raw = env->GetStringUTFChars(command, nullptr);
    if (raw == nullptr) {
        return false;
    }

    const std::string commandText(raw);
    env->ReleaseStringUTFChars(command, raw);
    return clientState().dispatchCommand(commandText);
}
