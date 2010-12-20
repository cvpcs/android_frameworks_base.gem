/* //device/include/server/AudioFlinger/AudioMixer.cpp
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "AudioMixer"
//#define LOG_NDEBUG 0

#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <utils/Log.h>

#include "AudioMixer.h"

namespace android {
// ----------------------------------------------------------------------------

static inline int16_t clamp16(int32_t sample)
{
    if ((sample>>15) ^ (sample>>31))
        sample = 0x7FFF ^ (sample>>31);
    return sample;
}

inline static int32_t prng() {
    static int32_t seed = 1;
    seed = (seed * 12345) + 1103515245;
    return int32_t(seed & 0xfff);
}

// ----------------------------------------------------------------------------

AudioMixer::AudioMixer(size_t frameCount, uint32_t sampleRate, AudioDSP& dsp)
    :   mActiveTrack(0), mTrackNames(0), mSampleRate(sampleRate), mDsp(dsp)
{
    mDsp.configure(sampleRate);
    mState.enabledTracks= 0;
    mState.needsChanged = 0;
    mState.frameCount   = frameCount;
    mState.outputTemp   = 0;
    mState.resampleTemp = 0;
    mState.hook         = process__nop;
    track_t* t = mState.tracks;
    for (int i=0 ; i<32 ; i++) {
        t->needs = 0;
        t->volume[0] = UNITY_GAIN;
        t->volume[1] = UNITY_GAIN;
        t->prevVolume[0] = UNITY_GAIN << 16;
        t->prevVolume[1] = UNITY_GAIN << 16;
        t->volumeInc[0] = 0;
        t->volumeInc[1] = 0;
        t->auxLevel = 0;
        t->auxInc = 0;
        t->channelCount = 2;
        t->enabled = 0;
        t->format = 16;
        t->buffer.raw = 0;
        t->bufferProvider = 0;
        t->hook = 0;
        t->resampler = 0;
        t->sampleRate = mSampleRate;
        t->in = 0;
        t->mainBuffer = NULL;
        t->auxBuffer = NULL;
        t++;
    }
}

 AudioMixer::~AudioMixer()
 {
     track_t* t = mState.tracks;
     for (int i=0 ; i<32 ; i++) {
         delete t->resampler;
         t++;
     }
     delete [] mState.outputTemp;
     delete [] mState.resampleTemp;
 }

 int AudioMixer::getTrackName()
 {
    uint32_t names = mTrackNames;
    uint32_t mask = 1;
    int n = 0;
    while (names & mask) {
        mask <<= 1;
        n++;
    }
    if (mask) {
        LOGV("add track (%d)", n);
        mTrackNames |= mask;
        return TRACK0 + n;
    }
    return -1;
 }

 void AudioMixer::invalidateState(uint32_t mask)
 {
    if (mask) {
        mState.needsChanged |= mask;
        mState.hook = process__validate;
    }
 }

 void AudioMixer::deleteTrackName(int name)
 {
    name -= TRACK0;
    if (uint32_t(name) < MAX_NUM_TRACKS) {
        LOGV("deleteTrackName(%d)", name);
        track_t& track(mState.tracks[ name ]);
        if (track.enabled != 0) {
            track.enabled = 0;
            invalidateState(1<<name);
        }
        if (track.resampler) {
            // delete  the resampler
            delete track.resampler;
            track.resampler = 0;
            track.sampleRate = mSampleRate;
            invalidateState(1<<name);
        }
        track.volumeInc[0] = 0;
        track.volumeInc[1] = 0;
        mTrackNames &= ~(1<<name);
    }
 }

status_t AudioMixer::enable(int name)
{
    switch (name) {
        case MIXING: {
            if (mState.tracks[ mActiveTrack ].enabled != 1) {
                mState.tracks[ mActiveTrack ].enabled = 1;
                LOGV("enable(%d)", mActiveTrack);
                invalidateState(1<<mActiveTrack);
            }
        } break;
        default:
            return NAME_NOT_FOUND;
    }
    return NO_ERROR;
}

status_t AudioMixer::disable(int name)
{
    switch (name) {
        case MIXING: {
            if (mState.tracks[ mActiveTrack ].enabled != 0) {
                mState.tracks[ mActiveTrack ].enabled = 0;
                LOGV("disable(%d)", mActiveTrack);
                invalidateState(1<<mActiveTrack);
            }
        } break;
        default:
            return NAME_NOT_FOUND;
    }
    return NO_ERROR;
}

status_t AudioMixer::setActiveTrack(int track)
{
    if (uint32_t(track-TRACK0) >= MAX_NUM_TRACKS) {
        return BAD_VALUE;
    }
    mActiveTrack = track - TRACK0;
    return NO_ERROR;
}

status_t AudioMixer::setParameter(int target, int name, void *value)
{
    int valueInt = (int)value;
    int32_t *valueBuf = (int32_t *)value;

    switch (target) {
    case TRACK:
        if (name == CHANNEL_COUNT) {
            if ((uint32_t(valueInt) <= MAX_NUM_CHANNELS) && (valueInt)) {
                if (mState.tracks[ mActiveTrack ].channelCount != valueInt) {
                    mState.tracks[ mActiveTrack ].channelCount = valueInt;
                    LOGV("setParameter(TRACK, CHANNEL_COUNT, %d)", valueInt);
                    invalidateState(1<<mActiveTrack);
                }
                return NO_ERROR;
            }
        }
        if (name == MAIN_BUFFER) {
            if (mState.tracks[ mActiveTrack ].mainBuffer != valueBuf) {
                mState.tracks[ mActiveTrack ].mainBuffer = valueBuf;
                LOGV("setParameter(TRACK, MAIN_BUFFER, %p)", valueBuf);
                invalidateState(1<<mActiveTrack);
            }
            return NO_ERROR;
        }
        if (name == AUX_BUFFER) {
            if (mState.tracks[ mActiveTrack ].auxBuffer != valueBuf) {
                mState.tracks[ mActiveTrack ].auxBuffer = valueBuf;
                LOGV("setParameter(TRACK, AUX_BUFFER, %p)", valueBuf);
                invalidateState(1<<mActiveTrack);
            }
            return NO_ERROR;
        }

        break;
    case RESAMPLE:
        if (name == SAMPLE_RATE) {
            if (valueInt > 0) {
                track_t& track = mState.tracks[ mActiveTrack ];
                if (track.setResampler(uint32_t(valueInt), mSampleRate)) {
                    LOGV("setParameter(RESAMPLE, SAMPLE_RATE, %u)",
                            uint32_t(valueInt));
                    invalidateState(1<<mActiveTrack);
                }
                return NO_ERROR;
            }
        }
        break;
    case RAMP_VOLUME:
    case VOLUME:
        if ((uint32_t(name-VOLUME0) < MAX_NUM_CHANNELS)) {
            track_t& track = mState.tracks[ mActiveTrack ];
            if (track.volume[name-VOLUME0] != valueInt) {
                LOGV("setParameter(VOLUME, VOLUME0/1: %04x)", valueInt);
                track.volume[name-VOLUME0] = valueInt;
                if (target == VOLUME) {
                    track.prevVolume[name-VOLUME0] = valueInt << 16;
                    track.volumeInc[name-VOLUME0] = 0;
                }
                invalidateState(1<<mActiveTrack);
            }
            return NO_ERROR;
        } else if (name == AUXLEVEL) {
            track_t& track = mState.tracks[ mActiveTrack ];
            if (track.auxLevel != valueInt) {
                LOGV("setParameter(VOLUME, AUXLEVEL: %04x)", valueInt);
                track.prevAuxLevel = track.auxLevel << 16;
                track.auxLevel = valueInt;
                if (target == VOLUME) {
                    track.prevAuxLevel = valueInt << 16;
                    track.auxInc = 0;
                } else {
                    int32_t d = (valueInt<<16) - track.prevAuxLevel;
                    int32_t volInc = d / int32_t(mState.frameCount);
                    track.auxInc = volInc;
                    if (volInc == 0) {
                        track.prevAuxLevel = valueInt << 16;
                    }
                }
                invalidateState(1<<mActiveTrack);
            }
            return NO_ERROR;
        }
        break;
    }
    return BAD_VALUE;
}

bool AudioMixer::track_t::setResampler(uint32_t value, uint32_t devSampleRate)
{
    if (value!=devSampleRate || resampler) {
        if (sampleRate != value) {
            sampleRate = value;
            if (resampler == 0) {
                resampler = AudioResampler::create(
                        format, channelCount, devSampleRate);
            }
            return true;
        }
    }
    return false;
}

bool AudioMixer::track_t::doesResample() const
{
    return resampler != 0;
}

void AudioMixer::track_t::adjustVolumeRamp(bool aux, AudioDSP &dsp, size_t frames)
{
    int32_t dynamicRangeCompressionFactor = dsp.estimateLevel(
        static_cast<const int16_t*>(in), int32_t(frames), channelCount
    );

    for (int i = 0; i < 2; i ++) {
        /* Ramp from current to new volume level if necessary */
        int32_t desiredVolume = volume[i] * dynamicRangeCompressionFactor;
        int32_t d = desiredVolume - prevVolume[i];
        /* limit change rate to smooth the compressor. */
        int32_t volChangeLimit = prevVolume[i] >> 9;
        volChangeLimit -= 1;
        int32_t volInc = d / int32_t(frames);
        if (volInc < -(volChangeLimit)) {
            volInc = -(volChangeLimit);
        }
        /* Make ramps up slower, but ramps down fast. */
        volChangeLimit >>= 4;
        volChangeLimit += 1;
        if (volInc > volChangeLimit) {
            volInc = volChangeLimit;
        }
        volumeInc[i] = volInc;
    }
    if (aux) {
        /* Ramp from current to new volume level if necessary */
        int32_t desiredVolume = auxLevel * dynamicRangeCompressionFactor;
        int32_t d = desiredVolume - prevAuxLevel;
        /* limit change rate to smooth the compressor. */
        int32_t volChangeLimit = prevAuxLevel >> 9;
        volChangeLimit -= 1;
        int32_t volInc = d / int32_t(frames);
        if (volInc < -(volChangeLimit)) {
            volInc = -(volChangeLimit);
        }
        /* Make ramps up slower, but ramps down fast. */
        volChangeLimit >>= 4;
        volChangeLimit += 1;
        if (volInc > volChangeLimit) {
            volInc = volChangeLimit;
        }
        auxInc = volInc;
    }
}


