package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TerminalSignalLog {
	public static final int MAX_ENTRIES_PER_BAND = 32;

	private TerminalSignalLog() { }

	public static Entry append(CompoundTag record, SignalBand band, String type, long gameTime, long dayTime,
			String dimension, long position, int variant, int severity, boolean unread) {
		int sequence = Math.max(0, record.getIntOr(TerminalData.SIGNAL_EVENT_SEQUENCE, 0)) + 1;
		return append(record, sequence, band, type, gameTime, dayTime, dimension, position, variant, severity, unread);
	}

	public static Entry importLegacy(CompoundTag record, int legacySequence, SignalBand band, String type,
			long gameTime, long dayTime, String dimension, long position, int variant, int severity, boolean unread) {
		int sequence = legacySequence > 0 ? legacySequence
				: Math.max(0, record.getIntOr(TerminalData.SIGNAL_EVENT_SEQUENCE, 0)) + 1;
		return append(record, sequence, band, type, gameTime, dayTime, dimension, position, variant, severity, unread);
	}

	private static Entry append(CompoundTag record, int sequence, SignalBand band, String type,
			long gameTime, long dayTime, String dimension, long position, int variant, int severity, boolean unread) {
		ListTag events = record.getListOrEmpty(TerminalData.SIGNAL_EVENTS).copy();
		CompoundTag encoded = new CompoundTag();
		encoded.putInt("sequence", sequence);
		encoded.putInt("band", band.wireId());
		encoded.putString("type", bounded(type, 64));
		encoded.putLong("game_time", Math.max(0L, gameTime));
		encoded.putLong("day_time", Math.max(0L, dayTime));
		encoded.putString("dimension", bounded(dimension, 128));
		encoded.putLong("position", position);
		encoded.putInt("variant", Math.floorMod(variant, 16));
		encoded.putInt("severity", Math.clamp(severity, 0, 2));
		encoded.putBoolean("unread", unread);
		events.add(encoded);
		trim(events, band);
		record.put(TerminalData.SIGNAL_EVENTS, events);
		record.putInt(TerminalData.SIGNAL_EVENT_SEQUENCE,
				Math.max(sequence, record.getIntOr(TerminalData.SIGNAL_EVENT_SEQUENCE, 0)));
		record.putInt(TerminalData.UNREAD_SIGNAL_COUNT, unreadCount(events));
		return decode(encoded);
	}

	public static List<Entry> entries(CompoundTag record) {
		ListTag events = record.getListOrEmpty(TerminalData.SIGNAL_EVENTS);
		List<Entry> result = new ArrayList<>(events.size());
		for (int index = 0; index < events.size(); index++) result.add(decode(events.getCompoundOrEmpty(index)));
		result.sort(Comparator.comparingInt(Entry::sequence).reversed());
		return List.copyOf(result);
	}

	public static List<Entry> entries(CompoundTag record, SignalBand band) {
		return entries(record).stream().filter(entry -> entry.band() == band).toList();
	}

	/** Legacy helper retained for migration tooling; the current records page uses {@link #markAllRead}. */
	public static boolean markBandRead(CompoundTag record, SignalBand band) {
		ListTag events = record.getListOrEmpty(TerminalData.SIGNAL_EVENTS).copy();
		boolean changed = false;
		for (int index = 0; index < events.size(); index++) {
			CompoundTag entry = events.getCompoundOrEmpty(index);
			if (SignalBand.fromWire(entry.getIntOr("band", SignalBand.UNKNOWN.wireId())) != band
					|| !entry.getBooleanOr("unread", false)) continue;
			entry.putBoolean("unread", false);
			events.set(index, entry);
			changed = true;
		}
		if (changed) record.put(TerminalData.SIGNAL_EVENTS, events);
		record.putInt(TerminalData.UNREAD_SIGNAL_COUNT, unreadCount(events));
		return changed;
	}

	public static boolean markAllRead(CompoundTag record) {
		ListTag events = record.getListOrEmpty(TerminalData.SIGNAL_EVENTS).copy();
		boolean changed = false;
		for (int index = 0; index < events.size(); index++) {
			CompoundTag entry = events.getCompoundOrEmpty(index);
			if (!entry.getBooleanOr("unread", false)) continue;
			entry.putBoolean("unread", false);
			events.set(index, entry);
			changed = true;
		}
		if (changed) record.put(TerminalData.SIGNAL_EVENTS, events);
		record.putInt(TerminalData.UNREAD_SIGNAL_COUNT, 0);
		return changed;
	}

	public static int unreadCount(CompoundTag record) {
		return unreadCount(record.getListOrEmpty(TerminalData.SIGNAL_EVENTS));
	}

	public static boolean containsType(CompoundTag record, String type) {
		return entries(record).stream().anyMatch(entry -> entry.type().equals(type));
	}

	public static String clock(long dayTime) {
		return SignalClock.format(dayTime);
	}

	private static void trim(ListTag events, SignalBand band) {
		int count = 0;
		for (int index = events.size() - 1; index >= 0; index--) {
			CompoundTag entry = events.getCompoundOrEmpty(index);
			if (SignalBand.fromWire(entry.getIntOr("band", SignalBand.UNKNOWN.wireId())) != band) continue;
			if (++count > MAX_ENTRIES_PER_BAND) events.remove(index);
		}
	}

	private static int unreadCount(ListTag events) {
		int count = 0;
		for (int index = 0; index < events.size(); index++)
			if (events.getCompoundOrEmpty(index).getBooleanOr("unread", false)) count++;
		return count;
	}

	private static Entry decode(CompoundTag encoded) {
		return new Entry(Math.max(0, encoded.getIntOr("sequence", 0)),
				SignalBand.fromWire(encoded.getIntOr("band", SignalBand.UNKNOWN.wireId())),
				bounded(encoded.getStringOr("type", "unknown"), 64),
				Math.max(0L, encoded.getLongOr("game_time", 0L)),
				Math.max(0L, encoded.getLongOr("day_time", encoded.getLongOr("game_time", 0L) % 24_000L)),
				bounded(encoded.getStringOr("dimension", "minecraft:overworld"), 128),
				encoded.getLongOr("position", 0L), Math.floorMod(encoded.getIntOr("variant", 0), 16),
				Math.clamp(encoded.getIntOr("severity", 0), 0, 2), encoded.getBooleanOr("unread", false));
	}

	private static String bounded(String value, int maximum) {
		if (value == null) return "";
		return value.length() <= maximum ? value : value.substring(0, maximum);
	}

	public record Entry(int sequence, SignalBand band, String type, long gameTime, long dayTime,
			String dimension, long position, int variant, int severity, boolean unread) { }
}
