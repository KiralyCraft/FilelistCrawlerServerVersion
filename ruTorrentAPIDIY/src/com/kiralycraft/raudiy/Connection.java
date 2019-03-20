package com.kiralycraft.raudiy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Connection
{
	class TorrentInfo
	{
		private String hashID;
		private float uploadRatio;
		private long lastActivitySeconds,timeAddedSeconds;
		private long uploadedEver;
		private long size;
		private long haveValid;
		private float uploadSpeed;
		private long bytesLeft;
		private String name;
		boolean shouldBeProcessed;
		boolean unregistered;
		public TorrentInfo(String hashID,long bytesLeft,float uploadSpeed, float uploadRatio, long lastActivitySeconds, long timeAddedSeconds,long uploadedEver, long size,long haveValid, String name) 
		{
			this.hashID = hashID;
			this.uploadRatio = uploadRatio;
			this.lastActivitySeconds = lastActivitySeconds;
			this.timeAddedSeconds = timeAddedSeconds;
			this.uploadedEver = uploadedEver;
			this.size = size;
			this.haveValid = haveValid;
			this.name = name;
			this.bytesLeft = bytesLeft;
			this.uploadSpeed = uploadSpeed;
			this.shouldBeProcessed = false;
		}
		public String getId() {
			return hashID;
		}
		public String getName()
		{
			return this.name;
		}
		public float getUploadRatio() {
			return uploadRatio;
		}
		public long getLastActivitySeconds() {
			return lastActivitySeconds;
		}
		public long getTimeAddedSeconds() {
			return timeAddedSeconds;
		}
		public long getUploadedEver() {
			return uploadedEver;
		}
		public long getSize() {
			return size;
		}
		public long haveValid() {
            return haveValid;
        }
		@Override
		public String toString()
		{
			return String.format(
					"TorrentInfo [hashID=%s, uploadRatio=%s, lastActivitySeconds=%s, timeAddedSeconds=%s, uploadedEver=%s, size=%s, haveValid=%s, name=%s, shouldBeProcessed=%s]",
					hashID, uploadRatio, lastActivitySeconds, timeAddedSeconds, uploadedEver, size, haveValid, name,
					shouldBeProcessed);
		}
		public float getUploadSpeed()
		{
			return uploadSpeed;
		}
		public long getBytesLeft()
		{
			return bytesLeft;
		}
		public TorrentInfo deepCopy()
		{
			return new TorrentInfo(hashID,bytesLeft,uploadSpeed, uploadRatio, lastActivitySeconds, timeAddedSeconds,uploadedEver, size,haveValid, name);
		}
		public void setUnregistered(boolean b)
		{
			this.unregistered = b;
		}
		public boolean isUnregistered()
		{
			return unregistered;
		}
	}
	
	
	private String baseURL;
	private String loginString;
	private JsonParser parser;
	private String cid = "NOCID";
	private ArrayList<TorrentInfo> torrentInfoCache;
	public Connection(String username,String password,String baseURL)
	{
		this.baseURL = baseURL;
		this.loginString ="Basic " + new String(Base64.getEncoder().encode((username+":"+password).getBytes()));
		this.parser = new JsonParser();
		this.torrentInfoCache = new ArrayList<TorrentInfo>();
	}
	
	public int login()  
	{
		try 
		{
			URL url = new URL(baseURL+"/");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Authorization", loginString);
			conn.connect();
			int response = conn.getResponseCode();
			if (response == 200)
			{
				return 1;//OK
			}
			else if (response == 401)
			{
				return 2;//WRONG CREDENTIALS
			}
			return -1;//UNDOCUMENTED BEHAVIOUR
		}
		catch(Exception e)
		{
			return 0;//O PUSCAT CEVA
		}
	}
	
	//TODO: GET http://185.106.123.6/rutorrent/plugins/diskspace/action.php?_=TIMESTAMP in 1549833424 (or fi secunde), currentMillis/1000
	public boolean uploadNewTorrent(String unused,File torrent)//downloadDir unused
	{
		try
		{
			String LINE_FEED = "\r\n";
			String boundary = "multipartformboundary1234567890";
			String request = baseURL+"/rutorrent/php/addtorrent.php";
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("Accept", "*/*");
			conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
			
			OutputStream outputStream = conn.getOutputStream();
	        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);
			
	        
	        writer.append("--" + boundary).append(LINE_FEED);
	        writer.append("Content-Disposition: form-data; name=\"" + "json" + "\"").append(LINE_FEED);
	        writer.append(LINE_FEED);
	        writer.append("1").append(LINE_FEED);
	        writer.append("--" + boundary).append(LINE_FEED);
	        writer.append("Content-Disposition: form-data; name=\"" + "torrent_file"+ "\"; filename=\"" + torrent.getName() + "\"").append(LINE_FEED);
	        writer.append( "Content-Type: application/x-bittorrent").append(LINE_FEED);
	        writer.append(LINE_FEED);
	 
	        writer.flush();//Nu scrie nimic in stream pana cand nu dam flush
	        
	        FileInputStream inputStream = new FileInputStream(torrent);
	        byte[] buffer = new byte[4096];
	        int bytesRead = -1;
	        while ((bytesRead = inputStream.read(buffer)) != -1) 
	        {
	            outputStream.write(buffer, 0, bytesRead);
	        }
	        inputStream.close();
	         
	        writer.append(LINE_FEED).flush();
	        writer.append("--" + boundary+"--").append(LINE_FEED);
	        writer.close();
	        
			int response = conn.getResponseCode();
			if (response == 302 && conn.getHeaderField("Location").startsWith("/rutorrent/php/addtorrent.php?result[]=Success"))
			{
				return true;
			}
		}
		catch(Exception e)
		{
			;
		}
		return false;
	}
	public boolean cleanup(long spaceNeeded,long softQuotaBytes)
	{
		if (!updateTorrentList())
		{
			System.out.println("Unable to update torrent info during cleanup.");
			return false;
		}
		else
		{
            ArrayList<TorrentInfo> tilist = new ArrayList<TorrentInfo>();
            
			for (TorrentInfo obj:this.torrentInfoCache) 
			{
				if (obj.getBytesLeft()==0)//nu includem torerntele care nu-s gata
				{
		            if (!obj.getName().contains("NODELETE"))
		            {   
			            if ((obj.getUploadRatio()>1.0f || obj.getTimeAddedSeconds()>172800) || obj.isUnregistered()) //daca are 48 de ore sau nu o mai facut upload de o zi
			            {
			            	TorrentInfo toRemove = obj.deepCopy();
			            	toRemove.setUnregistered(obj.isUnregistered());
				            tilist.add(toRemove); //doar daca indeplineste conditiile pentru a fi sters
				            toRemove.shouldBeProcessed=true;
			            	System.out.println("Should be removed: "+toRemove.getName());
			            }
		            }
				}
			}
			
			Collections.sort(tilist, new Comparator<TorrentInfo>() 
			{
			    @Override
			    public int compare(TorrentInfo o1, TorrentInfo o2) 
			    {
			        return Long.signum((long) (o1.getLastActivitySeconds()/o1.getUploadRatio() - o2.getLastActivitySeconds()/o2.getUploadRatio()))*-1; //descrescator
			    }
			});
			
			////////////////////////////
			////ACTUAL CLEANING/////////
			////////////////////////////
			long currentUsedSpace = getUsedSpace();
			long freeSpaceForDownloadingTorrents = getDownloadingTorrentsSpaceNeeded();  
			Pair<Long, String> freespaceAndDownDir = getFreeSpaceAndDownDir();
			long currentFreeSpace = freespaceAndDownDir.getKey()-freeSpaceForDownloadingTorrents;
			long freeableSpace = 0;//bytes
			
			boolean enoughSpaceFreeable = false;
			ArrayList<TorrentInfo> toRemove = new ArrayList<TorrentInfo>();
			for (TorrentInfo tmpti:tilist)
			{
				if (tmpti.shouldBeProcessed)
				{
					freeableSpace+=tmpti.getSize();
					toRemove.add(tmpti);
					System.out.println("Will remove: "+tmpti.getName()+". With this, we should have "+(freeableSpace/1000/1000)+" MB more free space.");
					if (spaceNeeded<currentFreeSpace+freeableSpace &&
							(softQuotaBytes==-1 || (currentUsedSpace-freeableSpace+spaceNeeded<softQuotaBytes)))
					{
						System.out.println("Looks like this is enough.");
						enoughSpaceFreeable = true;
						break;
					}
				}
			}
			if (enoughSpaceFreeable)
			{
				for (TorrentInfo tmpti:toRemove)
				{
					System.out.println("Removing torrent instance from ruTorrent ( with associated files, remotely): "+tmpti.getName());
					removeTorrent(tmpti.getId());
				}
				return true;
			}
			else
			{
				return false;
			}
		}
	}
	public float getUploadSpeed()
	{
		if (updateTorrentList())
		{
			float uploadSpeed = 0;
			for (TorrentInfo ti:torrentInfoCache)
			{
				uploadSpeed+=ti.getUploadSpeed();
			}
			return uploadSpeed/1024f/1024f;
		}
		else
		{
			return -2;
		}
	}
	public Pair<Long,String> getFreeSpaceAndDownDir()
	{
		try 
		{
			URL url = new URL(baseURL+"/rutorrent/plugins/diskspace/action.php?_="+System.currentTimeMillis());
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Authorization", loginString);
			conn.connect();
			int response = conn.getResponseCode();
			if (response == 200)
			{
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
				
				JsonObject o = parser.parse(responseString).getAsJsonObject();
				return new Pair<Long,String>(o.get("free").getAsLong(),"UNUSED");
			}
			return new Pair<Long,String>((long) -1,"");
		}
		catch(Exception e)
		{
			return new Pair<Long,String>((long) -2,"");
		}
	}
	public boolean isLocalInstance()
	{
		return false; //VA RULA MEREU IN REMOTE MODE. LOCAL MODE OBSOLETE
	}
	public long getDownloadingTorrentsSpaceNeeded() 
    {
	    long spaceNeeded=0;
        if (updateTorrentList())
        {
        	for (TorrentInfo ti:this.torrentInfoCache)
            {
                spaceNeeded += ti.getBytesLeft();
            }
            return spaceNeeded;
        }
        else
        {
            System.out.println("Unable to get downloading torrents info to calculate required space");
            return -1;
        }
    }
	public long getUsedSpace()
	{
		long spaceUsed = 0;
	    if (updateTorrentList())
        {
            for (TorrentInfo ti:this.torrentInfoCache)
            {
                spaceUsed += ti.getSize();
            }
            return spaceUsed;
        }
        else
        {
            System.out.println("Unable to get downloading torrents info to calculate used space");
            return -1;
        }
		
	}
	public boolean removeTorrent(String torrentHash)
	{
		long spaceNow = this.getFreeSpaceAndDownDir().getKey();
		try 
		{
			String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>system.multicall</methodName><params><param><value><array><data><value><struct><member><name>methodName</name><value><string>d.custom5.set</string></value></member><member><name>params</name><value><array><data><value><string>"+torrentHash+"</string></value><value><string>1</string></value></data></array></value></member></struct></value><value><struct><member><name>methodName</name><value><string>d.delete_tied</string></value></member><member><name>params</name><value><array><data><value><string>"+torrentHash+"</string></value></data></array></value></member></struct></value><value><struct><member><name>methodName</name><value><string>d.erase</string></value></member><member><name>params</name><value><array><data><value><string>"+torrentHash+"</string></value></data></array></value></member></struct></value></data></array></value></param></params></methodCall>";
			
			byte[] requestByteArray = request.getBytes(Charset.forName("UTF-8"));
			
			URL url = new URL(baseURL+"/rutorrent/plugins/httprpc/action.php");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("Content-Length", requestByteArray.length+"");
			conn.connect();
			
			OutputStream os = conn.getOutputStream();
			os.write(requestByteArray, 0, requestByteArray.length);
			os.flush();
			os.close();
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				System.out.println("Checking every 5 seconds if torrent is gone.");
				int checkcount = 0;
				while(this.getFreeSpaceAndDownDir().getKey()==spaceNow)
				{
					System.out.println("#"+checkcount+" Is it gone? Not yet.");
					checkcount++;
					Thread.sleep(5000);
				}
				System.out.println("#"+checkcount+" Is it gone? Yup!");
				return true;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}
	protected boolean updateTorrentList()
	{
		try 
		{
			String request = "mode=list&cmd=d.custom%3Daddtime";
			
			byte[] requestByteArray = request.getBytes(Charset.forName("UTF-8"));
			
			URL url = new URL(baseURL+"/rutorrent/plugins/httprpc/action.php");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("Content-Length", requestByteArray.length+"");
			conn.connect();
			
			OutputStream os = conn.getOutputStream();
			os.write(requestByteArray, 0, requestByteArray.length);
			os.flush();
			os.close();
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
				
				JsonObject o = parser.parse(responseString).getAsJsonObject();
				if (o.has("cid"))
				{
					JsonObject torrentList = o.get("t").getAsJsonObject();
					torrentInfoCache.clear();
					for (Entry<String, JsonElement> s: torrentList.entrySet())
					{
						JsonArray info = s.getValue().getAsJsonArray();
						
						float ratio = info.get(11-1).getAsFloat()/1000f;
						long uploadedEver = info.get(10-1).getAsLong();
						long size = info.get(6-1).getAsLong();
						String name = info.get(5-1).getAsString();
						boolean unregsitered = info.get(30-1).getAsString().contains("Unregistered torrent");
						
						long timeAddedSeconds;
						try
						{
							timeAddedSeconds = System.currentTimeMillis()/1000l - Long.parseLong(info.get(35-1).getAsString().replaceAll("[^\\d]", ""));
						}
						catch(NumberFormatException puscatura)
						{
							timeAddedSeconds = 0; //daca nu este coloana (am patit) presupunem ca s-a adaugat in momentul verificarii. astfel, numai ratia conteaza, nu si 48h
						}
						long haveValid = info.get(9-1).getAsLong();
						float upspeed = info.get(12-1).getAsFloat();
						long bytesLeft = info.get(20-1).getAsLong();
						TorrentInfo ti = new TorrentInfo(s.getKey(),bytesLeft,upspeed,ratio,-1,timeAddedSeconds,uploadedEver,size,haveValid,name);
						if (unregsitered)
						{
							ti.setUnregistered(true);
						}
						torrentInfoCache.add(ti);
					}
//					System.out.println(torrentList);
					return true;
				}
				else
				{
					throw new Exception("CID missing in action response!");
				}
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}
}