status_t AudioMixer::setBufferProvider(AudioBufferProvider* buffer)
{
    mState.tracks[ mActiveTrack ].bufferProvider = buffer;
    return NO_ERROR;
}



void AudioMixer::process()
{
    mState.hook(&mState, mDsp);
}


void AudioMixer::process__validate(state_t* state, AudioDSP& dsp)
{
    LOGW_IF(!state->needsChanged,
        "in process__validate() but nothing's invalid");

    uint32_t changed = state->needsChanged;
    state->needsChanged = 0; // clear the validation flag

    // recompute which tracks are enabled / disabled
    uint32_t enabled = 0;
    uint32_t disabled = 0;
    while (changed) {
        const int i = 31 - __builtin_clz(changed);
        const uint32_t mask = 1<<i;
        changed &= ~mask;
        track_t& t = state->tracks[i];
        (t.enabled ? enabled : disabled) |= mask;
    }
    state->enabledTracks &= ~disabled;
    state->enabledTracks |=  enabled;

    // compute everything we need...
    int countActiveTracks = 0;
    int all16BitsStereoNoResample = 1;
    int resampling = 0;
    int volumeRamp = 0;
    uint32_t en = state->enabledTracks;
    while (en) {
        const int i = 31 - __builtin_clz(en);
        en &= ~(1<<i);

        countActiveTracks++;
        track_t& t = state->tracks[i];
        uint32_t n = 0;
        n |= NEEDS_CHANNEL_1 + t.channelCount - 1;
        n |= NEEDS_FORMAT_16;
        n |= t.doesResample() ? NEEDS_RESAMPLE_ENABLED : NEEDS_RESAMPLE_DISABLED;
        if (t.auxLevel != 0 && t.auxBuffer != NULL) {
            n |= NEEDS_AUX_ENABLED;
        }

        if (t.volumeInc[0]|t.volumeInc[1]) {
            volumeRamp = 1;
        } else if (!t.doesResample() && t.volumeRL == 0) {
            n |= NEEDS_MUTE_ENABLED;
        }
        t.needs = n;

        if ((n & NEEDS_MUTE__MASK) == NEEDS_MUTE_ENABLED) {
            t.hook = track__nop;
        } else {
            if ((n & NEEDS_AUX__MASK) == NEEDS_AUX_ENABLED) {
                all16BitsStereoNoResample = 0;
            }
            if ((n & NEEDS_RESAMPLE__MASK) == NEEDS_RESAMPLE_ENABLED) {
                all16BitsStereoNoResample = 0;
                resampling = 1;
                t.hook = track__genericResample;
            } else {
                if ((n & NEEDS_CHANNEL_COUNT__MASK) == NEEDS_CHANNEL_1){
                    t.hook = track__16BitsMono;
                    all16BitsStereoNoResample = 0;
                }
                if ((n & NEEDS_CHANNEL_COUNT__MASK) == NEEDS_CHANNEL_2){
                    t.hook = track__16BitsStereo;
                }
            }
        }
    }

    // select the processing hooks
    state->hook = process__nop;
    if (countActiveTracks) {
        if (resampling) {
            if (!state->outputTemp) {
                state->outputTemp = new int32_t[MAX_NUM_CHANNELS * state->frameCount];
            }
            if (!state->resampleTemp) {
                state->resampleTemp = new int32_t[MAX_NUM_CHANNELS * state->frameCount];
            }
            state->hook = process__genericResampling;
        } else {
            if (state->outputTemp) {
                delete [] state->outputTemp;
                state->outputTemp = 0;
            }
            if (state->resampleTemp) {
                delete [] state->resampleTemp;
                state->resampleTemp = 0;
            }
            state->hook = process__genericNoResampling;
        }
    }

    LOGV("mixer configuration change: %d activeTracks (%08x) "
        "all16BitsStereoNoResample=%d, resampling=%d, volumeRamp=%d",
        countActiveTracks, state->enabledTracks,
        all16BitsStereoNoResample, resampling, volumeRamp);

   state->hook(state, dsp);

   // Now that the volume ramp has been done, set optimal state and
   // track hooks for subsequent mixer process
   if (countActiveTracks) {
       int allMuted = 1;
       uint32_t en = state->enabledTracks;
       while (en) {
           const int i = 31 - __builtin_clz(en);
           en &= ~(1<<i);
           track_t& t = state->tracks[i];
           if (!t.doesResample() && t.volumeRL == 0)
           {
               t.needs |= NEEDS_MUTE_ENABLED;
               t.hook = track__nop;
           } else {
               allMuted = 0;
           }
       }
       if (allMuted) {
           state->hook = process__nop;
       }
   }
}

