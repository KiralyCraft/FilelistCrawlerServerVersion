package com.kiralycraft.fcsv;

import java.io.Console;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Scanner;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.kiralycraft.fcsv.torrentinterfaces.AvailableClients;
import com.kiralycraft.fcsv.torrentinterfaces.GenericClientInterface;
import com.kiralycraft.fcsv.torrentinterfaces.RUTorrentInterface;
import com.kiralycraft.fcsv.torrentinterfaces.TransmissionInterface;

public class Main 
{

	public static void main(String[] args) 
	{
		if (args.length>=1)
		{
			if (args[0].equalsIgnoreCase("debug"))
			{
				Logger.log("ENABLING DEBUG MODE. PROXIED TO 127.0.0.1:8888, NO CERTIFICATE VALIDATION.");
				disableCertificateValidation();
				System.setProperty("http.proxyHost", "127.0.0.1");
			    System.setProperty("https.proxyHost", "127.0.0.1");
			    System.setProperty("http.proxyPort", "8888");
			    System.setProperty("https.proxyPort", "8888");
				System.setProperty( "sun.security.ssl.allowUnsafeRenegotiation", "true" );
			}
		}
//		disableCertificateValidation();
//		System.setProperty("http.proxyHost", "127.0.0.1");
//	    System.setProperty("https.proxyHost", "127.0.0.1");
//	    System.setProperty("http.proxyPort", "8888");
//	    System.setProperty("https.proxyPort", "8888");
//	    System.setProperty("https.cipherSuites","TLS_RSA_WITH_AES_256_CBC_SHA");
//		System.setProperty("https.protocols", "TLSv1.2");
//		System.setProperty( "sun.security.ssl.allowUnsafeRenegotiation", "true" );
	    new Main();
	}
	public static void disableCertificateValidation() 
	{
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { 
				new X509TrustManager() {
					public X509Certificate[] getAcceptedIssuers() { 
						return new X509Certificate[0]; 
					}
					public void checkClientTrusted(X509Certificate[] certs, String authType) {}
					public void checkServerTrusted(X509Certificate[] certs, String authType) {}
				}};

		// Ignore differences between given hostname and certificate hostname
		HostnameVerifier hv = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) { return true; }
		};

		// Install the all-trusting trust manager
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier(hv);
		} catch (Exception e) {}
	}
	public Main()
	{
		SaveManager saveman = new SaveManager();
		if (saveman.getKey("init").equals("true"))
		{
			performLaunch(saveman);
		}
		else
		{
			performInit(saveman);
		}
	}
	private void performLaunch(SaveManager saveman) 
	{
		String transmissionip = saveman.getKey("transmissionip");
		String transmissionuser = saveman.getKey("transmissionuser");
		String transmissionpassword = saveman.getKey("transmissionpassword");
		String usernamepassword = saveman.getKey("usernamepassword");
		String filelistusername = saveman.getKey("filelistusername");
		String filelistpassword = saveman.getKey("filelistpassword");
		String cfduid = saveman.getKey("cfduid");
		String phpsessionid = saveman.getKey("phpsessionid");
		String uid = saveman.getKey("uid");
		String pass = saveman.getKey("pass");
		String fl = saveman.getKey("fl");
		String freelechOnly = saveman.getKey("freelechOnly");
		String seedLeechRatio = saveman.getKey("seedLeechRatio");
		String downloadFolder = saveman.getKey("downloadFolder");
		String softQuotaBytesString = saveman.getKey("softQuotaBytes");
		
		AvailableClients client;
		try
		{
			client = AvailableClients.valueOf(saveman.getKey("chosenClient"));
		}
		catch(Exception e)
		{
			Logger.log("Whoops! Nu ai specificat tipul clientului de torrente, sau nu este unul valid. Te rugam sa reconfigurezi aplicatia.");
			return;
		}
		
		long softQuotaBytes = -1;
		if (!softQuotaBytesString.equals("null"))
		{
			softQuotaBytes = Long.parseLong(softQuotaBytesString);
		}
		
		
		Logger.log("You can type \"help\" to see various commands.");
		
//		Connection connection = new Connection(transmissionuser,transmissionpassword,transmissionip);
		GenericClientInterface gci = null; 
		if (client.equals(AvailableClients.Transmission))
		{
			gci = new TransmissionInterface(transmissionuser,transmissionpassword,transmissionip);
		}
		else if (client.equals(AvailableClients.ruTorrent))
		{
			gci = new RUTorrentInterface(transmissionuser,transmissionpassword,transmissionip);
		}
		
		
		RunThread runThread = new RunThread(Boolean.parseBoolean(usernamepassword),filelistusername,filelistpassword,cfduid,phpsessionid,pass,uid,fl,downloadFolder,freelechOnly,seedLeechRatio,saveman,gci,softQuotaBytes);
		runThread.start();
		
		Scanner scan = new Scanner(System.in);
		String lastScan = "";
		while (!lastScan.toLowerCase().equals("exit"))
		{
			lastScan = scan.nextLine();
			Logger.log("Nu-i nici o comanda inca.");
		}
		scan.close();
		System.exit(0);
	}
	private void performInit(SaveManager saveman) 
	{
		Scanner scan = new Scanner(System.in);
		Logger.log("Bine ai venit, vom face cativa pasi pentru a configura aceasta aplicatie. Introdu ce ti se cere, apoi apasa ENTER.");
		Logger.log("");
		Logger.log("Prima data vom avea nevoie de adresa IP a clientului de torrente. Ex: http://a.com:6882.");
		Logger.log("Daca e server local, foloseste http://localhost:<port>");
		String ip = scan.nextLine();
		Logger.log("Introdu username-ul cu care te loghezi pe serverul de clientul de torrente.");
		String userStr = scan.nextLine();
		Logger.log("Oblig utilizatorii sa isi puna username si parola pentru a spori securitatea.");
		Console console = System.console();
		Logger.log("Acum introdu parola clientului de torrente (nu va aparea pe ecran din motive de securitate)");
		
		String passwordStr = readPassowrd(console,scan);
		
		Logger.log("Ce tip de client este?");
		Logger.log("Pana acum aplicatia suporta:");
		int indx = 1;
		for (AvailableClients s:AvailableClients.values())
		{
			Logger.log(" ("+indx+") "+s);
			indx++;
		}
		String clientType = scan.nextLine();
		AvailableClients chosen = AvailableClients.values()[Integer.parseInt(clientType)-1];
		saveman.setKey("chosenClient", chosen.toString());
		
		Logger.log("Doresti sa te loghezi pe Filelist.ro cu username si parola, sau cookies din browser? <U/c>");
		String choice = scan.nextLine();
		boolean usernamePassowrd = true;
		if (choice.startsWith("c"))
		{
			usernamePassowrd = false;
		}
		
		String flUsername = "null";
		String flPassword = "null";
		
		
		String cfduid = "null";
		String phpsessionid = "null";
		String uid = "null";
		String pass = "null";
		String fl = "null";
		if (usernamePassowrd)
		{
			Logger.log("Introdu username-ul cu care te loghezi pe Filelist.ro");
			flUsername = scan.nextLine();
			Logger.log("Introdu parola cu care te loghezi pe Filelist.ro (nu va aparea pe ecran din motive de securitate)");
			flPassword = readPassowrd(console,scan);
		}
		else
		{
			Logger.log("Pentru a afla ce reprezinta urmatoarele campuri, te rugam sa urmaresti tutorialul de pe YouTube: https://youtu.be/Zs5WOmdKiVo?t=1133 ");
			Logger.log("Introdu CFDUID:");
			cfduid = scan.nextLine();
			Logger.log("Introdu PHPSessionID:");
			phpsessionid = scan.nextLine();
			Logger.log("Introdu UID:");
			uid = scan.nextLine();
			Logger.log("Introdu pass (nu stiu de ce ii zice asa dar e un cookie):");
			pass = scan.nextLine();
			Logger.log("Introdu fl:");
			fl = scan.nextLine();
		}
		
		Logger.log("Doresti sa descarci doar torrente freelech? <Y/n>");
		choice = scan.nextLine();
		boolean freelechOnly = true;
		if (choice.startsWith("n"))
		{
			freelechOnly = false;
		}
		Logger.log("Te rugam sa introduci SeedLeechRatio. Daca nu stii ce e, apasa ENTER fara sa scrii nimic si ne ocupam noi de restul.");
		float seedLeechRatio = 0.6f;
		String seedLeechRatioStr = scan.nextLine();
		if (seedLeechRatioStr.length()>0)
		{
			seedLeechRatio = Float.parseFloat(seedLeechRatioStr);
		}
		
		Logger.log("Te rugam sa introduci calea completa a folderului unde se vor descarca fisierele .torrent . Nu lasa acest camp gol!");
		String downloadFolder = scan.nextLine();
		
		Logger.log("Doresti sa activezi soft quota? Aceasta optiune limiteaza spatiul folosit de catre clientul de torrente. Daca da, te rugam sa introduci dimensiunea in MB");
		String softQuotaBytes = scan.nextLine();
		if (softQuotaBytes.length()>0)
		{
			saveman.setKey("softQuotaBytes", Long.parseLong(softQuotaBytes)*1000*1000+"");
		}
		else
		{
			saveman.setKey("softQuotaBytes", "null");
		}
		
	
		saveman.setKey("init", "true");
		saveman.setKey("transmissionip", ip);
		saveman.setKey("transmissionuser", userStr);
		saveman.setKey("transmissionpassword", passwordStr);
		saveman.setKey("usernamepassword", usernamePassowrd+"");
		saveman.setKey("filelistusername", flUsername+"");
		saveman.setKey("filelistpassword", flPassword+"");
		saveman.setKey("cfduid", cfduid+"");
		saveman.setKey("phpsessionid", phpsessionid+"");
		saveman.setKey("uid", uid+"");
		saveman.setKey("pass", pass+"");
		saveman.setKey("fl", fl+"");
		saveman.setKey("freelechOnly", freelechOnly+"");
		saveman.setKey("seedLeechRatio", seedLeechRatio+"");
		saveman.setKey("downloadFolder", downloadFolder+"");
		
		Logger.log("Configurare completa! Te rugam sa repornesti aplicatia.");
	}
	private String readPassowrd(Console console,Scanner scan) 
	{
		String passwordStr = "null";
		if (console != null)
		{
			
			char[] password = console.readPassword();
			passwordStr = new String(password);
		}
		else
		{
			Logger.log("Nu am reusit sa iti ascundem parola. Va fi vizibila cand o scrii, ai grija.");
			passwordStr = scan.nextLine();
		}
		return passwordStr;
	}
}
