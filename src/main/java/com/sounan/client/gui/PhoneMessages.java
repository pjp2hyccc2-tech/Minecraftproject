package com.sounan.client.gui;

import com.sounan.client.ClientState;

import java.util.ArrayList;
import java.util.List;

/**
 * The phone's message threads. Old ones are there from the start; the rest arrive only when the phone finds signal at
 * the pylon — and their dates say how long you have really been gone (the calendar jumps with every capture).
 */
public final class PhoneMessages {
    /** One message: day offset from 11/3 (negative = before), "hh:mm", mine?, lang key. */
    public record Msg(int day, String time, boolean mine, String key) {}

    public record Thread(String sender, List<Msg> msgs) {
        public Msg last() {
            return msgs.get(msgs.size() - 1);
        }
    }

    private PhoneMessages() {}

    public static List<Thread> threads() {
        boolean arrived = ClientState.messagesArrived;
        int days = ClientState.days;
        List<Thread> out = new ArrayList<>();

        List<Msg> mom = new ArrayList<>();
        mom.add(new Msg(-2, "21:40", false, "mom.1"));
        mom.add(new Msg(-2, "21:52", true, "mom.2"));
        mom.add(new Msg(-1, "06:10", false, "mom.3"));
        if (arrived) {
            mom.add(new Msg(-1, "19:40", false, "mom.4"));
            mom.add(new Msg(-1, "21:15", false, "mom.5"));
            mom.add(new Msg(-1, "23:50", false, "mom.6"));
            mom.add(new Msg(0, "01:30", false, "mom.7"));
            if (days >= 1) mom.add(new Msg(1, "08:02", false, "mom.8"));
            if (days >= 9) mom.add(new Msg(9, "17:20", false, "mom.9"));
            if (days >= 70) mom.add(new Msg(70, "22:41", false, "mom.10"));
            if (days >= 1165) mom.add(new Msg(1165, "03:13", false, "mom.11"));
        }
        out.add(new Thread("phone.sounan.who.mom", mom));

        List<Msg> kenta = new ArrayList<>();
        kenta.add(new Msg(-2, "23:05", false, "kenta.1"));
        kenta.add(new Msg(-2, "23:07", true, "kenta.2"));
        kenta.add(new Msg(-2, "23:08", false, "kenta.3"));
        if (arrived) {
            kenta.add(new Msg(-1, "22:30", false, "kenta.4"));
            kenta.add(new Msg(0, "01:12", false, "kenta.5"));
            if (days >= 9) kenta.add(new Msg(8, "20:15", false, "kenta.6"));
            if (days >= 1165) kenta.add(new Msg(1100, "12:00", false, "kenta.7"));
        }
        out.add(new Thread("phone.sounan.who.kenta", kenta));

        if (arrived) {
            List<Msg> work = new ArrayList<>();
            work.add(new Msg(0, "09:31", false, "work.1"));
            if (days >= 9) work.add(new Msg(7, "10:02", false, "work.2"));
            out.add(new Thread("phone.sounan.who.work", work));
            List<Msg> unknown = new ArrayList<>();
            unknown.add(new Msg(days, ClientState.clock(), false, "unknown.1"));
            out.add(0, new Thread("phone.sounan.who.unknown", unknown));
        }
        return out;
    }

    public static String date(int day) {
        int[] d = ClientState.dateAfter(day);
        return d[1] + "/" + d[2];
    }
}