static inline
int32_t mulAdd(int16_t in, int16_t v, int32_t a)
{
#if defined(__arm__) && !defined(__thumb__)
    int32_t out;
    asm( "smlabb %[out], %[in], %[v], %[a] \n"
         : [out]"=r"(out)
         : [in]"%r"(in), [v]"r"(v), [a]"r"(a)
         : );
    return out;
#else
    return a + in * int32_t(v);
#endif
}

static inline
int32_t mul(int16_t in, int16_t v)
{
#if defined(__arm__) && !defined(__thumb__)
    int32_t out;
    asm( "smulbb %[out], %[in], %[v] \n"
         : [out]"=r"(out)
         : [in]"%r"(in), [v]"r"(v)
         : );
    return out;
#else
    return in * int32_t(v);
#endif
}

static inline
int32_t mulAddRL(int left, uint32_t inRL, uint32_t vRL, int32_t a)
{
#if defined(__arm__) && !defined(__thumb__)
    int32_t out;
    if (left) {
        asm( "smlabb %[out], %[inRL], %[vRL], %[a] \n"
             : [out]"=r"(out)
             : [inRL]"%r"(inRL), [vRL]"r"(vRL), [a]"r"(a)
             : );
    } else {
        asm( "smlatt %[out], %[inRL], %[vRL], %[a] \n"
             : [out]"=r"(out)
             : [inRL]"%r"(inRL), [vRL]"r"(vRL), [a]"r"(a)
             : );
    }
    return out;
#else
    if (left) {
        return a + int16_t(inRL&0xFFFF) * int16_t(vRL&0xFFFF);
    } else {
        return a + int16_t(inRL>>16) * int16_t(vRL>>16);
    }
#endif
}

