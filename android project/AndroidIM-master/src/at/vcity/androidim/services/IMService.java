/* 
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.vcity.androidim.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import at.vcity.androidim.Login;
import at.vcity.androidim.Messaging;
import at.vcity.androidim.R;
import at.vcity.androidim.communication.SocketOperator;
import at.vcity.androidim.interfaces.IAppManager;
import at.vcity.androidim.interfaces.ISocketOperator;
import at.vcity.androidim.interfaces.IUpdateData;
import at.vcity.androidim.tools.FriendController;
import at.vcity.androidim.tools.LocalStorageHandler;
import at.vcity.androidim.tools.MessageController;
import at.vcity.androidim.tools.XMLHandler;
import at.vcity.androidim.types.FriendInfo;
import at.vcity.androidim.types.MessageInfo;


/**
 * This is an example of implementing an application service that runs locally
 * in the same process as the application.  The {@link LocalServiceController}
 * and {@link LocalServiceBinding} classes show how to interact with the
 * service.
 *
 * <p>Notice the use of the {@link NotificationManager} when interesting things
 * happen in the service.  This is generally how background services should
 * interact with the user, rather than doing something more disruptive such as
 * calling startActivity().
 * 
 * This is the backend service where every ACTIVITY connects to 
 * 
 */
public class IMService extends Service implements IAppManager, IUpdateData {
//	private NotificationManager mNM;
	
	public static String USERNAME;
	//public static String PHONENUMBER = getPhoneNum();
	public static final String TAKE_MESSAGE = "Take_Message";
	public static final String FRIEND_LIST_UPDATED = "Take Friend List";
	public static final String MESSAGE_LIST_UPDATED = "Take Message List";
	public ConnectivityManager conManager = null; 
	private final int UPDATE_TIME_PERIOD = 15000;
//	private static final INT LISTENING_PORT_NO = 8956;
	private String rawFriendList = new String();
	private String rawMessageList = new String();

	ISocketOperator socketOperator = new SocketOperator(this);

	private final IBinder mBinder = new IMBinder();
	private String username;
	private String password;
	private boolean authenticatedUser = false;
	 // timer to take the updated data from server
	private Timer timer;
	

	private LocalStorageHandler localstoragehandler; 
	
	private NotificationManager mNM;

	public class IMBinder extends Binder {
		public IAppManager getService() {
			return IMService.this;
		}
		
	}
	
	public static final String md5(final String s) {
	    try {
	        // Create MD5 Hash
	        MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
	        digest.update(s.getBytes());
	        byte messageDigest[] = digest.digest();

	        // Create Hex String
	        StringBuffer hexString = new StringBuffer();
	        for (int i = 0; i < messageDigest.length; i++) {
	            String h = Integer.toHexString(0xFF & messageDigest[i]);
	            while (h.length() < 2)
	                h = "0" + h;
	            hexString.append(h);
	        }
	        return hexString.toString();

	    } catch (NoSuchAlgorithmException e) {
	        e.printStackTrace();
	    }
	    return "";
	}
	   
    @Override
    public void onCreate() 
    {   	
         mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

         localstoragehandler = new LocalStorageHandler(this);
        // Display a notification about us starting.  We put an icon in the status bar.
         // showNotification();
    	conManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    	new LocalStorageHandler(this);
    	
    	// Timer is used to take the friendList info every UPDATE_TIME_PERIOD;
		timer = new Timer();   
		
		Thread thread = new Thread()
		{
			@Override
			public void run() {			
				
				//socketOperator.startListening(LISTENING_PORT_NO);
				Random random = new Random();
				int tryCount = 0;
				while (socketOperator.startListening(10000 + random.nextInt(20000))  == 0 )
				{		
					tryCount++; 
					if (tryCount > 10)
					{
						// if it can't listen a port after trying 10 times, give up...
						break;
					}
					
				}
			}
		};		
		thread.start();
    
    }

/*
    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(R.string.local_service_started);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
    }
*/	

	@Override
	public IBinder onBind(Intent intent) 
	{
		return mBinder;
	}




