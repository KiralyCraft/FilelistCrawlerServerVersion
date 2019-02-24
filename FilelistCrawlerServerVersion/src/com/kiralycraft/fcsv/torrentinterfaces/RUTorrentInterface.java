package com.kiralycraft.fcsv.torrentinterfaces;

import java.io.File;

import com.kiralycraft.raudiy.Connection;
import com.kiralycraft.raudiy.Pair;


public class RUTorrentInterface implements GenericClientInterface
{
	private Connection con;
	public RUTorrentInterface(String user,String password, String ip)
	{
		con = new Connection(user,password,ip);
	}
	@Override
	public int login()
	{
		return con.login();
	}

	@Override
	public float getUploadSpeed()
	{
		return con.getUploadSpeed();
	}

	@Override
	public com.kiralycraft.tapidiy.Pair<Long, String> getFreeSpaceAndDownDir()
	{
		Pair<Long,String> temp = con.getFreeSpaceAndDownDir();
		return new com.kiralycraft.tapidiy.Pair<Long, String>(temp.getKey(),temp.getValue());
	}

	@Override
	public boolean uploadNewTorrent(String downLocation, File torrent)
	{
		return con.uploadNewTorrent(downLocation, torrent);
	}

	@Override
	public boolean isLocalInstance()
	{
		return false;
	}

	@Override
	public long getDownloadingTorrentsSpaceNeeded()
	{
		return con.getDownloadingTorrentsSpaceNeeded();
	}

	@Override
	public long getUsedSpace()
	{
		return con.getUsedSpace();
	}

	@Override
	public boolean cleanup(long l, long softQuotaBytes)
	{
		return con.cleanup(l, softQuotaBytes);
	}

}
