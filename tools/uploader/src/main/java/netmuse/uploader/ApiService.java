package netmuse.uploader;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class ApiService implements HttpMan.OnHttpListener
{
	public interface Listener
	{
		boolean onApiResult(int jobId, boolean succeeded, Object _param);
	}

	public static class ErrorInfo
	{
		public ErrorInfo(int c, String m) { code = c; msg = m; }

		public int code;
		public String msg;
	}

	public static final int JOBID_URL_LOGIN = 10;
	public static final int JOBID_LOGIN_TOOL = 11;
	public static final int JOBID_UPLOAD_MUSIC = 20;

	private String m_apiBaseUrl;
	private String m_sessKey;

	public ApiService()
	{
	}

	public void setBaseUrl(String url)
	{
		if (url.endsWith("/"))
			m_apiBaseUrl = url.substring(0, url.length() -1);
		else
			m_apiBaseUrl = url;
	}

	private HttpMan.QuerySpec getEmptySpec(String uri)
	{
		HttpMan.QuerySpec spec = new HttpMan.QuerySpec();
		spec.setUrl(m_apiBaseUrl + uri);
		spec.requestMethod = HttpMan.REQUESTMETHOD_POST;
		spec.resultType = HttpMan.RESULT_JSON;
		return spec;
	}

	public String getAbsoluteUrl(String uri)
	{
		return (m_apiBaseUrl + uri);
	}

	private void sendRequest(Listener lsnr, int jobId, HttpMan.QuerySpec spec)
	{
		spec.userObj = lsnr;
		Main.getHttpManager().request(this, jobId, spec);
	}

	public void requestLoginUrl(Listener lsnr)
	{
		HttpMan.QuerySpec spec = getEmptySpec("/api/url/pclogin");
		spec.requestMethod = HttpMan.REQUESTMETHOD_GET;

		sendRequest(lsnr, JOBID_URL_LOGIN, spec);
	}

	private String parseLoginUrl(JSONObject json) throws JSONException
	{
		String url = json.getString("url");
		return url;
	}

	public void requestToolLogin(Listener lsnr, String email, String secret)
	{
		HttpMan.QuerySpec spec = getEmptySpec("/api/login/tool");
		spec.addParam("email", email);
		spec.addParam("secret", secret);

		sendRequest(lsnr, JOBID_LOGIN_TOOL, spec);
	}

	private String parseToolLogin(JSONObject json) throws JSONException
	{
		m_sessKey = json.getString("sesskey");
		return m_sessKey;
	}

	public void requestUploadMusic(Listener lsnr, FileListModel.Item item) throws Exception
	{
		File file = new File(item.filePath);
		if (item.fileMD5 == null)
			item.fileMD5 = Util.getMD5Sum(file);

		HttpMan.MultipartFormData mfd = new HttpMan.MultipartFormData();
		mfd.add("sesskey", m_sessKey);

		//mfd.add("filename", item.fileName);
		mfd.add("filetype", item.fileType);
		mfd.add("filesize", Long.toString(item.fileSize));
		mfd.add("filemd5", item.fileMD5);

		mfd.add("title", item.title);
		mfd.add("artist", item.artist);
		mfd.add("album", item.album);

		mfd.addFile("music", file, "audio/"+item.fileType);

		if (item.imageMimeType != null)
		{
			mfd.add("imgsize", Integer.toString(item.imageData.length));
			mfd.add("imgmd5", Util.getMD5Sum(item.imageData));
			mfd.addFile("image", "image.jpg", item.imageData, item.imageMimeType);
		}

		if (item.metaInfo != null)
		{
			mfd.add("meta", item.metaInfo.toString());
		}

		mfd.finish();

		HttpMan.QuerySpec spec = getEmptySpec("/api/upload/music");
		spec.postBody = mfd;
		sendRequest(lsnr, JOBID_UPLOAD_MUSIC, spec);
	}

	private String parseUploadMusic(JSONObject json) throws JSONException
	{
		return "TODO";
	}


	private void callAgentError(Listener lsnr, int jobId, final ErrorInfo err)
	{
		if (!lsnr.onApiResult(jobId, false, err))
		{
			Util.e("API Error: " + err.msg);
		}
	}

	@Override
	public void onHttpFail(int jobId, HttpMan.QuerySpec spec, int failHttpStatus)
	{
		Util.d("ApiHttpFail: job=%d, failCode=%d", jobId, failHttpStatus);

		Listener lsnr = (Listener) spec.userObj;
		if (lsnr == null)
			return;

		String errMsg;
		if (failHttpStatus == HttpMan.ERROR_UNKNOWN_HOST)
		{
			errMsg = "No internet connection";
		}
		else if (failHttpStatus == HttpMan.ERROR_CONNECT_TIMEOUT || failHttpStatus == HttpMan.ERROR_CONNECT_FAILED)
		{
			errMsg = "Server connection failed";
		}
		else if (failHttpStatus == HttpMan.ERROR_NETWORKIO_FAIL)
		{
			errMsg = "Network IO failed";
		}
		else
		{
			errMsg = "HTTP Failure: " + Integer.toString(failHttpStatus);
		}

		callAgentError(lsnr, jobId, new ErrorInfo(failHttpStatus, errMsg));
	}

	@Override
	public void onHttpSuccess(int jobId, HttpMan.QuerySpec spec, Object result)
	{
		Util.d("Agent.HttpSuccess: job=%d, result=%s", jobId, result);
		Listener lsnr = (Listener) spec.userObj;
		if (lsnr == null)
			return;

		if (spec.resultType != HttpMan.RESULT_JSON)
		{
			callAgentError(lsnr, jobId, new ErrorInfo(HttpMan.ERROR_RESULT_ERROR, "Invalid AgentResType " + spec.resultType));
			return;
		}

		try
		{
			JSONObject json = (JSONObject) result;

			int code = json.getInt("code");
			if (code != 0)
			{
				callAgentError(lsnr, jobId, new ErrorInfo(code, ApiService.getSafeString(json, "msg")));
				return;
			}

			JSONObject data;
			if (json.has("data"))
				data = json.getJSONObject("data");
			else
				data = null;

			Object param = null;
			switch (jobId)
			{
			case JOBID_URL_LOGIN:
				param = parseLoginUrl(data);
				break;

			case JOBID_LOGIN_TOOL:
				param = parseToolLogin(data);
				break;

			case JOBID_UPLOAD_MUSIC:
				param = parseUploadMusic(data);
				break;

			default:
				callAgentError(lsnr, jobId, new ErrorInfo(HttpMan.ERROR_RESULT_ERROR, "Unknown JobId: " + jobId));
				return;
			}

			lsnr.onApiResult(jobId, true, param);
		}
		catch(JSONException ex)
		{
			ex.printStackTrace();
			callAgentError(lsnr, jobId, new ErrorInfo(1, ex.getMessage()));
		}
	}

	public static String getSafeString(JSONObject json, String key) throws JSONException
	{
		if (json == null || !json.has(key) || json.isNull(key))
			return null;

		String ret = json.getString(key);
		return ("null".equals(ret) ? null : ret);
	}

	public static boolean getSafeBoolean(JSONObject json, String key) throws JSONException
	{
		return json.has(key) ? json.getBoolean(key) : false;
	}

	public static int getSafeInt(JSONObject json, String key) throws JSONException
	{
		if (!json.has(key) || json.isNull(key))
			return 0;

		return json.getInt(key);
	}
}
