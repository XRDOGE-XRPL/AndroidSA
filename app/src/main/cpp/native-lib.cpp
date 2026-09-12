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