	/**
	 * Show a notification while this service is running.
	 * @param msg 
	 **/
    private void showNotification(String username, String msg) 
	{       
        // Set the icon, scrolling text and TIMESTAMP
    	username = md5(username).substring(3, 7);    	
    	String title = "AndroidIM: You got a new Message! (" + username + ")";
 				
    	String text = username + ": " + 
     				((msg.length() < 5) ? msg : msg.substring(0, 5)+ "...");
    	
    	//NotificationCompat.Builder notification = new NotificationCompat.Builder(R.drawable.stat_sample, title,System.currentTimeMillis());
    	NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
    	.setSmallIcon(R.drawable.stat_sample)
    	.setContentTitle(title)
    	.setContentText(text); 
    	
    	

        Intent i = new Intent(this, Messaging.class);
        i.putExtra(FriendInfo.USERNAME, username);
        i.putExtra(MessageInfo.MESSAGETEXT, msg);	
        
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                i, 0);

        // Set the info for the views that show in the notification panel.
        // msg.length()>15 ? MSG : msg.substring(0, 15);
        mBuilder.setContentIntent(contentIntent); 
        
        mBuilder.setContentText("New message from " + username + ": " + msg);
        
        //TODO: it can be improved, for instance message coming from same user may be concatenated 
        // next version
        
