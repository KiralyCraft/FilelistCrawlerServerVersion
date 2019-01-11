package com.kiralycraft.tapidiy;

public class Main {

	public static void main(String[] args) 
	{
		System.setProperty("http.proxyHost", "127.0.0.1");
	    System.setProperty("https.proxyHost", "127.0.0.1");
	    System.setProperty("http.proxyPort", "8888");
	    System.setProperty("https.proxyPort", "8888");
		new Main();
	}
	public Main()
	{
		
	}

}
//EOF