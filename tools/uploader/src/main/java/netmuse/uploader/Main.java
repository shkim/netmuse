package netmuse.uploader;

import javax.swing.*;
import java.io.FileNotFoundException;

public class Main
{
	private static Main s_this;

	private HttpMan m_httpMan;
	private ApiService m_apiService;

	public static HttpMan getHttpManager()
	{
		return s_this.m_httpMan;
	}

	public static ApiService getApiService()
	{
		return s_this.m_apiService;
	}

	public static void main(String[] args) throws FileNotFoundException
	{
		s_this = new Main();
		s_this.m_httpMan = new HttpMan();
		s_this.m_apiService = new ApiService();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new LoginDlg2().main();
				//new MainForm().main();
			}
		});
	}
}
