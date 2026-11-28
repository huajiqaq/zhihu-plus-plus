/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

#ifndef ZHIHU_ORT_H
#define ZHIHU_ORT_H

#include <onnxruntime_c_api.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#if ORT_API_VERSION < 18
#error "Zhihu++ Apple NLP requires ONNX Runtime C API 18 or newer headers"
#endif

/* 会话持有环境引用；关闭时释放会话线程池、分配器及环境引用。 */
typedef struct ZhihuOrtSession {
    const OrtApi *api;
    OrtEnv *environment;
    OrtSession *session;
} ZhihuOrtSession;

typedef struct ZhihuOrtRun {
    const OrtApi *api;
    OrtRunOptions *options;
} ZhihuOrtRun;

/* 状态对象跨 cinterop 保留到 Kotlin 读取错误消息后再释放。 */
static inline const char *zhihu_ort_status_message(const ZhihuOrtSession *owner, const void *status) {
    return owner->api->GetErrorMessage((const OrtStatus *)status);
}

static inline void zhihu_ort_release_status(const ZhihuOrtSession *owner, void *status) {
    if (status) owner->api->ReleaseStatus((OrtStatus *)status);
}

static inline void zhihu_ort_close(ZhihuOrtSession *owner) {
    if (!owner) return;
    if (owner->session) owner->api->ReleaseSession(owner->session);
    if (owner->environment) owner->api->ReleaseEnv(owner->environment);
    free(owner);
}

/* NULL 表示不支持 API18 或分配失败；尚未加载模型。 */
static inline ZhihuOrtSession *zhihu_ort_allocate(void) {
    const OrtApiBase *base = OrtGetApiBase();
    const OrtApi *api = base ? base->GetApi(18) : NULL;
    if (!api) return NULL;
    ZhihuOrtSession *owner = (ZhihuOrtSession *)calloc(1, sizeof(ZhihuOrtSession));
    if (owner) owner->api = api;
    return owner;
}

static inline void *zhihu_ort_open(ZhihuOrtSession *owner, const ORTCHAR_T *path) {
    OrtSessionOptions *options = NULL;
    OrtStatus *status = owner->api->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "ZhihuOfflineNlp", &owner->environment);
    if (status) return status;
    /* 官方开关作用于进程平台的 telemetry provider，在创建推理会话前显式关闭。 */
    status = owner->api->DisableTelemetryEvents(owner->environment);
    if (status) return status;
    status = owner->api->CreateSessionOptions(&options);
    if (status) return status;
    /* 只启用默认 CPU provider；不注册 CoreML，不创建每次推理的新线程池。 */
    status = owner->api->SetIntraOpNumThreads(options, 2);
    if (!status) status = owner->api->SetInterOpNumThreads(options, 1);
    if (!status) status = owner->api->SetSessionExecutionMode(options, ORT_SEQUENTIAL);
    if (!status) status = owner->api->SetSessionGraphOptimizationLevel(options, ORT_ENABLE_ALL);
    if (!status) status = owner->api->CreateSession(owner->environment, path, options, &owner->session);
    owner->api->ReleaseSessionOptions(options);
    return status;
}

static inline void *zhihu_ort_create_run(const ZhihuOrtSession *owner, ZhihuOrtRun **out) {
    *out = NULL;
    ZhihuOrtRun *run = (ZhihuOrtRun *)calloc(1, sizeof(ZhihuOrtRun));
    if (!run) return owner->api->CreateStatus(ORT_FAIL, "Cannot allocate ONNX Runtime run");
    run->api = owner->api;
    OrtStatus *status = owner->api->CreateRunOptions(&run->options);
    if (status) {
        free(run);
        return status;
    }
    *out = run;
    return NULL;
}

/* ORT 明确允许另一线程终止正在执行的 Run；调用者须先等待取消观察者退出再释放。 */
static inline void zhihu_ort_cancel_run(ZhihuOrtRun *run) {
    OrtStatus *status = run->api->RunOptionsSetTerminate(run->options);
    if (status) run->api->ReleaseStatus(status);
}

static inline void zhihu_ort_release_run(ZhihuOrtRun *run) {
    if (!run) return;
    run->api->ReleaseRunOptions(run->options);
    free(run);
}

static inline void *zhihu_ort_encode(
    const ZhihuOrtSession *owner,
    const ZhihuOrtRun *run,
    int64_t *input_ids,
    int64_t *attention_mask,
    int64_t *token_type_ids,
    size_t token_count,
    float *output,
    size_t output_count
) {
    const OrtApi *api = owner->api;
    if (!owner->session || !run || !input_ids || !attention_mask || !token_type_ids || !output ||
        token_count == 0 || token_count > 512 || output_count != token_count * 768) {
        return api->CreateStatus(ORT_INVALID_ARGUMENT, "Invalid BERT input or output buffer");
    }
    const char *input_names[] = {"input_ids", "attention_mask", "token_type_ids"};
    const char *output_names[] = {"last_hidden_state"};
    int64_t shape[] = {1, (int64_t)token_count};
    int64_t *buffers[] = {input_ids, attention_mask, token_type_ids};
    OrtValue *inputs[3] = {NULL, NULL, NULL};
    const OrtValue *input_values[3] = {NULL, NULL, NULL};
    OrtValue *result = NULL;
    OrtMemoryInfo *memory_info = NULL;
    OrtTensorTypeAndShapeInfo *result_info = NULL;
    OrtStatus *status = api->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memory_info);
    for (size_t index = 0; index < 3 && !status; ++index) {
        status = api->CreateTensorWithDataAsOrtValue(
            memory_info, buffers[index], token_count * sizeof(int64_t), shape, 2,
            ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, &inputs[index]
        );
        input_values[index] = inputs[index];
    }
    if (!status) status = api->Run(owner->session, run->options, input_names, input_values, 3, output_names, 1, &result);
    int is_tensor = 0;
    if (!status) status = api->IsTensor(result, &is_tensor);
    if (!status && !is_tensor) status = api->CreateStatus(ORT_INVALID_ARGUMENT, "last_hidden_state is not a tensor");
    if (!status) status = api->GetTensorTypeAndShape(result, &result_info);
    ONNXTensorElementDataType element_type = ONNX_TENSOR_ELEMENT_DATA_TYPE_UNDEFINED;
    size_t rank = 0;
    if (!status) status = api->GetTensorElementType(result_info, &element_type);
    if (!status) status = api->GetDimensionsCount(result_info, &rank);
    if (!status && (element_type != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT || rank != 3)) {
        status = api->CreateStatus(ORT_INVALID_ARGUMENT, "Expected FP32 last_hidden_state[1,tokens,768]");
    }
    int64_t dimensions[3] = {0, 0, 0};
    if (!status) status = api->GetDimensions(result_info, dimensions, 3);
    if (!status && (dimensions[0] != 1 || dimensions[1] != (int64_t)token_count || dimensions[2] != 768)) {
        status = api->CreateStatus(ORT_INVALID_ARGUMENT, "Unexpected last_hidden_state dimensions");
    }
    void *values = NULL;
    if (!status) status = api->GetTensorMutableData(result, &values);
    if (!status && !values) status = api->CreateStatus(ORT_FAIL, "Missing last_hidden_state data");
    if (!status) memcpy(output, values, output_count * sizeof(float));
    if (result_info) api->ReleaseTensorTypeAndShapeInfo(result_info);
    if (result) api->ReleaseValue(result);
    for (size_t index = 0; index < 3; ++index) {
        if (inputs[index]) api->ReleaseValue(inputs[index]);
    }
    if (memory_info) api->ReleaseMemoryInfo(memory_info);
    return status;
}

#endif
