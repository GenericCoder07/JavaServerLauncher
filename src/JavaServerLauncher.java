import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class JavaServerLauncher extends JFrame {

    private JTextArea console;
    private JLabel status;
    private JButton browseBtn, startBtn, stopBtn, refreshBtn;
    private JCheckBox tunnelCheck;
    private File selectedJavaFile;
    private Process serverProcess;

    public JavaServerLauncher() {
    	
    	System.out.println(new File("UMpyRServer.png").getAbsolutePath());
    	
    	try
		{
			this.setIconImage(ImageIO.read(new File("UMpyRServer.png")));
		} catch (IOException e)
		{
			e.printStackTrace();
		}
        setTitle("Java Server Launcher (WSL)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 500);
        setLayout(new BorderLayout());

        addWindowListener(new WindowListener() {

			public void windowOpened(WindowEvent e)
			{
			}

			public void windowClosing(WindowEvent e)
			{
				stopServer();
			}

			public void windowClosed(WindowEvent e)
			{
			}

			public void windowIconified(WindowEvent e)
			{
			}

			public void windowDeiconified(WindowEvent e)
			{
			}

			public void windowActivated(WindowEvent e)
			{
			}

			public void windowDeactivated(WindowEvent e)
			{
			}
        	
        });
        
        console = new JTextArea();
        console.setEditable(false);
        status = new JLabel("Disconnected");
        status.setForeground(Color.RED);
        status.setFont(new Font("Consolas", Font.BOLD, 16));
        status.setHorizontalAlignment(JLabel.CENTER);
        status.setVerticalAlignment(JLabel.CENTER);
        JScrollPane scroll = new JScrollPane(console);
        add(scroll, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        browseBtn = new JButton("Select .java File");
        startBtn = new JButton("Start Server");
        stopBtn = new JButton("Stop Server");
        refreshBtn = new JButton("Refresh Server");
        tunnelCheck = new JCheckBox("Start Cloudflare tunnel");
        tunnelCheck.setSelected(true);

        controlPanel.add(status);
        controlPanel.add(browseBtn);
        controlPanel.add(startBtn);
        controlPanel.add(stopBtn);
        controlPanel.add(refreshBtn);
        controlPanel.add(tunnelCheck);
        add(controlPanel, BorderLayout.NORTH);

        browseBtn.addActionListener(e -> chooseJavaFile());
        startBtn.addActionListener(e -> launchServer());
        stopBtn.addActionListener(e -> stopServer());
        refreshBtn.addActionListener(e -> refreshServer());

        setVisible(true);
    }

    private void refreshServer() {
        if (selectedJavaFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a .java file first.");
            return;
        }

        try {
            if (serverProcess != null && serverProcess.isAlive()) {
                serverProcess.destroy();
                console.append("Java server stopped for refresh.\n");
            }
        	setStatus("Disconnected", Color.RED);

            // Stop Cloudflare tunnel (if running)
            if (tunnelCheck.isSelected()) {
                runCommand("wsl", "pkill", "-f", "cloudflared");
                console.append("Cloudflare tunnel stopped for refresh.\n");
            }

            File dir = selectedJavaFile.getParentFile();
            String fileName = selectedJavaFile.getName();
            String className = fileName.substring(0, fileName.lastIndexOf('.'));
            String wslPath = dir.getAbsolutePath().replace("\\", "/").replace("C:", "/mnt/c");

            // Recompile the Java file
            console.append("Recompiling " + fileName + "...\n");
            String compileCmd = "cd \"" + wslPath + "\" && javac \"" + fileName + "\"";
            runCommand("wsl", "bash", "-c", compileCmd);

            Thread.sleep(800);

        	setStatus("Connecting...", Color.ORANGE);
            // Launch the Java server
            console.append("Restarting Java server: " + className + "\n");
            String runCmd = "cd \"" + wslPath + "\" && java " + className;
            serverProcess = runCommand("wsl", "bash", "-c", runCmd);

            // Restart the Cloudflare tunnel
            if (tunnelCheck.isSelected()) {
                console.append("Restarting Cloudflare tunnel...\n");
                runCommand("wsl", "cloudflared", "tunnel", "run", "umpyr-tunnel");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            console.append("Refresh error: " + ex.getMessage() + "\n");
        }
    }
    
    private void setStatus(String Status, Color color)
    {
    	status.setText(Status);
    	status.setForeground(color);
    	status.repaint();
    }

	private void chooseJavaFile() {

    	File config = new File("config");
    	
    	String configContents = "";
    	if(config.exists())
    	{
    		try
			{
    			BufferedReader read = new BufferedReader(new FileReader(config));
				String temp = read.readLine();
				while(temp != null)
				{
					configContents += temp + "\n";
					temp = read.readLine();
				}
				
				read.close();
			} catch (IOException e)
			{
			}
    	}
    	
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Java File");
        
        if(!configContents.isEmpty())
        	chooser.setSelectedFile(new File(configContents.split("\n")[0]));
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedJavaFile = chooser.getSelectedFile();
            console.append("Selected: " + selectedJavaFile.getAbsolutePath() + "\n");
        }
    }

    private void launchServer() {
        if (selectedJavaFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a .java file first.");
            return;
        }

        try {
            
            File dir = selectedJavaFile.getParentFile();
            String fileName = selectedJavaFile.getName();
            String className = fileName.substring(0, fileName.lastIndexOf('.'));
            String wslPath = dir.getAbsolutePath().replace("\\", "/").replace("C:", "/mnt/c");

        	setStatus("Connecting...", Color.ORANGE);
            console.append("Compiling " + fileName + "...\n");
            String compileCmd = "cd \"" + wslPath + "\" && javac " + fileName;
            runCommand("wsl", "bash", "-c", compileCmd);

            console.append("Launching Java server: " + className + "\n");
            String runCmd = "cd \"" + wslPath + "\" && java " + className;
            
            System.out.println(runCmd);
            
            serverProcess = runCommand("wsl", "bash", "-c", runCmd);
			if (tunnelCheck.isSelected()) 
			{
				console.append("Starting Cloudflare tunnel...\n");
			    runCommand("wsl", "cloudflared", "tunnel", "run", "umpyr-tunnel");
			}

        } catch (Exception ex) {
            ex.printStackTrace();
            console.append("Error: " + ex.getMessage() + "\n");
        }
    }

    private void stopServer() {
    	setStatus("Stopping...", Color.ORANGE);
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            console.append("Stopping server...\n");
        } else {
            console.append("No server is currently running.\n");
        }

        // Stop Cloudflare tunnel if running
        try {
            runCommand("wsl", "pkill", "-f", "cloudflared");
            console.append("Cloudflare tunnel stopped.\n");
        } catch (IOException e) {
            console.append("Failed to stop Cloudflare tunnel.\n");
        }
    	setStatus("Disconnected", Color.RED);
    }

    private Process runCommand(String... command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        streamOutput(process);
        return process;
    }

    private void streamOutput(Process process) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String log = line;
                    if(log.contains("INF Registered tunnel connection connIndex=3"))
                    	setStatus("Connected", Color.GREEN);
                    SwingUtilities.invokeLater(() -> console.append(log + "\n"));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> console.append("Error reading output: " + e.getMessage() + "\n"));
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(JavaServerLauncher::new);
    }
}
