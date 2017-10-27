package freego.david;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;  
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.Toast;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import com.google.zxing.Result;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class Scanner extends Activity implements ZXingScannerView.ResultHandler {
    private ZXingScannerView mScannerView;
    private int resultCode = 0;  
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        mScannerView = new ZXingScannerView(this);   // Programmatically initialize the scanner view
        mScannerView.setAspectTolerance(0.5f);
        setContentView(mScannerView);          
    }

    @Override
    public void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
        mScannerView.setAutoFocus(true);
        mScannerView.setFlash(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();
    }

    @Override
    public void handleResult(Result rawResult) {
        // try {
        //     Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        //     Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
        //     r.play();
        // } catch (Exception e) {}
        // Toast.makeText(this, "Contents = " + rawResult.getText() +
        //         ", Format = " + rawResult.getBarcodeFormat().toString(), Toast.LENGTH_SHORT).show();

        // If you would like to resume scanning, call this method below:
        // mScannerView.resumeCameraPreview(this);
        Intent mIntent = new Intent();  
        mIntent.putExtra("qr_code", rawResult.getText());  
        mIntent.putExtra("format", rawResult.getBarcodeFormat().toString() );  
        // 设置结果，并进行传送  
        this.setResult(resultCode, mIntent);  
        this.finish();  
    }
}
