/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.glview.graphics.drawable;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;

import com.glview.content.GLContext;
import com.glview.graphics.Rect;
import com.glview.hwui.GLCanvas;

/**
 * A Drawable that insets another Drawable by a specified distance.
 * This is used when a View needs a background that is smaller than
 * the View's actual bounds.
 *
 * <p>It can be defined in an XML file with the <code>&lt;inset></code> element. For more
 * information, see the guide to <a
 * href="{@docRoot}guide/topics/resources/drawable-resource.html">Drawable Resources</a>.</p>
 *
 * @attr ref android.R.styleable#InsetDrawable_visible
 * @attr ref android.R.styleable#InsetDrawable_drawable
 * @attr ref android.R.styleable#InsetDrawable_insetLeft
 * @attr ref android.R.styleable#InsetDrawable_insetRight
 * @attr ref android.R.styleable#InsetDrawable_insetTop
 * @attr ref android.R.styleable#InsetDrawable_insetBottom
 */
public class InsetDrawable extends Drawable implements Drawable.Callback {
    private final Rect mTmpRect = new Rect();

    private InsetState mInsetState;
    private boolean mMutated;

    /*package*/ InsetDrawable() {
        this(null, null);
    }

    public InsetDrawable(Drawable drawable, int inset) {
        this(drawable, inset, inset, inset, inset);
    }

    public InsetDrawable(Drawable drawable, int insetLeft, int insetTop,
                         int insetRight, int insetBottom) {
        this(null, null);

        mInsetState.mDrawable = drawable;
        mInsetState.mInsetLeft = insetLeft;
        mInsetState.mInsetTop = insetTop;
        mInsetState.mInsetRight = insetRight;
        mInsetState.mInsetBottom = insetBottom;

        if (drawable != null) {
            drawable.setCallback(this);
        }
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, attrs, com.glview.R.styleable.InsetDrawable);
        super.inflateWithAttributes(r, parser, a, com.glview.R.styleable.InsetDrawable_visible);

        mInsetState.mDrawable = null;
        updateStateFromTypedArray(a);

        // Load inner XML elements.
        if (mInsetState.mDrawable == null) {
            int type;
            while ((type=parser.next()) == XmlPullParser.TEXT) {
            }
            if (type != XmlPullParser.START_TAG) {
                throw new XmlPullParserException(
                        parser.getPositionDescription()
                                + ": <inset> tag requires a 'drawable' attribute or "
                                + "child tag defining a drawable");
            }
            final Drawable dr = Drawable.createFromXmlInner(r, parser, attrs);
            mInsetState.mDrawable = dr;
            dr.setCallback(this);
        }

