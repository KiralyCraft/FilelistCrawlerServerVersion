package com.kiralycraft.fcsv;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.kiralycraft.fcsv.torrentinterfaces.GenericClientInterface;

public class RunThread extends Thread implements Runnable 
{
	String username;
	String password;
	String cfduid;
	String phpsessid;
	String pass;
	String uid;
	String fl;
	File downloadFolder;
	boolean loginwithusername;
	boolean freelechonly;
	double seedleechratio;
	SaveManager saveman;
	boolean interrupted=false;
	GenericClientInterface connection;
	ArrayList<String> torrentsPendingDownload;
	
	float uploadSpeedAvg = 0;
	int speedMeasurements = 0;
	float uploadSpeedAvgFinal = 0;
	int uploadPotential = 100;
	
	float maxSpeedUp = 0.1f;
	
	long softQuotaBytes;
	
	ThxHandler thxhandler;
	
	///////////////////////////////////////////////////
	ArrayList<TorrentData> torrentDataList = new ArrayList<TorrentData>();
	class TorrentData
	{
		String torrentName;
		boolean freeleech=false;
		String downloadLink;
		double downloadSize = 0;
		int leechers = 0;
		int seeders = 0;
		float leechseedratio=9999;
		String id;
		
		public TorrentData(String namex,boolean freelechx, String downLinkx, double sizeGBx,int leechersx,int seedersx, String id)
		{
			torrentName = namex;
			freeleech = freelechx;
			downloadLink = downLinkx;
			downloadSize = sizeGBx;
			leechers = leechersx;
			seeders = seedersx;
			if (leechers>0)
			{
				leechseedratio = (float)seeders / (float)leechers;
			}
			this.id = id;
		}
	}
	///////////////////////////////////////////////////
	
