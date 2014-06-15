package com.metze.billsplitter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.metze.billsplitter.R;

public class CropActivity extends Activity
{
    private ImageView mImageView;
    CropOverlay mCropOverlay;
    private Bitmap mOriginalBmp;
    private String mImageFilePath;

    private int mOffsetToImageY;
    private int mOffsetToImageX;
    private int mOverlayOffsetY;
    private int mOverlayOffsetX;
    
    private static int ACTION_DOWN = 4;
    private static int ACTION_MOVE = 5;
    private static int ACTION_UP = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_crop);
        mImageView = (ImageView)findViewById(R.id.imageView);
        
        mOffsetToImageX = 0;
        mOffsetToImageY = 0;
        mOverlayOffsetY = 0;
        mOverlayOffsetX = 0;
        
        Bundle extras = getIntent().getExtras();
        mOriginalBmp = (Bitmap)extras.get("picture");
        mImageFilePath = (String)extras.get("path");

        mCropOverlay = new CropOverlay(this);   
        addContentView(mCropOverlay, mImageView.getLayoutParams());
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
    	if(hasFocus)
    	{	
	    	if(mOriginalBmp == null)
	    	{
	            // Get the dimensions of the bitmap
	            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
	            bmOptions.inJustDecodeBounds = true;
	            BitmapFactory.decodeFile(mImageFilePath, bmOptions);
	            int photoW = bmOptions.outWidth;
	            int photoH = bmOptions.outHeight;

	            // Determine how much to scale down the image
	            int scaleFactor = Math.min(photoW/mImageView.getWidth(), photoH/mImageView.getHeight());

	            // Decode the image file into a Bitmap sized to fill the View
	            bmOptions.inJustDecodeBounds = false;
	            bmOptions.inSampleSize = scaleFactor;
	            bmOptions.inPurgeable = true;

	            mOriginalBmp = BitmapFactory.decodeFile(mImageFilePath, bmOptions);
	    	}
	    	
	    	//get the pixel positions of the bitmap on the imageview
	    	Matrix matrix = mImageView.getImageMatrix();
	    	float[] values = new float[9];
	    	matrix.getValues(values);
	    	mOffsetToImageY = (int) (values[Matrix.MTRANS_Y] + mImageView.getY());
	    	mOffsetToImageX = (int) (values[Matrix.MTRANS_X] + mImageView.getX());
	    	
	    	//account for the offset from a touch on the imageview to coordinates in the cropoverlay
	    	int[] location = {0, 0};
	    	mImageView.getLocationInWindow(location);
	    	mOverlayOffsetY = Math.abs(mImageView.getTop()-location[1]);
	    	mOverlayOffsetX = Math.abs(mImageView.getLeft()-location[0]);

	    	resetChanges(null);
    	}
    	

    }

    public void cancel(View view)
    {
        finish();
    }

    private Bitmap doCrop()
    {
    	//need to convert the rect positions/dimensions to "image coordinates"
    	Rect crop = mCropOverlay.getCropRectangle();
    	return Bitmap.createBitmap(mOriginalBmp, crop.left-mOverlayOffsetX, crop.top-mOverlayOffsetY, crop.width(), crop.height());
    }
    
    public void acceptPic(View view)
    {
        OCRTask task = new OCRTask(this);
        task.setImageToProcess(mOriginalBmp);
        task.execute();
        
        finish();
    }

    public void resetChanges(View view)
    {
    	mImageView.setImageBitmap(mOriginalBmp);
    	mCropOverlay.setCropRect(mOffsetToImageX, mOffsetToImageY, mCropOverlay.getWidth()-mOffsetToImageX, mCropOverlay.getHeight()-mOffsetToImageY);
        mImageView.invalidate();
        mCropOverlay.invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
    	
    	int overlayX = (int) event.getX() - mOverlayOffsetX;
    	int overlayY = (int) event.getY() - mOverlayOffsetY;
    	
        switch(event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
            	mCropOverlay.touchEventInfo(overlayX, overlayY, ACTION_DOWN);
                break;

            case MotionEvent.ACTION_MOVE:
                mCropOverlay.touchEventInfo(overlayX, overlayY, ACTION_MOVE);
                break;
                
            case MotionEvent.ACTION_UP:
            	mCropOverlay.touchEventInfo(overlayX, overlayY, ACTION_UP);
            	break;
        }
        mCropOverlay.invalidate();
        return true;
    }
    
    
    class CropOverlay extends View
    {
    	private static final String TAG = "CropOverlay";
    	private static final float mCornerRadius = 10.f;
        private static final int LEFT = 0;
        private static final int TOP = 1;
        private static final int RIGHT = 2;
        private static final int BOTTOM = 3;
    	
        Paint mBoarderPaint;
        Paint mCornerPaint;
        Rect mBoarderRect;

        int[] mCorners;					//left, top, right, bottom
        int[] mOnTouchCenter;			//x, y
        int[] mSelectedCornerIndex;		//x, y
        boolean mCornerIsSelected;
        boolean mRectIsSelected;

        public CropOverlay(Context context)
        {
            super(context);
            mBoarderPaint = new Paint();
            mBoarderPaint.setColor(Color.GREEN);
            mBoarderPaint.setStyle(Style.STROKE);
            mBoarderPaint.setStrokeWidth(2);

            mCornerPaint = new Paint();
            mCornerPaint.setColor(Color.GREEN);
            
            mRectIsSelected = false;
            mCornerIsSelected = false;
            
            mOnTouchCenter = new int[]{100, 100};
            mSelectedCornerIndex = new int[]{0, 0};
            mCorners = new int[]{100, 100, 300, 400};
            
            mBoarderRect = new Rect(mCorners[LEFT], mCorners[TOP], mCorners[RIGHT], mCorners[BOTTOM]);
        }

        private void checkRect()
        {
        	boolean valid = true;
        	
			//make sure left < right, top < bottom as required by Rect
			if(mCorners[RIGHT] <= mCorners[LEFT] || mCorners[BOTTOM] <= mCorners[TOP])
				valid = false;
			
    		//make sure the crop bounds stay within the image/screen
        	if(mCorners[RIGHT] >= this.getWidth() || mCorners[LEFT] <= 0 || mCorners[TOP] <= 0 || mCorners[BOTTOM] >= this.getHeight())
        		valid = false;
        	
        	//invalid rect's shouldn't be updated
        	if(!valid)
        	{
        		mCorners[LEFT] = mBoarderRect.left;
        		mCorners[TOP] = mBoarderRect.top;
        		mCorners[RIGHT] = mBoarderRect.right;
        		mCorners[BOTTOM] = mBoarderRect.bottom;
        	}
        }
        
        //debug function to see what coorinates set from cropactivity's image view show up as
        public void setCropRect(int left, int top, int right, int bottom)
        {
        	mCorners[LEFT] = left;
        	mCorners[TOP] = top;
        	mCorners[RIGHT] = right;
        	mCorners[BOTTOM] = bottom;
        	mBoarderRect.set(mCorners[LEFT], mCorners[TOP], mCorners[RIGHT], mCorners[BOTTOM]);
        }
        
        public void touchEventInfo(int x, int y, int action)
        {	
        	if(action == ACTION_DOWN)
        	{
        		//check left and right for closest to touch x
        		float xdiff = Math.abs(x - mCorners[LEFT]);
        		float tmpdiff = Math.abs(x - mCorners[RIGHT]);
        		mSelectedCornerIndex[0] = LEFT;
        		if( tmpdiff < xdiff)
        		{
        			xdiff = tmpdiff;
        			mSelectedCornerIndex[0] = RIGHT;
        		}
        		
        		//check top and bottom for closest to touch y
        		float ydiff = Math.abs(y - mCorners[TOP]);
        		tmpdiff = Math.abs(y-mCorners[BOTTOM]);
        		mSelectedCornerIndex[1] = TOP;
        		if( tmpdiff < ydiff)
        		{
        			ydiff = tmpdiff;
        			mSelectedCornerIndex[1] = BOTTOM;
        		}
        		
        		//check if it's within the handle circle
        		double dist = Math.sqrt(xdiff*xdiff + ydiff*ydiff);
        		if(dist - mCornerRadius < 0.0001)
        		{
        			mCornerIsSelected = true;
        		}
        		
        		//if we didn't select a corner, see if we got the rectangle for translation
        		if(!mCornerIsSelected)
        		{
        			if(mBoarderRect.contains(x, y) )
        			{
        				mRectIsSelected = true;
        			}
        		}
        	}
        	else if(action == ACTION_UP)
        	{
        		mCornerIsSelected = false;
        		mRectIsSelected = false;
        	}
        	else if(action == ACTION_MOVE)
        	{
    			int dx = (int) (mOnTouchCenter[0]-x);
    			int dy = (int) (mOnTouchCenter[1]-y);
    			
        		if(mCornerIsSelected)
        		{
        			mCorners[mSelectedCornerIndex[0]] -= dx;
        			mCorners[mSelectedCornerIndex[1]] -= dy;
        		}
        		else if(mRectIsSelected)
        		{
        			mCorners[LEFT] -= dx;
        			mCorners[RIGHT] -= dx;
        			mCorners[TOP] -= dy;
        			mCorners[BOTTOM] -= dy;
        		}
        		
        		checkRect();

    			mBoarderRect.set(mCorners[LEFT], mCorners[TOP], mCorners[RIGHT], mCorners[BOTTOM]);
        	}
        	
			mOnTouchCenter[0] = x;
			mOnTouchCenter[1] = y;
        }
        
        public Rect getCropRectangle()
        {
        	return mBoarderRect;
        }
        
        @Override
        protected void onDraw(Canvas canvas)
        {
        //	canvas.drawBitmap(mOriginalBmp, 0, 0, null);
            canvas.drawRect(mBoarderRect, mBoarderPaint);
            for(int i=0; i<4; i+=2)
            {
            	canvas.drawCircle(mCorners[i], mCorners[TOP], mCornerRadius, mCornerPaint);
            	canvas.drawCircle(mCorners[i], mCorners[BOTTOM], mCornerRadius, mCornerPaint);
            }
            super.onDraw(canvas);
        }
    }
}