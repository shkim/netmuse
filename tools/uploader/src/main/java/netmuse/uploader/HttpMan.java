package netmuse.uploader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpMan //implements Runnable
{
	public static final int RESULT_IGNORE = 0;
	public static final int RESULT_TEXT = 1;
	public static final int RESULT_JSON = 2;
	public static final int RESULT_JSON_ARRAY = 3;
	public static final int RESULT_BINARY = 4;
	public static final int RESULT_SAVE_TO_FILE = 5;	// Object result should have the pathname
	public static final int RESULT_SIGNAL = 6;

	public static final int REQUESTMETHOD_GET = 0;
	public static final int REQUESTMETHOD_POST = 1;
	public static final int REQUESTMETHOD_PUT = 2;
	public static final int REQUESTMETHOD_DELETE = 3;

	public static final int ERROR_UNKNOWN = -1;
	public static final int ERROR_REQUEST_FAIL = -2;
	public static final int ERROR_UNKNOWN_HOST = -3;
	public static final int ERROR_CONNECT_FAILED = -4;
	public static final int ERROR_CONNECT_TIMEOUT = -5;
	public static final int ERROR_NETWORKIO_FAIL = -6;
	public static final int ERROR_RESULT_ERROR = -9;

	private static final String SVR_ENCODING = "UTF-8";
	//private static final Charset SVR_CHARSET = Charset.forName(SVR_ENCODING);

	public interface OnHttpListener
	{
		void onHttpFail(int jobId, QuerySpec spec, int failHttpStatus);
		void onHttpSuccess(int jobId, QuerySpec spec, Object result);
	}

	public static class QuerySpec
	{
		public int port;	// can be 0 if 80
		public boolean isSecure;
		public boolean isNotifyOnNetThread;
		public int resultType;
		public int requestMethod;

		public String address;
		public String path;
		public Object postBody;
		public HashMap<String,String> params;

		public HashMap<String,String> userVars;	// userVars will not be sent to web server
		public Object userObj;

		public void setUrl(String url)
		{
			int r1 = url.indexOf("://");
			if (r1 <= 1)
			{
				throw new RuntimeException("Invalid http url: " + url);
			}

			this.isSecure = (url.charAt(r1 -1) == 's');

			String core;
			r1 += 3;
			int pathBegin = url.indexOf('/', r1);
			if (pathBegin <= 0)
			{
				// has no uri
				this.path = null;
				core = url.substring(r1);
			}
			else
			{
				this.path = url.substring(pathBegin +1);
				core = url.substring(r1, pathBegin);
			}

			int colon = core.indexOf(':');
			if (colon <= 0)
			{
				this.port = 0;	// default http port (80)
				this.address = core;
			}
			else
			{
				this.port = Integer.parseInt(core.substring(colon+1));
				this.address = core.substring(0, colon);
			}
		}

		public void addParam(String key, String val)
		{
			if (this.params == null)
				this.params = new HashMap<String,String>();

			//Util.d("addParam(%s,%s)", key, val);
			this.params.put(key, val);
		}

		public void addParam(String key, int ival)
		{
			addParam(key, Integer.toString(ival));
		}

		public void addParam(String key, float fval)
		{
			addParam(key, Float.toString(fval));
		}

		public String getParam(String key)
		{
			if (params == null)
				return null;

			return params.containsKey(key) ? params.get(key) : null;
		}

		public void addUserVar(String key, String val)
		{
			if (userVars == null)
				userVars = new HashMap<String,String>();

			userVars.put(key, val);
		}

		public String getUserVar(String key)
		{
			if (userVars == null)
				return null;

			return userVars.containsKey(key) ? userVars.get(key) : null;
		}
	}

	private static class JobItem implements Runnable
	{
		public OnHttpListener listener;
		public int jobId;
		public QuerySpec spec;
		public Object result;

		public int _errorCode;

		@Override
		public void run()
		{
			callListener(this);
		}
	}

	private ExecutorService m_tpool;

	public HttpMan()
	{
		m_tpool = Executors.newFixedThreadPool(2);
	}

	public void destroy()
	{
		try
		{
			//System.out.println("attempt to shutdown executor");
			m_tpool.shutdown();
			m_tpool.awaitTermination(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			System.err.println("tasks interrupted");
		}
		finally
		{
			if (!m_tpool.isTerminated())
			{
				System.err.println("cancel non-finished tasks");
			}

			m_tpool.shutdownNow();
			//System.out.println("shutdown finished");
		}
	}

	public void postSignal(OnHttpListener lsnr, int jobId, Object userObj, boolean notifyOnNetThread)
	{
		QuerySpec spec = new QuerySpec();
		spec.isNotifyOnNetThread = notifyOnNetThread;
		spec.userObj = userObj;
		spec.resultType = RESULT_SIGNAL;

		request(lsnr, jobId, spec);
	}

	public void request(OnHttpListener lsnr, int jobId, QuerySpec spec)
	{
		JobItem job = new JobItem();
		job.listener = lsnr;
		job.jobId = jobId;
		job.spec = spec;

		addJobToQueue(job);
	}

	public void requestDownload(OnHttpListener lsnr, int jobId, QuerySpec spec, String pathToSaveFile)
	{
		JobItem job = new JobItem();
		job.listener = lsnr;
		job.jobId = jobId;
		job.spec = spec;
		job.spec.resultType = RESULT_SAVE_TO_FILE;
		job.result = pathToSaveFile;

		addJobToQueue(job);
	}

	private void addJobToQueue(final JobItem job)
	{
		m_tpool.submit(new Runnable() {
			public void run() {
				job._errorCode = processJob(job);

				if (job.listener == null)
					return;

				if (job.spec.isNotifyOnNetThread)
				{
					callListener(job);
				}
				else
				{
					SwingUtilities.invokeLater(job);
				}
			}
		});
	}

	private static int processJob(JobItem job)
	{
		try
		{
			if (job.spec.address == null)
			{
				// empty job
				return 0;
			}

			StringBuilder ub = new StringBuilder();
			ub.append(job.spec.isSecure ? "https" : "http");
			ub.append("://");
			ub.append(job.spec.port > 0 ? (job.spec.address + ":" + job.spec.port) : job.spec.address);
			if (job.spec.path != null)
			{
				if (!job.spec.path.startsWith("/"))
					ub.append('/');
				ub.append(job.spec.path);
			}

			String requestMethod;
			String postBody = null;
			MultipartFormData mfd = null;

			if (job.spec.requestMethod == REQUESTMETHOD_GET)
			{
				requestMethod = "GET";

				if (job.spec.params != null)
				{
					int i=0;
					for (HashMap.Entry<String, String> entry : job.spec.params.entrySet())
					{
						if (++i > 1)
							ub.append('&');

						ub.append(URLEncoder.encode(entry.getKey(), SVR_ENCODING));
						ub.append('=');
						ub.append(URLEncoder.encode(entry.getValue(), SVR_ENCODING));
					}
				}
			}
			else
			{
				switch(job.spec.requestMethod)
				{
				case REQUESTMETHOD_POST:
					requestMethod = "POST";
					break;
				case REQUESTMETHOD_PUT:
					requestMethod = "PUT";
					break;
				case REQUESTMETHOD_DELETE:
					requestMethod = "DELETE";
					break;
				default:
					throw new RuntimeException("Invalid request method: " + job.spec.requestMethod);
				}

				if (job.spec.params != null && !job.spec.params.isEmpty())
				{
					StringBuilder sb = new StringBuilder();
					for (HashMap.Entry<String, String> entry : job.spec.params.entrySet())
					{
						sb.append(URLEncoder.encode(entry.getKey(), SVR_ENCODING));
						sb.append('=');
						sb.append(URLEncoder.encode(entry.getValue(), SVR_ENCODING));
						sb.append('&');
					}

					sb.deleteCharAt(sb.length() - 1);
					postBody = sb.toString();
				}
				else if (job.spec.postBody instanceof MultipartFormData)
				{
					mfd = (MultipartFormData)job.spec.postBody;
				}
				else if (job.spec.postBody != null)
				{
					postBody = job.spec.postBody.toString();
				}
			}

			URL url = new URL(ub.toString());
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod(requestMethod);
			conn.setDoInput(true);
			conn.setUseCaches(false);

			if (mfd != null)
			{
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + mfd.m_boundary);

				OutputStream os = conn.getOutputStream();
				mfd.m_baos.writeTo(os);
				os.close();
			}
			else if (postBody != null)
			{
				conn.setDoOutput(true);
				if (job.spec.postBody != null)
				{
					conn.setRequestProperty("Content-Type", "application/json");
				}

				OutputStream os = conn.getOutputStream();
				BufferedWriter br = new BufferedWriter(new OutputStreamWriter(os, SVR_ENCODING));
				br.write(postBody);
				br.flush();
				br.close();
				os.close();
			}

			conn.connect();
			int responseCode = conn.getResponseCode();
			Util.d("HttpMan(%s) RespCode:%d", url.toString(), responseCode);

			if (job.spec.resultType == RESULT_IGNORE)
			{
				job.listener = null;
				return 0;
			}

			if(responseCode != HttpURLConnection.HTTP_OK)
			{
				return ERROR_REQUEST_FAIL;
			}

			InputStream inStrm = conn.getInputStream();
			OutputStream outStrm;
			ByteArrayOutputStream ba;
			if (job.spec.resultType == RESULT_SAVE_TO_FILE)
			{
				FileOutputStream fos = new FileOutputStream((String)job.result);
				outStrm = fos;
				ba = null;
			}
			else
			{
				ba = new ByteArrayOutputStream();
				outStrm = ba;
			}

			byte[] buff = new byte[2048];

			while(true)
			{
				int read = inStrm.read(buff);
				if(read <= 0)
					break;

				outStrm.write(buff, 0, read);
			}
			outStrm.close();

			switch(job.spec.resultType)
			{
			case RESULT_TEXT:
				job.result = new String(ba.toByteArray(), SVR_ENCODING);
				break;

			case RESULT_JSON:
			case RESULT_JSON_ARRAY:
				String jsonSrc = new String(ba.toByteArray(), SVR_ENCODING);
				try
				{
					if (job.spec.resultType == RESULT_JSON)
						job.result = new JSONObject(jsonSrc);
					else
						job.result = new JSONArray(jsonSrc);
				}
				catch (JSONException jse)
				{
					Util.e("JSON parsing failed: " + jsonSrc);
					throw jse;
				}
				break;

			case RESULT_BINARY:
				job.result = ba.toByteArray();
				break;
			case RESULT_SAVE_TO_FILE:
				Util.d("HTTP stream saved to file: " + job.result);
				break;
			default:
				Util.e("Unknown resultType: %d", job.spec.resultType);
				return ERROR_UNKNOWN;
			}

			return 0;
		}
		catch(UnknownHostException uhe)
		{
			Util.e("HttpMan: Unknown host, " + uhe.getMessage());
			return ERROR_UNKNOWN_HOST;
		}
		catch (JSONException jse)
		{
			return ERROR_RESULT_ERROR;
		}
		catch (ConnectException jse)
		{
			return ERROR_CONNECT_FAILED;
		}
		catch (IOException ioe)
		{
			return ERROR_NETWORKIO_FAIL;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return ERROR_UNKNOWN;
		}
	}

	private static void callListener(JobItem job)
	{
		if (job._errorCode == 0)
		{
			job.listener.onHttpSuccess(job.jobId, job.spec, job.result);
		}
		else
		{
			if (job.spec.resultType == RESULT_SAVE_TO_FILE)
			{
				// delete file if exists
				try
				{
					File f = new File((String)job.result);
					if (f.exists())
						f.delete();
				}
				catch(Exception ex) {}
			}

			try
			{
				job.listener.onHttpFail(job.jobId, job.spec, job._errorCode);
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}



	public static class MultipartFormData
	{
		private static final byte[] CRLF = new byte[] { '\r','\n' };

		private String m_boundary;
		private ByteArrayOutputStream m_baos;
		private byte[] m_boundaryBytes;

		public MultipartFormData()
		{
			m_boundary = java.util.UUID.randomUUID().toString();
			m_baos = new ByteArrayOutputStream();

			m_boundaryBytes = ("--"+m_boundary).getBytes();
		}

		public void add(String key, String value) throws IOException
		{
			if (Util.isNullOrEmpty(value)) return;

			m_baos.write(m_boundaryBytes);

			String data = String.format("\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", key, value);
			m_baos.write(data.getBytes());
		}

		private void startFile(String key, String filename, String mimeType) throws IOException
		{
			m_baos.write(m_boundaryBytes);
			//String head = String.format("\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\nContent-Transfer-Encoding: binary\r\n\r\n", key, filename, mimeType);
			String head = String.format("\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n", key, filename, mimeType);
			m_baos.write(head.getBytes());
		}

		public void addFile(String key, String filename, byte[] file, String mimeType) throws IOException
		{
			startFile(key, filename, mimeType);
			m_baos.write(file);
			m_baos.write(CRLF);
		}

		public void addFile(String key, File file, String mimeType) throws IOException
		{
			startFile(key, file.getName(), mimeType);

			byte[] buff = new byte[2048];
			FileInputStream fis = new FileInputStream(file);
			for(;;)
			{
				int len = fis.read(buff);
				if (len <= 0)
					break;

				m_baos.write(buff, 0, len);
			}
			fis.close();
			m_baos.write(CRLF);
		}

		public void finish() throws IOException
		{
			m_baos.write(m_boundaryBytes);
			m_baos.write(m_boundaryBytes, 0, 2);
			m_baos.write(CRLF);
			m_baos.close();
		}
	}
}
