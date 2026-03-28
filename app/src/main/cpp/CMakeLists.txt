cmake_minimum_required(VERSION 3.22.1)
project("llama_jni" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ──────────────────────────────────────────
# llama.cpp 소스 경로 설정
# 사용 방법:
#   1. llama.cpp 소스를 app/src/main/cpp/llama.cpp/ 에 배치
#   2. 또는 LLAMA_CPP_DIR 변수로 외부 경로 지정
# ──────────────────────────────────────────
if(NOT DEFINED LLAMA_CPP_DIR)
    set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")
endif()

# llama.cpp 소스가 있으면 빌드, 없으면 stub 모드
if(EXISTS "${LLAMA_CPP_DIR}/src/llama.cpp")
    message(STATUS "llama.cpp 소스 발견: ${LLAMA_CPP_DIR}")

    # llama.cpp 핵심 소스
    set(LLAMA_SOURCES
        ${LLAMA_CPP_DIR}/src/llama.cpp
        ${LLAMA_CPP_DIR}/src/llama-vocab.cpp
        ${LLAMA_CPP_DIR}/src/llama-grammar.cpp
        ${LLAMA_CPP_DIR}/src/llama-sampling.cpp
        ${LLAMA_CPP_DIR}/src/unicode.cpp
        ${LLAMA_CPP_DIR}/src/unicode-data.cpp
        ${LLAMA_CPP_DIR}/ggml/src/ggml.c
        ${LLAMA_CPP_DIR}/ggml/src/ggml-alloc.c
        ${LLAMA_CPP_DIR}/ggml/src/ggml-backend.c
        ${LLAMA_CPP_DIR}/ggml/src/ggml-quants.c
        ${LLAMA_CPP_DIR}/common/common.cpp
        ${LLAMA_CPP_DIR}/common/sampling.cpp
        ${LLAMA_CPP_DIR}/common/grammar-parser.cpp
    )

    add_library(llama_core STATIC ${LLAMA_SOURCES})

    target_include_directories(llama_core PUBLIC
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/src
        ${LLAMA_CPP_DIR}/ggml/include
        ${LLAMA_CPP_DIR}/ggml/src
        ${LLAMA_CPP_DIR}/common
    )

    target_compile_definitions(llama_core PRIVATE
        GGML_USE_CPU
        NDEBUG
    )

    # ARM NEON 최적화 (arm64-v8a)
    if(${ANDROID_ABI} STREQUAL "arm64-v8a")
        target_compile_options(llama_core PRIVATE -march=armv8-a+dotprod)
    endif()

    # JNI 브릿지 라이브러리 (llama.cpp 연동)
    add_library(llama_jni SHARED llama_jni.cpp)

    target_include_directories(llama_jni PRIVATE
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/common
        ${LLAMA_CPP_DIR}/ggml/include
    )

    target_link_libraries(llama_jni
        llama_core
        android
        log
    )

else()
    message(STATUS "llama.cpp 소스 미발견 — stub 모드로 빌드")
    message(STATUS "  경로: ${LLAMA_CPP_DIR}")
    message(STATUS "  llama.cpp를 다운로드하여 ${LLAMA_CPP_DIR} 에 배치하세요")

    # Stub 모드: JNI 함수는 정의되지만 실제 추론 불가
    add_library(llama_jni SHARED llama_jni_stub.cpp)

    target_link_libraries(llama_jni
        android
        log
    )
endif()

# 공통 컴파일 옵션
target_compile_options(llama_jni PRIVATE
    -O3
    -fvisibility=hidden
)
