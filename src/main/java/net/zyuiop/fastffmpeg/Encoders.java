package net.zyuiop.fastffmpeg;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zyuiop
 */
public enum Encoders {
	MP4("x264", "libx264", "mp4"),
	XVID("DivX", "libxvid", "avi");

	private final String display;
	private final String codev;
	private final String fileExtension;

	private static final Map<String, Encoders> byDisplay = new HashMap<>();

	static {
		for (Encoders encoders : Encoders.values())
			byDisplay.put(encoders.getDisplay(), encoders);
	}

	Encoders(String display, String codev, String fileExtension) {

		this.display = display;
		this.codev = codev;
		this.fileExtension = fileExtension;
	}

	public String getDisplay() {
		return display;
	}

	public String getCodev() {
		return codev;
	}

	public String getFileExtension() {
		return fileExtension;
	}

	public static Encoders getByDisplay(String display) {
		return byDisplay.get(display);
	}
}
