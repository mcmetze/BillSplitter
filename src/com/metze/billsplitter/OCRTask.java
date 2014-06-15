package com.metze.billsplitter;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.Pixa;
import com.googlecode.tesseract.android.TessBaseAPI;

public class OCRTask extends AsyncTask<Void, Integer, Bitmap>
{
	protected static final String TAG = "ProcessTask";
	protected static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/BillSplitter/";
	protected static final String lang = "eng";
	private Context mContext;

	private Bitmap mBmp;
    private int mImageWidth;
    private int mImageHeight;
    
    private Mat mMatToProcess;
    private List<Rect> bboxes;
    
    public OCRTask(Context context)
    {
    	mBmp = null;
    	mImageWidth = 0;
    	mImageHeight = 0;
    	mContext = context;
    	mMatToProcess = null;
    	bboxes = new ArrayList<Rect>();
    }

    @Override
    protected Bitmap doInBackground(Void... params) 
    {
    	Log.i(TAG, "doInBackground");

        preProcess();
        checkForTrainedDataFile();
        segmentImage();

        return overlayRects();
    }
    
    private Bitmap overlayRects()
    {
    	Utils.matToBitmap(mMatToProcess, mBmp, true);
    	Bitmap bmp = mBmp.copy(mBmp.getConfig(), true);
    	for(Rect r : bboxes)
    	{
    		for(int x = r.left; x<r.right; ++x)
    		{
    			bmp.setPixel(x, r.top, Color.RED);
    			bmp.setPixel(x, r.bottom, Color.RED);
    		}
    		for(int y = r.top; y<r.bottom; ++y)
    		{
    			bmp.setPixel(r.left, y, Color.RED);
    			bmp.setPixel(r.right, y, Color.RED);
    		}
    	}
    	
    	return bmp;
    }

    @Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
	}

	@Override
	protected void onPostExecute(Bitmap result) {
		super.onPostExecute(result);
		Log.i(TAG, "onPostExecute");
		FileOutputStream out = null;
		String path = Environment.getExternalStorageDirectory().toString();
		try 
		{
			File file = new File(path + "/out.PNG");
			out = new FileOutputStream(file);
			Log.i(TAG, "Saving image to "+path);
			result.compress(Bitmap.CompressFormat.PNG, 90, out);
		} 
		catch (Exception e) 
		{
		    e.printStackTrace();
		} 
		finally 
		{
			try
			{
				out.close();
		    } 
			catch(Throwable ignore) {}
		}
	}

	public void setImageToProcess(Bitmap bmpToProcess)
    {
    	mBmp = bmpToProcess;
    	mImageWidth = bmpToProcess.getWidth();
    	mImageHeight = bmpToProcess.getHeight();
    	mMatToProcess = new Mat(mImageHeight, mImageWidth, CvType.CV_8UC4);
        Utils.bitmapToMat(mBmp, mMatToProcess, true);	//is created as 'CV_8UC4' type, it keeps the image in RGBA format.
    }
    
	private void checkForTrainedDataFile()
	{
		String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };

		for (String path : paths) 
		{
			File dir = new File(path);
			if (!dir.exists()) 
			{
				if (!dir.mkdirs()) 
				{
					Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
					return;
				} 
				else 
					Log.v(TAG, "Created directory " + path + " on sdcard");
			}
		}

		// lang.traineddata file with the app (in assets folder)
		// This area needs work and optimization
		if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) 
		{
			try 
			{
				AssetManager assetManager = mContext.getAssets();
				InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
				OutputStream out = new FileOutputStream(DATA_PATH + "tessdata/" + lang + ".traineddata");

				// Transfer bytes from in to out
				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) 
				{
					out.write(buf, 0, len);
				}
				in.close();
				out.close();

				Log.v(TAG, "Copied " + lang + " traineddata");
			} 
			catch (IOException e) 
			{
				Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
			}
		}
		
	}

    private List<Rect> getBoundingBoxes(Mat in)
    {
    	Log.i(TAG, "getBoundindBoxes");
    	List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
    	Imgproc.findContours(in, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
    	
    	List<Rect> boundingRects = new ArrayList<Rect>(contours.size());

		//find bounding rects of each contour/connected comp and add it to the list to be returned
    	for(MatOfPoint curComp : contours)
    	{
    		org.opencv.core.Rect rectObj = Imgproc.boundingRect(curComp);
            Rect boundRect = new Rect(rectObj.x, rectObj.y+rectObj.height, rectObj.x+rectObj.width, rectObj.y);
			boundingRects.add(boundRect);
    	}
    	
    	return boundingRects;
    }
    
    private void segmentImage()
    {	
    	Mat mask = new Mat();
    	Imgproc.dilate(mMatToProcess, mask, Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT, new Size(mImageWidth, 2)));
    	
    	bboxes = getBoundingBoxes(mask);

    	for(Rect r : bboxes)
    	{
    		Mat crop = mMatToProcess.submat(r.bottom, r.top, r.left, r.right);
    		Bitmap region = Bitmap.createBitmap(crop.cols(), crop.rows(), mBmp.getConfig());
    		Utils.matToBitmap(crop, region, true);
    		getText(region);
    	}
    	
	
    }
	
	private void preProcess()
    {
    	Log.i(TAG, "preProcess");
    	Imgproc.cvtColor(mMatToProcess, mMatToProcess, Imgproc.COLOR_RGBA2GRAY);	
    
    	Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5,5));
    	Mat temp = new Mat(); 

    	Imgproc.resize(mMatToProcess, temp, new Size(mImageWidth/4, mImageHeight/4));
    	Imgproc.morphologyEx(temp, temp, Imgproc.MORPH_CLOSE, kernel);
    	Imgproc.resize(temp, temp, new Size(mImageWidth, mImageHeight));

    	Core.divide(mMatToProcess, temp, temp, 1, CvType.CV_32F); // temp will now have type CV_32F
    	Core.normalize(temp, mMatToProcess, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);

    	Imgproc.threshold(mMatToProcess, mMatToProcess, -1, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);    	
    }

    private String[] getText(Bitmap image)
    {
		TessBaseAPI baseApi = new TessBaseAPI();
		baseApi.setDebug(true);
		baseApi.init(DATA_PATH, "eng");
		baseApi.setImage(image);

		String recognizedText = baseApi.getUTF8Text();
		baseApi.end();
		
		String[] words = recognizedText.split(" ");
		for(String w : words)
		{
			System.out.print(w+", ");
		}
		System.out.println("");
		
		return words;
    }
    
}