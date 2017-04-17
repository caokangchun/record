package com.example.ckc.record;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CAO";
    private static final int OVER_SHRESHOLD = 1;
    private static final int UNDER_SHRESHOLD = 0;
    private static final int CALLPHONE = 1;
    private static final int MESPHONE = 2;
    private static final int RECORDAUDIO = 3;
    private static final int WRITEEXTERNAL = 4;

    private TextView recordState;
    private TextView voiceDB;
    private RadioButton messageMother;
    private RadioButton callMother;

    private Button recordPR;
    private ExecutorService mExecutorService;
    private MediaRecorder mMediaRecorder;
    private File recordFile;
    private long timeRecordStart;
    private long timeRecordEnd;
    Handler  mainHandle;
    private boolean btnPress = false;
    private int cry = 0;
    private int notCry = 0;
    private EditText threshold;
    private EditText phoneNo;
    private RadioGroup radioGroup;
    String getNo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandle = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                DecimalFormat dec = new DecimalFormat("###.00");
                voiceDB.setText(dec.format(msg.getData().getDouble("DB_DISPLAY"))+"dB");
                switch(msg.arg1)
                {
                    case OVER_SHRESHOLD:
                        recordState.setText(recordState.getText() + "\n超过阈值");
                        getNo = phoneNo.getText().toString();
                        //停止录音
                        stopRecord();
                        stopDB();

                        //复位按钮状态
                        btnPress = !btnPress;
                        /*获得checkbox状态，打电话 或者  发信息*/
                        if(callMother.isChecked()) {
                            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.CALL_PHONE))//是否需要解释
                                {
                                    Toast.makeText(MainActivity.this, "需要打电话权限", Toast.LENGTH_SHORT).show();
                                }
//                              //申请权限
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CALL_PHONE}, CALLPHONE);
                            }
                            else
                            {
                                //打电话
                                //直接拨打电话
                                Uri uri = Uri.parse("tel:" + getNo);
                                Intent call = new Intent(Intent.ACTION_CALL, uri); //直接播出电话
//                                Intent call = new Intent(Intent.ACTION_DIAL, uri); //显示拨打号码，未播出
                                recordState.setText(recordState.getText() + "\n拨打电话");
                                startActivity(call);
                            }
                        }
                        else if(messageMother.isChecked())
                        {
                            if(PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.SEND_SMS))
                            {
                                if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,Manifest.permission.SEND_SMS))//是否需要解释
                                {
                                    Toast.makeText(MainActivity.this,"需要发信息权限",Toast.LENGTH_SHORT).show();
                                }
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.SEND_SMS},MESPHONE);

                            }
                            else
                            {
                                recordState.setText(recordState.getText() + "\n发短信");
                                doSendSMSTo(getNo,"testing---你宝宝发短信给你");
                            }
                        }
                        break;

                    case UNDER_SHRESHOLD:
                        if(msg.what == 1)
                        recordState.setText(recordState.getText() + "\n" + 100*msg.getData().getDouble("CRY_PERCENT") + "%");
                        break;

                    default: break;
                }
            }
        };
        /*获取控件*/
        recordState = (TextView) findViewById(R.id.record_state);
        voiceDB = (TextView) findViewById(R.id.db);
        messageMother = (RadioButton) findViewById(R.id.message_mother);
        callMother= (RadioButton) findViewById(R.id.call_mother);
        recordPR = (Button) findViewById(R.id.record_press_release);
        /*界面初始化*/
        recordState.setText(R.string.waiting);
        recordPR.setText(R.string.press_to_say);
        phoneNo = (EditText) findViewById(R.id.phone_input);
        threshold =(EditText) findViewById(R.id.db_value);
        threshold.setKeyListener(new DigitsKeyListener(false,true));//只输入数值
        phoneNo.setKeyListener(new DigitsKeyListener(false,true));
        radioGroup = (RadioGroup) findViewById(R.id.radio_group);
        RadioCheckCKC radioCheckCKC = new RadioCheckCKC();
        radioGroup.setOnCheckedChangeListener(radioCheckCKC);

        /*SharedPreferences读取文件，查看是否有配置好的数据
        * 有则把SharedPref文件的内容读入，不用每次启动应用都要手动配置*/
        SharedPreferences sharePreferences = getSharedPreferences("SharedPref",MODE_PRIVATE);   //新建文件以及对象
        String thresholdVal = sharePreferences.getString("Threshold","none");//获取声音检测阈值
        String telNo = sharePreferences.getString("TelNo","none");//获取联系人号码
        boolean call = sharePreferences.getBoolean("Call",false);//打电话或者发信息
        if(thresholdVal != "none")  //读取，设置阈值
        {
            threshold.setText(thresholdVal);
        }
        if(telNo != "null")//读取，设置联系人电话
        {
            phoneNo.setText(telNo);
        }
        if(call)//读取，设置动作
        {
            radioGroup.check(R.id.call_mother);
        }
        else
        {
            radioGroup.check(R.id.message_mother);
        }

