#include <jni.h>

#include <string>

#include "native/network/ClientState.h"

namespace {
androidsa::network::ClientState& clientState() {
    static androidsa::network::ClientState instance;
    return instance;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xrdoge_xrpl_androidsa_NativeBridge_nativeGetClientSummary(JNIEnv* env, jobject /* this */) {
    const auto summary = clientState().summary();
    return env->NewStringUTF(summary.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_xrdoge_xrpl_androidsa_NativeBridge_nativeDispatchCommand(JNIEnv* env, jobject /* this */, jstring command) {
    const char* raw = env->GetStringUTFChars(command, nullptr);
    const std::string commandText = raw == nullptr ? std::string() : std::string(raw);
    if (raw != nullptr) {
        env->ReleaseStringUTFChars(command, raw);
    }
    return clientState().dispatchCommand(commandText);
}
