package netmuse.uploader;

import net.iharder.FileDrop;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;


public class MainForm implements FileDrop.Listener, ApiService.Listener
{
	public static final String FRAME_TITLE = "NetMuse PC Uploader";

	private JPanel basePanel;
	private JTable tblFiles;
	private JTextField tfTitle;
	private JTextField tfArtist;
	private JTextField tfAlbum;
	private JButton btnUpdate;
	private JTextField tfFilePath;
	private JTextArea taMetaInfo;
	private JLabel lbCoverImg;
	private JPanel pnlCoverImg;

	private FileListModel m_model;
	private FileListModel.Item m_curEditItem;
	private int m_iCurrUploadItem = -1;

	public MainForm()
	{
		btnUpdate.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				onUpdateItemInfo();
			}
		});
	}

	public JPanel initComponents(JFrame frm)
	{
		m_model = new FileListModel();

		tblFiles.setModel(m_model);
		tblFiles.getSelectionModel().addListSelectionListener(new ListSelectionListener(){
			public void valueChanged(ListSelectionEvent event) {
				onTableItemSelected(m_model.getItem(tblFiles.getSelectedRow()));
			}
		});


		TableColumnModel cm = tblFiles.getColumnModel();
		cm.getColumn(0).setPreferredWidth(300);
		cm.getColumn(1).setPreferredWidth(40);
		cm.getColumn(2).setPreferredWidth(150);
		cm.getColumn(3).setPreferredWidth(100);
		cm.getColumn(4).setPreferredWidth(100);

		new FileDrop(basePanel, this);

		initMenuBar(frm);

		return basePanel;
	}

	private void initMenuBar(JFrame frm)
	{
		JMenuBar menuBar;
		JMenu menu, submenu;
		JMenuItem menuItem;

		menuBar = new JMenuBar();

		menu = new JMenu("File");
		menu.setMnemonic(KeyEvent.VK_F);
		menuBar.add(menu);

		menuItem = new JMenuItem("Import", KeyEvent.VK_I);
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, ActionEvent.CTRL_MASK));
		menuItem.getAccessibleContext().setAccessibleDescription("Browse music file to import");
		menu.add(menuItem);

		menuItem = new JMenuItem("Clear list", KeyEvent.VK_C);
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.ALT_MASK));
		menuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				onClearList();
			}
		});
		menu.add(menuItem);


		menu = new JMenu("Upload");
		menu.setMnemonic(KeyEvent.VK_U);
		menuBar.add(menu);

		menuItem = new JMenuItem("Start Upload", KeyEvent.VK_U);
		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, ActionEvent.CTRL_MASK));
		menuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				onStartUpload();
			}
		});
		menu.add(menuItem);

		// TODO

		frm.setJMenuBar(menuBar);
	}

	public void filesDropped(java.io.File[] files)
	{
		for (File file : files)
		{
			if (file.isDirectory())
				continue;

			m_model.addFile(file);
		}
	}

	public void onTableItemSelected(FileListModel.Item item)
	{
		m_curEditItem = item;

		tfFilePath.setText(item.filePath);
		tfTitle.setText(item.title);
		tfArtist.setText(item.artist);
		tfAlbum.setText(item.album);

		if (item.metaInfo != null)
		{
			taMetaInfo.setText(item.metaInfo.toString(4));
		}
		else
		{
			taMetaInfo.setText(null);
		}

		if (item.imageMimeType != null)
		{
			lbCoverImg.setText(null);

			try
			{
				ByteArrayInputStream ins = new ByteArrayInputStream(item.imageData);
				BufferedImage bi = ImageIO.read(ins);

				int srcWidth = bi.getWidth();
				int srcHeight = bi.getHeight();
				int dstWidth = pnlCoverImg.getWidth();
				int dstHeight = pnlCoverImg.getHeight();
				float srcRatio = (float)srcWidth / (float)srcHeight;
				float dstRatio = (float)dstWidth / (float)dstHeight;

				int adjWidth, adjHeight;
				if (dstRatio < srcRatio)
				{
					adjWidth = dstWidth;
					adjHeight = adjWidth * srcHeight / srcWidth;
				}
				else
				{
					adjHeight = dstHeight;
					adjWidth = adjHeight * srcWidth / srcHeight;
				}

				Image bi2 = bi.getScaledInstance(adjWidth,adjHeight,Image.SCALE_SMOOTH);
				ImageIcon ico = new ImageIcon(bi2);
				lbCoverImg.setIcon(ico);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			lbCoverImg.setIcon(null);
			lbCoverImg.setText("No Cover");
		}
	}

	private boolean checkIsUploadingThenAlert()
	{
		if (m_iCurrUploadItem >= 0)
		{
			JOptionPane.showMessageDialog(basePanel, "Currently uploading in progress", FRAME_TITLE, JOptionPane.ERROR_MESSAGE);
			return true;
		}

		return false;
	}

	private void onUpdateItemInfo()
	{
		if (checkIsUploadingThenAlert())
			return;

		if (m_curEditItem == null)
			return;

		m_curEditItem.title = tfTitle.getText().trim();
		m_curEditItem.artist = tfArtist.getText().trim();
		m_curEditItem.album = tfAlbum.getText().trim();

		tblFiles.updateUI();
	}

	private void onClearList()
	{
		if (checkIsUploadingThenAlert())
			return;

		m_curEditItem = null;
		m_model.clear();
	}

	private void onStartUpload()
	{
		if (checkIsUploadingThenAlert())
			return;

		if (m_model.getRowCount() <= 0)
		{
			JOptionPane.showMessageDialog(basePanel, "No items to upload", FRAME_TITLE, JOptionPane.ERROR_MESSAGE);
			return;
		}

		m_iCurrUploadItem = 0;
		uploadNextItem();

	}

	private void uploadNextItem()
	{
		if (m_iCurrUploadItem >= m_model.getRowCount())
		{
			m_iCurrUploadItem = -1;
			JOptionPane.showMessageDialog(basePanel, "Upload finished", FRAME_TITLE, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		FileListModel.Item item = m_model.getItem(m_iCurrUploadItem);

		try
		{
			item.uploadState = "Uploading";
			Main.getApiService().requestUploadMusic(this, item);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();

			m_iCurrUploadItem = -1;
			item.uploadState = "Request failed";
			JOptionPane.showMessageDialog(basePanel, "Upload exception: " + ex.getMessage(), FRAME_TITLE, JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public boolean onApiResult(int jobId, boolean succeeded, Object _param)
	{
		if (jobId == ApiService.JOBID_UPLOAD_MUSIC)
		{
			if (succeeded)
			{
				ApiService.UploadResult param = (ApiService.UploadResult)_param;

				// item check
				FileListModel.Item curItem = m_model.getItem(m_iCurrUploadItem);
				if (curItem.filePath.equals(param.srcPath))
				{
					if (param.state.equals("new"))
						curItem.uploadState = "Success";
					else if (param.state.equals("duplicate"))
						curItem.uploadState = "Duplicated";
					else
						curItem.uploadState = "Unknown: " + param.state;

					m_iCurrUploadItem++;
					uploadNextItem();
				}
				else
				{
					JOptionPane.showMessageDialog(basePanel, "Upload result mismatch: " + param.srcPath, FRAME_TITLE, JOptionPane.ERROR_MESSAGE);
				}
			}
			else
			{
				ApiService.ErrorInfo err = (ApiService.ErrorInfo)_param;

				FileListModel.Item curItem = m_model.getItem(m_iCurrUploadItem);
				String srcPath = err.spec.getUserVar("srcPath");
				if (curItem.filePath.equals(srcPath))
				{
					curItem.uploadState = "Failed: " + err.msg;
				}
				else
				{
					curItem.uploadState = "Mismatch: " + err.msg;
					JOptionPane.showMessageDialog(basePanel, "Upload error and mismatch: " + srcPath, FRAME_TITLE, JOptionPane.ERROR_MESSAGE);
				}
			}

			tblFiles.updateUI();
			return true;
		}

		System.out.println(String.format("ApiRes, Job=%d, s=%b, p=%s", jobId, succeeded, _param));
		return false;
	}

	public static void main()
	{
		JFrame frm = new JFrame();
		frm.setTitle(FRAME_TITLE);
		frm.setContentPane(new MainForm().initComponents(frm));
		frm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frm.setSize(800,500);
		frm.setLocationRelativeTo(null);
		frm.setVisible(true);
	}

}
