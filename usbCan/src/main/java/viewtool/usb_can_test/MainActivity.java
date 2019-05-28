package viewtool.usb_can_test;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.viewtool.Ginkgo.ControlCAN;
import com.viewtool.Ginkgo.GinkgoDriver;

import java.util.Timer;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "viewtool.usb_can_test.USB_PERMISSION";

    EditText printView;
    GinkgoDriver mGinkgoDriver;
    UsbManager mUsbManager;
    UsbDevice mUsbDevice;
    PendingIntent pendingIntent;
    ControlCAN.VCI_CAN_OBJ CAN_ReadDataBuffer[] =  new ControlCAN.VCI_CAN_OBJ[1024];

    // 要打开的can 索引号
    static final  byte openCanIndex = 0;

    Handler sendMessageHandle;
    
    private static final String TAG = "Can_Log";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        config_usb();

        printView = (EditText)findViewById(R.id.printTextView);
        Button button =(Button)findViewById(R.id.start);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                printView.clearComposingText();
                printView.setText("");
                int ret;

                if(mUsbDevice !=null){
                    mGinkgoDriver.ControlCAN.VCI_ClearBuffer(openCanIndex);
                    mGinkgoDriver.ControlCAN.VCI_LogoutReceiveCallback();
                    mGinkgoDriver.ControlCAN.VCI_CloseDevice();
                }

                // Scan device
                mUsbDevice = mGinkgoDriver.ControlCAN.VCI_ScanDevice();
                if(mUsbDevice == null){
                    printView.append("No device connected");
                    return;
                }
                //注册收消息；
                mGinkgoDriver.ControlCAN.VCI_RegisterReceiveCallback(new GetCanDataHandle());
                // Open device
                ret = mGinkgoDriver.ControlCAN.VCI_OpenDevice();
                if(ret != mGinkgoDriver.ErrorType.ERR_SUCCESS){
                    printView.append("Open device error!\n");
                    printView.append(String.format("Error code: %d\n",ret));
                    return;
                }else{
                    printView.append("Open device success!\n");
                }

                //配置can 适配器
                ret = configCan(mGinkgoDriver.ControlCAN);
                if(ret != mGinkgoDriver.ErrorType.ERR_SUCCESS){
                    printView.append("Init device failed!\n");
                    printView.append(String.format("Error code: %d\n",ret));
                    return;
                }else{
                    printView.append("Init device success!\n");
                }

                //Set filter
               ret = MainActivity.canSetFilter(mGinkgoDriver.ControlCAN,openCanIndex);

                if(ret != mGinkgoDriver.ErrorType.ERR_SUCCESS){
                    printView.append("Set filter failed!\n");
                    printView.append(String.format("Error code: %d\n",ret));
                    return;
                }else{
                    printView.append("Set filter success!\n");
                }

                // Start CAN
                ret = mGinkgoDriver.ControlCAN.VCI_StartCAN(openCanIndex);
                if(ret != mGinkgoDriver.ErrorType.ERR_SUCCESS){
                    printView.append("Start CAN failed!\n");
                    printView.append(String.format("Error code: %d\n",ret));
                    return;
                }else{
                    printView.append("Start CAN success!\n");
                }

                if(sendMessageHandle == null) {
                    HandlerThread thread = new HandlerThread("Can_Send_Data_Thread");
                    thread.start();
                    sendMessageHandle = new Handler(
                            thread.getLooper()){
                        @Override
                        public void handleMessage(Message msg) {
                            if(msg.what == 1){
                                canSendData();
                            }else if(msg.what == 2){
                                canSendData();
                            }
                            super.handleMessage(msg);
                        }
                    };


                    sendMessageHandle.sendEmptyMessage(1);
                }
            }
        });
    }

    /**
     * 配置can适配器的过滤器
     * @param controlCAN
     * @return
     */
    public static int canSetFilter(ControlCAN controlCAN,byte openCanIndex)
    {
        ControlCAN.VCI_FILTER_CONFIG CAN_FilterConfig = controlCAN.new VCI_FILTER_CONFIG();
        CAN_FilterConfig.FilterIndex = 0;
        CAN_FilterConfig.Enable = 1;//Enable
        CAN_FilterConfig.ExtFrame = 0;
        CAN_FilterConfig.FilterMode = 0;
        CAN_FilterConfig.ID_IDE = 0;
        CAN_FilterConfig.ID_RTR = 0;
        CAN_FilterConfig.ID_Std_Ext = 0;
        CAN_FilterConfig.MASK_IDE = 0;
        CAN_FilterConfig.MASK_RTR = 0;
        CAN_FilterConfig.MASK_Std_Ext = 0;
        int ret = controlCAN.VCI_SetFilter(openCanIndex, CAN_FilterConfig);
        return ret;
    }

    /**
     *  配置 can 总线的速率 等等
     *  @param controlCAN
     *  @return
     */
    public static int  configCan(ControlCAN controlCAN)
    {
        ControlCAN.VCI_INIT_CONFIG_EX CAN_InitEx = controlCAN.new VCI_INIT_CONFIG_EX();
        CAN_InitEx.CAN_ABOM = 0;//Automatic bus-off management
        // Loop back
        CAN_InitEx.CAN_Mode = 0;
        boolean use500KB = true;
        if(use500KB){
            // 500k
            CAN_InitEx.CAN_BRP = 12;
            CAN_InitEx.CAN_BS1 = 4;
            CAN_InitEx.CAN_BS2 = 1;
            CAN_InitEx.CAN_SJW = 1;
        }else {
            //1Mbps
            CAN_InitEx.CAN_BRP = 6;
            CAN_InitEx.CAN_BS1 = 3;
            CAN_InitEx.CAN_BS2 = 2;
            CAN_InitEx.CAN_SJW = 1;
        }

        CAN_InitEx.CAN_NART = 1;//No automatic retransmission
        CAN_InitEx.CAN_RFLM = 0;//Receive FIFO locked mode
        CAN_InitEx.CAN_TXFP = 0;//Transmit FIFO priority
        CAN_InitEx.CAN_RELAY = 0;
        int  ret = controlCAN.VCI_InitCANEx(openCanIndex, CAN_InitEx);
        return  ret;
    }


    public void canSendData(){
        sendMessageHandle.removeMessages(2);
        int ret = 0 ;
        ControlCAN.VCI_CAN_OBJ CAN_SendData[] = new ControlCAN.VCI_CAN_OBJ[2];
        for (int i = 0; i < CAN_SendData.length; i++) {
            CAN_SendData[i] = mGinkgoDriver.ControlCAN.new VCI_CAN_OBJ();
            CAN_SendData[i].DataLen = 8;
            byte[] data = new byte[8];
            data[0] = (byte)0x02;
            data[1] = (byte)0x01;
            data[2] = (byte)0x0c;


            CAN_SendData[i].Data = data;
//                        for (int j = 0; j < CAN_SendData[i].DataLen; j++) {
//                            CAN_SendData[i].Data[j] = (byte) (i + j);
//                        }
            CAN_SendData[i].ExternFlag = 0;
            CAN_SendData[i].RemoteFlag = 0;
            CAN_SendData[i].ID = 0x7DF;
            CAN_SendData[i].SendType = 2;
            CAN_SendData[i].SendType = 0;
        }
        ret = mGinkgoDriver.ControlCAN.VCI_Transmit(openCanIndex, CAN_SendData, CAN_SendData.length);
        if (ret != mGinkgoDriver.ErrorType.ERR_SUCCESS) {
            //printView.append("Send CAN data failed!\n");
            //printView.append(String.format("Error code: %d\n", ret));
            Log.d(TAG,"Send CAN data failed! code:"+ret);
        } else {
            //printView.append("Send CAN data success!\n");
            Log.d(TAG,"Send CAN data success");
        }
        //5s消息超时
        sendMessageHandle.sendEmptyMessageDelayed(2,5000);
    }


    public class GetCanDataHandle implements ControlCAN.PVCI_RECEIVE_CALLBACK{

        //存储日志信息；
        private  final StringBuilder strB = new StringBuilder();;
        /**
         *
         * @param channel 0 or 1 -> channel0 channel1
         * @param DataNum Received data
         */
        public void ReceiveCallback(byte channel, int DataNum) {
            Log.d(TAG,"ReceiveCallback length :"+DataNum);
            if(DataNum > 0)
            {
                final int ReadDataNum = mGinkgoDriver.ControlCAN.VCI_Receive(channel, CAN_ReadDataBuffer, CAN_ReadDataBuffer.length);
                for(int i = ReadDataNum-1; i < ReadDataNum; i++){
                    strB.setLength(0);
                    strB.append(" RemoteFlag:");
                    strB.append(CAN_ReadDataBuffer[i].RemoteFlag);
                    strB.append(" ExternFlag:");
                    strB.append(CAN_ReadDataBuffer[i].ExternFlag);
                    strB.append(String.format("id:%x", CAN_ReadDataBuffer[i].ID));
                    strB.append(" TimeStamp:");
                    strB.append(CAN_ReadDataBuffer[i].TimeStamp);
                    strB.append(" DataLen:");
                    strB.append(CAN_ReadDataBuffer[i].DataLen);
                    strB.append(" Data:");
                    String data = Helper.toHexString(CAN_ReadDataBuffer[i].Data,0,CAN_ReadDataBuffer[i].DataLen);
                    strB.append(data);

                    final  String finalStr = strB.toString();
                    Log.d(TAG,finalStr);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            printView.append(finalStr);
                        }
                    });
                }

                //读完就发数据；
                sendMessageHandle.sendEmptyMessage(1);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Intent intent = null;
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {

        }else if(id == R.id.action_goloop){
            intent=new Intent
                    (MainActivity.this,LoopMainActivity.class);

        }else if(id == R.id.action_read){
            intent=new Intent
                    (MainActivity.this,ReadOBDDataActivity.class);

        }
        if(intent !=null){
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void config_usb() {
        mUsbManager = (UsbManager) getSystemService(MainActivity.USB_SERVICE);

        pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        if((mUsbManager != null)&&(pendingIntent != null)){
            mGinkgoDriver = new GinkgoDriver(mUsbManager, pendingIntent);
        }
    }
}
