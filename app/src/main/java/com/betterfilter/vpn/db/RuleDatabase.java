/* Copyright (C) 2016 - 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.betterfilter.vpn.db;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;


import androidx.annotation.Nullable;

import com.betterfilter.Constants;
import com.betterfilter.Extensions.ExtensionsKt;
import com.betterfilter.R;
import com.betterfilter.vpn.util.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents hosts that are blocked.
 * <p>
 * This is a very basic set of hosts. But it supports lock-free
 * readers with writers active at the same time, only the writers
 * having to take a lock.
 */
public class RuleDatabase {

    private static final String TAG = "RuleDatabase";
    private static final RuleDatabase instance = new RuleDatabase();
    final AtomicReference<HashSet<String>> blockedHosts = new AtomicReference<>(new HashSet<String>());
    HashSet<String> nextBlockedHosts = null;

    /**
     * Package-private constructor for instance and unit tests.
     */
    RuleDatabase() {

    }


    /**
     * Returns the instance of the rule database.
     */
    public static RuleDatabase getInstance() {
        return instance;
    }

    /**
     * Parse a single line in a hosts file
     *
     * @param line A line to parse
     * @return A host
     */
    @Nullable
    static String parseLine(String line) {
        int endOfLine = line.indexOf('#');

        if (endOfLine == -1)
            endOfLine = line.length();

        // Trim spaces
        while (endOfLine > 0 && Character.isWhitespace(line.charAt(endOfLine - 1)))
            endOfLine--;

        // The line is empty.
        if (endOfLine <= 0)
            return null;

        // Find beginning of host field
        int startOfHost = 0;

        if (line.regionMatches(0, "127.0.0.1", 0, 9) && (endOfLine <= 9 || Character.isWhitespace(line.charAt(9))))
            startOfHost += 10;
        else if (line.regionMatches(0, "::1", 0, 3) && (endOfLine <= 3 || Character.isWhitespace(line.charAt(3))))
            startOfHost += 4;
        else if (line.regionMatches(0, "0.0.0.0", 0, 7) && (endOfLine <= 7 || Character.isWhitespace(line.charAt(7))))
            startOfHost += 8;

        // Trim of space at the beginning of the host.
        while (startOfHost < endOfLine && Character.isWhitespace(line.charAt(startOfHost)))
            startOfHost++;

        // Reject lines containing a space
        for (int i = startOfHost; i < endOfLine; i++) {
            if (Character.isWhitespace(line.charAt(i)))
                return null;
        }

        if (startOfHost >= endOfLine)
            return null;

        return line.substring(startOfHost, endOfLine).toLowerCase(Locale.ENGLISH);
    }

    /**
     * Checks if a host is blocked.
     *
     * @param host A hostname
     * @return true if the host is blocked, false otherwise.
     */
    public boolean isBlocked(String host) {

        //Wildcard check
        String subURL = host;
        while (subURL.contains(".")) {
            subURL = subURL.substring(subURL.indexOf(".") + 1);
            /*
            URLs are now in the form of g.doubleclick.net, doubleclick.net, net
            Wildcard is a dot, so check if the host is blocked when taking the wildcard into account.
             */
            if (blockedHosts.get().contains("." + subURL)) return true;
        }

        return blockedHosts.get().contains(host);
    }

    /**
     * Check if any hosts are blocked
     *
     * @return true if any hosts are blocked, false otherwise.
     */
    boolean isEmpty() {
        return blockedHosts.get().isEmpty();
    }

