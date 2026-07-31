#include <jni.h>

#include "whisper.h"

#include <algorithm>
#include <mutex>
#include <string>

namespace {
struct ModelContext {
    whisper_context* whisper = nullptr;
    std::mutex inference_mutex;
};

std::string to_string(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void throw_exception(JNIEnv* env, const char* class_name, const std::string& message) {
    jclass exception = env->FindClass(class_name);
    if (exception != nullptr) {
        env->ThrowNew(exception, message.c_str());
    }
}

bool is_auto_language(const std::string& language) {
    return language.empty() || language == "auto";
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_handy_android_WhisperLib_initContext(
    JNIEnv* env,
    jobject /* thiz */,
    jstring model_path_str) {
    const std::string model_path = to_string(env, model_path_str);
    if (model_path.empty()) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Whisper model path is empty");
        return 0;
    }

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;

    whisper_context* whisper = whisper_init_from_file_with_params(model_path.c_str(), context_params);
    if (whisper == nullptr) {
        throw_exception(env, "java/lang/IllegalStateException", "Unable to load Whisper model: " + model_path);
        return 0;
    }

    auto* context = new ModelContext();
    context->whisper = whisper;
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT void JNICALL
Java_com_handy_android_WhisperLib_freeContext(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr) {
    auto* context = reinterpret_cast<ModelContext*>(context_ptr);
    if (context == nullptr) {
        return;
    }

    std::lock_guard<std::mutex> lock(context->inference_mutex);
    if (context->whisper != nullptr) {
        whisper_free(context->whisper);
        context->whisper = nullptr;
    }
    delete context;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_handy_android_WhisperLib_fullTranscribe(
    JNIEnv* env,
    jobject /* thiz */,
    jlong context_ptr,
    jfloatArray audio_data,
    jint num_threads,
    jboolean translate,
    jstring language_str) {
    auto* context = reinterpret_cast<ModelContext*>(context_ptr);
    if (context == nullptr || context->whisper == nullptr) {
        throw_exception(env, "java/lang/IllegalStateException", "Whisper context is not initialized");
        return nullptr;
    }
    if (audio_data == nullptr) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Audio buffer is null");
        return nullptr;
    }

    const jsize sample_count = env->GetArrayLength(audio_data);
    if (sample_count == 0) {
        return env->NewStringUTF("");
    }

    jfloat* samples = env->GetFloatArrayElements(audio_data, nullptr);
    if (samples == nullptr) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Unable to access audio buffer");
        return nullptr;
    }

    const std::string language = to_string(env, language_str);
    const int threads = std::max(1, static_cast<int>(num_threads));
    std::lock_guard<std::mutex> lock(context->inference_mutex);

    whisper_full_params full_params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    full_params.n_threads = threads;
    full_params.translate = translate == JNI_TRUE;
    full_params.language = is_auto_language(language) ? nullptr : language.c_str();
    full_params.detect_language = is_auto_language(language);
    full_params.print_progress = false;
    full_params.print_realtime = false;
    full_params.print_timestamps = false;
    full_params.print_special = false;
    full_params.no_timestamps = true;
    full_params.single_segment = false;

    const int result = whisper_full(context->whisper, full_params, samples, sample_count);
    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    if (result != 0) {
        throw_exception(env, "java/lang/IllegalStateException", "Whisper inference failed");
        return nullptr;
    }

    std::string transcription;
    const int segment_count = whisper_full_n_segments(context->whisper);
    for (int index = 0; index < segment_count; ++index) {
        const char* segment = whisper_full_get_segment_text(context->whisper, index);
        if (segment != nullptr) {
            transcription += segment;
        }
    }

    return env->NewStringUTF(transcription.c_str());
}
