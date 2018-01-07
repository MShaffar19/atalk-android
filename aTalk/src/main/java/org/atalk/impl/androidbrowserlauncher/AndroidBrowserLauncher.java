/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidbrowserlauncher;

import net.java.sip.communicator.service.browserlauncher.BrowserLauncherService;

import org.atalk.android.aTalkApp;
import org.atalk.util.Logger;

import android.content.Intent;
import android.net.Uri;

/**
 * Android implementation of <tt>BrowserLauncherService</tt>.
 *
 * @author Pawel Domas
 */
public class AndroidBrowserLauncher implements BrowserLauncherService
{
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(AndroidBrowserLauncher.class);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void openURL(String url)
	{
		try {
			Uri uri = Uri.parse(url);

			Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uri);
			launchBrowser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

			aTalkApp.getGlobalContext().startActivity(launchBrowser);
		}
		catch (Exception e) {
			logger.error("Error opening URL", e);
		}
	}
}
