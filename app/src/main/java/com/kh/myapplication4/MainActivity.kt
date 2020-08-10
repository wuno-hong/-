package com.kh.myapplication4

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.baidu.aip.imageclassify.AipImageClassify
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import android.Manifest
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ActivityCompat
import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    val takePhoto = 1
    val fromAlbum = 2
    lateinit var imageUri: Uri
    lateinit var outputImage: File
    val APP_ID = "21784024"
    val API_KEY = "0n0F7yyZEu71oHKXAE7mAkxA"
    val SECRET_KEY = "c8CzgdESxq4G146L9LQHY2iezA2LWoIj"
    var resultCode: Int = 0
    var imagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)
        //隐藏标题栏
        /*if (getSupportActionBar() != null){
            getSupportActionBar()?.hide();
        }*/
        //隐藏状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        //////
        val editor = getSharedPreferences("data", Context.MODE_PRIVATE).edit()
        editor.putString("天空", "关于天空的文案")
        editor.apply()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1
            )
        }
        if (Build.VERSION.SDK_INT > 9) {
            val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
        }
        val client = AipImageClassify(APP_ID, API_KEY, SECRET_KEY)
        takePhotoBtn.setOnClickListener {
            // 创建File对象，用于存储拍照后的图片
            outputImage = File(externalCacheDir, "output_image.jpg")
            if (outputImage.exists()) {
                outputImage.delete()
            }
            outputImage.createNewFile()
            imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    this,
                    "com.example.cameraalbumtest.fileprovider",
                    outputImage
                );
            } else {
                Uri.fromFile(outputImage);
            }
            // 启动相机程序
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            startActivityForResult(intent, takePhoto)
        }
        fromAlbumBtn.setOnClickListener {
            // 打开文件选择器
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            // 指定只显示照片
            intent.type = "image/*"
            startActivityForResult(intent, fromAlbum)
        }
        upload.setOnClickListener {
            //上传图片
            val options: HashMap<String, String> = HashMap<String, String>()
            options["baike_num"] = "5"
            val client = AipImageClassify(APP_ID, API_KEY, SECRET_KEY)
            if (imagePath != null) {
                val res = client.advancedGeneral(imagePath, options)
                Log.d("test", res.toString(2))
                val key = res.getJSONArray("result").getJSONObject(0).getString("keyword")
                Log.d("test", key)
                val prefs = getSharedPreferences("data", Context.MODE_PRIVATE)
                val value = prefs.getString(key, "")
                Log.d("test", value)
                textView1.text = "检测到的对象：" + key
                textView2.text = "为您提供的文案：" + value

            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            takePhoto -> {
                if (resultCode == Activity.RESULT_OK) {
                    // 将拍摄的照片显示出来
                    val bitmap =
                        BitmapFactory.decodeStream(contentResolver.openInputStream(imageUri))
                    imageView.setImageBitmap(rotateIfRequired(bitmap))
                    imagePath = outputImage.getPath()
                }
            }
            fromAlbum -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    data.data?.let { uri ->
                        if (DocumentsContract.isDocumentUri(this, uri)) {
                            val docId = DocumentsContract.getDocumentId(uri)
                            if ("com.android.providers.media.documents" == uri.authority) { //Log.d(TAG, uri.toString());
                                val id = docId.split(":").toTypedArray()[1]
                                val selection =
                                    MediaStore.Images.Media._ID + "=" + id
                                imagePath = getImagePath(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    selection
                                )
                            } else if ("com.android.providers.downloads.documents" == uri.authority) { //Log.d(TAG, uri.toString());
                                val contentUri = ContentUris.withAppendedId(
                                    Uri.parse("content://downloads/public_downloads"),
                                    java.lang.Long.valueOf(docId)
                                )
                                imagePath = getImagePath(contentUri, null)
                            }
                        } else if ("content".equals(
                                uri.scheme,
                                ignoreCase = true
                            )
                        ) { //Log.d(TAG, "content: " + uri.toString());
                            imagePath = getImagePath(uri, null)
                        }
                        // 将选择的照片显示
                        val bitmap = getBitmapFromUri(uri)
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri) = contentResolver.openFileDescriptor(uri, "r")?.use {
        BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
    }

    private fun rotateIfRequired(bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(outputImage.path)
        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270)
            else -> bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotatedBitmap
    }

    private fun getImagePath(uri: Uri, selection: String?): String? {
        var path: String? = null
        val cursor: Cursor? = contentResolver.query(uri, null, selection, null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
            }
            cursor.close()
        }
        return path
    }

}



