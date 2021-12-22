/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import android.media.*;
import android.view.Surface;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;

import java.awt.Dimension;
import java.io.IOException;

import javax.media.*;

import timber.log.Timber;

/**
 * Abstract codec class uses android <tt>MediaCodec</tt> for video decoding/encoding.
 * Eventually <tt>AndroidDecoder</tt> and <tt>AndroidEncoder</tt> can be merged later.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class AndroidCodec extends AbstractCodec2
{
    /**
     * Copied from <tt>MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface</tt>
     */
    private final static int COLOR_FormatSurface = 0x7F000789;

    /**
     * Indicates that this instance is used for encoding(and not for decoding).
     */
    private final boolean isEncoder;

    /**
     * <tt>MediaCodec</tt> used by this instance.
     */
    private MediaCodec codec;

    /**
     * Input <tt>MediaCodec</tt> buffer.
     */
    java.nio.ByteBuffer codecInputBuf;

    /**
     * Output <tt>MediaCodec</tt> buffer.
     */
    java.nio.ByteBuffer codecOutputBuf;

    /**
     * <tt>BufferInfo</tt> object that stores codec buffer information.
     */
    MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    /**
     * Creates a new instance of <tt>AndroidCodec</tt>.
     *
     * @param name the <tt>PlugIn</tt> name of the new instance
     * @param formatClass the <tt>Class</tt> of input and output <tt>Format</tt>s supported by the new instance
     * @param supportedOutputFormats the list of <tt>Format</tt>s supported by the new instance as output.
     */
    protected AndroidCodec(String name, Class<? extends Format> formatClass,
            Format[] supportedOutputFormats, boolean isEncoder)
    {
        super(name, formatClass, supportedOutputFormats);
        this.isEncoder = isEncoder;
    }

    /**
     * Class should return <tt>true</tt> if surface will be used.
     *
     * @return <tt>true</tt> if surface will be used.
     */
    protected abstract boolean useSurface();

    /**
     * Returns <tt>Surface</tt> used by this instance for encoding or decoding.
     *
     * @return <tt>Surface</tt> used by this instance for encoding or decoding.
     */
    protected abstract Surface getSurface();

    /**
     * Template method used to configure <tt>MediaCodec</tt> instance. Called before starting the codec.
     *
     * @param codec <tt>MediaCodec</tt> instance to be configured.
     * @param codecType string codec media type.
     * @throws ResourceUnavailableException Resource Unavailable Exception if not supported
     */
    protected abstract void configureMediaCodec(MediaCodec codec, String codecType)
            throws ResourceUnavailableException;

    /**
     * Selects <tt>MediaFormat</tt> color format used.
     *
     * @return used <tt>MediaFormat</tt> color format.
     */
    protected int getColorFormat()
    {
        return useSurface() ? COLOR_FormatSurface : MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        if (codec != null) {
            try {
                // Throws IllegalStateException – if in the Released state.
                codec.stop();
                codec.release();
            } catch (IllegalStateException e) {
                Timber.w("Codec stop exception: %s", e.getMessage());
            } finally {
                codec = null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException
    {
        String codecType;
        String encoding = isEncoder ? outputFormat.getEncoding() : inputFormat.getEncoding();

        switch (encoding) {
            case Constants.VP9:
                codecType = CodecInfo.MEDIA_CODEC_TYPE_VP9;
                break;
            case Constants.VP8:
                codecType = CodecInfo.MEDIA_CODEC_TYPE_VP8;
                break;
            case Constants.H264:
                codecType = CodecInfo.MEDIA_CODEC_TYPE_H264;
                break;
            default:
                throw new RuntimeException("Unsupported encoding: " + encoding);
        }

        CodecInfo codecInfo = CodecInfo.getCodecForType(codecType, isEncoder);
        if (codecInfo == null) {
            throw new ResourceUnavailableException("No " + getStrName() + " found for type: " + codecType);
        }

        try {
            codec = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            Timber.e("Exception in create codec name: %s", e.getMessage());
        }
        // Timber.d("starting %s %s for: %s; useSurface: %s", codecType, getStrName(), codecInfo.getName(), useSurface());
        configureMediaCodec(codec, codecType);
        codec.start();
    }

    private String getStrName()
    {
        return isEncoder ? "encoder" : "decoder";
    }

    /**
     * {@inheritDoc}
     *
     * Exception: IllegalStateException thrown by codec.dequeueOutputBuffer or codec.dequeueInputBuffer
     * Any RuntimeException will close remote view container.
     */
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        try {
            return doProcessImpl(inputBuffer, outputBuffer);
        } catch (Exception e) {
            Timber.e(e, "Do process for codec: %s; Exception: %s", codec.getName(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Process the video stream:
     * We will first process the output data from the mediaCodec; then we will feed input into the decoder.
     *
     * @param inputBuffer input buffer
     * @param outputBuffer output buffer
     * @return process status
     */
    private int doProcessImpl(Buffer inputBuffer, Buffer outputBuffer)
    {
        Format outputFormat = this.outputFormat;
        int processed = INPUT_BUFFER_NOT_CONSUMED | OUTPUT_BUFFER_NOT_FILLED;

        // Process the output data from the codec
        // Returns the index of an output buffer that has been successfully decoded or one of the INFO_* constants.
        int outputBufferIdx = codec.dequeueOutputBuffer(mBufferInfo, 0);
        if (outputBufferIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat outFormat = codec.getOutputFormat();
            if (!isEncoder) {
                int pixelFormat = outFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                int requestedFormat = getColorFormat();
                if (!useSurface() && pixelFormat != requestedFormat) {
                    throw new RuntimeException("MediaCodec returned different color format: "
                            + pixelFormat + "(requested " + requestedFormat
                            + ", try using the Surface");
                }
            }
            Timber.d("Codec output format changed (encoder: %s): %s", isEncoder, outFormat);
            // Video size should be known at this point
            Dimension videoSize = new Dimension(outFormat.getInteger(MediaFormat.KEY_WIDTH), outFormat.getInteger(MediaFormat.KEY_HEIGHT));
            onSizeChanged(videoSize);
        }
        else if (outputBufferIdx >= 0) {
            // Timber.d("Reading output: %s:%s flag: %s", mBufferInfo.offset, mBufferInfo.size, mBufferInfo.flags);
            int outputLength = 0;
            codecOutputBuf = null;
            try {
                if (!isEncoder && useSurface()) {
                    processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                    outputBuffer.setFormat(outputFormat);
                    // Timber.d("Codec output format: %s", outputFormat);
                }
                else if ((outputLength = mBufferInfo.size) > 0) {
                    codecOutputBuf = codec.getOutputBuffer(outputBufferIdx);
                    codecOutputBuf.position(mBufferInfo.offset);
                    codecOutputBuf.limit(mBufferInfo.offset + mBufferInfo.size);

                    byte[] out = AbstractCodec2.validateByteArraySize(outputBuffer, mBufferInfo.size, false);
                    codecOutputBuf.get(out, 0, mBufferInfo.size);

                    outputBuffer.setFormat(outputFormat);
                    outputBuffer.setLength(outputLength);
                    outputBuffer.setOffset(0);

                    processed &= ~OUTPUT_BUFFER_NOT_FILLED;
                }
            } finally {
                if (codecOutputBuf != null)
                    codecOutputBuf.clear();
                /*
                 * releaseOutputBuffer: the output buffer data will be forwarded to SurfaceView for render if true.
                 * see https://developer.android.com/reference/android/media/MediaCodec
                 */
                codec.releaseOutputBuffer(outputBufferIdx, !isEncoder && useSurface());
            }
            /*
             * We will first exhaust the output of the mediaCodec, and then we will feed input into it.
             */
            if (outputLength > 0)
                return processed;
        }
        else if (outputBufferIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
            Timber.w("Codec output reports: %s", outputBufferIdx);
        }

        // Feed more data to the decoder.
        if (isEncoder && useSurface()) {
            inputBuffer.setData(getSurface());
            processed &= ~INPUT_BUFFER_NOT_CONSUMED;
        }
        else {
            int inputBufferIdx = codec.dequeueInputBuffer(0);
            if (inputBufferIdx >= 0) {
                byte[] buf_data = (byte[]) inputBuffer.getData();
                int buf_offset = inputBuffer.getOffset();
                int buf_size = inputBuffer.getLength();

                codecInputBuf = codec.getInputBuffer(inputBufferIdx);
                if (codecInputBuf.capacity() < buf_size) {
                    throw new RuntimeException("Input buffer too small: " + codecInputBuf.capacity() + " < " + buf_size);
                }

                codecInputBuf.clear();
                codecInputBuf.put(buf_data, buf_offset, buf_size);
                codec.queueInputBuffer(inputBufferIdx, 0, buf_size, inputBuffer.getTimeStamp(), 0);

                Timber.d("Fed input with %s bytes of data; Offset: %s.", buf_size, buf_offset);
                processed &= ~INPUT_BUFFER_NOT_CONSUMED;
            }
            else if (inputBufferIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
                Timber.w("Codec input reports: %s", inputBufferIdx);
            }
        }
        return processed;
    }

    /**
     * Method fired when <tt>MediaCodec</tt> detects video size.
     *
     * @param dimension video dimension.
     * @see AndroidDecoder#onSizeChanged(Dimension)
     */
    protected void onSizeChanged(Dimension dimension)
    {
    }
}
