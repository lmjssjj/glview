package com.glview.hwui.font;

import java.nio.ByteBuffer;
import java.util.Vector;

import com.glview.freetype.FreeType;
import com.glview.freetype.FreeType.Face;
import com.glview.freetype.FreeType.SizeMetrics;
import com.glview.graphics.Rect;
import com.glview.graphics.Typeface;
import com.glview.graphics.font.GlyphSlot;
import com.glview.graphics.shader.A8TextureShader;
import com.glview.hwui.GLCanvas;
import com.glview.hwui.GLPaint;
import com.glview.hwui.InnerGLCanvas;
import com.glview.hwui.packer.PackerRect;
import com.glview.internal.util.GrowingArrayUtils;
import com.glview.libgdx.graphics.opengl.GL20;
import com.glview.stackblur.BlurProcess;
import com.glview.stackblur.JavaBlurProcess;
import com.glview.text.TextUtils;

import android.graphics.Color;
import android.support.v4.util.LongSparseArray;

public class FontRenderer {
	
	public final static int TEXTURE_BORDER_SIZE = 1;
	
	FontRenderer() {}
	
	public static FontRenderer instance() {
		return GammaFontRenderer.instance().getFontRenderer();
	}
	
	private boolean mInitialized;
	
	int mSmallCacheWidth = 1024;
	int mSmallCacheHeight = 512;
	int mLargeCacheWidth = 2048;
	int mLargeCacheHeight = 1024;
	byte[] mGammaTable;
	byte[] mBuffer = new byte[2048];
	
	class FontCaches {
		Vector<CacheTexture> mACacheTextures = new Vector<CacheTexture>();
		LongSparseArray<FontRect> mCacheRects = new LongSparseArray<FontRect>();
	}
	
	FontCaches mCacheTextures = new FontCaches();
	FontCaches mShadowCacheTextures = new FontCaches();

    LongSparseArray<FontData> mFontDatas = new LongSparseArray<FontData>();
    
    GLCanvas mCanvas = null;
    
    BlurProcess mBlurProcess = new JavaBlurProcess();
    
    void setGammaTable(byte[] gammaTable) {
        mGammaTable = gammaTable;
    }
    
    public void release() {
    	clearCacheTextures(mCacheTextures);
    	clearCacheTextures(mShadowCacheTextures);
    	mFontDatas.clear();
    	mInitialized = false;
    }
    
	// We don't want to allocate anything unless we actually draw text
	void checkInit() {
	    if (mInitialized) {
	        return;
	    }

	    initTextTexture();

	    mInitialized = true;
	}
	
	void initTextTexture() {
	    clearCacheTextures(mCacheTextures);
	    clearCacheTextures(mShadowCacheTextures);

	    mCacheTextures.mACacheTextures.add(createCacheTexture(mSmallCacheWidth, mSmallCacheHeight,
	    		GL20.GL_ALPHA, true));
	    mCacheTextures.mACacheTextures.add(createCacheTexture(mLargeCacheWidth, mLargeCacheHeight >> 1,
	    		GL20.GL_ALPHA, false));
	    mCacheTextures.mACacheTextures.add(createCacheTexture(mLargeCacheWidth, mLargeCacheHeight >> 1,
	            GL20.GL_ALPHA, false));
	    mCacheTextures.mACacheTextures.add(createCacheTexture(mLargeCacheWidth, mLargeCacheHeight,
	    		GL20.GL_ALPHA, false));
	    mShadowCacheTextures.mACacheTextures.add(createCacheTexture(mSmallCacheWidth, mSmallCacheHeight,
	    		GL20.GL_ALPHA, false));
	    mShadowCacheTextures.mACacheTextures.add(createCacheTexture(mLargeCacheWidth, mLargeCacheHeight >> 1,
	    		GL20.GL_ALPHA, false));
	    mShadowCacheTextures.mACacheTextures.add(createCacheTexture(mLargeCacheWidth, mLargeCacheHeight,
	    		GL20.GL_ALPHA, false));
	}
	
