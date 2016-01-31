package net.zyuiop.fastffmpeg;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.controlsfx.dialog.ProgressDialog;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zyuiop
 */
public class FFMPEGConverter {
	private final String ffmpegPath;
	private final int    maxSimultaneous;
	private final Set<ConversionThread>   threads = Sets.newConcurrentHashSet();
	private final Queue<ConversionThread> waiting = new ArrayDeque<>();
	private final ExecutorService executor;

	public FFMPEGConverter(int maxSimultaneous, Stage stage) throws IOException {
		this.maxSimultaneous = maxSimultaneous;
		executor = Executors.newFixedThreadPool(maxSimultaneous);
		stage.show();

		if (SystemUtils.IS_OS_WINDOWS) {
			URL url = new URL("http://files.zyuiop.net/ffmpeg.exe");
			File ffmpeg = new File(new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile(), "ffmpeg.exe");
			ffmpegPath = ffmpeg.getAbsolutePath();

			if (!ffmpeg.exists()) {
				Task<Void> task = new Task<Void>() {
					@Override
					protected Void call() throws Exception {
						URLConnection connection = url.openConnection();
						BufferedInputStream stream = new BufferedInputStream(connection.getInputStream());
						FileOutputStream out = new FileOutputStream(ffmpeg);

						int avail = connection.getContentLength();
						int read = 0;

						try {
							final byte data[] = new byte[1024];
							int count;
							while ((count = stream.read(data, 0, 1024)) != -1) {
								out.write(data, 0, count);
								read += count;
								updateProgress((double) read, (double) avail);
							}
						} finally {
							stream.close();
							out.close();
						}

						return null;
					}
				};

				ProgressDialog dialog = new ProgressDialog(task);
				dialog.setTitle("Téléchargement de FFMpeg");
				dialog.setHeaderText("Téléchargement de FFMpeg...");
				dialog.setContentText("Veuillez patienter quelques instants");
				dialog.show();
				executor.execute(task);
			}
		} else {
			ffmpegPath = "ffmpeg";
		}
	}

	private static double getTimeSeconds(String timeString) {
		String[] parts = timeString.split(":");
		double sec = Double.parseDouble(parts[2].trim());
		if (!parts[1].equals(""))
			sec += Integer.parseInt(parts[1].trim()) * 60;
		if (!parts[0].equals(""))
			sec += Integer.parseInt(parts[0].trim()) * 3600;
		return sec;
	}

	public ConversionThread convert(String file, String output, String encoder) {
		ConversionThread thread = new ConversionThread(file, output, encoder, this);
		waiting.add(thread);
		checkNext();
		return thread;
	}

	protected void checkNext() {
		while (threads.size() < maxSimultaneous && waiting.size() > 0) {
			ConversionThread task = waiting.poll();
			if (task != null) {
				executor.execute(task);
				threads.add(task);
			}
		}
	}

	private void finishConv(ConversionThread thread) {
		threads.remove(thread);
		checkNext();
	}

	public void close() {
		waiting.clear();
		threads.forEach(ConversionThread::kill);
	}


	public static class ConversionThread extends Task<Void> {
		private       Process         process;
		private final String          path;
		private final String          output;
		private final String          encoder;
		private final FFMPEGConverter ffmpeg;

		private ConversionThread(String path, String output, String encoder, FFMPEGConverter ffmpeg) {
			this.path = path;
			this.output = output;
			this.encoder = encoder;
			this.ffmpeg = ffmpeg;
			this.updateMessage("En attente...");
			this.updateProgress(0, 1);
		}

		public void kill() {
			if (process != null)
				process.destroy();
			cancel();
		}

		@Override
		public Void call() {
			try {
				List<String> args = Lists.newArrayList(ffmpeg.ffmpegPath, "-i", path, "-y", "-strict", "-2", "-c:v", encoder, "-c:a", "aac", "-preset", "fast", output);
				System.out.println(args);
				ProcessBuilder builder = new ProcessBuilder(args);
				builder.redirectErrorStream(true);
				process = builder.start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charsets.UTF_8));

				String line;
				double sec = 0;
				this.updateMessage("Démarrage...");
				while ((line = reader.readLine()) != null) {
					if (line.contains("Duration: ")) {
						Pattern pattern = Pattern.compile("Duration: ([0-9:.]+)");
						Matcher matcher = pattern.matcher(line);
						if (matcher.find()) {
							String rawDuration = matcher.group(1);
							sec = getTimeSeconds(rawDuration);
						}
						this.updateMessage("Conversion...");
					}

					if (line.contains("time=")) {
						Pattern pattern = Pattern.compile("time=([0-9:.]+)");
						Matcher matcher = pattern.matcher(line);
						if (matcher.find()) {

							String rawDuration = matcher.group(1);
							double done = getTimeSeconds(rawDuration);

							if (sec > 0) {
								this.updateProgress(done, sec);
							}
						}
					}

					System.out.println("Converter[" + path + "] : " + line);
				}

				IOUtils.copy(reader, System.out);
				IOUtils.copy(process.getErrorStream(), System.out);
				this.updateProgress(1, 1);
				this.updateMessage("Terminé !");
			} catch (IOException e) {
				e.printStackTrace();
			}

			ffmpeg.finishConv(this);
			return null;
		}

		public String getEncoder() {
			return encoder;
		}

		public String getPath() {
			return path;
		}

		public String getOutput() {
			return output;
		}
	}
}
