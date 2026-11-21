package org.dhakaflexplus.player;

import android.content.Context;
import android.widget.VideoView;

/** Fill the combined SBS display. Only used when the user selects XREAL SBS output. */
public final class StretchVideoView extends VideoView {
    public StretchVideoView(Context c){super(c);}
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),MeasureSpec.getSize(heightMeasureSpec));
    }
}
