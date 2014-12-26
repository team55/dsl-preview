package org.jetbrains.kotlin.android.robowrapper.test;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class SomeView extends View {

    public SomeView(Context context) {
        super(context);
    }

    public SomeView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SomeView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
}
