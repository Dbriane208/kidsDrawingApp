package daniel.brian.kidsdrawingapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var drawingView : DrawingView? = null
    private var mImageButtonCurrentPaint : ImageButton? = null

    private val openGalleryLauncher : ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()){
        result ->
        if(result.resultCode == RESULT_OK && result.data != null){
            val backgroundImage = findViewById<ImageView>(R.id.backgroundImage)
            backgroundImage.setImageURI(result.data?.data)
        }
    }

    private val requestPermission : ActivityResultLauncher<Array<String>> = registerForActivityResult(
     ActivityResultContracts.RequestMultiplePermissions()){
        permissions ->
        permissions.entries.forEach{
            val permissionName = it.key
            val isGranted = it.value
            if (isGranted){
                Toast.makeText(this@MainActivity,"Permission granted for storage files.",Toast.LENGTH_SHORT).show()
                val pickIntent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }else{
                if(permissionName == Manifest.permission.READ_MEDIA_IMAGES){
                    Toast.makeText(this@MainActivity,"Oops you just Denied the permission.",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        drawingView?.setSizeForBrush(20.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.paintColorLayout)
        mImageButtonCurrentPaint = linearLayoutPaintColors[2] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )

        val btnBrush = findViewById<ImageButton>(R.id.btnBrush)
        btnBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val btnSetBackgroundImage = findViewById<ImageButton>(R.id.btnSetBackgroundImage)
        btnSetBackgroundImage.setOnClickListener {
            requestStoragePermission()
        }

        val btnRedo = findViewById<ImageButton>(R.id.btnRedo)
        btnRedo.setOnClickListener {
            drawingView?.onClickRedoPath()
        }

        val btnUndo = findViewById<ImageButton>(R.id.btnUndo)
        btnUndo.setOnClickListener {
            drawingView?.onClickUndoPath()
        }

        val btnSave = findViewById<ImageButton>(R.id.btnSave)
        btnSave.setOnClickListener {
            if(isReadStorageAllowed()) {
               lifecycleScope.launch{
                 val flDrawingView : FrameLayout = findViewById(R.id.frameLayoutBackground)
                   saveBitmapFile(getBitmapFromView(flDrawingView))
               }
            }
        }
    }
    private fun showBrushSizeChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size : ")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.smallBrush)
        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }

        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.mediumBrush)
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.largeBrush)
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View){
        if (view != mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }
    }
    @SuppressLint("SuspiciousIndentation")
    private fun showRationaleDialog(title : String, message : String){

     val builder : AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("cancel"){dialog, _->
                dialog.dismiss() }
        builder.create().show()
    }

    @SuppressLint("InlinedApi")
    private  fun isReadStorageAllowed() : Boolean{
        val result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_IMAGES)
        return result == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("InlinedApi")
    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity,Manifest.permission.READ_MEDIA_IMAGES)){
            showRationaleDialog("Kids Drawing App","Kids Drawing App need permission to Access your External storage")
        }else{
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun getBitmapFromView(view : View) : Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap : Bitmap?) : String{
        var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap != null){
                try{
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)
                    val createFile = File(externalCacheDir?.absoluteFile.toString() + File.separator + "kidsDrawingApp_"
                    + System.currentTimeMillis()/1000 + ".png")
                    val createFileOutput = FileOutputStream(createFile)
                    createFileOutput.write(bytes.toByteArray())
                    createFileOutput.close()

                    result = createFile.absolutePath

                    runOnUiThread{
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,"File saved successfully : $result",Toast.LENGTH_SHORT).show()
                        }else{
                            Toast.makeText(this@MainActivity,"Something went wrong while saving the file.",Toast.LENGTH_SHORT).show()
                        }
                    }
                }catch (e : Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }
}