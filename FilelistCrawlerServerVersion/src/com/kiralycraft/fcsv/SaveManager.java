package com.kiralycraft.fcsv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class SaveManager 
{
	static SaveManager thisInstance;
	static ArrayList<String> settingsName = new ArrayList<String>();
	static ArrayList<String> settingsValue = new ArrayList<String>();
	File savePath;
	public boolean loadFinished = false;
	boolean encrypted = true;
	public SaveManager()
	{
		if (System.getenv("APPDATA")!=null && !System.getenv("APPDATA").equals("null"))
		{
			savePath = new File(System.getenv("APPDATA")+"\\filelistcrawlersettingsserver.txt");
		}
		else
		{
			savePath = new File("filelistcrawlersettingsserver.txt");
		}
			
		thisInstance=this;
		try 
		{
			if (!savePath.exists())
			{
				savePath.createNewFile();
			}
			else
			{
				BufferedReader br = new BufferedReader(new FileReader(savePath));
				String line;
				while ((line = br.readLine()) != null) 
				{
					
					if (encrypted)
					{
						line = Utils.decode(line);
					}
					String data[] = line.split("\\s+");
					if (data.length!=2)
					{
						br.close();
					}
					else
					{
						settingsName.add(data[0]);
						settingsValue.add(data[1]); //IL CITESTE CU TOT CU <SPACE> IN MEMORIE, DOAR CAND SE CERE SE INLOCUIESTE
					}
				}
				br.close();
			}
		} catch (Exception e) 
		{
			e.printStackTrace();
		}
		loadFinished = true;
	}
	
	/**
	 * Returns the value of the requested key in a String form. If it doesn't exist, it returns "null"
	 * @param str 	The name of the key to be searched
	 * @return 		The value of they key, "null" if it doesn't exist
	 */
	public String getKey(String str) 
	{
		for (String s:settingsName)
		{
			if (s.equalsIgnoreCase(str))
			{
				return settingsValue.get(settingsName.indexOf(s)).replace("<space>", " ");
			}
		}
		return "null";
	}
	/**
	 * 
	 * @param key
	 * @param value
	 */
	public synchronized void setKey(String settingNameTMP,String settingValueTMP)
	{
//		if (settingValueTMP.equals("")&&settingValueTMP.indexOf(" ")==-1)
		if (settingValueTMP.equals("")&&settingsName.indexOf(settingNameTMP)!=-1)
		{
			settingsValue.remove(settingsName.indexOf(settingNameTMP));
			settingsName.remove(settingNameTMP);
		}
		else
		{
			if (!settingsName.contains(settingNameTMP))
			{
				settingsName.add(settingNameTMP);
				settingsValue.add(settingValueTMP.replace(" ", "<space>"));
			}
			else
			{
				settingsValue.set(settingsName.indexOf(settingNameTMP), settingValueTMP.replace(" ", "<space>"));
			}
		}
		 try 
		 {
		        BufferedWriter out = new BufferedWriter(new FileWriter(savePath));
	            for (int i=0;i<settingsName.size();i++)
	            {
	            	if (encrypted)
	            	{
	            		out.write(Utils.encode(settingsName.get(i)+" "+settingsValue.get(i)));
	            	}
	            	else
	            	{
	            		out.write(settingsName.get(i)+" "+settingsValue.get(i));
	            	}
	            	out.newLine();
	            }
	            out.close();
		 } 
		 catch (IOException e) 
		 {
			System.out.println("Cannot save settings! "+e.getMessage());
			e.printStackTrace();
		 }
	}
	/**
	 * Reads and returns an ArrayList of strings, according to the ID supplied
	 * @param ID
	 * @return The ArrayList<String>
	 */
	public ArrayList<String> readList(String ID)
	{
		String tmp = getKey(ID);
		String data[] = tmp.split("\\;;");
		ArrayList<String> result = new ArrayList<String>(Arrays.asList(data));
		if (result.size()==1 && result.get(0).equals("null"))
		{
			return new ArrayList<String>();
		}
		else
		{
			return result;
		}
	}
	/**
	 * Takes an ArrayList of strings, and saves it under a common identifier.
	 * @param tmplist
	 * @param ID
	 */
	public synchronized void saveList(ArrayList<String> tmplist,String ID)
	{
		String tmp="";
		for (String s:tmplist)
		{
			tmp+=s.replace(" ", "<space>")+";;";
		}
		setKey(ID,tmp);
	}
}