	public RunThread(boolean loginwithusername,String username,String password,String cfduid,String phpsessid,String pass,String uid,String fl,String downloadFolder,String freelechonly,String seedleechratio,SaveManager saveman,GenericClientInterface connection, long softQuotaBytes)
	{
		this.softQuotaBytes = softQuotaBytes;
		this.username = username;
		this.password = password;
		this.cfduid = cfduid;
		this.phpsessid = phpsessid;
		this.pass = pass;
		this.uid = uid;
		this.fl = fl;
		this.downloadFolder = new File(downloadFolder);
		this.freelechonly = freelechonly.toLowerCase().equals("true")?true:false;
		this.seedleechratio = Double.parseDouble(seedleechratio);
		this.saveman = saveman;
		this.loginwithusername = loginwithusername;
		this.connection = connection;
		this.torrentsPendingDownload = new ArrayList<String>();
		interrupted = false;
		try
		{
			thxhandler = new ThxHandler(this.cfduid,this.pass,this.phpsessid,this.uid,this.fl);
		}
		catch (IOException e)
		{
			thxhandler = new ThxHandler();
		}
	}
	public void interupt()
	{
		interrupted=true;
	}
	@Override
	public void run()
	{
		Logger.log("Starting in progress");
		Logger.log("Connecting to The Torrent Client ...");
		int loginResult = connection.login();
		if (loginResult == 1)
		{
			Logger.log("Connection to The Torrent Client successfull");
			if (loginwithusername)
			{
				Logger.log("Logging into Filelist.ro with username and password");
				 if (FLloginProcedure()==true)
				 {
					 Logger.log("Logged in!");
				 }
			}
			else
			{
				Logger.log("Logging into Filelist.ro with cookies.");
			}
			
			//////LOADING SAVED STATISTICS///////////
			String tmpRead = saveman.getKey("maxspeedup");
			if (!tmpRead.equals("null"))
			{
				maxSpeedUp = Float.parseFloat(tmpRead);
			}
			else
			{
				maxSpeedUp = 0.1f;
			}
			/////////////////////////////////////////
			
			while(true)
			{
				if (Thread.currentThread().isInterrupted())
				{	
					break;
				}
				try 
				{
					Logger.log("Clearing Torrent Cache ...");
					torrentDataList.clear();
					Logger.log("Getting the 1st torrent page ...");
					String browsePage = getBrowsePage(cfduid,pass,phpsessid,uid,fl);
					Logger.log("Grabbing torrent data ...");
					getTorrentData(browsePage);
					Logger.log("Analyzing results ...");
					Pair<Integer,Integer> torrentCount = parseTorrentData();
					Logger.log("Analysis complete. Torrents that meet the requirements: "+torrentCount.getKey()+" out of "+torrentCount.getValue());
					Logger.log("Now uploading to The Torrent Client ...");
					uploadPendingTorrents();
					
					saveman.setKey("cfduid", cfduid+"");
					saveman.setKey("phpsessionid", phpsessid+"");
					saveman.setKey("uid", uid+"");
					saveman.setKey("pass", pass+"");
					saveman.setKey("fl", fl+"");
					saveman.setKey("usernamepassword", "false");
					
					Logger.log("Got cookies from Filelist.ro. Will use those instead next time.");
					
	//				Utils.saveData(mainInstance, saveman);
					
	//				Thread.sleep(10000);
					
					uploadSpeedAvg = 0;
					speedMeasurements = 0;
					for (int i=1;i<=600/5;i++) 
					{
						if (interrupted)
						{
							break;
						}
						Utils.sleep(1000*5); 
						float tmpUploadSpeed = connection.getUploadSpeed();
						if (tmpUploadSpeed == -1)
						{
							Logger.log("The Torrent Client kicked us. Let's re-login!");
							if (connection.login() == 1)
							{
								Logger.log("OK");
							}
							else
							{
								Logger.log("Something failed terribly wrong. The Torrent Client will not accept us!");
							}
						}
						uploadSpeedAvg+=tmpUploadSpeed;
						speedMeasurements++;
						if (i%4 == 0) // la fiecare 5*4 secunde
						{
							if (speedMeasurements!=0)
							{
								uploadSpeedAvgFinal = uploadSpeedAvg/speedMeasurements;
								uploadPotential = Math.min(100,(int)((uploadSpeedAvgFinal*100)/maxSpeedUp));
								Logger.log("Avg upload speed: "+String.format("%5.2f", uploadSpeedAvgFinal)+" MB/s. Upload potential "+String.format("%2d", uploadPotential)+"%");
								maxSpeedUp = Math.max(maxSpeedUp, uploadSpeedAvgFinal);
								uploadSpeedAvg=0;
								speedMeasurements=0;
							}
						}
					}
					
					/////////SAVING STATISTICS//////////
					saveman.setKey("maxspeedup",maxSpeedUp+"");
					////////////////////////////////////
					
					if (interrupted)
					{
						break;
					}
				} catch (Exception e) 
				{
					if (!(e instanceof InterruptedException))
					{
						Logger.log("ERROR OCCURED! "+e.getMessage());
						e.printStackTrace();
						try 
						{
							Thread.sleep(600000);
						} catch (InterruptedException e1) {}
					}
				}
				
			}
			Logger.log("Worker thread has shut down");
		}
		else if (loginResult == 2)
		{
			Logger.log("Invalid The Torrent Client username or password. Authentication failed.");
		}
		else if (loginResult == 3)
		{
			Logger.log("Invalid JSON cand trimiteam spre The Torrent Client");
		}
		else if (loginResult == -1)
		{
			Logger.log("Conectarea la The Torrent Client a rezultat intr-o eroare necunoscuta/nedocumentata.");
		}
	}
	
