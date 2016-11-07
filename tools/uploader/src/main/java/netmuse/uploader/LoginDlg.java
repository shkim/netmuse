package netmuse.uploader;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import javax.swing.*;


// This approach finally failed:
// It seems that JavaFX WebView can't execute the firebase javascript code properly.
// I leave this code for the future try :)
public class LoginDlg implements ApiService.Listener
{
	private JFXPanel m_jfxPanel;
	private WebView m_webView;

	public void show()
	{
		JFrame frm = new JFrame("NetMuse PC Uploader");
		frm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		m_jfxPanel = new JFXPanel();
		Platform.runLater(new Runnable() {
			public void run() {
				initWebView();
			}
		});

		frm.setContentPane(m_jfxPanel);
		frm.setSize(600,400);
		frm.setVisible(true);
	}

	@Override
	public boolean onApiResult(int jobId, boolean succeeded, Object _param)
	{
		if (!succeeded)
		{
			final String errMsg = ((ApiService.ErrorInfo)_param).msg;

			Platform.runLater(new Runnable() {
				public void run() {
					m_webView.getEngine().executeScript("hideWait()");
					showAlert("Couldn't get the login url: " + errMsg);
				}
			});
		}
		else
		{
			String uri = (String) _param;
			Util.d("uri: " + uri);
			final String url = Main.getApiService().getAbsoluteUrl(uri);

			Platform.runLater(new Runnable() {
				public void run() {
					m_webView.getEngine().load(url);
				}
			});
		}

		return true;
	}

	final public class Bridge
	{
		public void startApp(String url)
		{
			Main.getApiService().setBaseUrl(url);
			Main.getApiService().requestLoginUrl(LoginDlg.this);
		}

		public void log(String text)
		{
			System.out.println(text);
		}
	}

	private void initWebView()
	{
		m_webView = new WebView();
		final WebEngine webEngine = m_webView.getEngine();

		webEngine.getLoadWorker().stateProperty().addListener(
			new ChangeListener<Worker.State>() {
				public void changed(ObservableValue<? extends Worker.State> p, Worker.State oldState, Worker.State newState) {
					if (newState == Worker.State.SUCCEEDED) {
						JSObject win = (JSObject) webEngine.executeScript("window");
						win.setMember("app", new Bridge());

						webEngine.executeScript("console.log = function(message)\n" +
							"{\n" +
							"    app.log(message);\n" +
							"};");
					}
				}
			}
		);

		webEngine.getLoadWorker().exceptionProperty().addListener(new ChangeListener<Throwable>() {
			@Override
			public void changed(ObservableValue<? extends Throwable> ov, Throwable t, Throwable t1) {
				System.out.println("Received exception: "+t1.getMessage());
			}
		});

		webEngine.setOnAlert(event -> showAlert(event.getData()));
		webEngine.setConfirmHandler(message -> showConfirm(message));

		String url = Main.class.getResource("/start.html").toExternalForm();
		webEngine.load(url);

		m_jfxPanel.setScene(new Scene(m_webView));
	}


	private void showAlert(String message)
	{
		Dialog<Void> alert = new Dialog<>();
		alert.getDialogPane().setContentText(message);
		alert.getDialogPane().getButtonTypes().add(ButtonType.OK);
		alert.showAndWait();
	}

	private boolean showConfirm(String message)
	{
		Dialog<ButtonType> confirm = new Dialog<>();
		confirm.getDialogPane().setContentText(message);
		confirm.getDialogPane().getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
		boolean result = confirm.showAndWait().filter(ButtonType.YES::equals).isPresent();
		//System.out.println(result);

		return result ;
	}
}