        verifyRequiredAttributes(a);
        a.recycle();
    }

    private void verifyRequiredAttributes(TypedArray a) throws XmlPullParserException {
        // If we're not waiting on a theme, verify required attributes.
        if (mInsetState.mDrawable == null) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    ": <inset> tag requires a 'drawable' attribute or "
                    + "child tag defining a drawable");
        }
    }

    private void updateStateFromTypedArray(TypedArray a) throws XmlPullParserException {
        final InsetState state = mInsetState;

        // Account for any configuration changes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        	state.mChangingConfigurations |= a.getChangingConfigurations();	
        }

        final int N = a.getIndexCount();
        for (int i = 0; i < N; i++) {
			final int attr = a.getIndex(i);
			if (attr == com.glview.R.styleable.InsetDrawable_drawable) {
				final Drawable dr = GLContext.get().getResources()
						.getDrawable(a.getResourceId(attr, 0));
				if (dr != null) {
					state.mDrawable = dr;
					dr.setCallback(this);
				}
			} else if (attr == com.glview.R.styleable.InsetDrawable_inset) {
				final int inset = a.getDimensionPixelOffset(attr,
						Integer.MIN_VALUE);
				if (inset != Integer.MIN_VALUE) {
					state.mInsetLeft = inset;
					state.mInsetTop = inset;
					state.mInsetRight = inset;
					state.mInsetBottom = inset;
				}
			} else if (attr == com.glview.R.styleable.InsetDrawable_insetLeft) {
				state.mInsetLeft = a.getDimensionPixelOffset(attr,
						state.mInsetLeft);
			} else if (attr == com.glview.R.styleable.InsetDrawable_insetTop) {
				state.mInsetTop = a.getDimensionPixelOffset(attr,
						state.mInsetTop);
			} else if (attr == com.glview.R.styleable.InsetDrawable_insetRight) {
				state.mInsetRight = a.getDimensionPixelOffset(attr,
						state.mInsetRight);
			} else if (attr == com.glview.R.styleable.InsetDrawable_insetBottom) {
				state.mInsetBottom = a.getDimensionPixelOffset(attr,
						state.mInsetBottom);
			}
        }
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.invalidateDrawable(this);
        }
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.scheduleDrawable(this, what, when);
        }
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.unscheduleDrawable(this, what);
        }
    }

    @Override
    public void draw(GLCanvas canvas) {
        mInsetState.mDrawable.draw(canvas);
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations()
                | mInsetState.mChangingConfigurations
                | mInsetState.mDrawable.getChangingConfigurations();
    }

    @Override
    public boolean getPadding(Rect padding) {
        boolean pad = mInsetState.mDrawable.getPadding(padding);

        padding.left += mInsetState.mInsetLeft;
        padding.right += mInsetState.mInsetRight;
        padding.top += mInsetState.mInsetTop;
        padding.bottom += mInsetState.mInsetBottom;

        return pad || (mInsetState.mInsetLeft | mInsetState.mInsetRight |
                mInsetState.mInsetTop | mInsetState.mInsetBottom) != 0;
    }

    /** @hide */
    @Override
    public Insets getOpticalInsets() {
        final Insets contentInsets = super.getOpticalInsets();
        return Insets.of(contentInsets.left + mInsetState.mInsetLeft,
                contentInsets.top + mInsetState.mInsetTop,
                contentInsets.right + mInsetState.mInsetRight,
                contentInsets.bottom + mInsetState.mInsetBottom);
    }

    @Override
    public void setHotspot(float x, float y) {
        mInsetState.mDrawable.setHotspot(x, y);
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        mInsetState.mDrawable.setHotspotBounds(left, top, right, bottom);
    }

    /** @hide */
    @Override
    public void getHotspotBounds(Rect outRect) {
        mInsetState.mDrawable.getHotspotBounds(outRect);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mInsetState.mDrawable.setVisible(visible, restart);
        return super.setVisible(visible, restart);
    }

    @Override
    public void setAlpha(int alpha) {
        mInsetState.mDrawable.setAlpha(alpha);
    }

    @Override
    public int getAlpha() {
        return mInsetState.mDrawable.getAlpha();
    }

    /** {@hide} */
    @Override
    public void setLayoutDirection(int layoutDirection) {
        mInsetState.mDrawable.setLayoutDirection(layoutDirection);
    }

    @Override
    public int getOpacity() {
        return mInsetState.mDrawable.getOpacity();
    }

    @Override
    public boolean isStateful() {
        return mInsetState.mDrawable.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean changed = mInsetState.mDrawable.setState(state);
        onBoundsChange(getBounds());
        return changed;
    }

    @Override
    protected boolean onLevelChange(int level) {
        return mInsetState.mDrawable.setLevel(level);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        final Rect r = mTmpRect;
        r.set(bounds);

        r.left += mInsetState.mInsetLeft;
        r.top += mInsetState.mInsetTop;
        r.right -= mInsetState.mInsetRight;
        r.bottom -= mInsetState.mInsetBottom;

        mInsetState.mDrawable.setBounds(r.left, r.top, r.right, r.bottom);
    }

    @Override
    public int getIntrinsicWidth() {
        return mInsetState.mDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mInsetState.mDrawable.getIntrinsicHeight();
    }

    @Override
    public ConstantState getConstantState() {
        if (mInsetState.canConstantState()) {
            mInsetState.mChangingConfigurations = getChangingConfigurations();
            return mInsetState;
        }
        return null;
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mInsetState.mDrawable.mutate();
            mMutated = true;
        }
        return this;
    }

    /**
     * Returns the drawable wrapped by this InsetDrawable. May be null.
     */
    public Drawable getDrawable() {
        return mInsetState.mDrawable;
    }

    final static class InsetState extends ConstantState {
        int mChangingConfigurations;

        Drawable mDrawable;

        int mInsetLeft;
        int mInsetTop;
        int mInsetRight;
        int mInsetBottom;

        boolean mCheckedConstantState;
        boolean mCanConstantState;

        InsetState(InsetState orig, InsetDrawable owner, Resources res) {
            if (orig != null) {
                mChangingConfigurations = orig.mChangingConfigurations;
                if (res != null) {
                    mDrawable = orig.mDrawable.getConstantState().newDrawable(res);
                } else {
                    mDrawable = orig.mDrawable.getConstantState().newDrawable();
                }
                mDrawable.setCallback(owner);
                mDrawable.setLayoutDirection(orig.mDrawable.getLayoutDirection());
                mDrawable.setBounds(orig.mDrawable.getBounds());
                mDrawable.setLevel(orig.mDrawable.getLevel());
                mInsetLeft = orig.mInsetLeft;
                mInsetTop = orig.mInsetTop;
                mInsetRight = orig.mInsetRight;
                mInsetBottom = orig.mInsetBottom;
                mCheckedConstantState = mCanConstantState = true;
            }
        }

        @Override
        public Drawable newDrawable() {
            return new InsetDrawable(this, null);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new InsetDrawable(this, res);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }

        boolean canConstantState() {
            if (!mCheckedConstantState) {
                mCanConstantState = mDrawable.getConstantState() != null;
                mCheckedConstantState = true;
            }

            return mCanConstantState;
        }
    }

    private InsetDrawable(InsetState state, Resources res) {
        mInsetState = new InsetState(state, this, res);
    }
}

