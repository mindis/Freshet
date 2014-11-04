/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.pathirage.freshet.utils;

import au.com.bytecode.opencsv.CSVWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Wikipedia activity feed IRC channel lister. This code is extracted from Samza examples code for the purpose
 * of generating test data for Freshet.
 */
public class WikipediaActivityFeed {
    private static final Logger log = LoggerFactory.getLogger(WikipediaActivityFeed.class);
    private static final Random random = new Random();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final Map<String, Set<WikipediaFeedListener>> channelListeners;
    private final String host;
    private final int port;
    private final IRCConnection conn;
    private final String nick;

    public WikipediaActivityFeed(String host, int port) {
        this.channelListeners = new HashMap<String, Set<WikipediaFeedListener>>();
        this.host = host;
        this.port = port;
        this.nick = "freshet-bot-" + Math.abs(random.nextInt());
        this.conn = new IRCConnection(host, new int[] { port }, "", nick, nick, nick);
        this.conn.addIRCEventListener(new WikipediaActivityFeedIrcListener());
        this.conn.setEncoding("UTF-8");
        this.conn.setPong(true);
        this.conn.setColors(false);
    }

    public void start() {
        try {
            this.conn.connect();
        } catch (IOException e) {
            throw new RuntimeException("Unable to connect to " + host + ":" + port + ".", e);
        }
    }

    public void stop() {
        this.conn.interrupt();

        try {
            this.conn.join();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to shutdown IRC connection for " + host + ":" + port, e);
        }

        if (this.conn.isAlive()) {
            throw new RuntimeException("Unable to shutdown IRC connection for " + host + ":" + port);
        }
    }

    public void listen(String channel, WikipediaFeedListener listener) {
        Set<WikipediaFeedListener> listeners = channelListeners.get(channel);

        if (listeners == null) {
            listeners = new HashSet<WikipediaFeedListener>();
            channelListeners.put(channel, listeners);
            join(channel);
        }

        listeners.add(listener);
    }

    public void unlisten(String channel, WikipediaFeedListener listener) {
        Set<WikipediaFeedListener> listeners = channelListeners.get(channel);

        if (listeners == null) {
            throw new RuntimeException("Trying to unlisten to a channel that has no listeners in it.");
        } else if (!listeners.contains(listener)) {
            throw new RuntimeException("Trying to unlisten to a channel that listener is not listening to.");
        }

        listeners.remove(listener);

        if (listeners.size() == 0) {
            leave(channel);
        }
    }

    public void join(String channel) {
        conn.send("JOIN " + channel);
    }

    public void leave(String channel) {
        conn.send("PART " + channel);
    }

    public class WikipediaActivityFeedIrcListener implements IRCEventListener {
        public void onRegistered() {
            log.info("Connected");
        }

        public void onDisconnected() {
            log.info("Disconnected");
        }

        public void onError(String msg) {
            log.info("Error: " + msg);
        }

        public void onError(int num, String msg) {
            log.info("Error #" + num + ": " + msg);
        }

        public void onInvite(String chan, IRCUser u, String nickPass) {
            log.info(chan + "> " + u.getNick() + " invites " + nickPass);
        }

        public void onJoin(String chan, IRCUser u) {
            log.info(chan + "> " + u.getNick() + " joins");
        }

        public void onKick(String chan, IRCUser u, String nickPass, String msg) {
            log.info(chan + "> " + u.getNick() + " kicks " + nickPass);
        }

        public void onMode(IRCUser u, String nickPass, String mode) {
            log.info("Mode: " + u.getNick() + " sets modes " + mode + " " + nickPass);
        }

        public void onMode(String chan, IRCUser u, IRCModeParser mp) {
            log.info(chan + "> " + u.getNick() + " sets mode: " + mp.getLine());
        }

        public void onNick(IRCUser u, String nickNew) {
            log.info("Nick: " + u.getNick() + " is now known as " + nickNew);
        }

        public void onNotice(String target, IRCUser u, String msg) {
            log.info(target + "> " + u.getNick() + " (notice): " + msg);
        }

        public void onPart(String chan, IRCUser u, String msg) {
            log.info(chan + "> " + u.getNick() + " parts");
        }

        public void onPrivmsg(String chan, IRCUser u, String msg) {
            Set<WikipediaFeedListener> listeners = channelListeners.get(chan);

            if (listeners != null) {
                WikipediaFeedEvent event = new WikipediaFeedEvent(System.currentTimeMillis(), chan, u.getNick(), msg);

                for (WikipediaFeedListener listener : listeners) {
                    listener.onEvent(event);
                }
            }

            log.debug(chan + "> " + u.getNick() + ": " + msg);
        }

        public void onQuit(IRCUser u, String msg) {
            log.info("Quit: " + u.getNick());
        }

        public void onReply(int num, String value, String msg) {
            log.info("Reply #" + num + ": " + value + " " + msg);
        }

        public void onTopic(String chan, IRCUser u, String topic) {
            log.info(chan + "> " + u.getNick() + " changes topic into: " + topic);
        }

