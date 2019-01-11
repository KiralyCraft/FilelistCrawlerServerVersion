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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.kiralycraft.tapidiy.Connection;
import com.kiralycraft.tapidiy.Pair;

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
	Connection connection;
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
	
	public RunThread(boolean loginwithusername,String username,String password,String cfduid,String phpsessid,String pass,String uid,String fl,String downloadFolder,String freelechonly,String seedleechratio,SaveManager saveman,Connection connection, long softQuotaBytes)
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
		log("Starting in progress");
		log("Connecting to Transmission ...");
		int loginResult = connection.login();
		if (loginResult == 1)
		{
			log("Connection to Transmission successfull");
			if (loginwithusername)
			{
				log("Logging into Filelist.ro with username and password");
				 if (FLloginProcedure()==true)
				 {
					 log("Logged in!");
				 }
			}
			else
			{
				log("Logging into Filelist.ro with cookies.");
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
					log("Clearing Torrent Cache ...");
					torrentDataList.clear();
					log("Getting the 1st torrent page ...");
					String browsePage = getBrowsePage(cfduid,pass,phpsessid,uid,fl);
					log("Grabbing torrent data ...");
					getTorrentData(browsePage);
					log("Analyzing results ...");
					Pair<Integer,Integer> torrentCount = parseTorrentData();
					log("Analysis complete. Torrents that meet the requirements: "+torrentCount.getKey()+" out of "+torrentCount.getValue());
					log("Now uploading to Transmission ...");
					uploadPendingTorrents();
					
					saveman.setKey("cfduid", cfduid+"");
					saveman.setKey("phpsessionid", phpsessid+"");
					saveman.setKey("uid", uid+"");
					saveman.setKey("pass", pass+"");
					saveman.setKey("fl", fl+"");
					saveman.setKey("usernamepassword", "false");
					
					log("Got cookies from Filelist.ro. Will use those instead next time.");
					
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
							log("Transmission kicked us. Let's re-login!");
							if (connection.login() == 1)
							{
								log("OK");
							}
							else
							{
								log("Something failed terribly wrong. Transmission will not accept us!");
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
								log("Avg upload speed: "+String.format("%5.2f", uploadSpeedAvgFinal)+" MB/s. Upload potential "+String.format("%2d", uploadPotential)+"%");
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
						log("ERROR OCCURED! "+e.getMessage());
						e.printStackTrace();
						try 
						{
							Thread.sleep(600000);
						} catch (InterruptedException e1) {}
					}
				}
				
			}
			log("Worker thread has shut down");
		}
		else if (loginResult == 2)
		{
			log("Invalid Transmission username or password. Authentication failed.");
		}
		else if (loginResult == 3)
		{
			log("Invalid JSON cand trimiteam spre Transmission");
		}
		else if (loginResult == -1)
		{
			log("Conectarea la Transmission a rezultat intr-o eroare necunoscuta/nedocumentata.");
		}
	}
	
	private boolean FLloginProcedure() 
	{
		try 
		{
			log("Acquiring CFDUID ...");
			List<String> tmpLoginData = getCFDUID();
			updateData(tmpLoginData);
			log("Logging in ...");
			updateData(getLoginData(cfduid, username, password));
			thxhandler.updateData(this.cfduid,this.pass,this.phpsessid,this.uid,this.fl);
			return true;
		} catch (Exception e) 
		{
			log("Error! "+e.getMessage());
			return false;
		}
	}
	private void uploadPendingTorrents() 
	{
		log("Asking Transmission where to store the torrent");
		String downLocation = this.connection.getFreeSpaceAndDownDir().getValue();
		if (downLocation.length()==0)
		{
			log("Transmission gave us fucked up download location!");
		}
		else
		{
			log("Transmission replied with: \""+downLocation+"\"");
			int counter = 0;
			for (String s:torrentsPendingDownload)
			{
				File torrent = new File(s);
				if (torrent.exists() && torrent.isFile())
				{
					log("Uploading \""+torrent.getName()+"\" to Transmission...");
					boolean okay = this.connection.uploadNewTorrent(downLocation, torrent);
					if (okay==false)
					{
						log("Upload failed!");
					}
					else
					{
						log("Done.");
						counter++;
					}
				}
				else
				{
					log("Torrent does not exist when trying to upload!");
					log("Expected filename: "+torrent.getName());
					log("Expected location: "+s);
				}
			}

			log("Upload done! "+counter+" / "+torrentsPendingDownload.size());
			torrentsPendingDownload.clear();
		}
	}
	public void log(String s)
	{
		System.out.println(getHour() +" "+ s);
	}
	private String getHour()
	{
		Date date=new Date();    
		return "["+new SimpleDateFormat("HH:mm").format(date)+"]";
	}
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
					log("CFDUID: "+cfduid);
					
				}
				else if (s.contains("PHPSESSID"))
				{
					phpsessid = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					log("PHPSESSID: "+phpsessid);
				}
				else if (s.contains("pass"))
				{
					pass = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					log("PASS: "+pass);
				}
				else if (s.contains("uid"))
				{
					uid = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					log("UID: "+uid);
				}
				else if (s.contains("fl"))
				{
					fl = s.substring(s.indexOf("=")+1, s.indexOf(";"));
					log("FL: "+fl);
				}
			}
		}
	}
	public List<String> getLoginData(String cfduidtmp,String user,String password) throws Exception
	{
		String urlParameters  = "username="+user+"&password="+password;
		if (!connection.isLocalInstance())
		{
			log("Activating \"Login on any IP\" because this is a remote instance.");
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
		log("Response Code : " + responseCode);
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
			log("");
			log("=====WARNING=====");
			log("Response code was supposed to be 200, it was "+responseCode);
			log("Check your Filelist.ro login data, it might not be correct");
			log("=================");
			log("");
			
			log("We will try to log in with username & password to recover from this.");
			if (FLloginProcedure())
			{
				log("Wohoo! Everything's okay now.");
			}
			else
			{
				log("Failed to log into Filelist.ro with username & password.");
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
		log("Response Code : " + responseCode);
		
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
		
		log("Asking Transmission how much free space there is.");
		long freeSpaceOnTransmission = connection.getFreeSpaceAndDownDir().getKey();
		
		long currentUsedSpace = 0;
		if (softQuotaBytes!=-1)
		{
			log("SOFT QUOTA IS ACTIVATED: "+softQuotaBytes/1000/1000+" MB");
			log("Asking Transmission how much current torrents take.");
			currentUsedSpace = connection.getUsedSpace();
			log("Got response: "+currentUsedSpace+" bytes = "+currentUsedSpace/1000/1000+" MB");
		}
		
		
		if (freeSpaceOnTransmission == -1)
		{
			log("Transmission returned wrong response code while asking for free space!");
		}
		else if (freeSpaceOnTransmission == -2)
		{
			log("Transmission got an error asking for free space. Oh shit.");
		}
		else
		{
			log("Got response: "+freeSpaceOnTransmission+" bytes = "+freeSpaceOnTransmission/1000/1000+" MB");
			
			log("Asking Transmission how much free is required for current downloading torrents.");
            long freeSpaceForDownloadingTorrents = connection.getDownloadingTorrentsSpaceNeeded();     
            if (freeSpaceForDownloadingTorrents == -1) 
            {
                log("Transmission returned wrong response code while asking for required space for downloading torrents!");
            }
            else 
            {
            	log("Got response: "+freeSpaceForDownloadingTorrents+" bytes = "+freeSpaceForDownloadingTorrents/1000/1000+" MB");
				for (int i=0;i<torrentDataList.size();i++)
				{
					TorrentData td = torrentDataList.get(i);
					boolean freelechCheck = freelechonly && td.freeleech;
					boolean ratioCheck = td.leechseedratio<=seedleechratio;
					if (freelechCheck && ratioCheck)
					{
						log("==========================================");
						log(td.torrentName+" - Checking requirements ...");
						File expectedTorrentPath = new File(downloadFolder.getAbsolutePath()+File.separator+getTorrentFilename(td.torrentName));
						if (!expectedTorrentPath.exists())
						{
							if ((freeSpaceOnTransmission-freeSpaceForDownloadingTorrents)/1000d/1000d/1000d>=(totalSize+td.downloadSize) //daca avem destul spatiu fizic pentru torrent
									&& (softQuotaBytes==-1 || (totalSize+td.downloadSize+currentUsedSpace/1000d/1000d/1000d)<softQuotaBytes/1000d/1000d/1000d))//daca quota e dezactivat, true aici. daca nu, si download curent + cat e ocupat deja > quota, false
 							{
								torrentsPendingDownload.add(expectedTorrentPath.getAbsolutePath());
								totalSize+=td.downloadSize;
								log("Okay! Downloading torrent: "+td.torrentName);
								log("Total size: "+td.downloadSize+" GB, seeders: "+td.seeders+", leechers: "+td.leechers+", ratio: "+td.leechseedratio);
								log("Free space remaining after the download: "+((freeSpaceOnTransmission-freeSpaceForDownloadingTorrents)/1000d/1000d/1000d-totalSize)+" GB");
								log("Adding pending THX for torrent ID: "+td.id);
								thxhandler.addPendingThx(td.id);
								try 
								{
									downloadTorrent(cfduid,pass,phpsessid,uid,td.downloadLink,expectedTorrentPath); //trebuie specificat numele aici
									log("Success!");
									torrentsDownloaded++;
								} 
								catch (Exception e) 
								{
									log("ERROR! "+e.getMessage());
								}
							}
							else
							{
                                if (softQuotaBytes!=-1)
                                {
                                	log("With this torrent, out total used space would be: "+(totalSize+td.downloadSize+currentUsedSpace/1000d/1000d/1000d)+" GB.");
                                }
								log("Not enough space to download "+td.torrentName+". It requires "+td.downloadSize+" GB");
								log("Will ask Transmission to do a cleanup.");
								if (retryCount>maxRetry)
								{
									retryCount = 0;
									i++;
									log("Infinite loop detected, skipping torrent.");
								}
								else
								{
									if (connection.cleanup((long) (td.downloadSize*1000l*1000l*1000l),softQuotaBytes))
									{
										log("Cleanup successful! Will try to download this torrent again.");
										log("Asking Transmission again how much free space there is.");
										freeSpaceOnTransmission = connection.getFreeSpaceAndDownDir().getKey();
										log("Got response: "+freeSpaceOnTransmission+" bytes = "+freeSpaceOnTransmission/1000/1000+" MB");
										
										log("Asking Transmission again  how much free is required for current downloading torrents.");
                                        freeSpaceForDownloadingTorrents = connection.getDownloadingTorrentsSpaceNeeded();
                                        log("Got response: "+freeSpaceForDownloadingTorrents+" bytes = "+freeSpaceForDownloadingTorrents/1000/1000+" MB");
                                        log("Available space is : "+(freeSpaceOnTransmission-freeSpaceForDownloadingTorrents)+" bytes = "+(freeSpaceOnTransmission-freeSpaceForDownloadingTorrents)/1000/1000+" MB");
                                        
                                        i--;
										retryCount++;
									}
									else
									{
										log("Transmission failed to clean up enough space for this torrent, so we're skipping it.");
									}
								}
							}
						}
						else
						{
							log(td.torrentName+" already exists.");
						}
						log("==========================================");
						log("");
					}
				}
            }
		}
		
		
		//THX ROUTINE
		log("Starting to THX torrents. To go: "+thxhandler.getPendingThxCount());
		int thxed = 0;
		while(thxhandler.doThx())
		{
			thxed++;
			log("Got one! So far: "+thxed);
		}
		log("Got "+(thxed*0.5d)+" FLCoins.");
		
		///
		
		return new Pair<Integer,Integer>(torrentsDownloaded,torrentDataList.size());
	}
	//////////////////////////////////////
	//////////////////////////////////////
	//////////////////////////////////////
}