	void clearCacheTextures(FontCaches caches) {
	    for (int i = 0; i < caches.mACacheTextures.size(); i++) {
	    	caches.mACacheTextures.get(i).release();
	    }
	    caches.mACacheTextures.clear();
	    caches.mCacheRects.clear();
	}
	
	CacheTexture createCacheTexture(int width, int height, int format,
	        boolean allocate) {
	    CacheTexture cacheTexture = new CacheTexture(this, width, height, format);

	    if (allocate) {
	        cacheTexture.allocateTexture();
	        cacheTexture.allocateMesh();
	    }

	    return cacheTexture;
	}
	
	public void flushBatch() {
		for (CacheTexture cacheTexture : mShadowCacheTextures.mACacheTextures) {
			if (cacheTexture.mFontBatch != null) {
				cacheTexture.mFontBatch.flush();
			}
		}
		for (CacheTexture cacheTexture : mCacheTextures.mACacheTextures) {
			if (cacheTexture.mFontBatch != null) {
				cacheTexture.mFontBatch.flush();
			}
		}
	}
	
	public void setGLCanvas(GLCanvas canvas) {
		mCanvas = canvas;
	}
	
	public GLCanvas getGLCanvas() {
		return mCanvas;
	}
	
	private final static boolean DEBUG_FONT_CACHE = false;
	GLPaint mTestPaint = null, mTestPaint2 = null;
	public void end(InnerGLCanvas canvas) {
		if (DEBUG_FONT_CACHE) {
			if (mTestPaint == null) {
				mTestPaint = new GLPaint();
				mTestPaint.setColor(Color.RED);
				mTestPaint.setShader(new A8TextureShader());
				mTestPaint2 = new GLPaint();
				mTestPaint2.setColor(Color.WHITE);
			}
			for (CacheTexture texture : mCacheTextures.mACacheTextures) {
				if (texture.mTexture.mId > 0) {
					((GLCanvas) canvas).drawRect(0, 0, texture.mWidth, texture.mHeight, mTestPaint2);
					canvas.drawTexture(texture.mTexture, 0, 0, texture.mWidth, texture.mHeight, mTestPaint);
				}
			}
		}
	}
 	
