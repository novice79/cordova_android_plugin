package freego.david;
import android.app.Activity;
import android.widget.Toast;
import android.annotation.SuppressLint;
import android.util.Base64;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;  
import android.graphics.BitmapFactory.Options;  
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import android.util.Log;
import android.os.Handler;
import android.os.Message;
import android.content.res.AssetManager; 


import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.itep.device.system.SystemInterface;

import com.itep.device.thermalPrinter.ThermalPrinterInterface;
import com.itep.device.thermalPrinter.ThremalPrinterException;
import com.itep.device.util.CreateBarCode;
import com.itep.device.util.HexDump;

import com.itep.device.barCode.BarCodeInterface;
import com.itep.device.barCode.barCodeReadAsyncArrayCallback;
import com.itep.device.bean.BarCode;

import com.itep.device.bean.IDCard;
import com.itep.device.idCard.IDCardInterface;
import static java.util.Arrays.copyOfRange;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
/**
 * This class echoes a string called from JavaScript.
 */
public class Pos extends CordovaPlugin {
    private SystemInterface systemInterface = new SystemInterface();
    
    private CordovaWebView mWebView;
    public static Handler handler;
    private IDCardInterface idCardInterface;
    private class QrHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    BarCode bc = (BarCode) msg.obj;
                    // final String qr_code = "errcode=" + bc.getErrCode() + "; data=" +  bc.getData() ;
                    // show( qr_code );           
                    final String qr_data = String.format(
                        "{\\\"ret\\\":%d,\\\"qr_code\\\":\\\"%s\\\"}", 
                        bc.getErrCode(), bc.getData() 
                    );
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            mWebView.sendJavascript(
                                String.format("javascript:on_qr(\"%s\");", qr_data)
                            );
                        }
                    });         
                    break;
            }
        }
    }
    public static String byteArrayToStr(byte[] byteArray) {
        if (byteArray == null) {
            return null;
        }
        String str = new String(byteArray);
        return str;
    }
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mWebView = webView;
        idCardInterface = new IDCardInterface();
        handler = new QrHandler();
    }
    private void show(String txt) {
        Toast.makeText(cordova.getActivity().getApplicationContext(),
                    txt, Toast.LENGTH_SHORT).show();
    }
    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("echo")) {
            String message = args.getString(0);
            this.echo("from java : " + message, callbackContext);            
            return true;
        } else if(action.equals("beep")) {
            systemInterface.beep(50, 0, 3);
            callbackContext.success(0);
            return true;
        } else if(action.equals("print")) {
            //tn, pri, ins, dt, qr, tid
            String tn = args.getString(0);
            String pri = args.getString(1);
            String ins = args.getString(2);
            String dt = args.getString(3);
            String qr = args.getString(4);
            String tid = args.getString(5);
            this.print(tn, pri, ins, dt, qr, tid, callbackContext);       
            // this.printSample();     
            // callbackContext.success();
            return true;
        } else if(action.equals("scan")) {
            int tm = args.getInt(0);
            this.scan(tm, callbackContext);            
            return true;
        } else if(action.equals("read_id")) {
            final int tm = args.getInt(0);
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    read_id(tm, callbackContext);    
                }
            });
               
            return true;
        } else if(action.equals("scan_by_camera")) {
            cordova.setActivityResultCallback (this);
            cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                    Context context = cordova.getActivity().getApplicationContext();
                    Intent intent=new Intent(context, Scanner.class);
                    cordova.getActivity().startActivityForResult(intent, 0);
                    // cordova.setActivityResultCallback (self);
                    // cordova.startActivityForResult(self, intent, 0);
                }
            });
            // callbackContext.success(); // Thread-safe.         
            return true;
        }  else if(action.equals("led")) {
            int color = args.getInt(0);
            int on_off = args.getInt(1);
            String para = String.format("%2x, %2x", color, on_off);
            // show(para);
            int ret = systemInterface.led(color, on_off, 3);    
            if( 0 == ret ) {
                callbackContext.success(ret);
            } else {
                callbackContext.error(ret);
            }
            return true;
        } else if(action.equals("exit")) {
            getActivity().finish();  
            System.exit(0);
            return true;
        }
        return false;
    }
    private void echo(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }
    
    private void read_id(final int tm, final CallbackContext callbackContext) {
        int retry_cnt = tm * 10;
        int ret = idCardInterface.open();
        Log.e("IDCard", "open --> ret = " + ret);        
        if (ret == 0) {
            IDCard idCard = idCardInterface.detect(1);
            ret = idCard.getErrCode();
            while( 0 != ret && --retry_cnt > 0) {
                idCard = idCardInterface.detect(1);
                ret = idCard.getErrCode();
                SystemClock.sleep(100);
            }
            Log.e("IDCard", "detect --> ret = " + HexDump.toHexString(ret));
            if (ret == 0) {
                idCard = idCardInterface.activeCard(5);
                ret = idCard.getErrCode();
                Log.e("IDCard", "activeCard --> ret = " + HexDump.toHexString(ret));
                if (ret == 0) {
                    idCard = idCardInterface.readCard(5);
                    ret = idCard.getErrCode();
                    Log.e("IDCard", "readCard --> ret = " + HexDump.toHexString(ret));
                    if (ret == 0) {
                        idCardInterface.close();
                        String str = idCard.getData();
                        //AAAAAA9669050800009001000400(14字节)+(256 字节文字信息)+(1024 字节 照片信息)+(1 字节 CRC)
                        Log.e("IDCard", str);
                        getIdCardDataBean(str, callbackContext);
                        return;
                    } 
                } 
            }
        } 
        callbackContext.error("读取身份证失败");
    }
    private void getIdCardDataBean(String dataStr, CallbackContext callbackContext) {
        byte[] data = hex2byte(dataStr);
        if (data.length >= 1295) {
            //1.文字信息处理
            byte[] idWordbytes = copyOfRange(data, 14, 270);
            try {
                String name = new String(copyOfRange(idWordbytes, 0, 30), "UTF-16LE").trim().trim();
                String gender = new String(copyOfRange(idWordbytes, 30, 32), "UTF-16LE").trim();
                String nation = new String(copyOfRange(idWordbytes, 32, 36), "UTF-16LE").trim();
                String birthday = new String(copyOfRange(idWordbytes, 36, 52), "UTF-16LE").trim();
                String address = new String(copyOfRange(idWordbytes, 52, 122), "UTF-16LE").trim();
                String idNo = new String(copyOfRange(idWordbytes, 122, 158), "UTF-16LE").trim();
                String issuingAuthority = new String(copyOfRange(idWordbytes, 158, 188), "UTF-16LE").trim();
                String startTime = new String(copyOfRange(idWordbytes, 188, 204), "UTF-16LE").trim();
                String stopTime = new String(copyOfRange(idWordbytes, 204, 220), "UTF-16LE").trim();
                Log.e("IDCard", "name = " + name);
                Log.e("IDCard", "gender = " + gender);
                Log.e("IDCard", "nation = " + decodeNation(Integer.parseInt(nation)));
                Log.e("IDCard", "birthday = " + birthday);
                Log.e("IDCard", "address = " + address);
                Log.e("IDCard", "idNo = " + idNo);
                Log.e("IDCard", "issuingAuthority = " + issuingAuthority);
                Log.e("IDCard", "startTime = " + startTime);
                Log.e("IDCard", "stopTime = " + stopTime);
                // show("idNo = " + idNo);
                final String id_info = String.format(
                    "{\"name\":\"%s\",\"gender\":\"%s\",\"nation\":\"%s\",\"birthday\":\"%s\",\"address\":\"%s\",\"idNo\":\"%s\",\"issuingAuthority\":\"%s\",\"startTime\":\"%s\",\"stopTime\":\"%s\"}", 
                    name, gender, decodeNation(Integer.parseInt(nation)), birthday, address,idNo, issuingAuthority, startTime, stopTime);
 
                callbackContext.success(id_info);
            } catch (UnsupportedEncodingException e) {
                callbackContext.error("读取身份证失败");
            }
        }

    }
    barCodeReadAsyncArrayCallback callback = new barCodeReadAsyncArrayCallback() {
        @Override
        public void callbackMethod(BarCode bc) {
            Message msg = new Message();
            msg.obj = bc;
            msg.what = 0;
            handler.sendMessage(msg);
        }
    };
    private void scan(int tm, CallbackContext callbackContext) {
        BarCodeInterface barCodeInterface = new BarCodeInterface();
        barCodeInterface.close();
        int ret = barCodeInterface.open(0);
        if(0 == ret) {
            ret = barCodeInterface.scan(callback, 1, tm);
            if(0 == ret) {
                callbackContext.success( ret );
            } else {
                callbackContext.error( ret );
            }
        } else {
            callbackContext.error( ret );
        }
    }
    private Activity getActivity() { return this.cordova.getActivity();}
    private void shortToast(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {
            Toast.makeText(cordova.getActivity().getApplicationContext(),
                    message, Toast.LENGTH_SHORT).show();
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    private void longToast(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {
            Toast.makeText(cordova.getActivity().getApplicationContext(),
                    message, Toast.LENGTH_LONG).show();
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }
    public void print(String tn, String pri, String ins, 
        String dt, String qr, String tid,
        CallbackContext callbackContext) {
        
        ThermalPrinterInterface printInterface  = new ThermalPrinterInterface();
        int ret = printInterface.open();
        CreateBarCode createBarCode = new CreateBarCode();
        try {
            AssetManager assetManager = cordova.getActivity().getApplicationContext().getAssets();
            Bitmap logo = BitmapFactory.decodeStream(assetManager.open("logo.bmp") );
            logo = Bitmap.createScaledBitmap(logo, 200, 200, true);
            printInterface.printBmp(logo, 90, 0, 5);
            ret = printInterface.feedPaper(50, 10);
            ret = printInterface.waitForResult();
            // 打印的模板
            printInterface.setFontSize(3);
            printInterface.setDoubleWH(2, 2);
            printInterface.setFontAlignment("center");
            String td = String.format( "票名：%s", tn );
            ret = printInterface.printStr(td, false, false, 5);
            ret = printInterface.feedPaper(5, 5);
            td = String.format( "价格：%s", pri );
            ret = printInterface.printStr(td, false, false, 5);
            ret = printInterface.feedPaper(5, 5);
            td = String.format( "含保险票：%s", ins );
            ret = printInterface.printStr(td, false, false, 5);
            ret = printInterface.feedPaper(5, 5);
            printInterface.setDoubleWH(1, 1);
            td = String.format( "有效期：%s", dt);
            ret = printInterface.printStr(td, false, false, 5);
            ret = printInterface.feedPaper(5, 5);           
            td = String.format( "交易号：%s", tid);
            ret = printInterface.printStr(td, false, false, 5);
            ret = printInterface.feedPaper(30, 5);
            ret = printInterface.waitForResult();            
            
            printInterface.printBmp(createBarCode.createQRCode(qr, 200, 200), 90, 0, 5);
            ret = printInterface.feedPaper(180, 10);
            ret = printInterface.waitForResult();
            
            if (ret != 0) {
                throw new ThremalPrinterException(ret);
            } else {
                callbackContext.success(0);
            }
        } catch (ThremalPrinterException e) {
            callbackContext.error( -1 );
            e.printStackTrace();
            printInterface.close();
        } catch (Exception e) {
            callbackContext.error( -1 );
            e.printStackTrace();
            printInterface.close();
        }
    }
    @Override  
    public void onActivityResult(int requestCode, int resultCode, Intent data) {  
        // show("in onActivityResult");        
        if (data != null) {
            final String qr_code = data.getStringExtra("qr_code");  
            final String format = data.getStringExtra("format");  
            final String qr_data = String.format(
                "{\\\"ret\\\":0,\\\"qr_code\\\":\\\"%s\\\",\\\"format\\\":\\\"%s\\\"}", 
                qr_code, format
            );
            // 根据上面发送过去的请求吗来区别  
            switch (requestCode) {  
            case 0:  
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        mWebView.sendJavascript(
                            String.format("javascript:on_qr(\"%s\");", qr_data)
                        );
                    }
                });   
                break;  
     
            default:  
                break;  
            } 
        } else {
            final String qr_data = "{\\\"ret\\\":-1}";
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    mWebView.sendJavascript(
                        String.format("javascript:on_qr(\"%s\");", qr_data)
                    );
                }
            });   
        }
         
    }  
    public void printSample() {
        ThermalPrinterInterface thermalPrinterInterface = new ThermalPrinterInterface();
        thermalPrinterInterface.open();

        CreateBarCode createBarCode = new CreateBarCode();
        int ret;
        try {
            // 打印的模板
            thermalPrinterInterface.printBmp(createBarCode.createBarCode("Printer Test", 380, 100), 0, 0, 5);

            thermalPrinterInterface.setFontSize(1);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("Printer Test", false, false, 5);

            thermalPrinterInterface.setFontSize(2);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("Printer Test", false, false, 5);


            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("Printer Test", false, false, 5);

            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            thermalPrinterInterface.setDoubleWH(2, 2);
            ret = thermalPrinterInterface.printStr("Printer Test", false, false, 5);

            thermalPrinterInterface.setDoubleWH(1, 1);
            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("Printer Test", true, false, 5);

            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("Printer Test", true, true, 5);

            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("Printer Test", false, true, 5);


            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("prueba de la impresora", false, false, 5);


            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("0123456789", false, false, 5);

            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("打印测试", false, false, 5);

            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("center");
            ret = thermalPrinterInterface.printStr("赋得古原草送别\n" +
                    "离离原上草 一岁一枯荣\n" +
                    "野火烧不尽 春风吹又生\n" +
                    "远芳侵古道 晴翠接荒城\n" +
                    "又送王孙去 萋萋满别情", false, false, 5);

            thermalPrinterInterface.setFontSize(3);
            thermalPrinterInterface.setFontAlignment("left");
            ret = thermalPrinterInterface.printStr("~@#￥%……&*（）《》？“：|{}。", false, false, 5);


            thermalPrinterInterface.printBmp(createBarCode.createQRCode("Printer Test", 200, 200), 90, 0, 5);

//            byte[] picByte = new byte[]{
//                    (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
//                    (byte) 0x0F, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x0F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00,
//                    (byte) 0x0F, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0xEF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x70, (byte) 0xF0, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF0, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF0, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF0, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF0, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF1, (byte) 0xCF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x3F, (byte) 0xFF, (byte) 0xEF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x3F, (byte) 0xFF, (byte) 0xEF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xFC, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xFE, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xFF, (byte) 0x8F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF7, (byte) 0xCF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF3, (byte) 0xCF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF3, (byte) 0xCF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF1, (byte) 0x8F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0xF0, (byte) 0xEF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x07, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00,
//                    (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x0F, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0x00,
//                    (byte) 0x0F, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00};

            byte[] picByte = new byte[48 * 32];
            for (int i = 0; i < picByte.length; i++) {
                if (i % 48 == 0) {
                    picByte[i] = (byte) 0x80;
                }

                if ((i + 1) % 48 == 0) {
                    picByte[i] = (byte) 0x01;
                }
            }

            ret = thermalPrinterInterface.waitForResult();
            ret = thermalPrinterInterface.bitmapPrint(picByte, 384, 32, 5); //不要超过384

            Log.e("打印", "打印点阵信息返回值：" + HexDump.toHexString(ret));

            ret = thermalPrinterInterface.feedPaper(150, 10);
            ret = thermalPrinterInterface.waitForResult();
            if (ret != 0) {
                throw new ThremalPrinterException(ret);
            } else {
                // tvResult.setText("print success");
            }
        } catch (ThremalPrinterException e) {
            // tvResult.setText("Print failed:" + getErrorMsg(e.getErrorCode()));
            e.printStackTrace();
            thermalPrinterInterface.close();
        } catch (Exception e) {
            e.printStackTrace();
            thermalPrinterInterface.close();
        }
    }
    /**
     * 根据错误码获取错误信息
     * m.0 0=有纸1=无纸
     * m.1 0=不卡纸1=卡纸
     * m.2 0=正常1=不正常
     * m.3 0=热敏头温度正常1=热敏头超温
     * m.4 0=切刀正常1=切刀出错
     * m.5 0=黑标正常1=黑标出错
     * m.6 0=热敏头正常1=热敏头被抬起
     * m.7 0=电源正常1=电源过低
     */
     public static byte[] hex2byte(String str) { // 字符串转二进制
        if (str == null)
            return null;
        str = str.trim();
        int len = str.length();
        if (len == 0 || len % 2 == 1)
            return null;
        byte[] b = new byte[len / 2];
        try {
            for (int i = 0; i < str.length(); i += 2) {
                b[i / 2] = (byte) Integer.decode("0X" + str.substring(i, i + 2)).intValue();
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }
    private String decodeNation(int code) {
        String nation;
        switch (code) {
            case 1:
                nation = "汉";
                break;
            case 2:
                nation = "蒙古";
                break;
            case 3:
                nation = "回";
                break;
            case 4:
                nation = "藏";
                break;
            case 5:
                nation = "维吾尔";
                break;
            case 6:
                nation = "苗";
                break;
            case 7:
                nation = "彝";
                break;
            case 8:
                nation = "壮";
                break;
            case 9:
                nation = "布依";
                break;
            case 10:
                nation = "朝鲜";
                break;
            case 11:
                nation = "满";
                break;
            case 12:
                nation = "侗";
                break;
            case 13:
                nation = "瑶";
                break;
            case 14:
                nation = "白";
                break;
            case 15:
                nation = "土家";
                break;
            case 16:
                nation = "哈尼";
                break;
            case 17:
                nation = "哈萨克";
                break;
            case 18:
                nation = "傣";
                break;
            case 19:
                nation = "黎";
                break;
            case 20:
                nation = "傈僳";
                break;
            case 21:
                nation = "佤";
                break;
            case 22:
                nation = "畲";
                break;
            case 23:
                nation = "高山";
                break;
            case 24:
                nation = "拉祜";
                break;
            case 25:
                nation = "水";
                break;
            case 26:
                nation = "东乡";
                break;
            case 27:
                nation = "纳西";
                break;
            case 28:
                nation = "景颇";
                break;
            case 29:
                nation = "柯尔克孜";
                break;
            case 30:
                nation = "土";
                break;
            case 31:
                nation = "达斡尔";
                break;
            case 32:
                nation = "仫佬";
                break;
            case 33:
                nation = "羌";
                break;
            case 34:
                nation = "布朗";
                break;
            case 35:
                nation = "撒拉";
                break;
            case 36:
                nation = "毛南";
                break;
            case 37:
                nation = "仡佬";
                break;
            case 38:
                nation = "锡伯";
                break;
            case 39:
                nation = "阿昌";
                break;
            case 40:
                nation = "普米";
                break;
            case 41:
                nation = "塔吉克";
                break;
            case 42:
                nation = "怒";
                break;
            case 43:
                nation = "乌孜别克";
                break;
            case 44:
                nation = "俄罗斯";
                break;
            case 45:
                nation = "鄂温克";
                break;
            case 46:
                nation = "德昂";
                break;
            case 47:
                nation = "保安";
                break;
            case 48:
                nation = "裕固";
                break;
            case 49:
                nation = "京";
                break;
            case 50:
                nation = "塔塔尔";
                break;
            case 51:
                nation = "独龙";
                break;
            case 52:
                nation = "鄂伦春";
                break;
            case 53:
                nation = "赫哲";
                break;
            case 54:
                nation = "门巴";
                break;
            case 55:
                nation = "珞巴";
                break;
            case 56:
                nation = "基诺";
                break;
            case 97:
                nation = "其他";
                break;
            case 98:
                nation = "外国血统中国籍人士";
                break;
            default:
                nation = "";
                break;
        }
        return nation;
    }
   
}
