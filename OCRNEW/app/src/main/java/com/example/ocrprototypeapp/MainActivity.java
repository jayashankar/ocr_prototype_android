package com.example.ocrprototypeapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ocrprototypeapp.R;
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

public class MainActivity extends AppCompatActivity {

    Button googleMLButton;
    ImageView previewImageView;
    TextView recognizedTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        googleMLButton = findViewById(R.id.GoogleMLKit);
        previewImageView = findViewById(R.id.PreviewImageView);
        recognizedTextView = findViewById(R.id.recognizedTextView);
        recognizedTextView.setMovementMethod(new ScrollingMovementMethod());

        PhotoViewAttacher pAttacher;
        pAttacher = new PhotoViewAttacher(previewImageView);
        pAttacher.update();

        googleMLButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddPhotoDialogueFragment();
            }
        });

       // copyAssets();


        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

    }

    void showAddPhotoDialogueFragment() {
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

    void showCamera() {
        Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(takePicture, 101);
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

//                try {
//                    recognizedTextView.setText(extractText(selectedImage));
//                } catch (Exception e) {
//                    Log.d("ERR", e.getLocalizedMessage());
//                }

                recognizedTextView.setText("");

                invokeGoogleMLRecognizer(selectedImage);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Something went wrong", Toast.LENGTH_LONG).show();
            }

        } else if (reqCode == 101 && resultCode == RESULT_OK) {
            Uri selectedImage = data.getData();
            //imageview.setImageURI(selectedImage);
        }
        else {
            Toast.makeText(MainActivity.this, "You haven't picked Image",Toast.LENGTH_LONG).show();
        }
    }

    private String extractText(Bitmap bitmap) throws Exception{
        TessBaseAPI tessBaseApi = new TessBaseAPI();

        File outFile = new File(getExternalFilesDir(null), File.separator);

        tessBaseApi.init(outFile.getAbsolutePath(), "eng");
        tessBaseApi.setImage(bitmap);
        String extractedText = tessBaseApi.getUTF8Text();
        tessBaseApi.end();
        return extractedText;
    }

    private void copyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        if (files != null) for (String filename : files) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(filename);
                File outFile = new File(getExternalFilesDir(null), File.separator + "tessdata" + File.separator + filename);
                outFile.mkdirs();

                out = new FileOutputStream(outFile);
                copyFile(in, out);
            } catch(Exception e) {
                Log.e("tag", "Failed to copy asset file: " + filename, e);
            }
            finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // NOOP
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        // NOOP
                    }
                }
            }
        }
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
        recognizedTextView.setText(resultText);

//        for (FirebaseVisionText.TextBlock block: result.getTextBlocks()) {
//            String blockText = block.getText();
//            Float blockConfidence = block.getConfidence();
//            List<RecognizedLanguage> blockLanguages = block.getRecognizedLanguages();
//            Point[] blockCornerPoints = block.getCornerPoints();
//            Rect blockFrame = block.getBoundingBox();
//            for (FirebaseVisionText.Line line: block.getLines()) {
//                String lineText = line.getText();
//                Float lineConfidence = line.getConfidence();
//                List<RecognizedLanguage> lineLanguages = line.getRecognizedLanguages();
//                Point[] lineCornerPoints = line.getCornerPoints();
//                Rect lineFrame = line.getBoundingBox();
//                for (FirebaseVisionText.Element element: line.getElements()) {
//                    String elementText = element.getText();
//                    Float elementConfidence = element.getConfidence();
//                    List<RecognizedLanguage> elementLanguages = element.getRecognizedLanguages();
//                    Point[] elementCornerPoints = element.getCornerPoints();
//                    Rect elementFrame = element.getBoundingBox();
//                }
//            }
//        }
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


    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

}
