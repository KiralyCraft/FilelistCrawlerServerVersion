package com.kiralycraft.radiy;

public class Connection
{
	private String username,password;
	private boolean usernameRequired;
	private String ip;
	private int port;
	public Connection(String ip,int port)
	{
		this.ip = ip;
		this.port = port;
		this.usernameRequired = false;
	}
	public Connection(String ip,int port,String username,String password)
	{
		this.ip = ip;
		this.port = port;
		this.usernameRequired = true;
		this.username = username;
		this.password = password;
	}
	
	public int login()
	{
		if (usernameRequired)
		{
			//TODO
			return 0;
		}
		else
		{
			return 1;
		}
	}
	
	public float getUploadSpeed()
	{
		return 0;
	}
}
