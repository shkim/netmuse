package netmuse.uploader;


import com.mpatric.mp3agic.*;
import org.json.JSONObject;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

class FileListModel implements TableModel
{
	public static class Item
	{
		public String uploadState;

		public String filePath;
		public String fileName;
		public String fileType;	// mp3
		public long fileSize;
		public String fileSizeStr;
		public String fileMD5;

		public String title;
		public String artist;
		public String album;

		public JSONObject metaInfo;

		public String imageMimeType;
		public byte[] imageData;

		public void addMeta(String key, String value)
		{
			if (value == null || value.isEmpty())
				return;

			if (metaInfo == null)
				metaInfo = new JSONObject();

			metaInfo.put(key, value);
		}

		public void addMeta(String key, long value)
		{
			if (value == 0 || value == -1)
				return;

			if (metaInfo == null)
				metaInfo = new JSONObject();

			metaInfo.put(key, value);
		}
	}

	private HashMap<String,Item> m_mapUniquePaths;
	private ArrayList<Item> m_items;

	public FileListModel()
	{
		m_items = new ArrayList<>();
		m_mapUniquePaths = new HashMap<>();
	}

	@Override
	public int getColumnCount()
	{
		return 6;
	}

	@Override
	public String getColumnName(int columnIndex)
	{
		switch(columnIndex)
		{
		case 0:
			return "File name";
		case 1:
			return "Size";

		case 2:
			return "Title";
		case 3:
			return "Artist";
		case 4:
			return "Album";

		case 5:
			return "State";
		}

		throw new RuntimeException("ERROR");
	}

	@Override
	public Class<?> getColumnClass(int columnIndex)
	{
		return String.class;
	}

	@Override
	public int getRowCount()
	{
		return m_items.size();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		Item item = m_items.get(rowIndex);

		switch (columnIndex)
		{
		case 0:
			return item.fileName;
		case 1:
			return item.fileSizeStr;
		case 2:
			return item.title;
		case 3:
			return item.artist;
		case 4:
			return item.album;

		case 5:
			return item.uploadState == null ? "Wait" : item.uploadState;
		}

		return "TODO";
	}

	@Override
	public void setValueAt(Object obj, int rowIndex, int columnIndex)
	{
		if (obj == null)
			return;	// maybe editing canceled

	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex)
	{
		return false;
	}

	@Override
	public void addTableModelListener(TableModelListener tableModelListener)
	{
	}

	@Override
	public void removeTableModelListener(TableModelListener tableModelListener)
	{
	}

	public Item getItem(int index)
	{
		return m_items.get(index);
	}


	public void clear()
	{
		m_items.clear();
	}

	public boolean addFile(File file)
	{
		String name = file.getName();
		int lastDot = name.lastIndexOf('.');
		if (lastDot <= 0)
		{
			// unknown file extension
			return false;
		}

		String path = file.getAbsolutePath();
		if (m_mapUniquePaths.containsKey(path))
		{
			// already added before
			return false;
		}

		String fileExt = name.substring(lastDot+1).toLowerCase();

		try
		{
			Item item;
			if (fileExt.equals("mp3"))
			{
				item = makeMp3Item(file);
			} else
			{
				// unsupported file type
				item = null;
			}

			if (item == null)
			{
				// make failed
				return false;
			}

			item.filePath = path;
			m_mapUniquePaths.put(path, item);
			m_items.add(item);
			System.out.println("Added: " + path);

			return true;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return false;
		}
	}

	public static final String META_TRACK = "track";
	public static final String META_ARTIST = "artist";
	public static final String META_TITLE = "title";
	public static final String META_ALBUM = "album";
	public static final String META_YEAR = "year";
	public static final String META_GENRE_CODE = "genre_code";
	public static final String META_GENRE = "genre";
	public static final String META_COMMENT = "comment";
	public static final String META_COMPOSER = "composer";
	public static final String META_PUBLISHER = "publisher";
	public static final String META_ORIGINAL_ARTIST = "original_artist";
	public static final String META_ALBUM_ARTIST = "album_artist";
	public static final String META_COPYRIGHT = "copyright";
	public static final String META_URL = "url";
	public static final String META_ENCODER = "encoder";

