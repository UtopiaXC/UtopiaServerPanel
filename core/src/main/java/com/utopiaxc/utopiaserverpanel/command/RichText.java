package com.utopiaxc.utopiaserverpanel.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Platform-independent rich styled message composed of formatted text segments.
 * Adapters translate this structure into Minecraft's version-specific {@code Component} /
 * {@code IChatComponent} / Bukkit components.
 */
public final class RichText {
    public enum Color {
        DEFAULT,
        GREEN,
        YELLOW,
        RED,
        GRAY,
        AQUA
    }

    public static final class Segment {
        private final String text;
        private final Color color;
        private final boolean bold;
        private final boolean underlined;
        private final String copyValue;

        public Segment(String text, Color color, boolean bold, boolean underlined, String copyValue) {
            this.text = text != null ? text : "";
            this.color = color != null ? color : Color.DEFAULT;
            this.bold = bold;
            this.underlined = underlined;
            this.copyValue = copyValue;
        }

        public String getText() { return text; }
        public Color getColor() { return color; }
        public boolean isBold() { return bold; }
        public boolean isUnderlined() { return underlined; }
        public String getCopyValue() { return copyValue; }
    }

    private final List<Segment> segments = new ArrayList<Segment>();

    public RichText append(String text) {
        return append(text, Color.DEFAULT, false, false, null);
    }

    public RichText append(String text, Color color) {
        return append(text, color, false, false, null);
    }

    public RichText appendCopyable(String value) {
        return append(value, Color.YELLOW, true, true, value);
    }

    public RichText append(String text, Color color, boolean bold, boolean underlined, String copyValue) {
        segments.add(new Segment(text, color, bold, underlined, copyValue));
        return this;
    }

    public List<Segment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        for (Segment seg : segments) {
            sb.append(seg.getText());
        }
        return sb.toString();
    }
}

