/**
 * llama.cpp JNI 브릿지
 *
 * Kotlin LlmRepositoryImpl의 native 메서드를 구현.
 * llama.cpp API를 사용하여 GGUF 모델 로드/추론/해제를 수행.
 *
 * 빌드 요구사항: llama.cpp 소스가 app/src/main/cpp/llama.cpp/ 에 있어야 함
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>

#include "llama.h"
#include "common.h"

#define TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 모델 컨텍스트를 묶는 구조체
struct LlamaContext {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    std::mutex mtx;
    int n_threads = 4;
};

extern "C" {

/**
 * 모델 로드
 * @return 컨텍스트 포인터 (long), 실패 시 0
 */
JNIEXPORT jlong JNICALL
Java_com_tinyoscillator_data_local_llm_LlmRepositoryImpl_nativeLoadModel(
        JNIEnv *env, jobject thiz,
        jstring model_path, jint n_threads) {

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("모델 로드 시작: %s (threads=%d)", path, n_threads);

    // 백엔드 초기화
    llama_backend_init();

    // 모델 파라미터
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU only (Android)

    llama_model *model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!model) {
        LOGE("모델 로드 실패");
        return 0;
    }

    // 컨텍스트 파라미터
    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    llama_context *ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("컨텍스트 생성 실패");
        llama_free_model(model);
        return 0;
    }

    auto *wrapper = new LlamaContext();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->n_threads = n_threads;

    LOGI("모델 로드 성공 (ctx=%p)", wrapper);
    return reinterpret_cast<jlong>(wrapper);
}

/**
 * 텍스트 생성 (전체 토큰을 한번에 생성하여 콜백으로 전달)
 * @return true = 정상 완료
 */
JNIEXPORT jboolean JNICALL
Java_com_tinyoscillator_data_local_llm_LlmRepositoryImpl_nativeGenerate(
        JNIEnv *env, jobject thiz,
        jlong model_handle, jstring jprompt,
        jint max_tokens, jfloat temperature,
        jobject callback) {

    auto *wrapper = reinterpret_cast<LlamaContext *>(model_handle);
    if (!wrapper || !wrapper->ctx) {
        LOGE("유효하지 않은 모델 핸들");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(wrapper->mtx);

    const char *prompt_cstr = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(jprompt, prompt_cstr);

    // 콜백 메서드 ID 캐싱
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    if (!onTokenMethod) {
        LOGE("onToken 메서드를 찾을 수 없음");
        return JNI_FALSE;
    }

    // 프롬프트 토큰화
    const int n_prompt_max = prompt.size() / 2 + 128; // 충분한 버퍼
    std::vector<llama_token> tokens(n_prompt_max);
    int n_tokens = llama_tokenize(
        llama_get_model(wrapper->ctx),
        prompt.c_str(), prompt.size(),
        tokens.data(), n_prompt_max,
        true, false
    );

    if (n_tokens < 0) {
        LOGE("토큰화 실패: %d", n_tokens);
        return JNI_FALSE;
    }
    tokens.resize(n_tokens);

    LOGI("토큰화 완료: %d tokens", n_tokens);

    // 프롬프트 평가 (KV 캐시에 입력)
    llama_kv_cache_clear(wrapper->ctx);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true; // 마지막 토큰만 logits 필요

    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("프롬프트 디코딩 실패");
        llama_batch_free(batch);
        return JNI_FALSE;
    }

    // 토큰 생성 루프
    int n_cur = n_tokens;
    const llama_model *model = llama_get_model(wrapper->ctx);
    const int n_vocab = llama_n_vocab(model);

    for (int i = 0; i < max_tokens; i++) {
        // 샘플링
        auto *logits = llama_get_logits_ith(wrapper->ctx, -1);

        std::vector<llama_token_data> candidates(n_vocab);
        for (int j = 0; j < n_vocab; j++) {
            candidates[j] = {j, logits[j], 0.0f};
        }
        llama_token_data_array candidates_p = {candidates.data(), (size_t)n_vocab, false};

        // Temperature sampling
        float temp = temperature;
        if (temp <= 0.0f) {
            // Greedy
            llama_sample_softmax(wrapper->ctx, &candidates_p);
        } else {
            llama_sample_temp(wrapper->ctx, &candidates_p, temp);
            llama_sample_softmax(wrapper->ctx, &candidates_p);
        }

        llama_token new_token = candidates_p.data[0].id;

        // EOS 체크
        if (llama_token_is_eog(model, new_token)) {
            LOGI("EOS 도달 (%d tokens 생성)", i);
            break;
        }

        // 토큰 → 문자열 변환
        char buf[256];
        int n = llama_token_to_piece(model, new_token, buf, sizeof(buf), 0, false);
        if (n > 0) {
            std::string token_str(buf, n);
            jstring jtoken = env->NewStringUTF(token_str.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jtoken);
            env->DeleteLocalRef(jtoken);
        }

        // 다음 토큰 준비
        llama_batch_clear(batch);
        llama_batch_add(batch, new_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(wrapper->ctx, batch) != 0) {
            LOGE("디코딩 실패 at token %d", i);
            break;
        }
    }

    llama_batch_free(batch);
    return JNI_TRUE;
}

/**
 * 모델 해제
 */
JNIEXPORT void JNICALL
Java_com_tinyoscillator_data_local_llm_LlmRepositoryImpl_nativeUnload(
        JNIEnv *env, jobject thiz, jlong model_handle) {

    auto *wrapper = reinterpret_cast<LlamaContext *>(model_handle);
    if (!wrapper) return;

    LOGI("모델 해제 시작");

    if (wrapper->ctx) {
        llama_free(wrapper->ctx);
    }
    if (wrapper->model) {
        llama_free_model(wrapper->model);
    }

    delete wrapper;
    llama_backend_free();

    LOGI("모델 해제 완료");
}

} // extern "C"
