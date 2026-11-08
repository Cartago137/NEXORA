#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_nexora_app_NativeBridge_nativeStatus(JNIEnv* env, jobject /* instance */) {
    return env->NewStringUTF(
        "Native bridge loaded. mGBA integration is the next milestone."
    );
}