//        SharedPreferences sharedPreferences = getSharedPreferences("share_pre",MODE_PRIVATE);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString("test_c","kangchun");
//        editor.commit();
//        Log.d(TAG, sharedPreferences.getString("test_c","cuole"));

        //实例化ExecutorService   ??
//        mExecutorService = Executors.newSingleThreadExecutor();
//        mExecutorService = Executors.newCachedThreadPool();
//        mExecutorService = Executors.newFixedThreadPool(5);
//        recordPR.append("\ntesting");/////////////////////////////////加了这句出现闪退？待解决
//        Log.d(TAG, " append");


        /*recordPR.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction())
                {
                    //按下，开始录音
                    case MotionEvent.ACTION_DOWN:
                        startRecord();
//                        upDateDB();   //此处容易引起空指针异常
                        break;

                    //释放，停止录音，且少于1秒钟不保存
                    case MotionEvent.ACTION_UP:
                        stopRecord();
                        break;

                    default:
                        break;
                }
                return false;
            }
        });*/
        recordPR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!btnPress)
                {
                    /*申请RECORD_AUDIO，writeExternal权限*/
                    //如果没权限
                    //申请权限

//                    if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
//                    {
//                        ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.RECORD_AUDIO},RECORDAUDIO);
//                    }
//                    //AUDIORECORD允许才继续申请权限，否则
//                    //此处在选择对话框打开之前就已经执行，显然逻辑不对
//                    if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
//                    {
//                        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
//                        {
//                            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},WRITEEXTERNAL);
//                        }
//                        else
//                        {
//                            startRecord();
//                        }
//                    }
//                    else
//                    {
//                        //不录音
//                        btnPress = !btnPress;
//                    }

                    /*两个权限按顺序申请很容易不同步，应该两个权限一起申请*/
                    if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                                    PackageManager.PERMISSION_GRANTED)
                    {
                        ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO},WRITEEXTERNAL);
                    }
                    else
                    {
                        startRecord();
                    }
                }
                else
                {
                    //停止检测录音与声音检测
                    stopRecord();
                    stopDB();
                }
                //改变按钮状态，使下次点击调到if
                btnPress = !btnPress;
            }
        });
    }

    private void stopDB() {
        mExecutorService.shutdown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //activity销毁时停止后台任务
        mExecutorService.shutdownNow();
        if(mMediaRecorder != null)
        {
            mMediaRecorder.release();
        }
        Log.d(TAG, "onDestroy: ");

        /*系统有可能直接杀死进程而不调用onDestroy()
        * 所以不能再Destroy中保存数据*/
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: ");
        /*系统有可能直接杀死进程而不调用onDestroy()
        * 所以不能再Destroy中保存数据
        * 选择onPause()*/

        /*需要保存配置信息*/
        SharedPreferences sharePreferences = getSharedPreferences("SharedPref",MODE_PRIVATE);   //新建文件以及对象
        SharedPreferences.Editor editor = sharePreferences.edit();  //新建编辑对象
        editor.putString("Threshold",threshold.getText().toString());   //保存阈值
        editor.putString("TelNo",phoneNo.getText().toString());         //保存号码
        if(radioGroup.getCheckedRadioButtonId() == R.id.call_mother)    //保存动作
        {
            editor.putBoolean("Call",true);
        }
        else
        {
            editor.putBoolean("Call",false);
        }
        editor.commit();    //保存更改  commit
    }

    /*开始录音*/
    private void startRecord() {
        //ui显示正在录音
        recordState.setText(R.string.speaking);
        recordPR.setText(R.string.record_end);

        mExecutorService = Executors.newFixedThreadPool(5);

        /*提交后台任务，以免阻塞ui*/
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
//                try{
//                    Handler handler = new Handler();
//
//                }
//                catch(Exception e)
//                {
//                    e.printStackTrace();
//                }

//                Handler handler = new Handler(Looper.getMainLooper()){
//                    @Override
//                    public void handleMessage(Message msg) {
//                        super.handleMessage(msg);
//                        Log.d(TAG, "handleMessage: "+Thread.currentThread().getName());
//                    }
//                };
//                handler.sendEmptyMessage(1);
//                Log.d(TAG, "handler send message");

                //释放MediaRecorder资源
                if (mMediaRecorder != null)
                {
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                }

                //新建文件保存录音
                Log.d(TAG, "state "+ Environment.getExternalStorageState());

                try {
                    recordFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/ckc/Demo/" + System.currentTimeMillis() +".m4a");

                    Log.d(TAG, "directory: " +recordFile.getParentFile());
                    boolean test = recordFile.getParentFile().mkdirs();//mkdirs返回了false，查原因

                    Log.d(TAG, "create directory: " +test);

                    test = recordFile.createNewFile();
                    Log.d(TAG, "create file: " +test);
                    Log.d(TAG, recordFile.getAbsolutePath());

                    //配置mmediaRecorder
                    mMediaRecorder = new MediaRecorder();
                    Log.d(TAG, "MediaRecoder instance");
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mMediaRecorder.setAudioSamplingRate(44100);
                    mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    mMediaRecorder.setAudioEncodingBitRate(96000);
                    mMediaRecorder.setOutputFile(recordFile.getAbsolutePath());
                    mMediaRecorder.prepare();

                    //记录开始时间
                    timeRecordStart = System.currentTimeMillis();
                    mMediaRecorder.start(); //开始录音

                    //更新声音大小   xxdB
                    upDateDB();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "record start Exception occurs");
                }
                catch (Exception e){
            Log.d(TAG, "record setAudioSource Exception occurs");
        }
    }
});
    }

    Runnable runUpdate = new Runnable() {
        @Override
        public void run() {
            /*子handler MainLooper 测试代码*/
//            Handler handler = new Handler(Looper.getMainLooper()){
//                @Override
//                public void handleMessage(Message msg) {
//                    super.handleMessage(msg);
//                    //经过测试，子线程中，通过MainLooper新建handler，handler发送消息时，时在main线程执行的
//                    Log.d(TAG, "handleMessage: "+Thread.currentThread().getName());
//                    recordState.setText(recordState.getText() + "\ntesting");
//                }
//            };
//            handler.sendEmptyMessage(1);
//            Log.d(TAG, "handler send message");


            Log.d(TAG, Thread.currentThread().getName() + "     " +((ThreadPoolExecutor)mExecutorService).getPoolSize());
            /*
            * 在主线程调用upDateDB()时，很可能此句先执行，导致空指针异常
            * java.lang.NullPointerException: Attempt to invoke virtual method 'int android.media.MediaRecorder.getMaxAmplitude()' on a null object reference
            * */
            if(mMediaRecorder !=null)
            {
                final double voiceBase=0.018;   //网上下载分贝仪 粗略估计一个基准
                double voice=0;
                DecimalFormat dec = new DecimalFormat("###.00");
//                for(int i=0;i<10;i++)
//                {
//                    voice += mMediaRecorder.getMaxAmplitude();
//                    Log.d(TAG, i+"add");
//                    try {
//                        Thread.sleep(50);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                        Log.d(TAG, "Thread sleep Exception occurs");
//                    }
//                }
//                voice/=10;
                voice = mMediaRecorder.getMaxAmplitude();

                if(voice == 0)
                {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.d(TAG, "Thread sleep Exception occurs1");
                    }
                    voice = mMediaRecorder.getMaxAmplitude();
                }



                try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Log.d(TAG, "Thread sleep Exception occurs2");
                    }
                double valueDB = 10*Math.log10(voice/voiceBase);
                double detect=0;
//                voiceDB.setText(dec.format(valueDB)+"dB");
//                voiceDB.setText(voice);   //此处整形数 是resource 资源里的  不是随意一个整形数
//                mainHandle.postDelayed(this,500);//500ms

                /*给主线程发送message 更新UI*/
                    //获取或新建message
                Message message =mainHandle.obtainMessage();
                /*此处验证获得的message对象，是否是曾经获得过的*/
                Log.d(TAG, "get Message.arg1:" + message.arg1);
                Log.d(TAG, "get Message.what:" + message.what);
                    //新建bundle 用于传送数据valueDB
                Bundle bundle = new Bundle();
                    //放置数据通过"DB_DISPLAY"-->valueDB
                bundle.putDouble("DB_DISPLAY",valueDB);
                /*若超出阈值，callMother*/

                String editable = threshold.getText().toString();
                if(!threshold.getText().toString().equals(""))   //若输入不为空
                {
                    if(valueDB > Double.parseDouble(threshold.getText().toString()))
                    {
                        cry++;
                    }
                    else
                    {
                        if(cry>0)
                        {
                            notCry++;
                        }
                    }

                    if(cry+notCry >=10)     //3s
                    {
                        detect = (double) cry/(cry+notCry);
                        cry=0;
                        notCry=0;

                        if(detect>0.7)
                        {
                            message.arg1 = OVER_SHRESHOLD;
                            detect=0;
                            message.what = 0;
                        }
                        else
                        {
                            //发送detect更新ui
                            message.arg1 = UNDER_SHRESHOLD;
                            bundle.putDouble("CRY_PERCENT",detect);
                            message.what = 1;
                        }
                    }
                }

                message.setData(bundle);
                    //发送消息
                mainHandle.sendMessage(message);
                mExecutorService.submit(this);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void upDateDB() {
//        mainHandle.post(runUpdate);
        mExecutorService.submit(runUpdate);
    }

    /*停止录音*/
    private void stopRecord() {

        //录音是否成功
        if(recordSuccessOrNot()){
            //提示成功
            recordState.setText(recordState.getText() + "\n录音成功");
        }
        else{
            //提示失败
            recordState.setText(recordState.getText() + "\n录音失败");
            recordFile.delete();
            recordState.setText(recordState.getText() + "\n文件删除");
        }
        mainHandle.removeCallbacks(runUpdate);

        recordPR.setText(R.string.press_to_say);
//        recordState.setText(R.string.waiting);
        recordState.append("\nwaiting");
        //释放录音资源
        if(mMediaRecorder != null)
        {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }

    }

    private boolean recordSuccessOrNot() {
        //停止录音
        try
        {
            mMediaRecorder.stop();
        }
        catch (RuntimeException e)
        {
            e.printStackTrace();
            Toast.makeText(MainActivity.this,"无效操作",Toast.LENGTH_SHORT).show();
            return false;
        }
        //记录停止时间，且计算录音时长，低于1s录音失败
        timeRecordEnd = System.currentTimeMillis();
        if((timeRecordEnd-timeRecordStart)/1000 >= 1)
        {
            return true;
        }
        Toast.makeText(MainActivity.this,"录音时长<1s",Toast.LENGTH_SHORT).show();
        return false;
    }

    public void doSendSMSTo(String phoneNumber,String message){
        if(PhoneNumberUtils.isGlobalPhoneNumber(phoneNumber)){
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"+phoneNumber));
            intent.putExtra("sms_body", message);
            startActivity(intent);
        }
    }

    private void doSendMessage(String phoneNum,String msg)
    {
        SmsManager smsManager = SmsManager.getDefault();

    }

    class RadioCheckCKC implements RadioGroup.OnCheckedChangeListener
    {
        @Override
        public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
            if(phoneNo.getText().toString().equals(""))
            {
                Toast.makeText(MainActivity.this,"请输入联系电话",Toast.LENGTH_SHORT).show();
                radioGroup.setOnCheckedChangeListener(null);
                radioGroup.clearCheck();    //清除选中
                radioGroup.setOnCheckedChangeListener(this);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode)
        {
            case CALLPHONE:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    recordState.setText(recordState.getText() + "\n电话权限允许");
                    //打电话
                    //直接拨打电话
                    Uri uri = Uri.parse("tel:" + getNo);
                    Intent call = new Intent(Intent.ACTION_CALL, uri); //直接播出电话
//                        Intent call = new Intent(Intent.ACTION_DIAL, uri); //显示拨打号码，未播出
                    recordState.setText(recordState.getText() + "\n拨打电话");
                    startActivity(call);
                }
                else
                {
                    recordState.setText(recordState.getText() + "\n电话权限禁止");
                }
                break;

            case MESPHONE:
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    recordState.setText(recordState.getText() + "\n信息权限允许");
                    recordState.setText(recordState.getText() + "\n发短信");
                    doSendSMSTo(getNo,"testing---你宝宝发短信给你");
                }
                else
                {
                    recordState.setText(recordState.getText() + "\n信息权限禁止");
                }
                break;

            case RECORDAUDIO:
                break;

            case WRITEEXTERNAL:
//                if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
//                {
//                    recordState.setText(recordState.getText() + "\n写外部文件权限允许");
//                    startRecord();
//                }
//                else
//                {
//                    recordState.setText(recordState.getText() + "\n写外部文件权限禁止");
//                }
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED)
                {
                    startRecord();
                }
                else
                {
                    recordState.setText(recordState.getText() + "\n权限禁止");
                    btnPress = !btnPress;
                }
                break;
        }
    }
}
