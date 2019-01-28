package com.kiralycraft.fcsv.torrentinterfaces;

import java.io.File;

import com.kiralycraft.tapidiy.Pair;

public interface GenericClientInterface
{

	int login();

	float getUploadSpeed();

	Pair<Long,String> getFreeSpaceAndDownDir();

	boolean uploadNewTorrent(String downLocation, File torrent);

	boolean isLocalInstance();

	long getDownloadingTorrentsSpaceNeeded();

	long getUsedSpace();

	boolean cleanup(long l, long softQuotaBytes);
	
}
