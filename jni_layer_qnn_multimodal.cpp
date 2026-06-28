/*
 * Plain-JNI bridge for the QNN split multimodal (SmolVLM) runner.
 *
 * Symbols mangle to com.electriclens.executorch.QnnMultimodalRunner so the
 * app can call them directly once libexecutorch.so is loaded. This mirrors
 * start_multimodal_runner() in qnn_multimodal_runner.cpp but keeps the runner
 * warm across calls and returns the generated text as a Java String.
 */
#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include <executorch/examples/qualcomm/oss_scripts/llama/runner/multimodal_runner/chat_template.h>
#include <executorch/examples/qualcomm/oss_scripts/llama/runner/multimodal_runner/multimodal_runner.h>
#include <executorch/examples/qualcomm/oss_scripts/llama/runner/multimodal_runner/utils.h>
#include <executorch/extension/llm/runner/image.h>
#include <executorch/extension/llm/runner/irunner.h>
#include <executorch/extension/llm/runner/multimodal_input.h>
#include <executorch/extension/module/module.h>
#include <executorch/runtime/platform/log.h>

using executorch::aten::ScalarType;
using executorch::extension::Module;
using executorch::extension::llm::GenerationConfig;
using executorch::extension::llm::Image;
using executorch::extension::llm::make_image_input;
using executorch::extension::llm::make_text_input;
using executorch::extension::llm::MultimodalInput;
using executorch::runtime::MethodMeta;
using executorch::runtime::Result;

namespace {
std::string to_std(JNIEnv* env, jstring s) {
  if (s == nullptr) {
    return std::string();
  }
  const char* c = env->GetStringUTFChars(s, nullptr);
  std::string r(c != nullptr ? c : "");
  if (c != nullptr) {
    env->ReleaseStringUTFChars(s, c);
  }
  return r;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_electriclens_executorch_QnnMultimodalRunner_nativeCreate(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring jEncoder,
    jstring jTokEmb,
    jstring jDecoder,
    jstring jTokenizer,
    jstring jModelVersion,
    jint evalMode,
    jfloat temperature) {
  const std::string encoder = to_std(env, jEncoder);
  const std::string tokemb = to_std(env, jTokEmb);
  const std::string decoder = to_std(env, jDecoder);
  const std::string tokenizer = to_std(env, jTokenizer);
  const std::string version = to_std(env, jModelVersion);

  auto mk = [](const std::string& p) {
    return std::make_unique<Module>(
        p, Module::LoadMode::MmapUseMlockIgnoreErrors);
  };

  try {
    auto* runner = new example::QNNMultimodalRunner(
        mk(encoder),
        mk(tokemb),
        mk(decoder),
        version,
        tokenizer,
        /*performance_output_path=*/std::string(),
        /*dump_logits_path=*/std::string(),
        /*temperature=*/static_cast<float>(temperature),
        /*eval_mode=*/static_cast<int>(evalMode),
        /*shared_buffer=*/false,
        /*ngram=*/0,
        /*window=*/0,
        /*gcap=*/0);
    ET_LOG(Info, "QnnMultimodalRunner created (eval_mode=%d)", (int)evalMode);
    return reinterpret_cast<jlong>(runner);
  } catch (...) {
    ET_LOG(Error, "nativeCreate: failed to construct QNNMultimodalRunner");
    return 0;
  }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_electriclens_executorch_QnnMultimodalRunner_nativeGenerate(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jstring jImagePath,
    jstring jPrompt,
    jstring jSystemPrompt,
    jint seqLen) {
  if (handle == 0) {
    return env->NewStringUTF("");
  }
  auto* runner = reinterpret_cast<example::QNNMultimodalRunner*>(handle);
  const std::string image_path = to_std(env, jImagePath);
  const std::string prompt = to_std(env, jPrompt);
  const std::string system_prompt = to_std(env, jSystemPrompt);

  std::string out;
  auto callback = [&out](const std::string& piece) { out += piece; };

  // Wrap everything: the QNN backend / ET runtime throw C++ exceptions on
  // failure (bad model, DSP init, etc.). An exception crossing the JNI boundary
  // aborts the process, so catch and return a diagnostic string instead.
  try {
    auto mv = runner->get_model_version();
    if (!mv.ok()) {
      return env->NewStringUTF("[ERR] get_model_version failed");
    }
    example::ModelVersion model_version = mv.get();

    std::vector<std::string> prompts{prompt};
    std::vector<std::string> image_files{image_path};
    std::vector<std::string> audio_files;
    std::vector<Message> messages =
        prepare_messages(prompts, image_files, audio_files);

    Result<MethodMeta> method_meta = runner->get_encoder_method_meta();
    if (!method_meta.ok()) {
      return env->NewStringUTF("[ERR] get_encoder_method_meta failed");
    }
    auto input_meta = method_meta->input_tensor_meta(0);
    if (!input_meta.ok()) {
      return env->NewStringUTF("[ERR] encoder input_tensor_meta(0) failed");
    }
    std::vector<int32_t> expected_size(
        input_meta->sizes().begin(), input_meta->sizes().end());
    ScalarType expected_dtype = input_meta->scalar_type();

    GenerationConfig config{
        .echo = false,
        .ignore_eos = false,
        .max_new_tokens = -1,
        .warming = false,
        .seq_len = static_cast<int32_t>(seqLen),
        .temperature = 0.0f,
        .num_bos = 0,
        .num_eos = 0,
    };

    for (size_t j = 0; j < messages.size(); ++j) {
      const auto& msg = messages[j];
      std::vector<MultimodalInput> inputs;
      for (const auto& fp : msg.files_path) {
        Image image;
        example::load_image(fp, image, expected_size, expected_dtype);
        inputs.emplace_back(make_image_input(image));
      }
      std::string formatted =
          apply_chat_template(msg.text, system_prompt, model_version);
      inputs.emplace_back(make_text_input(formatted));
      inputs = dispatch_inputs(inputs, formatted);
      runner->generate(inputs, config, callback);
    }
  } catch (const std::exception& e) {
    ET_LOG(Error, "nativeGenerate exception: %s", e.what());
    return env->NewStringUTF((std::string("[ERR] ") + e.what()).c_str());
  } catch (...) {
    ET_LOG(Error, "nativeGenerate unknown exception");
    return env->NewStringUTF("[ERR] unknown native exception");
  }

  return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_electriclens_executorch_QnnMultimodalRunner_nativeDestroy(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle) {
  if (handle != 0) {
    delete reinterpret_cast<example::QNNMultimodalRunner*>(handle);
  }
}
