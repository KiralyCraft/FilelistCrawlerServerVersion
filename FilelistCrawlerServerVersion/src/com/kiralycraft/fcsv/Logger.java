package com.kiralycraft.fcsv;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger 
{
	public static void log(String s)
	{
		System.out.println(getHour() +" "+ s);
	}
	private static String getHour()
	{
		Date date=new Date();    
		return "["+new SimpleDateFormat("HH:mm").format(date)+"]";
	}
}