static inline
int32_t mulRL(int left, uint32_t inRL, uint32_t vRL)
{
#if defined(__arm__) && !defined(__thumb__)
    int32_t out;
    if (left) {
        asm( "smulbb %[out], %[inRL], %[vRL] \n"
             : [out]"=r"(out)
             : [inRL]"%r"(inRL), [vRL]"r"(vRL)
             : );
    } else {
        asm( "smultt %[out], %[inRL], %[vRL] \n"
             : [out]"=r"(out)
             : [inRL]"%r"(inRL), [vRL]"r"(vRL)
             : );
    }
    return out;
#else
    if (left) {
        return int16_t(inRL&0xFFFF) * int16_t(vRL&0xFFFF);
    } else {
        return int16_t(inRL>>16) * int16_t(vRL>>16);
    }
#endif
}


void AudioMixer::track__genericResample(track_t* t, int32_t* out, size_t outFrameCount, int32_t* temp, int32_t* aux, AudioDSP& dsp)
{
    t->resampler->setSampleRate(t->sampleRate);

    // ramp gain - resample to temp buffer and scale/mix in 2nd step
    if (aux != NULL) {
        // always resample with unity gain when sending to auxiliary buffer to be able
        // to apply send level after resampling
        // TODO: modify each resampler to support aux channel?
        t->resampler->setVolume(UNITY_GAIN, UNITY_GAIN);
        memset(temp, 0, outFrameCount * MAX_NUM_CHANNELS * sizeof(int32_t));
        t->resampler->resample(temp, outFrameCount, t->bufferProvider);
        if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]|t->auxInc) {
            volumeRampStereo(t, out, outFrameCount, temp, aux, dsp);
        } else {
            volumeStereo(t, out, outFrameCount, temp, aux);
        }
    } else {
        if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]) {
            t->resampler->setVolume(UNITY_GAIN, UNITY_GAIN);
            memset(temp, 0, outFrameCount * MAX_NUM_CHANNELS * sizeof(int32_t));
            t->resampler->resample(temp, outFrameCount, t->bufferProvider);
            volumeRampStereo(t, out, outFrameCount, temp, aux, dsp);
        }

        // constant gain
        else {
            t->resampler->setVolume(t->volume[0], t->volume[1]);
            t->resampler->resample(out, outFrameCount, t->bufferProvider);
        }
    }
}

