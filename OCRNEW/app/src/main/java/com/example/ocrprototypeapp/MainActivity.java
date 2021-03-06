package com.example.ocrprototypeapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentManager;
import uk.co.senab.photoview.PhotoViewAttacher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    Button googleMLButton, tesseractButton;
    ImageView previewImageView;
    EditText recognizedEditText;
    LinearLayout progressBarLayout;

    public static final int MLKIT = 1;
    public static final int TESSERACT = 2;

    private String pictureImagePath = "";

    Integer selectedOption = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        googleMLButton = findViewById(R.id.GoogleMLKitButton);
        tesseractButton = findViewById(R.id.TesseractButton);
        previewImageView = findViewById(R.id.PreviewImageView);
        recognizedEditText = findViewById(R.id.recognizedEditText);
        recognizedEditText.setMovementMethod(new ScrollingMovementMethod());
        progressBarLayout = findViewById(R.id.ProgressBarLayout);

        PhotoViewAttacher pAttacher;
        pAttacher = new PhotoViewAttacher(previewImageView);
        pAttacher.update();

        googleMLButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedOption = MLKIT;
                showAddPhotoDialogueFragment();
            }
        });

        tesseractButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedOption = TESSERACT;
                showAddPhotoDialogueFragment();
            }
        });

        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                copyTrainingDataToInternalStorage();
            }
        });

    }

    private void copyTrainingDataToInternalStorage() {
        InputStream chineseTrainingFileStream = getResources().openRawResource(R.raw.chi_sim);
        File storageDir = getExternalFilesDir(null);
        String folderPath = storageDir.getAbsolutePath() + "/" + "tessdata";
        File folder = new File(folderPath);
        folder.mkdir();
        String filePath = folderPath + "/chi_sim.traineddata";
        File file = new File(filePath);
        try {
            file.createNewFile();
        } catch (Exception e) {
            Log.d("Error", "Error occurred during trained data file creation");
            e.printStackTrace();
        }

        copyInputStreamToFile(chineseTrainingFileStream, file);
    }

    // Copy an InputStream to a File.
    private void copyInputStreamToFile(InputStream in, File file) {
        OutputStream out = null;

        try {
            out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            // Ensure that the InputStreams are closed even if there's an exception.
            try {
                if ( out != null ) {
                    out.close();
                }

                // If you want to close the "in" InputStream yourself then remove this
                // from here but ensure that you close it yourself eventually.
                in.close();
            }
            catch ( IOException e ) {
                e.printStackTrace();
            }
        }
    }

    private void showAddPhotoDialogueFragment() {
        FragmentManager fm = getSupportFragmentManager();
        ListDialogFragment dialogueFragment = new ListDialogFragment();
        dialogueFragment.show(fm, "google_ml");


        dialogueFragment.setListener(new ListDialogFragment.DialogClickListener() {
            @Override
            public void didSelectOption(Integer buttonIndex) {
               if (buttonIndex == 1) {
                   showGallery();
               } else if (buttonIndex == 0) {
                   showCamera();
               }
            }
        });
    }

    private void showCamera() {
        Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            takePicture.putExtra(MediaStore.EXTRA_OUTPUT, getPhotoFileUri());
        } else {
            File file = new File(getPhotoFileUri().getPath());
            Uri photoUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", file);
            takePicture.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        }

        startActivityForResult(takePicture, 101);
    }

    Uri getPhotoFileUri() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = timeStamp + ".jpg";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        pictureImagePath = storageDir.getAbsolutePath() + "/" + imageFileName;
        File file = new File(pictureImagePath);
        Uri outputFileUri = Uri.fromFile(file);
        return outputFileUri;
    }

    void showGallery() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, 100);
    }

    @Override
    protected void onActivityResult(int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);


        if (reqCode == 100 && resultCode == RESULT_OK) {
            try {
                final Uri imageUri = data.getData();
                final InputStream imageStream = getContentResolver().openInputStream(imageUri);
                final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                previewImageView.setImageBitmap(selectedImage);
                recognizedEditText.setText("");
                progressBarLayout.setVisibility(View.VISIBLE);

                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (selectedOption == MLKIT) {
                            invokeGoogleMLRecognizer(selectedImage);
                        } else if (selectedOption == TESSERACT) {
                            try {
                                recognizedEditText.setText(extractText(selectedImage));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });

            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Something went wrong", Toast.LENGTH_LONG).show();
            }

        } else if (reqCode == 101 && resultCode == RESULT_OK) {
            File imgFile = new  File(pictureImagePath);
            if(imgFile.exists()){

                progressBarLayout.setVisibility(View.VISIBLE);
                final Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                previewImageView.setImageBitmap(myBitmap);

                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (selectedOption == MLKIT) {
                            invokeGoogleMLRecognizer(myBitmap);
                        } else if (selectedOption == TESSERACT) {
                            try {
                                recognizedEditText.setText(extractText(myBitmap));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });

            }

        }
        else {
            Toast.makeText(MainActivity.this, "You haven't picked Image",Toast.LENGTH_LONG).show();
        }
    }

    private String extractText(Bitmap bitmap) throws Exception{
        TessBaseAPI tessBaseApi = new TessBaseAPI();

        File outFile = new File(getExternalFilesDir(null), File.separator);

        tessBaseApi.init(outFile.getAbsolutePath(), "chi_sim");
        tessBaseApi.setImage(bitmap);
        String extractedText = tessBaseApi.getUTF8Text();
        tessBaseApi.end();
        return extractedText;
    }


    void invokeGoogleMLRecognizer(Bitmap selectedImage) {

        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(selectedImage);

        FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance()
                .getCloudTextRecognizer();

        Task<FirebaseVisionText> result = detector.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText firebaseVisionText) {
                        processResult(firebaseVisionText);
                    }
                })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(MainActivity.this, e.getLocalizedMessage(),Toast.LENGTH_LONG).show();
                            }
                        });
    }

    private void processResult(FirebaseVisionText result) {
        String resultText = result.getText();
        recognizedEditText.setText(resultText);
        progressBarLayout.setVisibility(View.GONE);
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

}
