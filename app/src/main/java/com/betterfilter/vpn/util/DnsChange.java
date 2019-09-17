/*
 **Copyright (C) 2017  xfalcon
 **
 **This program is free software: you can redistribute it and/or modify
 **it under the terms of the GNU General Public License as published by
 **the Free Software Foundation, either version 3 of the License, or
 **(at your option) any later version.
 **
 **This program is distributed in the hope that it will be useful,
 **but WITHOUT ANY WARRANTY; without even the implied warranty of
 **MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 **GNU General Public License for more details.
 **
 **You should have received a copy of the GNU General Public License
 **along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **
 */

package com.betterfilter.vpn.util;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.betterfilter.Constants;

import org.xbill.DNS.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DnsChange {

    static String TAG = DnsChange.class.getSimpleName();
    static ConcurrentHashMap<String, String> DOMAINS_IP_MAPS4 = null;
    static ConcurrentHashMap<String, String> DOMAINS_IP_MAPS6 = null;


    public static ByteBuffer handle_dns_packet(Packet packet) {
        if (DOMAINS_IP_MAPS4 == null) {
            Log.d(TAG, "DOMAINS_IP_MAPS IS　NULL　HOST FILE ERROR");
            return null;
        }
        try {
            ByteBuffer packet_buffer = packet.backingBuffer;
            packet_buffer.mark();
            byte[] tmp_bytes = new byte[packet_buffer.remaining()];
            packet_buffer.get(tmp_bytes);
            packet_buffer.reset();
            Message message = new Message(tmp_bytes);
            Record question = message.getQuestion();
            ConcurrentHashMap<String, String> DOMAINS_IP_MAPS;
            int type = question.getType();
            if (type == Type.A)
                DOMAINS_IP_MAPS = DOMAINS_IP_MAPS4;
            else if (type == Type.AAAA)
                DOMAINS_IP_MAPS = DOMAINS_IP_MAPS6;
            else return null;
            Name query_domain = message.getQuestion().getName();
            String query_string = query_domain.toString();
            Log.d(TAG, "query: " + question.getType() + " :" + query_string);
            if (!DOMAINS_IP_MAPS.containsKey(query_string)) {
                query_string = "." + query_string;
                int j = 0;
                while (true) {
                    int i = query_string.indexOf(".", j);
                    if (i == -1) {
                        return null;
                    }
                    String str = query_string.substring(i);

                    if (".".equals(str) || "".equals(str)) {
                        return null;
                    }
                    if (DOMAINS_IP_MAPS.containsKey(str)) {
                        query_string = str;
                        break;
                    }
                    j = i + 1;
                }
            }
            InetAddress address = Address.getByAddress(DOMAINS_IP_MAPS.get(query_string));
            Record record;
            if (type == Type.A) record = new ARecord(query_domain, 1, 86400, address);
            else record = new AAAARecord(query_domain, 1, 86400, address);
            message.addRecord(record, 1);
            message.getHeader().setFlag(Flags.QR);
            packet_buffer.limit(packet_buffer.capacity());
            packet_buffer.put(message.toWire());
            packet_buffer.limit(packet_buffer.position());
            packet_buffer.reset();
            packet.swapSourceAndDestination();
            packet.updateUDPBuffer(packet_buffer, packet_buffer.remaining());
            packet_buffer.position(packet_buffer.limit());
            Log.d(TAG, "hit: " + question.getType() + " :" + query_domain.toString() + " :" + address.getHostName());
            return packet_buffer;
        } catch (Exception e) {
            Log.d(TAG, "dns hook error", e);
            return null;
        }

    }

    public static int handle_hosts(List<InputStream> inputStreams, @Nullable SharedPreferences sharedPreferences) {
        try {
            DOMAINS_IP_MAPS4 = new ConcurrentHashMap<>();
            DOMAINS_IP_MAPS6 = new ConcurrentHashMap<>();

            Set<String> whitelistedUrls = null;
            if (sharedPreferences != null) {
                whitelistedUrls = sharedPreferences.getStringSet(Constants.Prefs.WHITELISTED_URLS, new HashSet<>());
                Log.i(TAG, "whitelisted urls: " + whitelistedUrls);
                Set<String> blacklistedUrls = sharedPreferences.getStringSet(Constants.Prefs.BLACKLISTED_URLS, new HashSet<>());
                for (String url : blacklistedUrls){
                    Log.i(TAG, "blocking " + url);
                    if (url.contains(":")) { //ipv6
                        //urls are expected with a . at the end, according to the original author of this code, for some reason
                        DOMAINS_IP_MAPS6.put(url + ".", "0.0.0.0");
                    } else DOMAINS_IP_MAPS4.put(url + ".", "0.0.0.0");
                }
            }

            for (InputStream inputStream : inputStreams) {
                String STR_COMMENT = "#";
                String HOST_PATTERN_STR = "^\\s*(" + STR_COMMENT + "?)\\s*(\\S*)\\s*([^" + STR_COMMENT + "]*)" + STR_COMMENT + "?(.*)$";
                Pattern HOST_PATTERN = Pattern.compile(HOST_PATTERN_STR);

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;

                while (!Thread.interrupted() && (line = reader.readLine()) != null) {
                    if (line.length() > 1000 || line.startsWith(STR_COMMENT)) continue;
                    Matcher matcher = HOST_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String url = matcher.group(3).trim();
                        String ip = matcher.group(2).trim();

                        //ignore all whitelisted urls
                        if (whitelistedUrls != null && whitelistedUrls.contains(url)){
                            Log.d(TAG, "skipping " + url);
                            continue;
                        }

                        try {
                            Address.getByAddress(ip);
                        } catch (Exception e) {
                            continue;
                        }
                        if (ip.contains(":")) {
                            DOMAINS_IP_MAPS6.put(url + ".", ip);
                        } else {
                            DOMAINS_IP_MAPS4.put(url + ".", ip);
                        }
                    }
                }
                reader.close();
                inputStream.close();
            }
            Log.d(TAG, DOMAINS_IP_MAPS4.toString());
            Log.d(TAG, DOMAINS_IP_MAPS6.toString());
            Log.i(TAG, "Total blocked hosts: " + (DOMAINS_IP_MAPS4.size() + DOMAINS_IP_MAPS6.size()));
            return DOMAINS_IP_MAPS4.size() + DOMAINS_IP_MAPS6.size();
        } catch (IOException e) {
            Log.d(TAG, "Hook dns error", e);
            return 0;
        }

    }

    public static void cleanup() {
        DOMAINS_IP_MAPS6.clear();
        DOMAINS_IP_MAPS4.clear();
    }

}