void AudioMixer::track__nop(track_t* t, int32_t* out, size_t outFrameCount, int32_t* temp, int32_t* aux, AudioDSP& dsp)
{
}

void AudioMixer::volumeRampStereo(track_t* t, int32_t* out, size_t frameCount, int32_t* temp, int32_t* aux, AudioDSP& dsp)
{
    t->adjustVolumeRamp((aux != NULL), dsp, frameCount);
    int32_t vl = t->prevVolume[0];
    int32_t vr = t->prevVolume[1];
    const int32_t vlInc = t->volumeInc[0];
    const int32_t vrInc = t->volumeInc[1];

    //LOGD("[0] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
    //        t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
    //       (vl + vlInc*frameCount)/65536.0f, frameCount);

    // ramp volume
    if UNLIKELY(aux != NULL) {
        int32_t va = t->prevAuxLevel;
        const int32_t vaInc = t->auxInc;
        int32_t l;
        int32_t r;

        do {
            l = (*temp++ >> 12);
            r = (*temp++ >> 12);
            *out++ += (vl >> 16) * l;
            *out++ += (vr >> 16) * r;
            *aux++ += (va >> 17) * (l + r);
            vl += vlInc;
            vr += vrInc;
            va += vaInc;
        } while (--frameCount);
        t->prevAuxLevel = va;
    } else {
        do {
            *out++ += (vl >> 16) * (*temp++ >> 12);
            *out++ += (vr >> 16) * (*temp++ >> 12);
            vl += vlInc;
            vr += vrInc;
        } while (--frameCount);
    }
    t->prevVolume[0] = vl;
    t->prevVolume[1] = vr;
}

void AudioMixer::volumeStereo(track_t* t, int32_t* out, size_t frameCount, int32_t* temp, int32_t* aux)
{
    const int16_t vl = t->volume[0];
    const int16_t vr = t->volume[1];

    if UNLIKELY(aux != NULL) {
        const int16_t va = (int16_t)t->auxLevel;
        do {
            int16_t l = (int16_t)(*temp++ >> 12);
            int16_t r = (int16_t)(*temp++ >> 12);
            out[0] = mulAdd(l, vl, out[0]);
            int16_t a = (int16_t)(((int32_t)l + r) >> 1);
            out[1] = mulAdd(r, vr, out[1]);
            out += 2;
            aux[0] = mulAdd(a, va, aux[0]);
            aux++;
        } while (--frameCount);
    } else {
        do {
            int16_t l = (int16_t)(*temp++ >> 12);
            int16_t r = (int16_t)(*temp++ >> 12);
            out[0] = mulAdd(l, vl, out[0]);
            out[1] = mulAdd(r, vr, out[1]);
            out += 2;
        } while (--frameCount);
    }
}

