package com.kiralycraft.fcsv;
import java.util.Base64;

public class Utils 
{
	public static String encode(String s)
	{
		byte[]   bytesEncoded = Base64.getEncoder().encode(s.getBytes());
//		return new String(bytesEncoded).replace("=", "");
		return new String(bytesEncoded);
	}
	public static String decode(String s)
	{
		byte[] valueDecoded= Base64.getDecoder().decode(s );
		return new String(valueDecoded);
	}
	public static String getAppdata()
	{
		return System.getenv("APPDATA");
	}
	public static void sleep(int ms)
	{
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}
}
