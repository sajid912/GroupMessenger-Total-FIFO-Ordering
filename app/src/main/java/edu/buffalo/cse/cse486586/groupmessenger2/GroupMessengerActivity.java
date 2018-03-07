package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity implements View.OnClickListener {

    private static final String REMOTE_PORT0 = "11108";
    private static final String REMOTE_PORT1 = "11112";
    private static final String REMOTE_PORT2 = "11116";
    private static final String REMOTE_PORT3 = "11120";
    private static final String REMOTE_PORT4 = "11124";
    private static final int SERVER_PORT = 10000;
    private EditText messageSpace;
    private int seqNum=0;
    private TextView tv;
    private MySharedPreferences sharedPreferences;
    PriorityQueue holdBackQueue1 = new PriorityQueue();
    private HashMap<Double, String> holdBackQueue = new HashMap<Double, String>();
    private HashMap<Integer, Double> holdBackQueue2 = new HashMap<Integer, Double>();
    //CustomPriorityQueue proposalMap1 = new CustomPriorityQueue();
    private HashMap<String, Double> proposalMap = new HashMap<String, Double>();
    //private HashSet<String> proposalTracker = new HashSet<String>();
    private String myPort;
    private int totalLiveNodes;
    private String crashedPort;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        Button sendButton = (Button) findViewById(R.id.button4);
        sendButton.setOnClickListener(this);

        messageSpace = (EditText) findViewById(R.id.editText1);

        sharedPreferences = new MySharedPreferences(getApplicationContext());
        sharedPreferences.clearPreferences();
        seqNum=0;
        holdBackQueue1.clear();
        holdBackQueue.clear();
        holdBackQueue2.clear();
        //proposalTracker.clear();
        proposalMap.clear();
        //proposalMap1.clear();
        totalLiveNodes = 5;
        crashedPort="";

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        Toast.makeText(getApplicationContext(),"My port number:"+myPort,Toast.LENGTH_SHORT).show();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    @Override
    public void onClick(View view) {

        if (view.getId() == R.id.button4) {
            String message = messageSpace.getText().toString();
            messageSpace.setText("");
            new multiCastTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, constructMessageObject(message));

        }
    }

    private String constructMessageObject(String msg)
    {
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put("message", msg);
            jsonObject.put("id",new Random().nextInt());
            jsonObject.put("agreed_seq_num",-1.0);
            jsonObject.put("proposed_seq_num",-1.0);
            jsonObject.put("deliver_status",0);
            jsonObject.put("my_port", myPort);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }

    private void handleMessageObject(String object)
    {
        if(object !=null && !object.isEmpty())
        {
            try {
                JSONObject jsonObject = new JSONObject(object);
                int status = jsonObject.getInt("deliver_status");
                switch (status)
                {
                    case 0: // Message sent from origin
                        handleMessageFromOrigin(object);
                        break;
                    case 1: // Message sent back
                        handleProposalMessage(object);
                        break;
                    case 2: // Message agreed
                        handleAgreedMessage(object);
                        break;
                    case 9: // Message delete
                        handleMessageDelete(object);
                        break;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private void handleMessageDelete(String object)
    {
        try {
            JSONObject jsonObject = new JSONObject(object);
            int id = jsonObject.getInt("id");

            Double proposalNum = holdBackQueue2.get(id);
            holdBackQueue1.remove(proposalNum);
            holdBackQueue.remove(proposalNum);
            holdBackQueue2.remove(id);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleAgreedMessage(String object)
    {
        try {
            JSONObject jsonObject = new JSONObject(object);
            //String port = jsonObject.getString("my_port");
            int id = jsonObject.getInt("id");
            Double agreed_seq_num = Double.parseDouble(jsonObject.getString("agreed_seq_num"));
            sharedPreferences.setAgreedSequenceNumber(agreed_seq_num.toString());
            // If agreed msg is in the queue and is at the top of the queue, deliver it
            // If agreed msg is not at the top of the queue, wait until the agreed seq num arrives for that msg
            // Use agreed msg to update the proposal seq num for a msg, if agreed num != proposal num

            Log.d(TAG,"Got an agreement signal");

            // Use agreed msg id to get the proposal no.
            // if(proposal no == agreed msg) no need to update the queue
            // else update the proposal no in the queue with latest agreed no
            try{
                Double oldProposalNo = holdBackQueue2.get(id);

                //Log.d("oldProposa")
                if(oldProposalNo == agreed_seq_num)
                {
                    // no need to update the queue
                }
                else
                {
                    // update the proposal no in the queue with latest agreed no
                    holdBackQueue1.remove(oldProposalNo);
                    String tempObject = holdBackQueue.get(oldProposalNo);
                    holdBackQueue.remove(oldProposalNo);

                    if(holdBackQueue1.contains(agreed_seq_num))
                    {
                        // Agreed no of Incoming msg and proposal no of some other msg in queue are same, so conflict
                        // To break the tie, use port no comparision
                        String portStr1 = jsonObject.getString("my_port");
                        String conflictObject = holdBackQueue.get(agreed_seq_num);
                        JSONObject conflictJsonObject = new JSONObject(conflictObject);
                        String portStr2  = conflictJsonObject.getString("my_port");
                        int conflictId  = conflictJsonObject.getInt("id");

                        // First remove port2 elements from hold back queues and then add port 1 and port 2 updates
                        holdBackQueue1.remove(agreed_seq_num);
                        holdBackQueue.remove(agreed_seq_num);

                        double a = getTieBrokenNoFromAgreedNo(agreed_seq_num, portStr1);
                        double b = getTieBrokenNoFromAgreedNo(agreed_seq_num, portStr2);

                        holdBackQueue1.add(a);
                        holdBackQueue1.add(b);

                        holdBackQueue.put(b, conflictObject);
                        holdBackQueue.put(a, object);

                        holdBackQueue2.put(id, a);
                        holdBackQueue2.put(conflictId, b);


                    }
                    /*else if(agreed_seq_num < (Double) holdBackQueue1.peek()) // Assuming the 7.2 is at the queue top when agreed is 7.0
                    {
                        Double newValue = (Double) holdBackQueue1.peek()+0.1;
                        holdBackQueue1.add(newValue);
                        holdBackQueue.put(newValue, jsonObject.toString());
                        holdBackQueue2.put(id,newValue);
                    }*/
                    else
                    {
                        if(queueHasLikeAgreedNo(agreed_seq_num))
                        {
                            String port = jsonObject.getString("my_port");
                            double a = getTieBrokenNoFromAgreedNo(agreed_seq_num, port);
                            holdBackQueue1.add(a);
                            holdBackQueue.put(a, object);
                            holdBackQueue2.put(id, a);
                        }
                        else
                        {
                            holdBackQueue1.add(agreed_seq_num);
                            holdBackQueue.put(agreed_seq_num, tempObject);
                            holdBackQueue2.put(id, agreed_seq_num); // no need to remove, since indexed by id - just got updated
                        }


                    }

                }
            } catch (NullPointerException e) {}


            PriorityQueue dummy = new PriorityQueue();
            dummy.addAll(holdBackQueue1);
            for(int i=0;i<holdBackQueue1.size();i++)
            {
                Log.d(TAG, dummy.poll()+"->");
            }
            while(true)
            {
                Double queueTopProposalNum = (Double) holdBackQueue1.peek();
                String queueTopObjectStr = holdBackQueue.get(queueTopProposalNum);
                JSONObject queueTopJSONObject = new JSONObject(queueTopObjectStr);
                int queueTopId = queueTopJSONObject.getInt("id");
                int status = queueTopJSONObject.getInt("deliver_status");
                if(id == queueTopId || status==4)
                {
                    // Agreed msg is at the top of the queue, so deliver it
                    Log.d(TAG,"Delivering queue top "+queueTopProposalNum);
                    String message = queueTopJSONObject.getString("message");
                    writeServerMsgToFile(seqNum+"", message);
                    seqNum++;
                    // Also remove the msg from both holdback queue's
                    holdBackQueue.remove(queueTopProposalNum);
                    holdBackQueue1.remove(queueTopProposalNum);
                    holdBackQueue2.remove(queueTopId);


                    // After delivery of top msg, also check if msgs below in the queue are ready to be delivered
                    // If ready, deliver them too
                    continue;
                }
                else
                {
                    // Agreed msg is not at the top, so find it and update delivery status to 4
                    if(holdBackQueue2.containsKey(id))
                    {
                        Double proposalSeqNum = holdBackQueue2.get(id);
                        String queueSomeWhereStr = holdBackQueue.get(proposalSeqNum);
                        JSONObject queueSomeWrJSONObject = new JSONObject(queueSomeWhereStr);
                        queueSomeWrJSONObject.put("deliver_status", 4); // 4 means ready to deliver
                        Log.d(TAG,"Update queue somewr to be deliverable "+queueTopProposalNum);
                        /*if(proposalSeqNum == agreed_seq_num)
                        {
                            holdBackQueue.remove(proposalSeqNum);*/
                            holdBackQueue.put(proposalSeqNum, queueSomeWrJSONObject.toString());
                            Log.d(TAG, "Updated status of proposal seqnum "+proposalSeqNum);
//                    holdBackQueue1.remove(proposalSeqNum);
//                    holdBackQueue1.add(proposalSeqNum);
//                    holdBackQueue2.remove(id);
//                    holdBackQueue2.put(id, proposalSeqNum);
                    /*    }
                        else
                        {
                            holdBackQueue.remove(proposalSeqNum);
                            holdBackQueue.put(agreed_seq_num, queueSomeWrJSONObject.toString());
                            holdBackQueue1.remove(proposalSeqNum);
                            holdBackQueue1.add(agreed_seq_num);
                            holdBackQueue2.remove(id);
                            holdBackQueue2.put(id, agreed_seq_num);
                            Log.d(TAG, "Updated the proposal with agreed no"+agreed_seq_num + " previously proposal"+proposalSeqNum);
                        }*/
                    }



                    break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (NullPointerException e){
            e.printStackTrace();
        }
    }

    private boolean queueHasLikeAgreedNo(Double agreedNo)
    {
        PriorityQueue temp = new PriorityQueue(holdBackQueue1);

        for(int i=0;i<holdBackQueue1.size();i++)
        {
            int queueTop1 = (int) Math.floor(Double.parseDouble(temp.poll().toString()));
            int queueTop2 = (int) Math.floor(agreedNo);
            Log.d(TAG,"queueHasLikeAgreedNo:"+agreedNo+"->"+queueTop1);
            if(queueTop1 == queueTop2)
                return true;
        }
        return false;
    }

    private Double getTieBrokenNoFromAgreedNo(Double agreedNo, String port)
    {
        int portNo = Integer.parseInt(port);
        Double factor=0.0;

        switch (portNo)
        {
            case 11108:
                factor=0.1; break;
            case 11112:
                factor=0.2; break;
            case 11116:
                factor=0.3; break;
            case 11120:
                factor=0.4; break;
            case 11124:
                factor=0.5; break;
        }

        return agreedNo+factor;
    }

    private void handleProposalMessage(String object)
    {
        try {
            JSONObject jsonObject = new JSONObject(object);
            int id = jsonObject.getInt("id");
            String port = jsonObject.getString("my_port");
            Double proposed_seq_num = Double.parseDouble(jsonObject.getString("proposed_seq_num"));
            //proposalTracker.add(port);
            //if (proposalTracker.add(port))
            proposalMap.put(id+":"+port, proposed_seq_num);
            //Log.d(TAG,proposalMap.size()+" new size");
            int count = proposalsReceivedCount(id);
            Log.d(TAG,"Proposal received count "+count);
            if(count==totalLiveNodes)
            {
                // Received proposals from all AVDs, find max proposal seq num using proposalMap
                // Once found max seq num, multicast the agreement
                Log.d(TAG,"All proposals received");
                PriorityQueue dummy = new PriorityQueue();
                Double maxProposedSeqNum = getMaxProposedSeqNum(id);
               /* if(maxProposedSeqNum>Double.parseDouble(sharedPreferences.getAgreedSequenceNumber()))
                {*/
                    Log.d(TAG,"Agreed seq num is:"+maxProposedSeqNum);
                    jsonObject.put("agreed_seq_num", maxProposedSeqNum);
                    jsonObject.put("proposed_seq_num", maxProposedSeqNum);
                    jsonObject.put("deliver_status",2);
                    jsonObject.put("my_port", myPort);
                    Log.d(TAG, "Sending agreement now");
                    new multiCastTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, jsonObject.toString());
              /*  }
                else
                {
                    // Some other process has claimed the maxProposedSeqnum during the time when you were waiting for proposals.
                    // It has updated the agreedSeqNum in cache, so now ask for proposals again... bskt
                    // Before you ask for new proposals, you should delete the current hold back msg in the queue.
                    // So set a special delivery flag for delete and let other processes delete it
                    jsonObject.put("deliver_status",9); // 9 for delete
                    jsonObject.put("my_port", myPort);
                    Log.d(TAG, "Sending agreement now");
                    new multiCastTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, jsonObject.toString());

                }*/


            }
            //Log.d(TAG, "Remote port is "+port);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private int proposalsReceivedCount(int id)
    {
        String[] ports = new String[]{REMOTE_PORT0,REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3,REMOTE_PORT4};

        int count=0;
        for (String port : ports) {

            if(proposalMap.containsKey(id+":"+port))
                count++;
        }

        Log.d(TAG, "Count is:"+count+" for msg id:"+id);
        return count;
    }

    private Double getMaxProposedSeqNum(int id)
    {
        ArrayList<Double> list = new ArrayList<Double>(5);
        String[] ports = new String[]{REMOTE_PORT0,REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3,REMOTE_PORT4};

        for (String port : ports) {
            list.add(proposalMap.get(id+":"+port));
            Log.d(TAG,"Proposal num:"+proposalMap.get(id+":"+port));
        }

        Collections.sort(list);

        return list.get(list.size()-1);

       /* int max=0;
        for(Map.Entry entry: proposalMap.entrySet())
        {
            int t = Integer.parseInt(entry.getValue().toString());
            if(t > max)
                max = t;
        }

        return max;*/
    }

    private void handleMessageFromOrigin(String object)
    {
        try {
            JSONObject jsonObject = new JSONObject(object);
            int id = jsonObject.getInt("id");
            String remotePort = jsonObject.getString("my_port");
            //holdBackQueue.put(id, object);
            Double a = Double.parseDouble(sharedPreferences.getAgreedSequenceNumber());
            Double p = Double.parseDouble(sharedPreferences.getProposedSequenceNumber());
            Double max = (a>p)?a:p;
            ++max;
            sharedPreferences.setProposedSequenceNumber(max.toString());
            holdBackQueue.put(max, object);
            holdBackQueue1.add(max);
            holdBackQueue2.put(id, max);
            jsonObject.put("proposed_seq_num", max);
            jsonObject.put("deliver_status", 1);
            jsonObject.put("my_port", myPort);
            String proposedObjectStr = jsonObject.toString();
            new uniCastTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, proposedObjectStr, remotePort);

        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void writeServerMsgToFile(String filename, String message) {
        Uri uri = new Uri.Builder().authority("edu.buffalo.cse.cse486586.groupmessenger2.provider")
                .scheme("content").build();

        ContentValues contentValues = new ContentValues();
        contentValues.put("key", filename);
        contentValues.put("value", message);

        try {
            getContentResolver().insert(uri, contentValues);

        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }


    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String message;
            Log.d(TAG, "Server task");

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    InetAddress inetAddress = socket.getInetAddress();
                    //inetAddress.getAddress();
                    Log.d(TAG, "Remote port on server side is "+socket.getPort());

                    InputStream inputStream = socket.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    message = bufferedReader.readLine();

                    if(myPort != null && !myPort.isEmpty())
                    {
                        OutputStream outputStream = socket.getOutputStream();
                        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
                        bufferedWriter.write(myPort);

                        bufferedWriter.flush();
                        bufferedWriter.close();
                    }

                    publishProgress(message);

                    bufferedReader.close();
                    socket.close();
                } catch (SocketTimeoutException e) {
                    Log.d(TAG,"Socket time out occurred sajid");
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //return null;
            }
        }

        protected void onProgressUpdate(String... strings) {

            Log.d(TAG,"Received:"+strings[0]+" My port:"+myPort);
            handleMessageObject(strings[0]);
           /* String message = strings[0].trim() + "\n";
            Log.d(TAG, "Message received:" + message);

            //int currentSeqNum = sharedPreferences.getSequenceNumber();
            String fileName = Integer.toString(seqNum);

            writeServerMsgToFile(fileName, message);
            //sharedPreferences.setSequenceNumber(++currentSeqNum);
            seqNum++;*/

            //tv.append(message);
            //Log.d(TAG,"Progress updated");
            return;
        }
    }

    private class multiCastTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            //proposalMap.clear();
            //proposalTracker.clear();
            String[] ports = new String[]{REMOTE_PORT0,REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3,REMOTE_PORT4};

            for (String remotePort : ports) {
                try {

                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    socket.setSoTimeout(5000);

                    String msgToSend = msgs[0];
                    Log.d(TAG, "Multicast to port" + msgToSend+" : "+remotePort);

                    OutputStream outputStream = socket.getOutputStream();
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
                    bufferedWriter.write(msgToSend);

                    InputStream inputStream = socket.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    String readStatus = bufferedReader.readLine();
                    Log.d(TAG,"Read status:"+readStatus);


                    bufferedWriter.flush();
                    bufferedWriter.close();
                    bufferedReader.close();
                    socket.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "Socket Timeout Exception, "+remotePort+" has failed");
                    crashedPort = remotePort;
                    totalLiveNodes=4;
                    e.printStackTrace();

                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "ClientTask socket IOException");
                } catch (Exception e) {
                    Log.e(TAG, "General exception");
                    e.printStackTrace();
                }
            }

            return null;
        }
    }

    private class uniCastTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

                String remotePort = msgs[1];

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    String msgToSend = msgs[0];
                    Log.d(TAG, "Unicast to port " + msgToSend + " : "+remotePort);

                    OutputStream outputStream = socket.getOutputStream();
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
                    bufferedWriter.write(msgToSend);

                    //socket.connect(socket.getRemoteSocketAddress(),500);

                    bufferedWriter.flush();
                    bufferedWriter.close();
                    socket.close();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "Socket Timeout Exception, "+remotePort+" has failed");
                    crashedPort = remotePort;
                    totalLiveNodes=4;
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "ClientTask socket IOException");
                }

            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Shutting myself");
    }
}