	public void renderText(GLCanvas canvas, CharSequence text, int start, int end, float x, float y,
			float alpha, GLPaint paint, Rect clip, float[] matrix, boolean forceFinish) {
		checkInit();
		Typeface typeface = paint.getTypeface();
		Face face = typeface.face();
		int textSize = paint.getTextSize();
		if (textSize < 5) return;
		int shadowRadius = (int) (paint.getShadowRadius() + 0.5f);
		int shadowColor = paint.getShadowColor();
		boolean hasShadow = paint.hasShadow();
		synchronized (face) {
			face.setPixelSizes(0, textSize);
			long k = typeface.index() * 10000L + textSize;
			FontData fontData = mFontDatas.get(k);
			if (fontData == null) {
				fontData = new FontData();
				SizeMetrics fontMetrics = face.getSize().getMetrics();
				fontData.ascent = FreeType.toInt(fontMetrics.getAscender());
				fontData.descent = FreeType.toInt(fontMetrics.getDescender());
				fontData.lineHeight = FreeType.toInt(fontMetrics.getHeight());
				if (face.loadChar(' ', FreeType.FT_LOAD_DEFAULT)) {
					fontData.spaceWidth = FreeType.toInt(face.getGlyph().getMetrics().getHoriAdvance());
				} else {
					fontData.spaceWidth = FreeType.toInt(face.getMaxAdvanceWidth());
				}
			}
			
			float baseline = y;
			
			for (int index = 0; index < end - start; index ++) {
				char c = text.charAt(index + start);
				if (TextUtils.isSpace(c)) {
					x += fontData.spaceWidth;
					continue;
				}
				int charIndex = face.getCharIndex(c);
				if (charIndex == 0) {
					c = 0;
					charIndex = face.getCharIndex(c);
				}
				if (charIndex == 0) continue;
				long key = charIndex * 10000000000L + typeface.index() * 10000000L + textSize * 10000L;
				FontRect r = mCacheTextures.mCacheRects.get(key);
				FontRect shadowR = null;
				if (hasShadow) {
					shadowR = mShadowCacheTextures.mCacheRects.get(key + shadowRadius);
				}
				if (r == null || (hasShadow && shadowR == null)) {
					if (!face.loadChar(c, FreeType.FT_LOAD_DEFAULT)) {
						continue;
					}
					FreeType.GlyphSlot slot = face.getGlyph();
					FreeType.Glyph glyph = slot.getGlyph();
					try {
						glyph.toBitmap(FreeType.FT_RENDER_MODE_NORMAL);
					} catch (RuntimeException e) {
						glyph.dispose();
						continue;
					}
					FreeType.Bitmap bitmap = glyph.getBitmap();
					int w = bitmap.getWidth();
					int h = bitmap.getRows();
					if (w <= 0 || h <= 0) {
						continue;
					}
					if (r == null) {
						r = cacheBitmap(mCacheTextures, w, h, TEXTURE_BORDER_SIZE, slot, glyph, bitmap, true);
						if (r != null) {
							mCacheTextures.mCacheRects.put(key, r);
						}
					}
					if (shadowR == null && hasShadow) {
						shadowR = cacheBitmapShadow(mShadowCacheTextures, w, h, shadowRadius, slot, glyph, bitmap, true);
						if (shadowR != null) {
							mShadowCacheTextures.mCacheRects.put(key + shadowRadius, shadowR);
						}
					}
					glyph.dispose();
				}
				if (r != null) {
					if (shadowR != null) {
						shadowR.mTexture.allocateMesh();
						shadowR.mTexture.mFontBatch.draw(x + shadowR.mLeft - shadowRadius + paint.getShadowDx(), 
								baseline - shadowR.mTop - shadowRadius + paint.getShadowDy(), shadowR.mRect.width(), shadowR.mRect.height(), 
								shadowR.mRect.rect().left, shadowR.mRect.rect().top, shadowR.mRect.width(), shadowR.mRect.height(), 
								matrix, alpha * paint.getAlpha() / 255, shadowColor, paint);
					}
					r.mTexture.allocateMesh();
					r.mTexture.mFontBatch.draw(x + r.mLeft, baseline - r.mTop, r.mRect.width(), r.mRect.height(), 
							r.mRect.rect().left, r.mRect.rect().top, r.mRect.width(), r.mRect.height(), 
							matrix, alpha * paint.getAlpha() / 255, paint.getColor(), paint);
					x += r.mGlyphSlot.getAdvanceX();
				}
			}
			if (forceFinish) {
				flushBatch();
			}
		}
		
	}
	
	private void flushAndInvalidate(FontCaches caches) {
		for (CacheTexture cacheTexture : caches.mACacheTextures) {
			if (cacheTexture.mFontBatch != null) {
				cacheTexture.mFontBatch.flush();
			}
			cacheTexture.setDirty(false);
		}
		caches.mCacheRects.clear();
	}
	
