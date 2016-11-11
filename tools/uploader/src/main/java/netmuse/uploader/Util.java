package netmuse.uploader;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Date;

public class Util
{
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_BLACK = "\u001B[30m";
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_YELLOW = "\u001B[33m";
	public static final String ANSI_BLUE = "\u001B[34m";
	public static final String ANSI_PURPLE = "\u001B[35m";
	public static final String ANSI_CYAN = "\u001B[36m";
	public static final String ANSI_WHITE = "\u001B[37m";

	private static PrintStream logger;

	static
	{
		try
		{
			logger = new PrintStream(System.out, true, "UTF-8");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public static PrintStream getStream()
	{
		return logger;
	}

	@SuppressWarnings("deprecation")
	public static void log(String format, Object ... args)
	{
		Date dt = new Date();
		logger.print(String.format("[%02d-%02d %02d:%02d.%02d] ",
				dt.getMonth()+1, dt.getDay(), dt.getHours(), dt.getMinutes(), dt.getSeconds()));
		logger.println(args.length == 0 ? format : String.format(format, args));
	}

	public static void print(String format, Object ... args)
	{
		logger.print(args.length == 0 ? format : String.format(format, args));
	}

	public static void println(String format, Object ... args)
	{
		logger.print(args.length == 0 ? format : String.format(format, args));
		logger.print("\n");
	}

	public static void d(String format, Object ... args)
	{
		println(args.length == 0 ? format : String.format(format, args));
	}

	public static void e(String format, Object ... args)
	{
		println(args.length == 0 ? format : String.format(format, args));
	}

	public static boolean isNullOrEmpty(String s)
	{
		return (s == null || s.length() == 0);
	}

	public static int parseInt(String num, int deflt)
	{
		try
		{
			return Integer.parseInt(num);
		}
		catch(Exception ex)
		{
			return deflt;
		}
	}

	public static float parseFloat(String num, float deflt)
	{
		try
		{
			return Float.parseFloat(num);
		}
		catch(Exception ex)
		{
			return deflt;
		}
	}

	public static String getMD5Sum(byte[] data) throws Exception
	{
		MessageDigest digest = MessageDigest.getInstance("MD5");
		digest.update(data);
		return DatatypeConverter.printHexBinary(digest.digest()).toLowerCase();
	}

	public static String getMD5Sum(File file) throws Exception
	{
		MessageDigest digest = MessageDigest.getInstance("MD5");
		FileInputStream is = new FileInputStream(file);
		byte[] buffer = new byte[8192];

		int read;
		while ((read = is.read(buffer)) > 0)
		{
			digest.update(buffer, 0, read);
		}
		is.close();

		return DatatypeConverter.printHexBinary(digest.digest()).toLowerCase();
	}
}
