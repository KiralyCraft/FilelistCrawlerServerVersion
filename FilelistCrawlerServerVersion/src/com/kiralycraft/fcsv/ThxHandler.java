package com.kiralycraft.fcsv;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class ThxHandler
{
	private String cfduidtmp;
	private String passtmp;
	private String phpsessidtmp;
	private String uidtmp;
	private String fl2;
	private File thxFile;
	
	private ArrayList<String> thxQueue;//on purpose not a Queue
	public ThxHandler(String cfduidtmp, String passtmp, String phpsessidtmp, String uidtmp, String fl2) throws IOException
	{
		this.cfduidtmp = cfduidtmp;
		this.passtmp = passtmp;
		this.phpsessidtmp = phpsessidtmp;
		this.uidtmp = uidtmp;
		this.fl2 = fl2;
		
		this.thxFile = new File("pendingthx.txt");
		this.thxQueue = new ArrayList<String>();
		if (!this.thxFile.exists())
		{
			this.thxFile.createNewFile();
		}
		else
		{
			try
			{
				BufferedReader reader = new BufferedReader(new FileReader(this.thxFile));
				String line;
				while ((line = reader.readLine())!= null)
				{
					this.thxQueue.add(line.trim());
				}
				reader.close();
			} 
			catch (IOException e)
			{
				Logger.log("Failed to read pending THX file because: "+e.getMessage());
			}
		}
	}
	public ThxHandler()
	{
		;
	}
	public void dumpToFile()
	{
		try
		{
			PrintWriter pw = new PrintWriter(thxFile);
			for (String s:thxQueue)
			{
				pw.println(s);
			}
			pw.flush();
			pw.close();
		}
		catch (FileNotFoundException e)
		{
			Logger.log("Failed to write pending THX file because: "+e.getMessage());
		}
	}
	public void addPendingThx(String torrentID)
	{
		if (!thxQueue.contains(torrentID))
		{
			Logger.log("Adding pending THX for torrent ID: "+torrentID);
			thxQueue.add(torrentID);
			dumpToFile();
		}
	}
	public boolean doThx() throws Exception
	{
		if (getPendingThxCount() > 0)
		{
			String torrentID = thxQueue.get(0);
			Logger.log("Thx-ing torrent ID: "+torrentID);
			///////ACTUAL OPERATION/////////
			String urlParameters  = "action=add&ajax=1&torrentid="+torrentID;
			byte[] postData       = urlParameters.getBytes( StandardCharsets.UTF_8 );
			int    postDataLength = postData.length;
			String request        = "https://filelist.ro/thanks.php";
			URL    url            = new URL( request );
			HttpsURLConnection conn= (HttpsURLConnection) url.openConnection();    
			conn.setDoOutput( true );
			conn.setInstanceFollowRedirects( false );
			conn.setRequestMethod( "POST" );
			
			conn.setRequestProperty( "Cookie", "__cfduid="+cfduidtmp+"; PHPSESSID="+phpsessidtmp+"; uid="+uidtmp+"; pass="+passtmp+"; fl="+fl2);
			conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
			conn.connect();
			
			try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
			{
			   wr.write( postData );
			}
			
			int responseCode = conn.getResponseCode();
			Logger.log("Response Code : " + responseCode);
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) 
			{
				response.append(inputLine+"\n");
			}
			in.close();
			
			if (responseCode!=200)
			{
				Logger.log("Something went horribly wrong when trying to THX. Got response code: "+responseCode+", expected 200");
			}
			else
			{
				String responseFinal = response.toString();
				if (responseFinal.contains("Hide who thanked!"))
				{
					thxQueue.remove(0);
					dumpToFile();
					Logger.log("THX successful! Removing from queue and dumping to file.");
					return true;
				}
			}
			////////////////////////////////
		}
		else
		{
			Logger.log("There's nothing to THX.");
			return false; 
		}
		Logger.log("THX failed! Quota hit?");
		return false;
	}
	public int getPendingThxCount()
	{
		return thxQueue.size();
	}
	public void updateData(String cfduid, String pass, String phpsessid, String uid, String fl)
	{
		this.cfduidtmp = cfduid;
		this.passtmp = pass;
		this.phpsessidtmp = phpsessid;
		this.uidtmp = uid;
		this.fl2 = fl;
		
	}
}