	public static final String META_BITRATE = "bitrate";
	public static final String META_SAMPLERATE = "samplerate";
	public static final String META_SECONDS = "seconds";
	public static final String META_ISVBR = "vbr";

	private Item makeMp3Item(File file) throws Exception
	{
		Item ret = new Item();
		ret.fileName = file.getName();
		ret.fileType = "mp3";
		ret.fileSize = file.length();
		ret.fileSizeStr = sizeToStr(ret.fileSize);

		Mp3File mp3file = new Mp3File(file);
		if (mp3file.hasId3v2Tag())
		{
			ID3v2 id3v2Tag = mp3file.getId3v2Tag();
			ret.addMeta(META_TRACK, id3v2Tag.getTrack());
			ret.addMeta(META_ARTIST, id3v2Tag.getArtist());
			ret.addMeta(META_TITLE, id3v2Tag.getTitle());
			ret.addMeta(META_ALBUM, id3v2Tag.getAlbum());
			ret.addMeta(META_YEAR, id3v2Tag.getYear());
			ret.addMeta(META_GENRE_CODE, id3v2Tag.getGenre());
			ret.addMeta(META_GENRE, id3v2Tag.getGenreDescription());
			ret.addMeta(META_COMMENT, id3v2Tag.getComment());
			ret.addMeta(META_COMPOSER, id3v2Tag.getComposer());
			ret.addMeta(META_PUBLISHER, id3v2Tag.getPublisher());
			ret.addMeta(META_ORIGINAL_ARTIST, id3v2Tag.getOriginalArtist());
			ret.addMeta(META_ALBUM_ARTIST, id3v2Tag.getAlbumArtist());
			ret.addMeta(META_COPYRIGHT, id3v2Tag.getCopyright());
			ret.addMeta(META_URL, id3v2Tag.getUrl());
			ret.addMeta(META_ENCODER, id3v2Tag.getEncoder());

			ret.imageData = id3v2Tag.getAlbumImage();
			if (ret.imageData != null)
			{
				ret.imageMimeType = id3v2Tag.getAlbumImageMimeType();
			}

			ret.title = id3v2Tag.getTitle();
			ret.artist = id3v2Tag.getArtist();
			ret.album = id3v2Tag.getAlbum();
		}
		else if (mp3file.hasId3v1Tag())
		{
			ID3v1 id3v1Tag = mp3file.getId3v1Tag();

			ret.addMeta(META_TRACK, id3v1Tag.getTrack());
			ret.addMeta(META_ARTIST, id3v1Tag.getArtist());
			ret.addMeta(META_TITLE, id3v1Tag.getTitle());
			ret.addMeta(META_ALBUM, id3v1Tag.getAlbum());
			ret.addMeta(META_YEAR, id3v1Tag.getYear());
			ret.addMeta(META_GENRE_CODE, id3v1Tag.getGenre());
			ret.addMeta(META_GENRE, id3v1Tag.getGenreDescription());
			ret.addMeta(META_COMMENT, id3v1Tag.getComment());

			ret.title = id3v1Tag.getTitle();
			ret.artist = id3v1Tag.getArtist();
			ret.album = id3v1Tag.getAlbum();
		}
		else if (mp3file.hasCustomTag())
		{
			// Unknown
		}
		else
		{
			// no id3 info
			int lastDot = ret.fileName.lastIndexOf('.');
			ret.title = ret.fileName.substring(0, lastDot);
		}

		ret.addMeta(META_SECONDS, mp3file.getLengthInSeconds());
		ret.addMeta(META_BITRATE, mp3file.getBitrate());
		ret.addMeta(META_SAMPLERATE, mp3file.getSampleRate());
		if (mp3file.isVbr())
		{
			ret.metaInfo.put(META_ISVBR, true);
		}

		return ret;
	}

	private String sizeToStr(long size)
	{
		if(size <= 0) return "0";
		final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
		int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
		return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}
}