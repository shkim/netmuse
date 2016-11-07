package netmuse.uploader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class LoginDlg2 extends JDialog implements ApiService.Listener
{
	public static final String FRAME_TITLE = "NetMuse Tool Login";

	private JPanel contentPane;
	private JButton buttonOK;
	private JButton buttonCancel;
	private JTextField tfSecret;
	private JTextField tfEmail;
	private JTextField tfServer;
	private boolean m_isWaitRequestResult;

	public LoginDlg2()
	{
		setContentPane(contentPane);
		setModal(true);
		getRootPane().setDefaultButton(buttonOK);

		// dev quick test
		tfServer.setText("http://localhost:9000"); tfEmail.setText("shkim5@gmail.com"); tfSecret.setText("ur8quyv1nknb");

		buttonOK.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				onOK();
			}
		});

		buttonCancel.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				onCancel();
			}
		});

		// call onCancel() when cross is clicked
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				onCancel();
			}
		});

		// call onCancel() on ESCAPE
		contentPane.registerKeyboardAction(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				onCancel();
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
	}

	private void onOK()
	{
		if (m_isWaitRequestResult)
			return;

		String server = tfServer.getText();
		String email = tfEmail.getText();
		String secret = tfSecret.getText();
		if (server.isEmpty() || email.isEmpty() || secret.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Please fill in the all text fields.",
				FRAME_TITLE, JOptionPane.WARNING_MESSAGE);
			return;
		}

		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		m_isWaitRequestResult = true;
		Main.getApiService().setBaseUrl(server);
		Main.getApiService().requestToolLogin(this, email, secret);
	}

	@Override
	public boolean onApiResult(int jobId, boolean succeeded, Object _param)
	{
		m_isWaitRequestResult = false;
		setCursor(Cursor.getDefaultCursor());

		if (jobId == ApiService.JOBID_LOGIN_TOOL)
		{
			if (succeeded)
			{
				dispose();
				MainForm.main();
			}
			else
			{
				String msg = ((ApiService.ErrorInfo)_param).msg;
				JOptionPane.showMessageDialog(this, msg, FRAME_TITLE, JOptionPane.ERROR_MESSAGE);
			}
		}

		return true;
	}

	private void onCancel()
	{
		dispose();
		System.exit(0);
	}

	public static void main()
	{
		LoginDlg2 dialog = new LoginDlg2();
		dialog.setTitle(FRAME_TITLE);
		dialog.setSize(300,150);
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
	}

}
