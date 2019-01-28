package com.kiralycraft.fcsv.torrentinterfaces;

import java.io.File;

import com.kiralycraft.tapidiy.Connection;
import com.kiralycraft.tapidiy.Pair;

public class TransmissionInterface implements GenericClientInterface
{
	private Connection connection;
	public TransmissionInterface(String user,String password, String ip)
	{
		this.connection = new Connection(user,password,ip);
	}
	@Override
	public int login()
	{
		return connection.login();
	}

	@Override
	public float getUploadSpeed()
	{
		return connection.getUploadSpeed();
	}

	@Override
	public Pair<Long, String> getFreeSpaceAndDownDir()
	{
		return connection.getFreeSpaceAndDownDir();
	}

	@Override
	public boolean uploadNewTorrent(String downLocation, File torrent)
	{
		return connection.uploadNewTorrent(downLocation, torrent);
	}

	@Override
	public boolean isLocalInstance()
	{
		return connection.isLocalInstance();
	}

	@Override
	public long getDownloadingTorrentsSpaceNeeded()
	{
		return connection.getDownloadingTorrentsSpaceNeeded();
	}

	@Override
	public long getUsedSpace()
	{
		return connection.getUsedSpace();
	}

	@Override
	public boolean cleanup(long l, long softQuotaBytes)
	{
		return connection.cleanup(l, softQuotaBytes);
	}

}