    /**
     * Load the hosts according to the configuration
     *
     * @param context A context used for opening files.
     * @throws InterruptedException Thrown if the thread was interrupted, so we don't waste time
     *                              reading more host files than needed.
     */
    public synchronized void initialize(Context context) throws InterruptedException {
        //Configuration config = FileHelper.loadCurrentSettings(context);

        nextBlockedHosts = new HashSet<>(blockedHosts.get().size());

        Log.i(TAG, "Loading block list");

        Configuration.Item item = new Configuration.Item();
        item.title = "net_hosts";
        item.location = "net_hosts";
        item.state = Configuration.Item.STATE_DENY;


        List<String> urls = ExtensionsKt.getAllHostsUrls(PreferenceManager.getDefaultSharedPreferences(context));
        //the filename is each url's hashcode (APIClient#downloadHostsFiles), so load the files from the filename. If the file couldn't download and we don't have it, we'll catch that error.
        //Since we are getting the url's from the sharedpreferences, these are the most up-to-date files we have, in the sense of only adding enabled hosts files
        for (String url : urls) {
            String filename = "" + url.hashCode();
            try {
                loadReader(item, new FileReader(new File(context.getFilesDir(), filename)));
            } catch (Exception e) {
                Log.e(TAG, "couldn't load file " + filename);
            }
        }
        if (urls.isEmpty()) {
            //if we have no hosts files downloaded, use the built in file
            Log.i(TAG, "using built in hosts file");
            loadReader(item, new InputStreamReader(context.getResources().openRawResource(R.raw.hosts_porn)));
        }


/*        Set<String> hostsFiles = PreferenceManager.getDefaultSharedPreferences(context).getStringSet(Constants.Prefs.HOSTS_FILES, new HashSet<>());
        for (String hostsFile : hostsFiles) {
            try {
                loadReader(item, new FileReader(new File(context.getFilesDir(), hostsFile)));
            } catch (Exception e) {
                Log.e(TAG, "error loading hostsfile");
            }
        }*/

/*        if (hostsFiles.isEmpty()) {
            //if we have no hosts files downloaded, use the built in file
            Log.i(TAG, "using built in hosts file");
            loadReader(item, new InputStreamReader(context.getResources().openRawResource(R.raw.hosts_porn)));
        }*/

        Configuration.Item blacklistedItem = new Configuration.Item();
        blacklistedItem.state = Configuration.Item.STATE_DENY;

        Configuration.Item whitelistedItem = new Configuration.Item();
        whitelistedItem.state = Configuration.Item.STATE_ALLOW;

        Set<String> blacklistedHosts = PreferenceManager.getDefaultSharedPreferences(context).getStringSet(Constants.Prefs.BLACKLISTED_URLS, new HashSet<>());
        for (String black : blacklistedHosts) {
            addHost(blacklistedItem, black);
        }

        Set<String> whitelistedHosts = PreferenceManager.getDefaultSharedPreferences(context).getStringSet(Constants.Prefs.WHITELISTED_URLS, new HashSet<>());
        for (String white : whitelistedHosts) {
            addHost(whitelistedItem, white);
        }

        blockedHosts.set(nextBlockedHosts);

        Log.i(TAG, blockedHosts.get().toString());
        Runtime.getRuntime().gc();
    }

    /**
     * Loads an item. An item can be backed by a file or contain a value in the location field.
     *
     * @param context Context to open files
     * @param item    The item to load.
     * @throws InterruptedException If the thread was interrupted.
     */
    private void loadItem(Context context, Configuration.Item item) throws InterruptedException {
/*        if (item.state == Configuration.Item.STATE_IGNORE)
            return;

        InputStreamReader reader;
        try {
            reader = FileHelper.openItemFile(context, item);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "loadItem: File not found: " + item.location);
            return;
        }

        if (reader == null) {
            addHost(item, item.location);
            return;
        } else {
            loadReader(item, reader);
        }*/
    }

    /**
     * Add a single host for an item.
     *
     * @param item The item the host belongs to
     * @param host The host
     */
    private void addHost(Configuration.Item item, String host) {
        // Single address to block
        if (item.state == Configuration.Item.STATE_ALLOW) {
            nextBlockedHosts.remove(host);
        } else if (item.state == Configuration.Item.STATE_DENY) {
            nextBlockedHosts.add(host);
        }
    }

    /**
     * Load a single file
     *
     * @param item   The configuration item referencing the file
     * @param reader A reader to read lines from
     * @throws InterruptedException If thread was interrupted
     */
    boolean loadReader(Configuration.Item item, Reader reader) throws InterruptedException {
        int count = 0;
        try {
            Log.d(TAG, "loadBlockedHosts: Reading: " + item.location);
            try (BufferedReader br = new BufferedReader(reader)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (Thread.interrupted())
                        throw new InterruptedException("Interrupted");
                    String host = parseLine(line);
                    if (host != null) {
                        count += 1;
                        addHost(item, host);
                    }
                }
            }
            Log.d(TAG, "loadBlockedHosts: Loaded " + count + " hosts from " + item.location);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "loadBlockedHosts: Error while reading " + item.location + " after " + count + " items", e);
            return false;
        } finally {
            //FileHelper.closeOrWarn(reader, TAG, "loadBlockedHosts: Error closing " + item.location);
        }
    }
}
