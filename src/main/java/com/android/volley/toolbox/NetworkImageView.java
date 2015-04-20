/**
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.volley.toolbox;

import net.comikon.reader.utils.cache.RecyclingBitmapDrawable;
import net.comikon.reader.utils.cache.Utils;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;

/**
 * Handles fetching an image from a URL as well as the life-cycle of the
 * associated request.
 */
public class NetworkImageView extends ImageView {
    /** The URL of the network image to load */
    private String mUrl;

    /**
     * Resource ID of the image to be used as a placeholder until the network image is loaded.
     */
    private int mDefaultImageId;

    /**
     * Resource ID of the image to be used if the network response fails.
     */
    private int mErrorImageId;

    /** Local copy of the ImageLoader. */
    private ImageLoader mImageLoader;

    /** Current ImageContainer. (either in-flight or finished) */
    private ImageContainer mImageContainer;

	/**
	 * Target bitmap width
	 */
	private int mMaxWidth;

	/**
	 * Targe bitmap height;
	 */
	private int mMaxHeight;

	/**
	 * �Ƿ�ü�
	 */
	private boolean isClip = false;

	/**
	 * ����Tag�������ڸ���ҳ���CacheKey
	 */
	private String mCacheTag;

    public NetworkImageView(Context context) {
        this(context, null);
    }

