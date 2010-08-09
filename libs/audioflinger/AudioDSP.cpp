/* //device/include/server/AudioFlinger/AudioDSP.cpp
**
** Copyright 2010, Antti S. Lankila
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

#include <math.h>
#include <stdio.h>

#include "AudioDSP.h"

/* 
 * Filter definitions from the good old Audio EQ Cookbook
 * by Robert Bristow-Johnson. */
namespace android {

/* Keep this in sync with AudioMixer's FP decimal count. */
static const int fixedPointDecimals = 12;

const String8 AudioDSP::keyCompressionEnable = String8("dsp.compression.enable"); 
const String8 AudioDSP::keyCompressionRatio = String8("dsp.compression.ratio"); 

const String8 AudioDSP::keyToneEnable = String8("dsp.tone.enable"); 
const String8 AudioDSP::keyToneEq1 = String8("dsp.tone.eq1"); 
const String8 AudioDSP::keyToneEq2 = String8("dsp.tone.eq2"); 
const String8 AudioDSP::keyToneEq3 = String8("dsp.tone.eq3"); 
const String8 AudioDSP::keyToneEq4 = String8("dsp.tone.eq4"); 
const String8 AudioDSP::keyToneEq5 = String8("dsp.tone.eq5"); 

const String8 AudioDSP::keyHeadphoneEnable = String8("dsp.headphone.enable"); 

static int toFixedPoint(float x)
{
    return (int) (x * (1 << fixedPointDecimals) + 0.5f);
}

/* bah, rewrite me as a proper random number generator */
static uint32_t seed = 1;
static const uint32_t ditherBits = (1 << fixedPointDecimals) - 1;
static int32_t dither() {
    seed = (seed * 22695477) + 1;
    return int32_t((seed >> 16) & ditherBits);
}

Allpass::Allpass()
    : mK(0), mState(0), mIndex(0), mLength(0)
{
}

Allpass::~Allpass()
{
    if (mState != 0) {
        delete[] mState;
        mState = 0;
    }
}

void Allpass::setParameters(float samplingFrequency, float k, float time)
{
    mK = toFixedPoint(k);
    mLength = int32_t(time * samplingFrequency + 0.5f);
    if (mState != 0) {
        delete[] mState;
    }
    mState = new int32_t[mLength];
    for (int i = 0; i < mLength; i ++) {
        mState[i] = 0;
    }
}

int Allpass::process(int x0)
{
    int tmp = x0 - mK * (mState[mIndex] >> fixedPointDecimals);
    int y0 = mState[mIndex] + mK * (tmp >> fixedPointDecimals);
    mState[mIndex] = tmp;
    mIndex = (mIndex + 1) % mLength;
    return y0;
}

Biquad::Biquad()
   : mA1(0), mA2(0), mB0(0), mB1(0), mB2(0), mY1(0), mY2(0), mX1(0), mX2(0), mY0over(0)
{
}

void Biquad::setCoefficients(float a0, float a1, float a2, float b0, float b1, float b2)
{
    mA1 = toFixedPoint(a1/a0);
    mA2 = toFixedPoint(a2/a0);
    mB0 = toFixedPoint(b0/a0);
    mB1 = toFixedPoint(b1/a0);
    mB2 = toFixedPoint(b2/a0);
}

void Biquad::setRC(float center_frequency, float sampling_frequency)
{
    float DT_div_RC = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float b0 = DT_div_RC / (1 + DT_div_RC);
    float a1 = -1 + b0;

    setCoefficients(1, a1, 0, b0, 0, 0);
}

void Biquad::setPeakingEqualizer(float center_frequency, float sampling_frequency, float db_gain, float bandwidth)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, db_gain/40);

    float alpha = sinf(w0)/2 * sinhf( logf(2)/2 * bandwidth * w0/sinf(w0) );
    float b0 =   1 + alpha*A;
    float b1 =  -2*cosf(w0);
    float b2 =   1 - alpha*A;
    float a0 =   1 + alpha/A;
    float a1 =  -2*cosf(w0);
    float a2 =   1 - alpha/A;
    
    setCoefficients(a0, a1, a2, b0, b1, b2);
}

