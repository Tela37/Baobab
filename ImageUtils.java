package td.teladoumbaobabtd;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ImageUtils {

    public static String saveImageToInternalStorage(Context context, Uri imageUri) {
        if (context == null || imageUri == null) {
            return null;
        }

        try {
            File dir = new File(context.getFilesDir(), "profile_images");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filename = "profile_" + System.currentTimeMillis() + ".jpg";
            File destinationFile = new File(dir, filename);

            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (bitmap == null) {
                return null;
            }

            FileOutputStream outputStream = new FileOutputStream(destinationFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.flush();
            outputStream.close();

            return destinationFile.getAbsolutePath();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void loadProfileImage(Context context, String imagePath, ImageView imageView) {
        if (imageView == null) {
            return;
        }

        if (imagePath != null && !imagePath.trim().isEmpty()) {
            File file = new File(imagePath);
            if (file.exists()) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                    int targetWidth = 200;
                    int targetHeight = 200;

                    int scale = 1;
                    while (options.outWidth / scale / 2 >= targetWidth &&
                            options.outHeight / scale / 2 >= targetHeight) {
                        scale *= 2;
                    }

                    options.inSampleSize = scale;
                    options.inJustDecodeBounds = false;

                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        imageView.setImageResource(R.drawable.ic_profile_2);
    }

    public static void loadFullImage(Context context, String imagePath, ImageView imageView) {
        if (imageView == null) {
            return;
        }

        if (imagePath != null && !imagePath.trim().isEmpty()) {
            File file = new File(imagePath);
            if (file.exists()) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        imageView.setImageResource(R.drawable.ic_profile_2);
    }
}