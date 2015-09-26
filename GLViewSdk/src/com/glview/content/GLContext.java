package com.glview.content;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import com.glview.content.res.GLResources;

public final class GLContext {
	
	static GLContext sInstance;
	
	GLResources mResources;
	
	final Context mApplicationContext;
	
	boolean mLargeHeap = false;

	private GLContext(Context context) {
		sInstance = this;
		mResources = new GLResources(context.getAssets(), context.getResources());
		mApplicationContext = context.getApplicationContext();
		try {
			mLargeHeap = (context.getPackageManager().getPackageInfo(context.getPackageName(), 0).applicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) == ApplicationInfo.FLAG_LARGE_HEAP;
		} catch (NameNotFoundException e) {
		}
	}
	
	public boolean isLargeHeap() {
		return mLargeHeap;
	}
	
	/**
	 * Application should initialize the GLContext, so we can use GLResources.
	 * @see #getResources()
	 * @param context
	 */
	public static void initialize(Context context) {
		if (sInstance == null) {
			sInstance = new GLContext(context);
		}
	}
	
	public static GLContext get() {
		if (sInstance == null) {
			throw new RuntimeException("GLCantext has not initialized yet.");
		}
		return sInstance;
	}
	
	public GLResources getResources() {
		return mResources;
	}
	
	public Context getApplicationContext() {
		return mApplicationContext;
	}

}
