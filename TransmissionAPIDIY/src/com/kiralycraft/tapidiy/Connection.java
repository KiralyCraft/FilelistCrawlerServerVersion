package com.kiralycraft.tapidiy;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;


public class Connection 
{
	private String url;
	
	private String loginString,authKey;
	
	private JsonObject torrentInfo = null;
	
	class TorrentInfo
	{
		private int id;
		private float uploadRatio;
		private long lastActivitySeconds,timeAddedSeconds;
		private long uploadedEver;
		private long size;
		private long haveValid;
		private String name;
		boolean shouldBeProcessed;
		
		public TorrentInfo(int id, float uploadRatio, long lastActivitySeconds, long timeAddedSeconds,long uploadedEver, long size,long haveValid, String name) 
		{
			this.id = id;
			this.uploadRatio = uploadRatio;
			this.lastActivitySeconds = lastActivitySeconds;
			this.timeAddedSeconds = timeAddedSeconds;
			this.uploadedEver = uploadedEver;
			this.size = size;
			this.haveValid = haveValid;
			this.name = name;
			this.shouldBeProcessed = false;
		}
		public int getId() {
			return id;
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
	}
	
	public Connection(String username,String password,String url)
	{
		byte[] encodedBytes = Base64.getEncoder().encode((username+":"+password).getBytes());
		url+="/transmission/rpc";
		this.url = url;
		loginString = "Basic "+new String(encodedBytes);
	}
	public int login()  
	{
		try 
		{
			byte[] postData       = "furestricteadheaders".getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 409)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				return 1;
			}
			else if (response == 401)
			{
				return 2;
			}
			else if (response == 400)
			{
				return 3;
			}
			return -1;
		} catch (Exception e) 
		{
			return 0;
		}
	}
    public long getDownloadingTorrentsSpaceNeeded() 
    {
        long spaceNeeded=0;
        if (updateTorrentInfo())
        {
            JsonArray allTorrents = torrentInfo.getAsJsonObject("arguments").getAsJsonArray("torrents");
 
            for (JsonElement obj:allTorrents)
            {
                float percentDone = obj.getAsJsonObject().getAsJsonPrimitive("percentDone").getAsFloat();
                if (percentDone!=1f)
                {
                    long completeSize = obj.getAsJsonObject().getAsJsonPrimitive("sizeWhenDone").getAsLong();
                    long haveValid = obj.getAsJsonObject().getAsJsonPrimitive("haveValid").getAsLong();
                    long tmp = 0;
                    tmp = completeSize - haveValid;
                    spaceNeeded += tmp;
                }
            }
            return spaceNeeded;
        }
        else
        {
            System.out.println("Unable to get downloading torrents info to calculate required space");
            return -1;
        }
    }
	public boolean cleanup(long spaceNeeded)
	{
		if (!updateTorrentInfo())
		{
			return false;
		}
		else
		{
			JsonArray allTorrents = torrentInfo.getAsJsonObject("arguments").getAsJsonArray("torrents");

            ArrayList<TorrentInfo> tilist = new ArrayList<TorrentInfo>();
            
			for (JsonElement obj:allTorrents) 
			{
				float percentDone = obj.getAsJsonObject().getAsJsonPrimitive("percentDone").getAsFloat();
				if (percentDone==1f)//nu includem torerntele care nu-s gata
				{
		            int id = obj.getAsJsonObject().getAsJsonPrimitive("id").getAsInt();
		            float ratio = obj.getAsJsonObject().getAsJsonPrimitive("uploadRatio").getAsFloat();
		            long uploadEver = obj.getAsJsonObject().getAsJsonPrimitive("uploadedEver").getAsLong();
		            long size = obj.getAsJsonObject().getAsJsonPrimitive("sizeWhenDone").getAsLong();
		            long haveValid = obj.getAsJsonObject().getAsJsonPrimitive("haveValid").getAsLong();
		            JsonObject activityInfo = getTorrentInfo(id);
		            JsonObject torrentList = activityInfo.getAsJsonArray("torrents").get(0).getAsJsonObject();
		            
	
		            String name = torrentList.getAsJsonPrimitive("name").getAsString();
		            
		            long timeAddedSeconds = System.currentTimeMillis()/1000-torrentList.getAsJsonPrimitive("startDate").getAsLong();
		            long lastActivitySeconds = System.currentTimeMillis()/1000 - torrentList.getAsJsonPrimitive("activityDate").getAsLong();
		            
		            TorrentInfo ti = new TorrentInfo(id,ratio,lastActivitySeconds,timeAddedSeconds,uploadEver,size,haveValid,name);
		            
		            tilist.add(ti); //doar daca indeplineste conditiile pentru a fi sters
		            if ((ti.getUploadRatio()>1.0f || ti.getTimeAddedSeconds()>172800)) //daca are 48 de ore si nu o mai facut upload de o zi
		            {
	//		            System.out.println(id+" "+ratio+" "+uploadEver+" "+size+" "+timeAddedSeconds+" "+lastActivitySeconds+" "+name);
		            	ti.shouldBeProcessed=true;
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
			Pair<Long, String> freespaceAndDownDir = getFreeSpaceAndDownDir();
			long currentFreeSpace = freespaceAndDownDir.getKey();
			String downloadDir = freespaceAndDownDir.getValue();
			long freeableSpace = 0;
			
			if (spaceNeeded<currentFreeSpace)
			{
				return true;
			}
			else
			{
				boolean enoughSpaceFreeable = false;
				ArrayList<TorrentInfo> toRemove = new ArrayList<TorrentInfo>();
				for (TorrentInfo tmpti:tilist)
				{
					if (tmpti.shouldBeProcessed)
					{
						freeableSpace+=tmpti.getSize();
						toRemove.add(tmpti);
						if (spaceNeeded<currentFreeSpace+freeableSpace)
						{
							enoughSpaceFreeable = true;
							break;
						}
					}
				}
				if (enoughSpaceFreeable)
				{
					for (TorrentInfo tmpti:toRemove)
					{
						System.out.println("Removing torrent instance from Transmission");
						removeTorrent(tmpti.getId());
						System.out.println("Removing associated files for:"+tmpti.getName());
						JsonArray files = getTorrentFiles(tmpti.getId());
						for (JsonElement je:files)
						{
							String toDeletePath = je.getAsJsonObject().getAsJsonPrimitive("name").getAsString();
							File fileToDelete = new File(downloadDir+File.separator+toDeletePath);
							System.out.println("Erasing "+toDeletePath);
							if (!fileToDelete.delete())
							{
								System.out.println("Failed to delete!");
							}
						}
					}
					return true;
				}
				else
				{
					return false;
				}
			}
		}
	}
//	public boolean removeTorrent(int id)
//	{
//		System.out.println(id);
//		return true;
//	}
	public boolean removeTorrent(int id)
	{
		try
		{
			byte[] postData       = ("{\"method\": \"torrent-remove\",\"arguments\": {\"delete-local-data\": false,\"ids\": ["+id+"]}}").getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");
	
			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("X-Transmission-Session-Id", authKey);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
				
				JsonParser parser = new JsonParser();
				String result = parser.parse(responseString).getAsJsonObject().getAsJsonPrimitive("result").getAsString();
				return result.equals("success");
			}
		}
		catch(Exception e)
		{
			return false;
		}
		return false;
	}
	public Pair<Long,String> getFreeSpaceAndDownDir()
	{
		try 
		{
			byte[] postData       = "{\"method\":\"session-get\"}".getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("X-Transmission-Session-Id", authKey);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
				
				JsonParser parser = new JsonParser();
				JsonObject o = parser.parse(responseString).getAsJsonObject().getAsJsonObject("arguments");
				JsonPrimitive space = o.getAsJsonPrimitive("download-dir-free-space");
				JsonPrimitive downdir = o.getAsJsonPrimitive("download-dir");
				String downdirStr = downdir.getAsString();
				if (downdirStr.endsWith("/")||downdirStr.endsWith("\\"))
				{
					downdirStr = downdirStr.substring(0, downdirStr.length()-1);
				}
				return new Pair<Long,String>(space.getAsLong(),downdirStr);
			}
			return new Pair<Long,String>((long) -1,"");
		} catch (Exception e) 
		{
			return new Pair<Long,String>((long) -2,"");
		}

	}
	public boolean uploadNewTorrent(String downloadDir,File torrent)
	{
		try 
		{
			FileInputStream fileStream = new FileInputStream(torrent);
			// Instantiate array
			byte[] arr = new byte[(int) torrent.length()];
			/// read All bytes of File stream
			fileStream.read(arr, 0, arr.length);
			
			fileStream.close();
			
			byte[] encoded = Base64.getEncoder().encode(arr);
		    String encodedString = new String(encoded,StandardCharsets.US_ASCII);
		    
		    byte[] postData = ("{\"method\":\"torrent-add\",\"arguments\":{\"paused\":false,\"download-dir\":\""+downloadDir.replace("\\", "\\\\")+"\",\"metainfo\":\""+encodedString+"\"}}").getBytes();
		    
		    int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("X-Transmission-Session-Id", authKey);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
				
				JsonParser parser = new JsonParser();
				String result = parser.parse(responseString).getAsJsonObject().getAsJsonPrimitive("result").getAsString();
				return result.equals("success");
			}
		
		} 
		catch (Exception e) 
		{
			return false;
		}
		return false;
		
	}
	public boolean updateTorrentInfo()
	{
		try 
		{
			byte[] postData       = "{\"method\":\"torrent-get\",\"arguments\":{\"fields\":[\"id\",\"error\",\"errorString\",\"eta\",\"isFinished\",\"isStalled\",\"leftUntilDone\",\"metadataPercentComplete\",\"peersConnected\",\"peersGettingFromUs\",\"peersSendingToUs\",\"percentDone\",\"queuePosition\",\"rateDownload\",\"rateUpload\",\"recheckProgress\",\"seedRatioMode\",\"seedRatioLimit\",\"sizeWhenDone\",\"haveValid\",\"status\",\"trackers\",\"downloadDir\",\"uploadedEver\",\"uploadRatio\",\"webseedsSendingToUs\"]}}".getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("X-Transmission-Session-Id", authKey);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
				
				JsonParser parser = new JsonParser();
				this.torrentInfo = parser.parse(responseString).getAsJsonObject();
				return true;
			}
		} catch (Exception e) 
		{
			;
		}
		return false;
	}
	public float getUploadSpeed()
	{
		try 
		{
			byte[] postData       = "{\"method\":\"session-stats\"}".getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("X-Transmission-Session-Id", authKey);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
//				
				JsonParser parser = new JsonParser();
				JsonObject o = parser.parse(responseString).getAsJsonObject().getAsJsonObject("arguments");
				JsonPrimitive space = o.getAsJsonPrimitive("uploadSpeed");
				return space.getAsLong()/1024f/1024f;
			}
			return -1;
		} catch (Exception e) 
		{
			return -2;
		}
	}
	private JsonObject getTorrentInfo(int id)
	{
		try 
		{
			byte[] postData       = ("{\"method\": \"torrent-get\",\"arguments\": {\"fields\": [\"name\",\"id\",\"uploadedEver\",\"activityDate\",\"corruptEver\",\"desiredAvailable\",\"downloadedEver\",\"haveUnchecked\",\"haveValid\",\"peers\",\"startDate\"],\"ids\": ["+id+"]}}").getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("X-Transmission-Session-Id", authKey);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
//				
				JsonParser parser = new JsonParser();
				return parser.parse(responseString).getAsJsonObject().getAsJsonObject("arguments");
			}
		} catch (Exception e) 
		{
			;
		}
		return null;
	}
	public JsonArray getTorrentFiles(int id)
	{
		try 
		{
			byte[] postData       = ("{\"method\":\"torrent-get\",\"arguments\":{\"fields\":[\"files\"],\"ids\":["+id+"]}}").getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("X-Transmission-Session-Id", authKey);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
//				
				JsonParser parser = new JsonParser();
				
				return parser.parse(responseString).getAsJsonObject().getAsJsonObject("arguments").getAsJsonArray("torrents").get(0).getAsJsonObject().getAsJsonArray("files");
			}
		} catch (Exception e) 
		{
			e.printStackTrace();
		}
		return null;
	}
	public float getDownloadSpeed()
	{
		try 
		{
			byte[] postData       = "{\"method\":\"session-stats\"}".getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request = url;
			URL url = new URL(request);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", loginString);
			conn.setRequestProperty("X-Transmission-Session-Id", authKey);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int response = conn.getResponseCode();
			if (response == 200)
			{
				String authKey = conn.getHeaderField("X-Transmission-Session-Id");
				if (authKey!=null)
				{
					this.authKey = authKey;
				}
				
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				String responseString = "";

				while ((inputLine = in.readLine()) != null) 
				{
					responseString+=inputLine;
				}
				in.close();
//				
				JsonParser parser = new JsonParser();
				JsonObject o = parser.parse(responseString).getAsJsonObject().getAsJsonObject("arguments");
				JsonPrimitive space = o.getAsJsonPrimitive("downloadSpeed");
				return space.getAsLong()/1024f/1024f;
			}
			return -1;
		} catch (Exception e) 
		{
			return -2;
		}
	}
}