void AudioMixer::track__16BitsStereo(track_t* t, int32_t* out, size_t frameCount, int32_t* temp, int32_t* aux, AudioDSP& dsp)
{
    int16_t const *in = static_cast<int16_t const *>(t->in);

    if UNLIKELY(aux != NULL) {
        int32_t l;
        int32_t r;
        // ramp gain
        t->adjustVolumeRamp(true, dsp, frameCount);
        if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]|t->auxInc) {
            int32_t vl = t->prevVolume[0];
            int32_t vr = t->prevVolume[1];
            int32_t va = t->prevAuxLevel;
            const int32_t vlInc = t->volumeInc[0];
            const int32_t vrInc = t->volumeInc[1];
            const int32_t vaInc = t->auxInc;
            // LOGD("[1] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
            //        t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
            //        (vl + vlInc*frameCount)/65536.0f, frameCount);

            do {
                l = (int32_t)*in++;
                r = (int32_t)*in++;
                *out++ += (vl >> 16) * l;
                *out++ += (vr >> 16) * r;
                *aux++ += (va >> 17) * (l + r);
                vl += vlInc;
                vr += vrInc;
                va += vaInc;
            } while (--frameCount);

            t->prevVolume[0] = vl;
            t->prevVolume[1] = vr;
            t->prevAuxLevel = va;
        }

        // constant gain
        else {
            const uint32_t vrl = t->volumeRL;
            const int16_t va = (int16_t)t->auxLevel;
            do {
                uint32_t rl = *reinterpret_cast<uint32_t const *>(in);
                int16_t a = (int16_t)(((int32_t)in[0] + in[1]) >> 1);
                in += 2;
                out[0] = mulAddRL(1, rl, vrl, out[0]);
                out[1] = mulAddRL(0, rl, vrl, out[1]);
                out += 2;
                aux[0] = mulAdd(a, va, aux[0]);
                aux++;
            } while (--frameCount);
        }
    } else {
        // ramp gain
        t->adjustVolumeRamp(false, dsp, frameCount);
        if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]) {
            int32_t vl = t->prevVolume[0];
            int32_t vr = t->prevVolume[1];
            const int32_t vlInc = t->volumeInc[0];
            const int32_t vrInc = t->volumeInc[1];

            // LOGD("[1] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
            //        t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
            //        (vl + vlInc*frameCount)/65536.0f, frameCount);

            do {
                *out++ += (vl >> 16) * (int32_t) *in++;
                *out++ += (vr >> 16) * (int32_t) *in++;
                vl += vlInc;
                vr += vrInc;
            } while (--frameCount);

            t->prevVolume[0] = vl;
            t->prevVolume[1] = vr;
        }

        // constant gain
        else {
            const uint32_t vrl = t->volumeRL;
            do {
                uint32_t rl = *reinterpret_cast<uint32_t const *>(in);
                in += 2;
                out[0] = mulAddRL(1, rl, vrl, out[0]);
                out[1] = mulAddRL(0, rl, vrl, out[1]);
                out += 2;
            } while (--frameCount);
        }
    }
    t->in = in;
}

void AudioMixer::track__16BitsMono(track_t* t, int32_t* out, size_t frameCount, int32_t* temp, int32_t* aux, AudioDSP& dsp)
{
    int16_t const *in = static_cast<int16_t const *>(t->in);

    if UNLIKELY(aux != NULL) {
        // ramp gain
        t->adjustVolumeRamp(true, dsp, frameCount);
        if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]|t->auxInc) {
            int32_t vl = t->prevVolume[0];
            int32_t vr = t->prevVolume[1];
            int32_t va = t->prevAuxLevel;
            const int32_t vlInc = t->volumeInc[0];
            const int32_t vrInc = t->volumeInc[1];
            const int32_t vaInc = t->auxInc;

            // LOGD("[2] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
            //         t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
            //         (vl + vlInc*frameCount)/65536.0f, frameCount);

            do {
                int32_t l = *in++;
                *out++ += (vl >> 16) * l;
                *out++ += (vr >> 16) * l;
                *aux++ += (va >> 16) * l;
                vl += vlInc;
                vr += vrInc;
                va += vaInc;
            } while (--frameCount);

            t->prevVolume[0] = vl;
            t->prevVolume[1] = vr;
            t->prevAuxLevel = va;
        }
        // constant gain
        else {
            const int16_t vl = t->volume[0];
            const int16_t vr = t->volume[1];
            const int16_t va = (int16_t)t->auxLevel;
            do {
                int16_t l = *in++;
                out[0] = mulAdd(l, vl, out[0]);
                out[1] = mulAdd(l, vr, out[1]);
                out += 2;
                aux[0] = mulAdd(l, va, aux[0]);
                aux++;
            } while (--frameCount);
        }
    } else {
        // ramp gain
        t->adjustVolumeRamp(false, dsp, frameCount);
        if UNLIKELY(t->volumeInc[0]|t->volumeInc[1]) {
            int32_t vl = t->prevVolume[0];
            int32_t vr = t->prevVolume[1];
            const int32_t vlInc = t->volumeInc[0];
            const int32_t vrInc = t->volumeInc[1];

            // LOGD("[2] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
            //         t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
            //         (vl + vlInc*frameCount)/65536.0f, frameCount);

            do {
                int32_t l = *in++;
                *out++ += (vl >> 16) * l;
                *out++ += (vr >> 16) * l;
                vl += vlInc;
                vr += vrInc;
            } while (--frameCount);

            t->prevVolume[0] = vl;
            t->prevVolume[1] = vr;
        }
        // constant gain
        else {
            const int16_t vl = t->volume[0];
            const int16_t vr = t->volume[1];
            do {
                int16_t l = *in++;
                out[0] = mulAdd(l, vl, out[0]);
                out[1] = mulAdd(l, vr, out[1]);
                out += 2;
            } while (--frameCount);
        }
    }
    t->in = in;
}