void Biquad::setHighShelf(float center_frequency, float sampling_frequency, float db_gain, float slope)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, db_gain/40);
    float alpha = sinf(w0)/2 * sqrtf( (A + 1/A)*(1/slope - 1) + 2 );

    float b0 =    A*( (A+1) + (A-1)*cosf(w0) + 2*sqrtf(A)*alpha );
    float b1 = -2*A*( (A-1) + (A+1)*cosf(w0)                   );
    float b2 =    A*( (A+1) + (A-1)*cosf(w0) - 2*sqrtf(A)*alpha );
    float a0 =        (A+1) - (A-1)*cosf(w0) + 2*sqrtf(A)*alpha  ;
    float a1 =    2*( (A-1) - (A+1)*cosf(w0)                   );
    float a2 =        (A+1) - (A-1)*cosf(w0) - 2*sqrtf(A)*alpha  ;

    setCoefficients(a0, a1, a2, b0, b1, b2);
}

void Biquad::setLowShelf(float center_frequency, float sampling_frequency, float db_gain, float slope)
{
    float w0 = 2 * (float) M_PI * center_frequency / sampling_frequency;
    float A = powf(10, db_gain/40);
    float alpha = sinf(w0)/2 * sqrtf( (A + 1/A)*(1/slope - 1) + 2 );

    float b0 =    A*( (A+1) - (A-1)*cosf(w0) + 2*sqrtf(A)*alpha );
    float b1 =  2*A*( (A-1) - (A+1)*cosf(w0)                   );
    float b2 =    A*( (A+1) - (A-1)*cosf(w0) - 2*sqrtf(A)*alpha );
    float a0 =        (A+1) + (A-1)*cosf(w0) + 2*sqrtf(A)*alpha  ;
    float a1 =   -2*( (A-1) + (A+1)*cosf(w0)                   );
    float a2 =        (A+1) + (A-1)*cosf(w0) - 2*sqrtf(A)*alpha  ;

    setCoefficients(a0, a1, a2, b0, b1, b2);
}

/* returns output scaled by fixedPoint factor */
int Biquad::process(int x0)
{
    int y0 = mB0 * x0 + mB1 * mX1 + mB2 * mX2 - mY1 * mA1 - mY2 * mA2;

    mY2 = mY1;
    mY0over += y0 & ((1 << fixedPointDecimals) - 1);
    mY1 = (y0 >> fixedPointDecimals) + (mY0over >> fixedPointDecimals);
    mY0over &= (1 << fixedPointDecimals) - 1;

    mX2 = mX1;
    mX1 = x0;

    return y0;
}


Effect::Effect()
    : mSamplingFrequency(44100)
{
}


Effect::~Effect() {
}

void Effect::configure(const float samplingFrequency) {
    mSamplingFrequency = samplingFrequency;
}


EffectCompression::EffectCompression()
    : mOldCorrectionDb(0), mCompressionRatio(2)
{
}

EffectCompression::~EffectCompression()
{
}

void EffectCompression::setRatio(float compressionRatio) {
    mCompressionRatio = compressionRatio;
}

void EffectCompression::process(int *inout, int frames)
{
}