    public NetworkImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets URL of the image that should be loaded into this view. Note that calling this will
     * immediately either set the cached image (if available) or the default image specified by
     * {@link NetworkImageView#setDefaultImageResId(int)} on the view.
     *
     * NOTE: If applicable, {@link NetworkImageView#setDefaultImageResId(int)} and
     * {@link NetworkImageView#setErrorImageResId(int)} should be called prior to calling
     * this function.
     *
     * @param url The URL that should be loaded into this ImageView.
     * @param imageLoader ImageLoader that will be used to make the request.
     */
    public void setImageUrl(String url, ImageLoader imageLoader, String cacheTag) {
        mUrl = url;
        mImageLoader = imageLoader;
		mMaxWidth = 0;
		mMaxHeight = 0;
		mCacheTag = cacheTag;
        // The URL has potentially changed. See if we need to load it.
        loadImageIfNecessary(false);
	}

	public void setImageUrl(String url, ImageLoader imageLoader, int targetWidth, int targetHeight, String cacheTag) {
		setImageUrl(url, imageLoader, targetWidth, targetHeight, false, cacheTag);
	}

	public void setImageUrl(String url, ImageLoader imageLoader, float targetWidth, float targetHeight, boolean clip, String cacheTag) {
		// TODO Auto-generated method stub
		mUrl = url;
		mImageLoader = imageLoader;
		mMaxWidth = (int) targetWidth;
		mMaxHeight = (int) targetHeight;
		mCacheTag = cacheTag;
		// The URL has potentially changed. See if we need to load it.
		loadImageIfNecessary(false);
		isClip = clip;
    }

    /**
     * Sets the default image resource ID to be used for this view until the attempt to load it
     * completes.
     */
    public void setDefaultImageResId(int defaultImage) {
        mDefaultImageId = defaultImage;
    }

    /**
     * Sets the error image resource ID to be used for this view in the event that the image
     * requested fails to load.
     */
    public void setErrorImageResId(int errorImage) {
        mErrorImageId = errorImage;
    }

    /**
     * Loads the image for the view if it isn't already loaded.
     * @param isInLayoutPass True if this was invoked from a layout pass, false otherwise.
     */
    void loadImageIfNecessary(final boolean isInLayoutPass) {
        int width = getWidth();
        int height = getHeight();
        ScaleType scaleType = getScaleType();

        boolean wrapWidth = false, wrapHeight = false;
        if (getLayoutParams() != null) {
            wrapWidth = getLayoutParams().width == LayoutParams.WRAP_CONTENT;
            wrapHeight = getLayoutParams().height == LayoutParams.WRAP_CONTENT;
        }

        // if the view's bounds aren't known yet, and this is not a wrap-content/wrap-content
        // view, hold off on loading the image.
        boolean isFullyWrapContent = wrapWidth && wrapHeight;
        if (width == 0 && height == 0 && !isFullyWrapContent) {
            return;
        }

        // if the URL to be loaded in this view is empty, cancel any old requests and clear the
        // currently loaded image.
        if (TextUtils.isEmpty(mUrl)) {
            if (mImageContainer != null) {
                mImageContainer.cancelRequest();
                mImageContainer = null;
            }
            setDefaultImageOrNull();
            return;
        }

        // if there was an old request in this view, check if it needs to be canceled.
        if (mImageContainer != null && mImageContainer.getRequestUrl() != null) {
            if (mImageContainer.getRequestUrl().equals(mUrl)) {
                // if the request is from the same URL, return.
                return;
            } else {
                // if there is a pre-existing request, cancel it if it's fetching a different URL.
                mImageContainer.cancelRequest();
                setDefaultImageOrNull();
            }
        }

        // Calculate the max image width / height to use while ignoring WRAP_CONTENT dimens.
//        int maxWidth = wrapWidth ? 0 : width;
//        int maxHeight = wrapHeight ? 0 : height;

        // The pre-existing content of this view didn't match the current URL. Load the new image
        // from the network.
        ImageListener imageListener = new ImageListener() {
			@Override
			public void onErrorResponse(VolleyError error) {
				if (mErrorImageId != 0) {
					setImageResource(mErrorImageId);
				}
			}

			@Override
			public void onResponse(final ImageContainer response, boolean isImmediate) {
				// If this was an immediate response that was delivered inside
				// of a layout
				// pass do not set the image immediately as it will trigger a
				// requestLayout
				// inside of a layout. Instead, defer setting the image by
				// posting back to
				// the main thread.
				if (isImmediate && isInLayoutPass) {
					post(new Runnable() {
						@Override
						public void run() {
							onResponse(response, false);
						}
					});
					return;
				}

				if (response.getBitmap() != null) {
					setImageBitmap(response.getBitmap());
				} else if (mDefaultImageId != 0) {
					setImageResource(mDefaultImageId);
				}
			}
		};
		ImageContainer newContainer;
		if (mMaxWidth != 0 && mMaxHeight != 0) {
			newContainer = mImageLoader.get(mUrl, imageListener, mMaxWidth, mMaxHeight, scaleType, null, isClip, mCacheTag);
		} else {
			newContainer = mImageLoader.get(mUrl, imageListener, scaleType, mCacheTag);
		}

        // update the ImageContainer to be the new bitmap container.
        mImageContainer = newContainer;
    }

    private void setDefaultImageOrNull() {
        if(mDefaultImageId != 0) {
            setImageResource(mDefaultImageId);
        }
        else {
            setImageBitmap(null);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        loadImageIfNecessary(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mImageContainer != null) {
            // If the view was bound to an image request, cancel it and clear
            // out the image from the view.
            mImageContainer.cancelRequest();
            setImageBitmap(null);
            // also clear out the container so we can reload the image if necessary.
            mImageContainer = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    
    // belows are add to volley from comikon volley.
	/**
	 * @see android.widget.ImageView#setImageDrawable(android.graphics.drawable.Drawable)
	 */
	@Override
	public void setImageDrawable(Drawable drawable) {
		// Keep hold of previous Drawable
		final Drawable previousDrawable = getDrawable();

		// Call super to set new Drawable
		super.setImageDrawable(drawable);

		// Notify new Drawable that it is being displayed
		notifyDrawable(drawable, true);

		// Notify old Drawable so it is no longer being displayed
		notifyDrawable(previousDrawable, false);
	}

	@Override
	public void setImageBitmap(Bitmap bitmap) {
		BitmapDrawable drawable = null;
		if (Utils.hasHoneycomb()) {
			// Running on Honeycomb or newer, so wrap in a standard
			// BitmapDrawable
			drawable = new BitmapDrawable(null, bitmap);
		} else {
			// Running on Gingerbread or older, so wrap in a
			// RecyclingBitmapDrawable
			// which will recycle automagically
			drawable = new RecyclingBitmapDrawable(null, bitmap);
		}
		setImageDrawable(drawable);
	}

	/**
	 * Notifies the drawable that it's displayed state has changed.
	 *
	 * @param drawable
	 * @param isDisplayed
	 */
	private static void notifyDrawable(Drawable drawable, final boolean isDisplayed) {
		if (drawable instanceof RecyclingBitmapDrawable) {
			// The drawable is a CountingBitmapDrawable, so notify it
			((RecyclingBitmapDrawable) drawable).setIsDisplayed(isDisplayed);
		} else if (drawable instanceof LayerDrawable) {
			// The drawable is a LayerDrawable, so recurse on each layer
			LayerDrawable layerDrawable = (LayerDrawable) drawable;
			for (int i = 0, z = layerDrawable.getNumberOfLayers(); i < z; i++) {
				notifyDrawable(layerDrawable.getDrawable(i), isDisplayed);
			}
		}
	}
}