void AudioMixer::ditherAndClamp(int32_t* out, int32_t const *sums, size_t c)
{
    int32_t oldDitherValue = prng();
    for (size_t i=0 ; i<c ; i++) {
        int32_t l = *sums++;
        int32_t r = *sums++;

        /* Apply dither to output. This is the high-passed triangular
         * probability density function, discussed in "A Theory of
         * Nonsubtractive Dither", by Robert A. Wannamaker et al. */
        int32_t ditherValue = prng();
        int32_t dithering = oldDitherValue - ditherValue;
        oldDitherValue = ditherValue;

        int32_t nl = (l + ditherValue) >> 12;
        int32_t nr = (r + ditherValue) >> 12;
        l = clamp16(nl);
        r = clamp16(nr);
        *out++ = (r<<16) | (l & 0xFFFF);
    }
}

// no-op case
void AudioMixer::process__nop(state_t* state, AudioDSP& dsp)
{
    uint32_t e0 = state->enabledTracks;
    size_t bufSize = state->frameCount * sizeof(int16_t) * MAX_NUM_CHANNELS;
    while (e0) {
        // process by group of tracks with same output buffer to
        // avoid multiple memset() on same buffer
        uint32_t e1 = e0, e2 = e0;
        int i = 31 - __builtin_clz(e1);
        track_t& t1 = state->tracks[i];
        e2 &= ~(1<<i);
        while (e2) {
            i = 31 - __builtin_clz(e2);
            e2 &= ~(1<<i);
            track_t& t2 = state->tracks[i];
            if UNLIKELY(t2.mainBuffer != t1.mainBuffer) {
                e1 &= ~(1<<i);
            }
        }
        e0 &= ~(e1);

        memset(t1.mainBuffer, 0, bufSize);

        while (e1) {
            i = 31 - __builtin_clz(e1);
            e1 &= ~(1<<i);
            t1 = state->tracks[i];
            size_t outFrames = state->frameCount;
            while (outFrames) {
                t1.buffer.frameCount = outFrames;
                t1.bufferProvider->getNextBuffer(&t1.buffer);
                if (!t1.buffer.raw) break;
                outFrames -= t1.buffer.frameCount;
                t1.bufferProvider->releaseBuffer(&t1.buffer);
            }
        }
    }
}

