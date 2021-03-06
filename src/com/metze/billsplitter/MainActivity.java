package com.metze.billsplitter;


import java.io.FileNotFoundException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import com.metze.billsplitter.R;

public class MainActivity extends Activity
{
    private static final int TAKE_PIC_CODE = 1888;
    private static final int ADJUST_PIC_CODE = 1999;
    private static final int CHOOSE_PIC_CODE = 1777;
	protected static final String TAG = "MainActivity";
	
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    
    public void choosePicture(View view)
    {
        //read from gallery or SD card
    	Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
    	photoPickerIntent.setType("image/*");
    	startActivityForResult(photoPickerIntent, CHOOSE_PIC_CODE); 
    }

    public void takePicture(View view)
    {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(takePictureIntent, TAKE_PIC_CODE);
    }

    private void cropAndProcess(Bitmap bmp, String path)
    {
    	Intent adjustImageIntent = new Intent(this, CropActivity.class);
    	if(bmp != null)
    		adjustImageIntent.putExtra("picture", bmp);
    	if(path != null)
    		adjustImageIntent.putExtra("path", path);
    	
        startActivityForResult(adjustImageIntent, ADJUST_PIC_CODE);
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode)
        {
            case TAKE_PIC_CODE:
                if (resultCode == RESULT_OK)
                {
                    Bitmap picBmp = (Bitmap)(data.getExtras().get("data"));
                    cropAndProcess(picBmp, null);
                }
                break;
              
            case ADJUST_PIC_CODE:
            		if(resultCode == RESULT_OK)
            		{
	            		//new activity to create the party of diners 
            			Log.i(TAG, "done crop");
            		}
            		break;
            
            case CHOOSE_PIC_CODE:
            	if(resultCode == RESULT_OK)
            	{  
            		Uri selectedImageUri = data.getData();

                    //OI FILE Manager
                    String filemanagerstring = selectedImageUri.getPath();
                    //MEDIA GALLERY
                    String selectedImagePath = getRealPathFromURI(selectedImageUri);

                    if(selectedImagePath!=null)
                    	cropAndProcess(null, selectedImagePath);
                    else
                    	cropAndProcess(null, filemanagerstring);
                    
                }
            	break;
        }
    }
    
    public String getRealPathFromURI(Uri contentUri) 
    {
        String res = null;
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if(cursor.moveToFirst())
        {
           int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
           res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }
    
}