	private FontRect cacheBitmap(FontCaches caches, int w, int h, int border, FreeType.GlyphSlot slot, FreeType.Glyph glyph, FreeType.Bitmap bitmap, boolean c) {
		w = w + border * 2;
		h = h + border * 2;
		for (CacheTexture cacheTexture : caches.mACacheTextures) {
			PackerRect rect = cacheTexture.mPacker.insert(w, h);
			if (rect != null) {
				FontRect r = new FontRect(cacheTexture, rect, new GlyphSlot(FreeType.toInt(slot.getAdvanceX()), FreeType.toInt(slot.getAdvanceY())), glyph.getLeft(), glyph.getTop());
				if (cacheTexture.getPixelBuffer() == null) {
					cacheTexture.allocateTexture();
				}
				ByteBuffer byteBuffer = cacheTexture.getPixelBuffer().map();
				ByteBuffer buffer = bitmap.getBuffer();
				int pitch = bitmap.getPitch();
				if (mGammaTable != null) {
					for (int i = 0; i < rect.height(); i ++) {
						for (int j = 0; j < rect.width(); j ++) {
							if (i < border || i >= rect.height() - border || j < border || j >= rect.width() - border) {
								byteBuffer.put((i + rect.rect().top) * cacheTexture.mWidth + j + rect.rect().left, (byte) 0);
							} else {
								int t = buffer.get((i - border) * pitch + j - border) & 0xFF;
								byteBuffer.put((i + rect.rect().top) * cacheTexture.mWidth + j + rect.rect().left, mGammaTable[t]);
							}
						}
					}
				} else {
					for (int i = 0; i < rect.height(); i ++) {
						for (int j = 0; j < rect.width(); j ++) {
							if (i < border || i >= rect.height() - border || j < border || j >= rect.width() - border) {
								byteBuffer.put((i + rect.rect().top) * cacheTexture.mWidth + j + rect.rect().left, (byte) 0);
							} else {
								byteBuffer.put((i + rect.rect().top) * cacheTexture.mWidth + j + rect.rect().left, buffer.get((i - border) * pitch + j - border));
							}
						}
					}
				}
				cacheTexture.mDirtyRect.union(rect.rect());
				cacheTexture.setDirty(true);
				return r;
			}
		}
		if (c) {
			flushAndInvalidate(caches);
			return cacheBitmap(caches, w, h, border, slot, glyph, bitmap, false);
		}
		return null;
	}
	
	private FontRect cacheBitmapShadow(FontCaches caches, int w, int h, int shadowRadius, FreeType.GlyphSlot slot, FreeType.Glyph glyph, FreeType.Bitmap bitmap, boolean c) {
		w = w + shadowRadius * 2;
		h = h + shadowRadius * 2;
		for (CacheTexture cacheTexture : caches.mACacheTextures) {
			PackerRect rect = cacheTexture.mPacker.insert(w, h);
			if (rect != null) {
				FontRect r = new FontRect(cacheTexture, rect, new GlyphSlot(FreeType.toInt(slot.getAdvanceX()), FreeType.toInt(slot.getAdvanceY())), glyph.getLeft(), glyph.getTop());
				if (cacheTexture.getPixelBuffer() == null) {
					cacheTexture.allocateTexture();
				}
				ByteBuffer byteBuffer = cacheTexture.getPixelBuffer().map();
				ByteBuffer buffer = bitmap.getBuffer();
				int pitch = bitmap.getPitch();
				int size = w * h;
				if (mBuffer.length < size) {
					mBuffer = new byte[GrowingArrayUtils.growSize(size)];
				}
				if (mGammaTable != null) {
					for (int i = 0; i < h; i ++) {
						for (int j = 0; j < w; j ++) {
							if (i < shadowRadius || i >= rect.height() - shadowRadius || j < shadowRadius || j >= rect.width() - shadowRadius) {
								mBuffer[i * w + j] = 0;
							} else {
								int t = buffer.get((i - shadowRadius) * pitch + j - shadowRadius) & 0xFF;
								mBuffer[i * w + j] = mGammaTable[t];
							}
						}
					}
				} else {
					for (int i = 0; i < h; i ++) {
						for (int j = 0; j < w; j ++) {
							if (i < shadowRadius || i >= rect.height() - shadowRadius || j < shadowRadius || j >= rect.width() - shadowRadius) {
								mBuffer[i* w + j] = (byte) 0;
							} else {
								mBuffer[i * w + j] = buffer.get((i - shadowRadius) * pitch + j - shadowRadius);
							}
						}
					}
				}
				mBlurProcess.blur(mBuffer, w, h, shadowRadius);
				for (int i = 0; i < h; i ++) {
					for (int j = 0; j < w; j ++) {
						byteBuffer.put((i + rect.rect().top) * cacheTexture.mWidth + j + rect.rect().left, mBuffer[i * w + j]);
					}
				}
				cacheTexture.mDirtyRect.union(rect.rect());
				cacheTexture.setDirty(true);
				return r;
			}
		}
		if (c) {
			flushAndInvalidate(caches);
			return cacheBitmap(caches, w, h, shadowRadius, slot, glyph, bitmap, false);
		}
		return null;
	}
	
	private static class FontData {
		int ascent;
		int descent;
		int lineHeight;
		int spaceWidth;
	}

}
