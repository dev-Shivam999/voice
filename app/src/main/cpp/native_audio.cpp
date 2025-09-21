#include <jni.h>
#include <string>
#include <atomic>
#include <memory>
#include <android/log.h>

#include <oboe/Oboe.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "native_audio", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "native_audio", __VA_ARGS__)

using namespace oboe;

// Simple lock-free ring buffer for float samples (mono)
class FloatRingBuffer
{
public:
    FloatRingBuffer(size_t capacity) : capacity_(capacity), buffer_(new float[capacity])
    {
        head_.store(0);
        tail_.store(0);
    }

    ~FloatRingBuffer() { delete[] buffer_; }

    size_t write(const float *data, size_t frames)
    {
        size_t head = head_.load(std::memory_order_relaxed);
        size_t tail = tail_.load(std::memory_order_acquire);
        size_t free = capacity_ - (head - tail);
        size_t toWrite = std::min(frames, free);
        for (size_t i = 0; i < toWrite; ++i)
        {
            buffer_[(head + i) % capacity_] = data[i];
        }
        head_.store(head + toWrite, std::memory_order_release);
        return toWrite;
    }

    size_t read(float *out, size_t frames)
    {
        size_t head = head_.load(std::memory_order_acquire);
        size_t tail = tail_.load(std::memory_order_relaxed);
        size_t available = head - tail;
        size_t toRead = std::min(frames, available);
        for (size_t i = 0; i < toRead; ++i)
        {
            out[i] = buffer_[(tail + i) % capacity_];
        }
        tail_.store(tail + toRead, std::memory_order_release);
        return toRead;
    }

private:
    const size_t capacity_;
    float *buffer_;
    std::atomic<size_t> head_;
    std::atomic<size_t> tail_;
};

static std::shared_ptr<AudioStream> gInputStream = nullptr;
static std::shared_ptr<AudioStream> gOutputStream = nullptr;
static std::unique_ptr<FloatRingBuffer> gRing;
static std::atomic<bool> gStarted(false);

class InputCallback : public AudioStreamCallback
{
public:
    DataCallbackResult onAudioReady(AudioStream *oboeStream, void *audioData, int32_t numFrames) override
    {
        // audioData is float* when stream format is Float
        float *in = static_cast<float *>(audioData);
        // write to ring buffer (may write less than provided)
        if (gRing)
        {
            gRing->write(in, (size_t)numFrames);
        }
        return DataCallbackResult::Continue;
    }
};

class OutputCallback : public AudioStreamCallback
{
public:
    DataCallbackResult onAudioReady(AudioStream *oboeStream, void *audioData, int32_t numFrames) override
    {
        float *out = static_cast<float *>(audioData);
        if (!gRing)
        {
            // silence
            memset(out, 0, sizeof(float) * numFrames);
            return DataCallbackResult::Continue;
        }
        size_t read = gRing->read(out, (size_t)numFrames);
        if (read < (size_t)numFrames)
        {
            // zero the remainder
            memset(out + read, 0, sizeof(float) * (numFrames - (int)read));
        }
        return DataCallbackResult::Continue;
    }
};

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_voicechanger_NativeAudio_nativeInit(JNIEnv *env, jobject /* this */)
{
    LOGI("nativeInit called");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_voicechanger_NativeAudio_nativeStart(JNIEnv *env, jobject /* this */)
{
    LOGI("nativeStart called");
    if (gStarted.load())
        return JNI_TRUE;

    // Create ring buffer for 1 second of float audio at 48000 Hz (adjust later)
    const int sampleRate = 48000;
    const size_t capacity = sampleRate; // 1 second buffer
    gRing.reset(new FloatRingBuffer(capacity));

    InputCallback *inCb = new InputCallback();
    OutputCallback *outCb = new OutputCallback();

    AudioStreamBuilder inBuilder;
    inBuilder.setDirection(Direction::Input);
    inBuilder.setFormat(AudioFormat::Float);
    inBuilder.setChannelCount(1);
    inBuilder.setPerformanceMode(PerformanceMode::LowLatency);
    inBuilder.setCallback(inCb);

    AudioStream *inStream = nullptr;
    Result r1 = inBuilder.openStream(&inStream);
    if (r1 != Result::OK || inStream == nullptr)
    {
        LOGE("Failed to open input stream: %s", convertToText(r1));
        delete inCb;
        return JNI_FALSE;
    }

    AudioStreamBuilder outBuilder;
    outBuilder.setDirection(Direction::Output);
    outBuilder.setFormat(AudioFormat::Float);
    outBuilder.setChannelCount(1);
    outBuilder.setPerformanceMode(PerformanceMode::LowLatency);
    outBuilder.setCallback(outCb);

    AudioStream *outStream = nullptr;
    Result r2 = outBuilder.openStream(&outStream);
    if (r2 != Result::OK || outStream == nullptr)
    {
        LOGE("Failed to open output stream: %s", convertToText(r2));
        delete outCb;
        inStream->close();
        delete inCb;
        return JNI_FALSE;
    }

    gInputStream = std::shared_ptr<AudioStream>(inStream);
    gOutputStream = std::shared_ptr<AudioStream>(outStream);

    Result s1 = gInputStream->requestStart();
    Result s2 = gOutputStream->requestStart();
    if (s1 != Result::OK || s2 != Result::OK)
    {
        LOGE("Failed to start streams: %s %s", convertToText(s1), convertToText(s2));
        gInputStream->close();
        gOutputStream->close();
        gInputStream.reset();
        gOutputStream.reset();
        delete inCb;
        delete outCb;
        return JNI_FALSE;
    }

    gStarted.store(true);
    LOGI("Input+Output Oboe streams started");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_voicechanger_NativeAudio_nativeStop(JNIEnv *env, jobject /* this */)
{
    LOGI("nativeStop called");
    if (!gStarted.load())
        return;
    if (gInputStream)
    {
        gInputStream->requestStop();
        gInputStream->close();
        gInputStream.reset();
    }
    if (gOutputStream)
    {
        gOutputStream->requestStop();
        gOutputStream->close();
        gOutputStream.reset();
    }
    gRing.reset();
    gStarted.store(false);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_voicechanger_NativeAudio_nativeVersion(JNIEnv *env, jobject /* this */)
{
    std::string ver = "native-audio-oboe-0.1";
    return env->NewStringUTF(ver.c_str());
}