	private boolean FLloginProcedure() 
	{
		try 
		{
			Logger.log("Acquiring CFDUID ...");
			List<String> tmpLoginData = getCFDUID();
			updateData(tmpLoginData);
			Logger.log("Logging in ...");
			updateData(getLoginData(cfduid, username, password));
			thxhandler.updateData(this.cfduid,this.pass,this.phpsessid,this.uid,this.fl);
			return true;
		} catch (Exception e) 
		{
			Logger.log("Error! "+e.getMessage());
			return false;
		}
	}
	private void uploadPendingTorrents() 
	{
		Logger.log("Asking The Torrent Client where to store the torrent");
		String downLocation = this.connection.getFreeSpaceAndDownDir().getValue();
		if (downLocation.length()==0)
		{
			Logger.log("The Torrent Client gave us fucked up download location!");
		}
		else
		{
			Logger.log("The Torrent Client replied with: \""+downLocation+"\"");
			int counter = 0;
			for (String s:torrentsPendingDownload)
			{
				File torrent = new File(s);
				if (torrent.exists() && torrent.isFile())
				{
					Logger.log("Uploading \""+torrent.getName()+"\" to The Torrent Client...");
					boolean okay = this.connection.uploadNewTorrent(downLocation, torrent);
					if (okay==false)
					{
						Logger.log("Upload failed!");
					}
					else
					{
						Logger.log("Done.");
						counter++;
					}
				}
				else
				{
					Logger.log("Torrent does not exist when trying to upload!");
					Logger.log("Expected filename: "+torrent.getName());
					Logger.log("Expected location: "+s);
				}
			}

			Logger.log("Upload done! "+counter+" / "+torrentsPendingDownload.size());
			torrentsPendingDownload.clear();
		}
	}
//	public void Logger.log(String s)
//	{
//		System.out.println(getHour() +" "+ s);
//	}
//	private String getHour()
//	{
//		Date date=new Date();    
//		return "["+new SimpleDateFormat("HH:mm").format(date)+"]";
//	}
	/////////////////////////////////////////
	/////////////////////////////////////////
	/////////////////////////////////////////
	/////////////////////////////////////////
	/////////////////////////////////////////
	public String getTorrentFilename(String str)
	{
//		try 
//		{
//			return URLDecoder.decode(str.substring(str.lastIndexOf("=")+1,str.length()),"UTF-8").replace("\"", "");
//		} catch (UnsupportedEncodingException e) {
//			return str.substring(str.lastIndexOf("=")+1,str.length()).replace("\"", "");
//		}
		return str.trim().replace(" ", ".")+".torrent";
	}
	/////////////////////////////////////
	///////////WEB INTERFACE/////////////
	/////////////////////////////////////
	public List<String> getCFDUID() throws Exception
	{
		String request        = "https://filelist.ro/login.php";
		URL    url            = new URL( request );
		HttpsURLConnection conn= (HttpsURLConnection) url.openConnection();           
		conn.setDoOutput( true );
		conn.setInstanceFollowRedirects( false );
		conn.setRequestMethod( "GET" );
		conn.setUseCaches( false );
		List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
		return cookies;
	}
	private void updateData(List<String> tmpCookies) 
	{
		for (String s:tmpCookies)
		{
			if (!s.contains("deleted"))
			{
				if (s.contains("__cfduid"))
				{
					cfduid = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					Logger.log("CFDUID: "+cfduid);
					
				}
				else if (s.contains("PHPSESSID"))
				{
					phpsessid = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					Logger.log("PHPSESSID: "+phpsessid);
				}
				else if (s.contains("pass"))
				{
					pass = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					Logger.log("PASS: "+pass);
				}
				else if (s.contains("uid"))
				{
					uid = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					Logger.log("UID: "+uid);
				}
				else if (s.contains("fl"))
				{
					fl = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					Logger.log("FL: "+fl);
				}
			}
		}
	}
	public List<String> getLoginData(String cfduidtmp,String user,String password) throws Exception
	{
		String urlParameters  = "username="+user+"&password="+password;
		if (!connection.isLocalInstance())
		{
			Logger.log("Activating \"Login on any IP\" because this is a remote instance.");
			urlParameters+="&unlock=1";
		}
		byte[] postData       = urlParameters.getBytes( StandardCharsets.UTF_8 );
		int    postDataLength = postData.length;
		String request        = "https://filelist.ro/takelogin.php";
		URL    url            = new URL( request );
		HttpsURLConnection conn= (HttpsURLConnection) url.openConnection();    
		conn.setDoOutput( true );
		conn.setInstanceFollowRedirects( false );
		conn.setRequestMethod( "POST" );

		conn.setRequestProperty( "Cookie", "__cfduid="+cfduidtmp);
		conn.setRequestProperty( "Content-Length", Integer.toString( postDataLength ));
		conn.connect();
		try( DataOutputStream wr = new DataOutputStream( conn.getOutputStream())) 
		{
		   wr.write( postData );
		}
		
		List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
		return cookies;
	}
	public String getBrowsePage(String cfduidtmp,String passtmp,String phpsessidtmp,String uidtmp, String fl2) throws Exception
	{
		String request        = "https://filelist.ro/browse.php";
		URL    url            = new URL( request );
		HttpsURLConnection conn= (HttpsURLConnection) url.openConnection();    
		conn.setDoOutput( true );
		conn.setInstanceFollowRedirects( false );
		conn.setRequestMethod( "POST" );
		conn.setRequestProperty( "Cookie", "__cfduid="+cfduidtmp+"; PHPSESSID="+phpsessidtmp+"; uid="+uidtmp+"; pass="+passtmp+"; fl="+fl2);
		conn.connect();
		
		int responseCode = conn.getResponseCode();
		Logger.log("Response Code : " + responseCode);
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(conn.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine+"\n");
		}
		in.close();
		
