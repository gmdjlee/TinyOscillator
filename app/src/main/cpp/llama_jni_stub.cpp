/**
 * llama.cpp JNI 스텁 — llama.cpp 소스 없이 빌드할 때 사용
 *
 * 모든 native 함수가 정의되어 앱이 크래시하지 않지만,
 * 실제 추론은 수행하지 않고 오류 메시지를 반환.
 *
 * 실제 추론을 사용하려면:
 * 1. llama.cpp 소스를 app/src/main/cpp/llama.cpp/ 에 배치
 * 2. 프로젝트 재빌드
 */

#include <jni.h>
#include <android/log.h>
#include <string>

#define TAG "llama_jni_stub"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_tinyoscillator_data_local_llm_LlmRepositoryImpl_nativeLoadModel(
        JNIEnv *env, jobject thiz,
        jstring model_path, jint n_threads) {
    LOGW("STUB: nativeLoadModel — llama.cpp 소스가 필요합니다");
    return 0; // 실패
}

JNIEXPORT jboolean JNICALL
Java_com_tinyoscillator_data_local_llm_LlmRepositoryImpl_nativeGenerate(
        JNIEnv *env, jobject thiz,
        jlong model_handle, jstring jprompt,
        jint max_tokens, jfloat temperature,
        jobject callback) {
    LOGW("STUB: nativeGenerate — llama.cpp 소스가 필요합니다");

    // 콜백으로 stub 메시지 전달
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    if (onTokenMethod) {
        jstring msg = env->NewStringUTF("[llama.cpp 미설치 — stub 모드]");
        env->CallVoidMethod(callback, onTokenMethod, msg);
        env->DeleteLocalRef(msg);
    }

    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tinyoscillator_data_local_llm_LlmRepositoryImpl_nativeUnload(
        JNIEnv *env, jobject thiz, jlong model_handle) {
    LOGW("STUB: nativeUnload — llama.cpp 소스가 필요합니다");
}

} // extern "C"
