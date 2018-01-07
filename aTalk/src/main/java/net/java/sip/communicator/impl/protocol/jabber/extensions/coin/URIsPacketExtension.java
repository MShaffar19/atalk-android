/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

/**
 * URIs packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class URIsPacketExtension extends AbstractPacketExtension
{
	/**
	 * The name of the element that contains the URIs data.
	 */
	public static final String ELEMENT_NAME = "uris";

	/**
	 * The namespace that URIs belongs to.
	 */
	public static final String NAMESPACE = "";

	/**
	 * Constructor.
	 */
	public URIsPacketExtension()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}
}
