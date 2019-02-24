package com.kiralycraft.raudiy;

import java.io.File;

public class MainDebug
{

	public static void main(String[] args)
	{
		System.setProperty("http.proxyHost", "127.0.0.1");
	    System.setProperty("https.proxyHost", "127.0.0.1");
	    System.setProperty("http.proxyPort", "8888");
	    System.setProperty("https.proxyPort", "8888");
		Connection c = new Connection("OBFUSCATED","OBFUSCATED","http://OBFUSCATED");
//		c.login();
//		c.uploadNewTorrent(null, new File("toast.torrent") );
//		System.out.println(c.getFreeSpaceAndDownDir().getKey());
		c.updateTorrentList();
//		System.out.println(c.getFreeSpaceAndDownDir().getKey());
//		System.out.println(c.removeTorrent("61EDAE8BE2AF49126E6FCA085EDC5A9D31DF54E9"));
//		System.out.println(c.getFreeSpaceAndDownDir().getKey());
//		System.out.println(c.sendListCidRequest("d.get_hash=EF571B98C3026A662BDC35324626FC7DF60D101A"));
	}

}