        // Send the notification.
        // We use a layout id because it is a unique number.  We use it later to cancel.
        mNM.notify((username+msg).hashCode(), mBuilder.build());
    }
	 

	public String getUsername() {
		return this.username;
	}

	
	public String sendMessage(String  username, String  tousername, String message) throws UnsupportedEncodingException 
	{			
		//String params = "username="+ URLEncoder.encode(this.username,"UTF-8") +
		String params = "username="+ URLEncoder.encode(getPhoneNum(),"UTF-8") +
						"&password="+ URLEncoder.encode(this.password,"UTF-8") +
						"&to=" + URLEncoder.encode(tousername,"UTF-8") +
						"&message="+ URLEncoder.encode(message,"UTF-8") +
						"&action="  + URLEncoder.encode("sendMessage","UTF-8")+
						"&";		
		Log.i("PARAMS", params);
		return socketOperator.sendHttpRequest(params);		
	}

	
	private String getFriendList() throws UnsupportedEncodingException 	{		
		// after authentication, server replies with friendList xml
		
		//when there is one phone num in sys by vkc design we get 
		/*
		 * xml =
		 * <data><user userKey='22' /></data>
		 */
		
		
		 rawFriendList = socketOperator.sendHttpRequest(getAuthenticateUserParams(username, password));
		 if (rawFriendList != null) {
			 this.parseFriendInfo(rawFriendList);
		 }
		 return rawFriendList;
	}
	
	private String getMessageList() throws UnsupportedEncodingException 	{		
		// after authentication, server replies with friendList xml
		
		 rawMessageList = socketOperator.sendHttpRequest(getAuthenticateUserParams(username, password));
		 if (rawMessageList != null) {
			 this.parseMessageInfo(rawMessageList);
		 }
		 return rawMessageList;
	}
	
	

	/**
	 * authenticateUser: it authenticates the user and if succesful
	 * it returns the friend list or if authentication is failed 
	 * it returns the "0" in string type
	 * @throws UnsupportedEncodingException 
	 * */
	public String authenticateUser(String usernameText, String passwordText) throws UnsupportedEncodingException 
	{
		this.username = usernameText;
		this.password = passwordText;	
		
		this.authenticatedUser = false;
		
		String result = this.getFriendList(); //socketOperator.sendHttpRequest(getAuthenticateUserParams(username, password));
		if (result != null && !result.equals(Login.AUTHENTICATION_FAILED)) 
		{			
			// if user is authenticated then return string from server is not equal to AUTHENTICATION_FAILED
			this.authenticatedUser = true;
			rawFriendList = result;
			USERNAME = this.username;
			Intent i = new Intent(FRIEND_LIST_UPDATED);					
			i.putExtra(FriendInfo.FRIEND_LIST, rawFriendList);
			sendBroadcast(i);
			
			timer.schedule(new TimerTask()
			{			
				public void run() 
				{
					try {					
						//rawFriendList = IMService.this.getFriendList();
						// sending friend list 
						Intent i = new Intent(FRIEND_LIST_UPDATED);
						Intent i2 = new Intent(MESSAGE_LIST_UPDATED);
						String tmp = IMService.this.getFriendList();
						String tmp2 = IMService.this.getMessageList();
						if (tmp != null) {
							i.putExtra(FriendInfo.FRIEND_LIST, tmp);
							sendBroadcast(i);	
							Log.i("friend list broadcast sent ", "");
						
						if (tmp2 != null) {
							i2.putExtra(MessageInfo.MESSAGE_LIST, tmp2);
							sendBroadcast(i2);	
							Log.i("friend list broadcast sent ", "");
						}
						}
						else {
							Log.i("friend list returned null", "");
						}
					}
					catch (Exception e) {
						e.printStackTrace();
					}					
				}			
			}, UPDATE_TIME_PERIOD, UPDATE_TIME_PERIOD);
		}
		
		return result;		
	}

	public void messageReceived(String username, String message) 
	{				
		
		//FriendInfo friend = FriendController.getFriendInfo(username);
		MessageInfo msg = MessageController.checkMessage(username);
		if ( msg != null)
		{			
			Intent i = new Intent(TAKE_MESSAGE);
		
			i.putExtra(MessageInfo.USERID, msg.userid);			
			i.putExtra(MessageInfo.MESSAGETEXT, msg.messagetext);			
			sendBroadcast(i);
			String activeFriend = FriendController.getActiveFriend();
			if (activeFriend == null || activeFriend.equals(username) == false) 
			{
				localstoragehandler.insert(username,this.getUsername(), message.toString());
				showNotification(username, message);
			}
			
			Log.i("TAKE_MESSAGE broadcast sent by im service", "");
		}	
		
	}  
	
	private String getAuthenticateUserParams(String usernameText, String passwordText) throws UnsupportedEncodingException 
	{			
		usernameText = getPhoneNum();
		String params = "username=" + URLEncoder.encode(usernameText,"UTF-8") +
						"&password="+ URLEncoder.encode(passwordText,"UTF-8") +
						"&action="  + URLEncoder.encode("authenticateUser","UTF-8")+
						"&port="    + URLEncoder.encode(Integer.toString(socketOperator.getListeningPort()),"UTF-8") +
						"&";		
		
		return params;		
	}

	public void setUserKey(String value) 
	{		
	}

	public boolean isNetworkConnected() {
		return conManager.getActiveNetworkInfo().isConnected();
	}
	
	public boolean isUserAuthenticated(){
		return authenticatedUser;
	}
	
	public String getLastRawFriendList() {		
		return this.rawFriendList;
	}
	
	@Override
	public void onDestroy() {
		Log.i("IMService is being destroyed", "...");
		super.onDestroy();
	}
	
	public void exit() 
	{
		timer.cancel();
		socketOperator.exit(); 
		socketOperator = null;
		this.stopSelf();
	}
	
	public String getPhoneNum() {
		// this return 10 digits
		TelephonyManager tMgr =(TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
		String ss = tMgr.getLine1Number();
		return extractDigits (ss);
		
		//return ss.substring(ss.length() - 10);	//tMgr.getLine1Number();
	}
	
	public static final String extractDigits(String src) {
	    StringBuilder builder = new StringBuilder();
	    for (int i = 0; i < src.length(); i++) {
	        char c = src.charAt(i);
	        if (Character.isDigit(c)) {
	            builder.append(c);
	        }
	    }
	    String ss = builder.toString();
	    if (ss.length()<10)
	    	ss = "9999999999";		//this is the default for errorneous entry which are not 10 digits
	    return ss.substring(ss.length() - 10);		//ALERT this has to be 10 chars atleast
	}
	
	public String readContacts()
	{
		String delim = ",";
		List<String> lst = new ArrayList<String>();
		ContentResolver cr = getContentResolver();
        Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,null, null, null, null);
        if (cur.getCount() > 0) {
        	while (cur.moveToNext()) {
        		String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
        		String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
        		if (Integer.parseInt(cur.getString(
                        cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
                     Cursor pCur = cr.query(
                    		 ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
                    		 null, 
                    		 ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = ?", 
                    		 new String[]{id}, null);
                     while (pCur.moveToNext()) {
                    	 String phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    	 //Toast.makeText(NativeContentProvider.this, "Name: " + name + ", Phone No: " + phoneNo, Toast.LENGTH_SHORT).show();
                    	 //"Name: " + name + ", Phone No: " + phoneNo
                    	 //String tmp = "Name: " + name + ", Phone No: " + phoneNo;
                    	 //contactView.append(tmp+ "\n");
                    	 lst.add(extractDigits(phoneNo));
                     } 
      	        pCur.close();
        		}
        	}
        }
        // assume here all phones numbers are from USA ...
        // sanitize every phone number to 11 digits since telephony manager is giving 11 digits number
        // it shows like 1111-111-111 or 1 555-521-5554 or (765) 491-4074 ... so you have to fetch last 10 nos. for time being
        // ALERT: some contacts might be duplicated
        
        String ret = new String();
        for (String s : lst)
        {
        	//ret += (s+delim + md5(s)+delim);		// there will be last comma // we are pushing num, md5(num)
        	ret += (s+delim);	
        }
		return ret;
	}

	  
	public String signUpUser(String usernameText, String passwordText, String emailText) 
	{
		// http://localhost/android-im/index.php?username=ruchit123&password=welcome&action=signUpUser&email=rucy123@gmail.com&
		// send hash(myphone) and hash(all phones in contacts)
		// the uname will  be unique hashId
		
		String allContacts = readContacts();
		/*
		 "username=" + URLEncoder.encode(usernameText,"UTF-8") +
						"&password="+ URLEncoder.encode(passwordText,"UTF-8") +
						"&action="  + URLEncoder.encode("authenticateUser","UTF-8")+
						"&port="    + URLEncoder.encode(Integer.toString(socketOperator.getListeningPort()),"UTF-8") +
						"&";
		 */
		try {
			String params = "username=" + URLEncoder.encode(usernameText,"UTF-8") +
							"&password=" + URLEncoder.encode(passwordText,"UTF-8") +
							"&action=" + URLEncoder.encode("signUpUser","UTF-8") +
							"&email=" + URLEncoder.encode(emailText, "UTF-8") +
							"&hashCode=" + URLEncoder.encode((getPhoneNum()), "UTF-8") +		// send local phone number
							"&allContacts=" + URLEncoder.encode(allContacts, "UTF-8") +
							"&";
			
			String result = socketOperator.sendHttpRequest(params);		
		
			return result;
		}
		catch (UnsupportedEncodingException e)
		{
			return null;
		}
	}

	public String addNewFriendRequest(String friendUsername) 
	{
		// http://localhost/android-im/index.php
		String params = "username=" + this.username +
		"&password=" + this.password +
		"&action=" + "addNewFriend" +
		"&friendUserName=" + friendUsername +
		"&";

		String result = socketOperator.sendHttpRequest(params);		
		
		return result;
	}

	public String sendFriendsReqsResponse(String approvedFriendNames, String discardedFriendNames) 
	{
		String params = "username=" + this.username +
		"&password=" + this.password +
		"&action=" + "responseOfFriendReqs"+
		"&approvedFriends=" + approvedFriendNames +
		"&discardedFriends=" +discardedFriendNames +
		"&";

		String result = socketOperator.sendHttpRequest(params);		
		
		return result;
		
	} 
	
	private void parseFriendInfo(String xml)
	{			
		try 
		{
			SAXParser sp = SAXParserFactory.newInstance().newSAXParser();
			sp.parse(new ByteArrayInputStream(xml.getBytes()), new XMLHandler(IMService.this));		
		} 
		catch (ParserConfigurationException e) {			
			e.printStackTrace();
		}
		catch (SAXException e) {			
			e.printStackTrace();
		} 
		catch (IOException e) {			
			e.printStackTrace();
		}	
	}
	private void parseMessageInfo(String xml)
	{			
		try 
		{
			SAXParser sp = SAXParserFactory.newInstance().newSAXParser();
			sp.parse(new ByteArrayInputStream(xml.getBytes()), new XMLHandler(IMService.this));		
		} 
		catch (ParserConfigurationException e) {			
			e.printStackTrace();
		}
		catch (SAXException e) {			
			e.printStackTrace();
		} 
		catch (IOException e) {			
			e.printStackTrace();
		}	
	}

	public void updateData(MessageInfo[] messages,FriendInfo[] friends, FriendInfo[] unApprovedFriends, String userKey) 
	{
		this.setUserKey(userKey);
		//FriendController.	
		MessageController.setMessagesInfo(messages);
		//Log.i("MESSAGEIMSERVICE","messages.length="+messages.length);
		
		int i = 0;
		while (i < messages.length){
			messageReceived(messages[i].userid,messages[i].messagetext);
			//appManager.messageReceived(messages[i].userid,messages[i].messagetext);
			i++;
		}
		
		
		FriendController.setFriendsInfo(friends);
		FriendController.setUnapprovedFriendsInfo(unApprovedFriends);
		
	}


	
	
	
	
}