// generic code without resampling
void AudioMixer::process__genericNoResampling(state_t* state, AudioDSP& dsp)
{
    int32_t outTemp[BLOCKSIZE * MAX_NUM_CHANNELS] __attribute__((aligned(32)));

    // acquire each track's buffer
    uint32_t enabledTracks = state->enabledTracks;
    uint32_t e0 = enabledTracks;
    while (e0) {
        const int i = 31 - __builtin_clz(e0);
        e0 &= ~(1<<i);
        track_t& t = state->tracks[i];
        t.buffer.frameCount = state->frameCount;
        t.bufferProvider->getNextBuffer(&t.buffer);
        t.frameCount = t.buffer.frameCount;
        t.in = t.buffer.raw;
        // t.in == NULL can happen if the track was flushed just after having
        // been enabled for mixing.
        if (t.in == NULL)
            enabledTracks &= ~(1<<i);
    }

    e0 = enabledTracks;
    while (e0) {
        // process by group of tracks with same output buffer to
        // optimize cache use
        uint32_t e1 = e0, e2 = e0;
        int j = 31 - __builtin_clz(e1);
        track_t& t1 = state->tracks[j];
        e2 &= ~(1<<j);
        while (e2) {
            j = 31 - __builtin_clz(e2);
            e2 &= ~(1<<j);
            track_t& t2 = state->tracks[j];
            if UNLIKELY(t2.mainBuffer != t1.mainBuffer) {
                e1 &= ~(1<<j);
            }
        }
        e0 &= ~(e1);
        // this assumes output 16 bits stereo, no resampling
        int32_t *out = t1.mainBuffer;
        size_t numFrames = 0;
        do {
            memset(outTemp, 0, sizeof(outTemp));
            e2 = e1;
            while (e2) {
                const int i = 31 - __builtin_clz(e2);
                e2 &= ~(1<<i);
                track_t& t = state->tracks[i];
                size_t outFrames = BLOCKSIZE;
                int32_t *aux = NULL;
                if UNLIKELY((t.needs & NEEDS_AUX__MASK) == NEEDS_AUX_ENABLED) {
                    aux = t.auxBuffer + numFrames;
                }
                while (outFrames) {
                    size_t inFrames = (t.frameCount > outFrames)?outFrames:t.frameCount;
                    if (inFrames) {
                        (t.hook)(&t, outTemp + (BLOCKSIZE-outFrames)*MAX_NUM_CHANNELS, inFrames, state->resampleTemp, aux, dsp);
                        t.frameCount -= inFrames;
                        outFrames -= inFrames;
                        if UNLIKELY(aux != NULL) {
                            aux += inFrames;
                        }
                    }
                    if (t.frameCount == 0 && outFrames) {
                        t.bufferProvider->releaseBuffer(&t.buffer);
                        t.buffer.frameCount = (state->frameCount - numFrames) - (BLOCKSIZE - outFrames);
                        t.bufferProvider->getNextBuffer(&t.buffer);
                        t.in = t.buffer.raw;
                        if (t.in == NULL) {
                            enabledTracks &= ~(1<<i);
                            e1 &= ~(1<<i);
                            break;
                        }
                        t.frameCount = t.buffer.frameCount;
                    }
                }
            }
            dsp.process(outTemp, BLOCKSIZE);
            ditherAndClamp(out, outTemp, BLOCKSIZE);
            out += BLOCKSIZE;
            numFrames += BLOCKSIZE;
        } while (numFrames < state->frameCount);
    }

    // release each track's buffer
    e0 = enabledTracks;
    while (e0) {
        const int i = 31 - __builtin_clz(e0);
        e0 &= ~(1<<i);
        track_t& t = state->tracks[i];
        t.bufferProvider->releaseBuffer(&t.buffer);
    }
}


  // generic code with resampling
void AudioMixer::process__genericResampling(state_t* state, AudioDSP& dsp)
{
    int32_t* const outTemp = state->outputTemp;
    const size_t size = sizeof(int32_t) * MAX_NUM_CHANNELS * state->frameCount;
    memset(outTemp, 0, size);

    size_t numFrames = state->frameCount;

    uint32_t e0 = state->enabledTracks;
    while (e0) {
        // process by group of tracks with same output buffer
        // to optimize cache use
        uint32_t e1 = e0, e2 = e0;
        int j = 31 - __builtin_clz(e1);
        track_t& t1 = state->tracks[j];
        e2 &= ~(1<<j);
        while (e2) {
            j = 31 - __builtin_clz(e2);
            e2 &= ~(1<<j);
            track_t& t2 = state->tracks[j];
            if UNLIKELY(t2.mainBuffer != t1.mainBuffer) {
                e1 &= ~(1<<j);
            }
        }
        e0 &= ~(e1);
        int32_t *out = t1.mainBuffer;
        while (e1) {
            const int i = 31 - __builtin_clz(e1);
            e1 &= ~(1<<i);
            track_t& t = state->tracks[i];
            int32_t *aux = NULL;
            if UNLIKELY((t.needs & NEEDS_AUX__MASK) == NEEDS_AUX_ENABLED) {
                aux = t.auxBuffer;
            }

            // this is a little goofy, on the resampling case we don't
            // acquire/release the buffers because it's done by
            // the resampler.
            if ((t.needs & NEEDS_RESAMPLE__MASK) == NEEDS_RESAMPLE_ENABLED) {
                (t.hook)(&t, outTemp, numFrames, state->resampleTemp, aux, dsp);
            } else {

                size_t outFrames = 0;

                while (outFrames < numFrames) {
                    t.buffer.frameCount = numFrames - outFrames;
                    t.bufferProvider->getNextBuffer(&t.buffer);
                    t.in = t.buffer.raw;
                    // t.in == NULL can happen if the track was flushed just after having
                    // been enabled for mixing.
                    if (t.in == NULL) break;

                    if UNLIKELY(aux != NULL) {
                        aux += outFrames;
                    }
                    (t.hook)(&t, outTemp + outFrames*MAX_NUM_CHANNELS, t.buffer.frameCount, state->resampleTemp, aux, dsp);
                    outFrames += t.buffer.frameCount;
                    t.bufferProvider->releaseBuffer(&t.buffer);
                }
            }
        }
        dsp.process(outTemp, numFrames);
        ditherAndClamp(out, outTemp, numFrames);
    }
}

// ----------------------------------------------------------------------------
}; // namespace android

