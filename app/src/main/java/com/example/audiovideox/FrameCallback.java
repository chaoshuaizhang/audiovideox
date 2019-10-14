package com.example.audiovideox;

public interface FrameCallback {
    void preRender(long presentationTimeUsec);

    /**
     * Called immediately after the frame render call returns.  The frame may not have
     * actually been rendered yet.
     * TODO: is this actually useful?
     */
    void postRender();

    /**
     * Called after the last frame of a looped movie has been rendered.  This allows the
     * callback to adjust its expectations of the next presentation time stamp.
     */
    void loopReset();
}