        public void onPing(String p) {
        }

        public void unknown(String a, String b, String c, String d) {
            log.warn("UNKNOWN: " + a + " " + b + " " + c + " " + d);
        }
    }

    public static interface WikipediaFeedListener {
        void onEvent(WikipediaFeedEvent event);
    }

    public static final class WikipediaFeedEvent {
        private final long time;
        private final String channel;
        private final String source;
        private final String rawEvent;

        public WikipediaFeedEvent(long time, String channel, String source, String rawEvent) {
            this.time = time;
            this.channel = channel;
            this.source = source;
            this.rawEvent = rawEvent;
        }

        public WikipediaFeedEvent(Map<String, Object> jsonObject) {
            this((Long) jsonObject.get("time"), (String) jsonObject.get("channel"), (String) jsonObject.get("source"), (String) jsonObject.get("raw"));
        }

        public long getTime() {
            return time;
        }

        public String getChannel() {
            return channel;
        }

        public String getSource() {
            return source;
        }

        public String getRawEvent() {
            return rawEvent;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((channel == null) ? 0 : channel.hashCode());
            result = prime * result + ((rawEvent == null) ? 0 : rawEvent.hashCode());
            result = prime * result + ((source == null) ? 0 : source.hashCode());
            result = prime * result + (int) (time ^ (time >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            WikipediaFeedEvent other = (WikipediaFeedEvent) obj;
            if (channel == null) {
                if (other.channel != null)
                    return false;
            } else if (!channel.equals(other.channel))
                return false;
            if (rawEvent == null) {
                if (other.rawEvent != null)
                    return false;
            } else if (!rawEvent.equals(other.rawEvent))
                return false;
            if (source == null) {
                if (other.source != null)
                    return false;
            } else if (!source.equals(other.source))
                return false;
            if (time != other.time)
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "WikipediaFeedEvent [time=" + time + ", channel=" + channel + ", source=" + source + ", rawEvent=" + rawEvent + "]";
        }

        public String toJson() {
            return toJson(this);
        }

        public static Map<String, Object> toMap(WikipediaFeedEvent event) {
            Map<String, Object> jsonObject = new HashMap<String, Object>();

            jsonObject.put("time", event.getTime());
            jsonObject.put("channel", event.getChannel());
            jsonObject.put("source", event.getSource());
            jsonObject.put("raw", event.getRawEvent());

            return jsonObject;
        }

        public static String toJson(WikipediaFeedEvent event) {
            Map<String, Object> jsonObject = toMap(event);

            try {
                return jsonMapper.writeValueAsString(jsonObject);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        public static WikipediaFeedEvent fromJson(String json) {
            try {
                return new WikipediaFeedEvent((Map<String, Object>) jsonMapper.readValue(json, Map.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class WikipediaActivitiesToCSV implements WikipediaFeedListener {
        private CSVWriter writer;

        public WikipediaActivitiesToCSV(){
            try {
                this.writer = new CSVWriter(new FileWriter("wikipedia-actvities-" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S").format(new Date()) + ".csv"), ',');
            } catch (IOException e) {
                throw new RuntimeException("Cannot create file writer.");
            }
        }

        @Override
        public void onEvent(WikipediaFeedEvent event) {
            Map<String,Object> parsedEvent = ParseWikipediaActivity.parse(event.getRawEvent());

            List<String> row = new LinkedList<String>();

            if(parsedEvent == null){
                return;
            }

            row.add(event.channel);
            row.add(event.source);
            row.add(Long.toString(event.time));
            row.add((String)parsedEvent.get("title"));
            row.add((String)parsedEvent.get("user"));
            row.add(Integer.toString((Integer)parsedEvent.get("diff-bytes")));
            row.add((String)parsedEvent.get("diff-url"));
            row.add((String)parsedEvent.get("summary"));

            Map<String,Boolean> flags = (Map<String,Boolean>)parsedEvent.get("flags");

            row.add(Boolean.toString(flags.get("is-minor")));
            row.add(Boolean.toString(flags.get("is-talk")));
            row.add(Boolean.toString(flags.get("is-bot-edit")));
            row.add(Boolean.toString(flags.get("is-new")));
            row.add(Boolean.toString(flags.get("is-unpatrolled")));
            row.add(Boolean.toString(flags.get("is-special")));
            row.add((String)parsedEvent.get("unparsed-flags"));


            writer.writeNext(row.toArray(new String[row.size()]));

            try {
                writer.flush();
            } catch (IOException e) {
                throw new RuntimeException("Cannot flush the content to CSV file.");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        WikipediaActivityFeed wikipediaActivityFeed = new WikipediaActivityFeed("irc.wikimedia.org", 6667);
        wikipediaActivityFeed.start();
        wikipediaActivityFeed.listen("#en.wikipedia", new WikipediaActivitiesToCSV());
        Thread.sleep(60000);
        wikipediaActivityFeed.stop();
    }
}
