package jp.cameratest;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 * Created by masanori on 2016/05/13.
 */
public class PreviewTextureView extends TextureView {
    private int ratioWidth = 0;
    private int ratioHeight = 0;

    public PreviewTextureView(Context context) {
        this(context, null);
    }

    public PreviewTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreviewTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;
        // サイズを指定してViewを更新する(onMeasure実行).
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Viewのサイズを確定させる.
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * ratioWidth / ratioHeight) {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth);
            } else {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height);
            }
        }
    }
}