		if (responseCode!=200)
		{
			Logger.log("");
			Logger.log("=====WARNING=====");
			Logger.log("Response code was supposed to be 200, it was "+responseCode);
			Logger.log("Check your Filelist.ro login data, it might not be correct");
			Logger.log("=================");
			Logger.log("");
			
			Logger.log("We will try to log in with username & password to recover from this.");
			if (FLloginProcedure())
			{
				Logger.log("Wohoo! Everything's okay now.");
			}
			else
			{
				Logger.log("Failed to log into Filelist.ro with username & password.");
			}
		}
		return response.toString();
	}
	public void downloadTorrent(String cfduidtmp,String passtmp,String phpsessidtmp,String uidtmp,String downloadLink,File expectedTorrentPath) throws Exception
	{
		String request        = "https://filelist.ro/"+downloadLink;
		URL    url            = new URL( request );
		HttpsURLConnection conn= (HttpsURLConnection) url.openConnection();    
		conn.setDoOutput( true );
		conn.setInstanceFollowRedirects( false );
		conn.setRequestMethod( "GET" );
		conn.setRequestProperty( "Cookie", "__cfduid="+cfduidtmp+"; PHPSESSID="+phpsessidtmp+"; uid="+uidtmp+"; pass="+passtmp);
		conn.connect();
		
		int responseCode = conn.getResponseCode();
		Logger.log("Response Code : " + responseCode);
		
//		String contentDisposition = conn.getHeaderField("Content-Disposition"); //old methods of getting filename
//		String fileName = contentDisposition.substring(contentDisposition.indexOf("=")+1, contentDisposition.length()).replace("\"", "");
		
		BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
		FileOutputStream fos = new FileOutputStream(expectedTorrentPath);
		byte buf[] = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0)
        {
			fos.write(buf, 0, len);
		}
		in.close();
		fos.close();
	}
	//////////////////////////////////////
	//////////////////////////////////////
	//////////////////////////////////////
	
	//////////////////////////////////////
	////////PROCESSING DATA///////////////
	//////////////////////////////////////
	private void getTorrentData(String str) 
	{
		Document doc = Jsoup.parse(str);
		
//		Element content = doc.getElementById("content");
		Elements torrents = doc.select("div.torrentrow");
		for (Element link : torrents)
		{
			  String torrentName;
			  boolean freeleech=false;
			  String downloadLink;
			  double downloadSize = 0;
			  int leechers = 0;
			  int seeders = 0;
			  String id;
			  ////////////////////////////
	          Elements torrentData = link.select("div.torrenttable");
	          torrentName = torrentData.get(1).select("a[href]").attr("title");
	          for (Element e:torrentData.get(1).getElementsByTag("img"))
	          {
	        	  if (e.attr("src").contains("freeleech"))
	        	  {
	        		  freeleech = true;
	        		  break;
	        	  }
	          }
	          downloadLink = torrentData.get(3).select("a[href]").get(0).attr("href");
	          id = downloadLink.substring(downloadLink.lastIndexOf("id=")+"id=".length());
	          
	          Element torrentSizeDiv = torrentData.get(6);
	          String torrentSizeData[] = torrentSizeDiv.text().split("\\ ");

	          if (torrentSizeData[1].equalsIgnoreCase("GB"))
	          {
	        	  downloadSize = Double.parseDouble(torrentSizeData[0]);
	          }
	          else if (torrentSizeData[1].equalsIgnoreCase("MB"))
	          {
	        	  downloadSize = Double.parseDouble(torrentSizeData[0])/1000;
	          }
	          Element seedersDiv = torrentData.get(8);
	          seeders = Integer.parseInt(seedersDiv.text().replaceAll("\\D", ""));
	          Element leechersDiv = torrentData.get(9);
	          leechers = Integer.parseInt(leechersDiv.text().replaceAll("\\D", ""));
	          ////////////////////////////////////////////////
	          torrentDataList.add(new TorrentData(torrentName,freeleech,downloadLink,downloadSize,leechers,seeders,id));
	    }
	}

	private Pair<Integer,Integer> parseTorrentData() throws Exception 
	{
		int torrentsDownloaded = 0;
		int totalSize=0;
		
		int retryCount = 0;
		int maxRetry = 2;
		
		long currentUsedSpace = 0;
		if (softQuotaBytes!=-1)
		{
			Logger.log("SOFT QUOTA IS ACTIVATED: "+softQuotaBytes/1000/1000+" MB");
			Logger.log("Asking The Torrent Client how much current torrents take.");
			currentUsedSpace = connection.getUsedSpace();
			Logger.log("Got response: "+currentUsedSpace+" bytes = "+currentUsedSpace/1000/1000+" MB");
		}
		
		Logger.log("Asking The Torrent Client how much free space there is.");
		long freeSpaceOnTransmission = connection.getFreeSpaceAndDownDir().getKey();
				
		if (freeSpaceOnTransmission == -1)
		{
			Logger.log("The Torrent Client returned wrong response code while asking for free space!");
		}
		else if (freeSpaceOnTransmission == -2)
		{
			Logger.log("The Torrent Client got an error asking for free space. Oh shit.");
		}
		else
		{
			Logger.log("Got response: "+freeSpaceOnTransmission+" bytes = "+freeSpaceOnTransmission/1000/1000+" MB");
			
			Logger.log("Asking The Torrent Client how much free is required for current downloading torrents.");
            long freeSpaceForDownloadingTorrents = connection.getDownloadingTorrentsSpaceNeeded();     
            if (freeSpaceForDownloadingTorrents == -1) 
            {
                Logger.log("The Torrent Client returned wrong response code while asking for required space for downloading torrents!");
            }
            else 
            {
            	Logger.log("Got response: "+freeSpaceForDownloadingTorrents+" bytes = "+freeSpaceForDownloadingTorrents/1000/1000+" MB");
				for (int i=0;i<torrentDataList.size();i++)
				{
					TorrentData td = torrentDataList.get(i);
					boolean freelechCheck = freelechonly && td.freeleech;
					boolean ratioCheck = td.leechseedratio<=seedleechratio;
					if (freelechCheck && ratioCheck)
					{
						Logger.log("==========================================");
						Logger.log(td.torrentName+" - Checking requirements ...");
						File expectedTorrentPath = new File(downloadFolder.getAbsolutePath()+File.separator+getTorrentFilename(td.torrentName));
						if (!expectedTorrentPath.exists())
						{
							if ((freeSpaceOnTransmission-freeSpaceForDownloadingTorrents)/1000d/1000d/1000d>=(totalSize+td.downloadSize) //daca avem destul spatiu fizic pentru torrent
									&& (softQuotaBytes==-1 || (totalSize+td.downloadSize+currentUsedSpace/1000d/1000d/1000d)<softQuotaBytes/1000d/1000d/1000d))//daca quota e dezactivat, true aici. daca nu, si download curent + cat e ocupat deja > quota, false
 							{
								torrentsPendingDownload.add(expectedTorrentPath.getAbsolutePath());
								totalSize+=td.downloadSize;
								Logger.log("Okay! Downloading torrent: "+td.torrentName);
								Logger.log("Total size: "+td.downloadSize+" GB, seeders: "+td.seeders+", leechers: "+td.leechers+", ratio: "+td.leechseedratio);
								Logger.log("Free space remaining after the download: "+((freeSpaceOnTransmission-freeSpaceForDownloadingTorrents)/1000d/1000d/1000d-totalSize)+" GB");
								Logger.log("Adding pending THX for torrent ID: "+td.id);
								thxhandler.addPendingThx(td.id);
								try 
								{
									downloadTorrent(cfduid,pass,phpsessid,uid,td.downloadLink,expectedTorrentPath); //trebuie specificat numele aici
									Logger.log("Success!");
									torrentsDownloaded++;
								} 
								catch (Exception e) 
								{
									Logger.log("ERROR! "+e.getMessage());
								}
							}
							else
							{
                                if (softQuotaBytes!=-1)
                                {
                                	Logger.log("With this torrent, out total used space would be: "+(totalSize+td.downloadSize+currentUsedSpace/1000d/1000d/1000d)+" GB.");
                                }
								Logger.log("Not enough space to download "+td.torrentName+". It requires "+td.downloadSize+" GB");
								Logger.log("Will ask The Torrent Client to do a cleanup.");
								if (retryCount>maxRetry)
								{
									retryCount = 0;
									i++;
									Logger.log("Infinite loop detected, skipping torrent.");
								}
								else
								{
									if (connection.cleanup((long) (td.downloadSize*1000l*1000l*1000l),softQuotaBytes))
									{
										Logger.log("Cleanup successful! Will try to download this torrent again.");
										
										Logger.log("Sleeping for 10 seconds to allow The Torrent Client to update it's stats about free space");
										Utils.sleep(10000);
										
										Logger.log("Asking The Torrent Client again how much free space there is.");
										freeSpaceOnTransmission = connection.getFreeSpaceAndDownDir().getKey();
										Logger.log("Got response: "+freeSpaceOnTransmission+" bytes = "+freeSpaceOnTransmission/1000/1000+" MB");
										
										Logger.log("Asking The Torrent Client again  how much free is required for current downloading torrents.");
                                        freeSpaceForDownloadingTorrents = connection.getDownloadingTorrentsSpaceNeeded();
                                        Logger.log("Got response: "+freeSpaceForDownloadingTorrents+" bytes = "+freeSpaceForDownloadingTorrents/1000/1000+" MB");
                                        Logger.log("Available space is : "+(freeSpaceOnTransmission-freeSpaceForDownloadingTorrents)+" bytes = "+(freeSpaceOnTransmission-freeSpaceForDownloadingTorrents)/1000/1000+" MB");
                                        
                                        Logger.log("Asking The Torrent Client how much current torrents take.");
                            			currentUsedSpace = connection.getUsedSpace();
                            			Logger.log("Got response: "+currentUsedSpace+" bytes = "+currentUsedSpace/1000/1000+" MB");
                                        
                                        i--;
										retryCount++;
										
										
									}
									else
									{
										Logger.log("The Torrent Client failed to clean up enough space for this torrent, so we're skipping it.");
									}
								}
							}
						}
						else
						{
							Logger.log(td.torrentName+" already exists.");
						}
						Logger.log("==========================================");
						Logger.log("");
					}
				}
            }
		}
		
		
		//THX ROUTINE
		Logger.log("Starting to THX torrents. To go: "+thxhandler.getPendingThxCount());
		int thxed = 0;
		int raspThx;
		int index = 0;
		do {
			raspThx = thxhandler.doThx(index);
			if (raspThx == 1) {
				thxed++;
				Logger.log("Got one! So far: "+thxed);
			} else if (raspThx == 0) {
				Logger.log("This one was skiped");
				index++;
			}
		} while (raspThx != -1);
		Logger.log("Got "+(thxed*0.5d)+" FLCoins.");
		
		//
		
		return new Pair<Integer,Integer>(torrentsDownloaded,torrentDataList.size());
	}
	//////////////////////////////////////
	//////////////////////////////////////
	//////////////////////////////////////
}
