/*
 * JNI binding for SVOX Pico: loads one voice (text analysis + signal generation files) and
 * synthesizes UTF-8 text into 16 kHz mono 16-bit PCM, handed to Java in chunks.
 * Calls are serialized by the Java side.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "picoapi.h"

#define MEM_SIZE 2500000
#define OUT_CHUNK 1024
static const char *VOICE = "PicoVoice";

static void *mem = NULL;
static pico_System sys = NULL;
static pico_Resource taRes = NULL, sgRes = NULL;
static pico_Engine eng = NULL;
static char loadedTa[512] = "";

static void unload(void) {
    if (eng) { pico_disposeEngine(sys, &eng); pico_releaseVoiceDefinition(sys, (pico_Char *) VOICE); }
    if (sgRes) pico_unloadResource(sys, &sgRes);
    if (taRes) pico_unloadResource(sys, &taRes);
    if (sys) pico_terminate(&sys);
    free(mem);
    mem = NULL; sys = NULL; taRes = NULL; sgRes = NULL; eng = NULL;
    loadedTa[0] = 0;
}

static int load(const char *ta, const char *sg) {
    if (eng && strcmp(loadedTa, ta) == 0) return 1;
    unload();
    char taName[PICO_MAX_RESOURCE_NAME_SIZE], sgName[PICO_MAX_RESOURCE_NAME_SIZE];
    mem = malloc(MEM_SIZE);
    if (!mem || pico_initialize(mem, MEM_SIZE, &sys)) goto fail;
    if (pico_loadResource(sys, (const pico_Char *) ta, &taRes)) goto fail;
    if (pico_loadResource(sys, (const pico_Char *) sg, &sgRes)) goto fail;
    if (pico_getResourceName(sys, taRes, taName) || pico_getResourceName(sys, sgRes, sgName)) goto fail;
    if (pico_createVoiceDefinition(sys, (const pico_Char *) VOICE)) goto fail;
    if (pico_addResourceToVoiceDefinition(sys, (const pico_Char *) VOICE, (pico_Char *) taName) ||
        pico_addResourceToVoiceDefinition(sys, (const pico_Char *) VOICE, (pico_Char *) sgName)) goto fail;
    if (pico_newEngine(sys, (const pico_Char *) VOICE, &eng)) goto fail;
    strncpy(loadedTa, ta, sizeof(loadedTa) - 1);
    return 1;
fail:
    unload();
    return 0;
}

/*
 * Returns 0 when done, 1 when stopped by the callback, -1 on error.
 * sink.onAudio(byte[] buffer, int length) returns false to stop.
 */
JNIEXPORT jint JNICALL
Java_com_mg4link_picotts_PicoEngine_nativeSynthesize(JNIEnv *env, jclass cls, jstring jta, jstring jsg,
        jstring jtext, jobject sink) {
    const char *ta = (*env)->GetStringUTFChars(env, jta, NULL);
    const char *sg = (*env)->GetStringUTFChars(env, jsg, NULL);
    const char *text = (*env)->GetStringUTFChars(env, jtext, NULL);
    jint result = -1;
    jbyteArray buffer = NULL;
    jmethodID onAudio = (*env)->GetMethodID(env, (*env)->GetObjectClass(env, sink), "onAudio", "([BI)Z");

    if (!onAudio || !load(ta, sg)) goto done;
    pico_resetEngine(eng, PICO_RESET_SOFT);
    buffer = (*env)->NewByteArray(env, OUT_CHUNK);
    if (!buffer) goto done;
    {
        const pico_Char *inp = (const pico_Char *) text;
        pico_Int16 remaining = (pico_Int16) (strlen(text) + 1), sent, recv, type;
        short out[OUT_CHUNK / 2];
        result = 0;
        while (remaining > 0 && result == 0) {
            if (pico_putTextUtf8(eng, inp, remaining, &sent)) { result = -1; break; }
            remaining -= sent;
            inp += sent;
            pico_Status st;
            do {
                st = pico_getData(eng, out, sizeof(out), &recv, &type);
                if (st != PICO_STEP_BUSY && st != PICO_STEP_IDLE) { result = -1; break; }
                if (recv > 0) {
                    (*env)->SetByteArrayRegion(env, buffer, 0, recv, (const jbyte *) out);
                    if (!(*env)->CallBooleanMethod(env, sink, onAudio, buffer, (jint) recv) ||
                        (*env)->ExceptionCheck(env)) {
                        pico_resetEngine(eng, PICO_RESET_SOFT);
                        result = 1;
                        break;
                    }
                }
            } while (st == PICO_STEP_BUSY);
        }
    }

done:
    if (result < 0 && eng) unload();
    (*env)->ReleaseStringUTFChars(env, jtext, text);
    (*env)->ReleaseStringUTFChars(env, jsg, sg);
    (*env)->ReleaseStringUTFChars(env, jta, ta);
    return result;
}
