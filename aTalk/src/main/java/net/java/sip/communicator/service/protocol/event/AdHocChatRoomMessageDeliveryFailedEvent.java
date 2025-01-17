/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event;

import java.util.*;

import net.java.sip.communicator.service.protocol.*;

/**
 * <code>AdHocChatRoomMessageDeliveredEvent</code>s confirm successful delivery of an instant message.
 *
 * @author Valentin Martinet
 */
@SuppressWarnings("serial")
public class AdHocChatRoomMessageDeliveryFailedEvent extends EventObject
{
	/**
	 * The ad-hoc chat room participant that this message has been sent to.
	 */
	private Contact to = null;

	/**
	 * Set when no other error code can describe the exception that occurred.
	 */
	public static final int UNKNOWN_ERROR = 1;

	/**
	 * Set when delivery fails due to a failure in network communications or a transport error.
	 */
	public static final int NETWORK_FAILURE = 2;

	/**
	 * Set to indicate that delivery has failed because the provider was not registered.
	 */
	public static final int PROVIDER_NOT_REGISTERED = 3;

	/**
	 * Set when delivery fails for implementation specific reasons.
	 */
	public static final int INTERNAL_ERROR = 4;

	/**
	 * Set when delivery fails because we're trying to send a message to a contact that is currently
	 * offline and the server does not support offline messages.
	 */
	public static final int OFFLINE_MESSAGES_NOT_SUPPORTED = 5;

	/**
	 * An error code indicating the reason for the failure of this delivery.
	 */
	private int errorCode = UNKNOWN_ERROR;

	/**
	 * A timestamp indicating the exact date when the event occurred.
	 */
	private Date timestamp = null;

	/**
	 * The received <code>IMessage</code>.
	 */
	private IMessage message = null;

	/**
	 * Creates a <code>AdHocChatRoomMessageDeliveryFailedEvent</code> indicating failure of delivery of
	 * a message to the specified <code>Contact</code> in the specified <code>AdHocChatRoom</code>.
	 *
	 * @param source
	 *        the <code>AdHocChatRoom</code> in which the message was sent
	 * @param to
	 *        the <code>Contact</code> that this message was sent to.
	 * @param errorCode
	 *        an errorCode indicating the reason of the failure.
	 * @param timestamp
	 *        the exact Date when it was determined that delivery had failed.
	 * @param message
	 *        the received <code>IMessage</code>.
	 */
	public AdHocChatRoomMessageDeliveryFailedEvent(AdHocChatRoom source, Contact to, int errorCode,
		Date timestamp, IMessage message)
	{
		super(source);

		this.to = to;
		this.errorCode = errorCode;
		this.timestamp = timestamp;
		this.message = message;
	}

	/**
	 * Returns a reference to the <code>Contact</code> that the source (failed) <code>IMessage</code> was
	 * sent to.
	 *
	 * @return a reference to the <code>Contact</code> that the source failed <code>IMessage</code> was sent
	 *         to.
	 */
	public Contact getDestinationParticipant()
	{
		return to;
	}

	/**
	 * Returns the received message.
	 * 
	 * @return the <code>IMessage</code> that triggered this event.
	 */
	public IMessage getMessage()
	{
		return message;
	}

	/**
	 * Returns an error code descibing the reason for the failure of the message delivery.
	 * 
	 * @return an error code descibing the reason for the failure of the message delivery.
	 */
	public int getErrorCode()
	{
		return errorCode;
	}

	/**
	 * A timestamp indicating the exact date when the event ocurred (in this case it is the moment
	 * when it was determined that message delivery has failed).
	 * 
	 * @return a Date indicating when the event ocurred.
	 */
	public Date getTimestamp()
	{
		return timestamp;
	}

	/**
	 * Returns the <code>AdHocChatRoom</code> that triggered this event.
	 * 
	 * @return the <code>AdHocChatRoom</code> that triggered this event.
	 */
	public AdHocChatRoom getSourceChatRoom()
	{
		return (AdHocChatRoom) getSource();
	}
}