int32_t EffectCompression::estimateLevel(const int16_t *audioData, int samples)
{
    int32_t power = 0;
    int32_t powerAccum = 0;
    for (int i = 0; i < samples; i ++) {
        int32_t tmp = *audioData ++;
        tmp = tmp * tmp;

        powerAccum += tmp & 0xffff;
        power += tmp >> 16;
        power += powerAccum >> 16;
        powerAccum &= 0xffff;
    }

    float signalPower = sqrtf((65536.0f*power + powerAccum) / samples);
    float signalPowerDb = -96.0f;
    if (signalPower != 0) {
        signalPowerDb = logf(signalPower / 32768.0f) / logf(10) * 10;
    }

    /* now we have an estimate of the signal power in range from
     * -96 dB to 0 dB. Now we estimate what level we want. */
    float desiredLevelDb = signalPowerDb / mCompressionRatio;

    /* turn back to multiplier */
    float correctionDb = desiredLevelDb - signalPowerDb;
    /* filter envelope for stability. This is currently a crude approximation
     * to get something semi-reasonable going. */
    if (correctionDb > mOldCorrectionDb + 0.2f) {
        correctionDb = mOldCorrectionDb + 0.2f;
    }
    if (correctionDb < mOldCorrectionDb - 0.2f) {
        correctionDb = mOldCorrectionDb - 0.2f;
    }
    mOldCorrectionDb = correctionDb;

    int32_t desiredMultiplier = int32_t(65536 * powf(10, correctionDb / 20));

    return desiredMultiplier; 
}


EffectTone::EffectTone()
{
    for (int i = 0; i < 5; i ++) {
        mBand[i] = 0;
    }
    for (int i = 0; i < 5; i ++) {
        setBand(i, 0);
    }
}

EffectTone::~EffectTone() {
    for (int i = 0; i < 4; i ++) {
        delete &mFilterL[i];
        delete &mFilterR[i];
    }
}

void EffectTone::configure(const float samplingFrequency) {
    Effect::configure(samplingFrequency);
    refreshBands();
}
 
void EffectTone::setBand(int band, float dB)
{
    mBand[band] = dB;
    refreshBands();
}

void EffectTone::refreshBands() {
    mGain = toFixedPoint(powf(10, mBand[0] / 20));

    for (int band = 0; band < 3; band ++) {
        float dB = mBand[band + 1] - mBand[0];
        float centerFrequency = 250.0f * powf(4, band);

        mFilterL[band].setPeakingEqualizer(centerFrequency, mSamplingFrequency, dB, 3.0);
        mFilterR[band].setPeakingEqualizer(centerFrequency, mSamplingFrequency, dB, 3.0);
    }

    {
        int band = 3;

        float dB = mBand[band + 1] - mBand[0];
        float centerFrequency = 250.0f * powf(4, band);

        mFilterL[band].setHighShelf(centerFrequency * 0.5f, mSamplingFrequency, dB, 1.0);
        mFilterR[band].setHighShelf(centerFrequency * 0.5f, mSamplingFrequency, dB, 1.0);
    }
}

void EffectTone::process(int *inout, int frames)
{
    for (int i = 0; i < frames; i ++) {
        int tmpL = inout[0] >> fixedPointDecimals;
        int tmpR = inout[1] >> fixedPointDecimals;
        /* 16 bits */
       
        /* bass control is really a global gain compensated by other
         * controls */
        tmpL = tmpL * mGain;
        tmpR = tmpR * mGain;
        /* 28 bits */

        /* evaluate the other filters */
        for (int j = 0; j < 4; j ++) {
            tmpL = mFilterL[j].process(tmpL >> 12);
            tmpR = mFilterR[j].process(tmpR >> 12);
        }
        /* 28 bits */

        inout[0] = tmpL;
        inout[1] = tmpR;
        inout += 2;
    }
}

EffectHeadphone::~EffectHeadphone() {
    for (int i = 0; i < 4; i ++) {
        delete &mAllpassL[i];
        delete &mAllpassR[i];
    }
    delete &mLowpassL;
    delete &mLowpassR;
}

void EffectHeadphone::configure(const float samplingFrequency) {
    Effect::configure(samplingFrequency);

    mAllpassL[0].setParameters(mSamplingFrequency, 0.0, 0.00033);
    mAllpassR[0].setParameters(mSamplingFrequency, 0.0, 0.00033);
    mAllpassL[1].setParameters(mSamplingFrequency, 0.4, 0.00031);
    mAllpassR[1].setParameters(mSamplingFrequency, 0.4, 0.00031);
    mAllpassL[2].setParameters(mSamplingFrequency, 0.4, 0.00021);
    mAllpassR[2].setParameters(mSamplingFrequency, 0.4, 0.00021);
    mAllpassL[3].setParameters(mSamplingFrequency, 0.4, 0.00011);
    mAllpassR[3].setParameters(mSamplingFrequency, 0.4, 0.00011);
    mLowpassL.setRC(4000.0, mSamplingFrequency);
    mLowpassR.setRC(4000.0, mSamplingFrequency);
}

