
package com.android.server.status;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextSwitcher;
import android.widget.TextView;

public class TickerView extends TextSwitcher
{
    Ticker mTicker;
    private int mTextColor = 0xff000000;

    public TickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mTicker.reflowText();
    }

    @Override
    public void setText(CharSequence text) {
        final TextView t = (TextView) getNextView();
        t.setTextColor(mTextColor);
        t.setText(text);
        showNext();
    }

    public void updateColors(int color) {
        mTextColor = color;
    }
}