void EffectHeadphone::process(int* inout, int frames)
{
    for (int i = 0; i < frames; i ++) {
        int dL = inout[0];
        int dR = inout[1];
        int pL = dL;
        int pR = dR;
        /* 28 bits */

        for (int j = 0; j < 4; j ++) {
            pL = mAllpassL[j].process(pL);
            pR = mAllpassR[j].process(pR);
        }

        pL = mLowpassL.process(pL >> fixedPointDecimals);
        pR = mLowpassR.process(pR >> fixedPointDecimals);
        /* 28 bits */
        
        inout[0] = dL + pR;
        inout[1] = dR + pL;
        inout += 2;
    }
}


AudioDSP::AudioDSP()
    : mCompressionEnable(false), mToneEnable(false),  mHeadphoneEnable(false)
{
}

AudioDSP::~AudioDSP()
{
    delete &mCompression;
    delete &mTone;
    delete &mHeadphone;
}

void AudioDSP::configure(const float samplingRate)
{
    mCompression.configure(samplingRate);
    mTone.configure(samplingRate);
    mHeadphone.configure(samplingRate);
}

void AudioDSP::setParameters(const String8& keyValuePairs)
{
    int intValue;
    float floatValue;
    status_t result;
    AudioParameter param = AudioParameter(keyValuePairs);

    result = param.getInt(keyCompressionEnable, intValue);
    if (result == NO_ERROR) {
       mCompressionEnable = intValue != 0;
    }
    result = param.getFloat(keyCompressionRatio, floatValue);
    if (result == NO_ERROR) {
        mCompression.setRatio(floatValue);
    }

    result = param.getInt(keyToneEnable, intValue);
    if (result == NO_ERROR) {
        mToneEnable = intValue != 0;
    }
    /* FIXME: figure out a for loop for this some day */
    result = param.getFloat(keyToneEq1, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(0, floatValue);
    }
    result = param.getFloat(keyToneEq2, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(1, floatValue);
    }
    result = param.getFloat(keyToneEq3, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(2, floatValue);
    }
    result = param.getFloat(keyToneEq4, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(3, floatValue);
    }
    result = param.getFloat(keyToneEq5, floatValue);
    if (result == NO_ERROR) {
        mTone.setBand(4, floatValue);
    }
    
    result = param.getInt(keyHeadphoneEnable, intValue);
    if (result == NO_ERROR) {
        mHeadphoneEnable = intValue != 0;
    }
}

int32_t AudioDSP::estimateLevel(const int16_t *input, int samples) {
    if (! mCompressionEnable) {
        return 65536;
    } else {
        return mCompression.estimateLevel(input, samples);
    }
}

/* input is 28-bit interleaved stereo in integer format */
void AudioDSP::process(int32_t* audioData, int frames)
{
    if (mToneEnable) {
        mTone.process(audioData, frames);
    }
    if (mHeadphoneEnable) {
        mHeadphone.process(audioData, frames);
    }

    /* Apply dither to output. This is the so-called
     * high-pass triangular probability density function, discussed in
     * "A Theory of Nonsubtractive Dither", by Robert A. Wannamaker,
     * Stanley P. Lipshitz, John Vanderkooy, J. Nelson Fright. */
    for (int i = 0; i < frames; i ++) {
        int32_t ditherValue = dither();
        int32_t dithering = mDitherValue - ditherValue;
        mDitherValue = ditherValue;
        audioData[0] += ditherValue;
        audioData[1] += ditherValue;
        audioData += 2;
    }
}

}
